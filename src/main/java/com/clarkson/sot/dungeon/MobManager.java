package com.clarkson.sot.dungeon;

import com.clarkson.sot.events.FloorItemManager;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.player.SoTPlayerData;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.logging.Level;

/**
 * Runs the hostile-mob encounters a segment template asked for, at its {@code MOB_SPAWNER} markers.
 *
 * <p>A marker becomes a real {@link Material#SPAWNER} block when the instance is built. The block is
 * the encounter: it starts producing mobs once a member of the owning team comes within
 * {@link #ACTIVATION_RADIUS} blocks, and it <b>keeps producing them every
 * {@link #SPAWN_INTERVAL_TICKS}</b> for as long as someone is in range. The only way to stop it is
 * to break it with a pickaxe, which pays out coins. Activation range is deliberately wider than a
 * player's reach, so the fight always starts before the spawner can be touched.
 *
 * <p>Two things keep that from running away. Each spawner holds at most
 * {@link #MAX_LIVE_MOBS_PER_SPAWNER} live mobs at a time, so ignoring one costs a bounded number of
 * entities rather than an unbounded stream; and the vanilla spawner logic inside the placed block is
 * neutralised, so every mob in the dungeon comes from this class.
 *
 * <p>Every mob is tagged in its PDC with {@link #MOB_TAG_KEY} and its owning team, the same idiom
 * {@link DungeonManager#removeBakedBuildMarkers()} uses for build markers. The tag is what separates
 * a designed encounter from the ambient mobs vanilla spawns in a dark room: only tagged mobs are
 * tracked, cleaned up, and counted toward {@link SoTPlayerData#incrementMonstersKilled()}.
 *
 * <p>Registered as a listener by {@code SoT.onEnable()} only — like its sibling managers this class
 * deliberately never registers itself (see {@code ListenerRegistrationTest}). Its repeating task is
 * owned by {@link GameManager}, started in {@code beginPlay} and stopped in {@code tearDownRound},
 * the same shape as {@code GameScoreboardManager}.
 */
public class MobManager implements Listener {

    /** PDC key (BYTE) set on every mob this manager spawns. */
    public static final String MOB_TAG_KEY = "sot_dungeon_mob";
    /** PDC key (STRING) holding the UUID of the team whose instance the mob belongs to. */
    public static final String MOB_TEAM_KEY = "sot_mob_team";

    /** The block a MOB_SPAWNER marker becomes, matching the builder tool's marker material. */
    public static final Material SPAWNER_BLOCK = Material.SPAWNER;

    /**
     * How close a player must get before a spawner starts producing mobs.
     *
     * <p>Must stay comfortably greater than a player's ~5-block reach: the encounter is meant to
     * begin before the spawner can be attacked, so clearing a room means fighting your way to the
     * block rather than walking up and breaking it.
     */
    private static final double ACTIVATION_RADIUS = 10.0;
    private static final double ACTIVATION_RADIUS_SQUARED = ACTIVATION_RADIUS * ACTIVATION_RADIUS;

    /** How often an active spawner produces a wave (8 seconds). */
    static final long SPAWN_INTERVAL_TICKS = 160L;

    /** How often the manager re-checks every spawner for players in range (1 second). */
    private static final long TICK_PERIOD_TICKS = 20L;

    /**
     * Live mobs one spawner may have out at once.
     *
     * <p>Without a cap, a team that walks past a spawner and leaves it running would accumulate mobs
     * for the rest of the round. Six matches vanilla's own nearby-entity cap and is enough to make
     * ignoring a spawner genuinely costly.
     */
    static final int MAX_LIVE_MOBS_PER_SPAWNER = 6;

    /** Depth at which the second and third difficulty bands begin. */
    private static final int MID_DEPTH = 3;
    private static final int DEEP_DEPTH = 6;

    /** Horizontal spread applied to the second and later mobs of a wave, in blocks. */
    private static final int GROUP_SPREAD = 1;

    private final Plugin plugin;
    private final GameManager gameManager;
    private final NamespacedKey mobTagKey;
    private final NamespacedKey mobTeamKey;
    private final Random random = new Random();
    /** Server tick source; injected so the tests can drive the spawn cadence deterministically. */
    private final LongSupplier clock;

    /** teamId -> that team's live spawners. A spawner is removed when broken or on teardown. */
    private final Map<UUID, List<Spawner>> spawnersByTeam = new ConcurrentHashMap<>();
    /** Block position -> spawner, so a break event resolves in one lookup. */
    private final Map<BlockKey, Spawner> spawnersByBlock = new ConcurrentHashMap<>();
    /** teamId -> UUIDs of every mob spawned for that team, so cleanup can find them again. */
    private final Map<UUID, Set<UUID>> mobsByTeam = new ConcurrentHashMap<>();

    private BukkitTask spawnTask;

    /** A spawner's block position, as a map key that ignores Location's mutable double precision. */
    private record BlockKey(UUID worldId, int x, int y, int z) {
        static BlockKey of(@NotNull Location location) {
            World world = location.getWorld();
            return new BlockKey(world == null ? null : world.getUID(),
                    location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }

    /** One placed spawner and everything the tick needs to decide whether it fires. */
    private static final class Spawner {
        final Location location;
        final int depth;
        final UUID teamId;
        final UUID instanceId;
        /** Mobs from this spawner that are still alive, for the per-spawner cap. */
        final Set<UUID> liveMobs = ConcurrentHashMap.newKeySet();
        /** Server tick at which this spawner may next produce a wave; 0 means "on first approach". */
        long nextSpawnAt;

        Spawner(Location location, int depth, UUID teamId, UUID instanceId) {
            this.location = location;
            this.depth = depth;
            this.teamId = teamId;
            this.instanceId = instanceId;
        }
    }

    public MobManager(@NotNull Plugin plugin, @NotNull GameManager gameManager) {
        this(plugin, gameManager, Bukkit::getCurrentTick);
    }

    /** Test seam: the same manager with a clock the caller controls. */
    MobManager(@NotNull Plugin plugin, @NotNull GameManager gameManager, @NotNull LongSupplier clock) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.clock = clock;
        this.mobTagKey = new NamespacedKey(plugin, MOB_TAG_KEY);
        this.mobTeamKey = new NamespacedKey(plugin, MOB_TEAM_KEY);
        // Deliberately NOT registering as a listener here; SoT.onEnable() is the single
        // registration point and registering in both places makes every handler run twice.
    }

    // --- Placement ---

    /**
     * Places the spawner block for one {@code MOB_SPAWNER} marker and registers it.
     *
     * <p>Nothing spawns until a member of the team walks into range; see {@link #tick()}.
     *
     * @param location   Absolute location of the marker cell.
     * @param teamId     The team whose dungeon instance this marker belongs to.
     * @param instanceId The dungeon instance, for the coins a broken spawner drops.
     * @param depth      Depth of the segment containing the marker; drives difficulty and payout.
     */
    public void armSpawner(@NotNull Location location, @NotNull UUID teamId, @NotNull UUID instanceId,
                           int depth) {
        if (location.getWorld() == null) {
            plugin.getLogger().warning("Ignoring mob spawner with no world for team " + teamId);
            return;
        }

        Spawner spawner = new Spawner(location.clone(), depth, teamId, instanceId);
        placeSpawnerBlock(spawner);

        spawnersByTeam.computeIfAbsent(teamId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(spawner);
        spawnersByBlock.put(BlockKey.of(spawner.location), spawner);
    }

    /**
     * Writes the spawner block and disables its vanilla behaviour.
     *
     * <p>A stock {@link Material#SPAWNER} would run its own spawn logic on top of this class's, which
     * would both double the encounter and produce untagged mobs that nothing tracks or counts.
     */
    private void placeSpawnerBlock(@NotNull Spawner spawner) {
        try {
            Block block = spawner.location.getBlock();
            block.setType(SPAWNER_BLOCK, false);
            if (block.getState() instanceof CreatureSpawner vanilla) {
                vanilla.setSpawnCount(0);
                vanilla.setRequiredPlayerRange(0);
                vanilla.setDelay(Integer.MAX_VALUE);
                vanilla.update(true, false);
            }
        } catch (Exception e) {
            // Best effort, as elsewhere: a spawner we could not fully neutralise still beats no
            // encounter, and the block may simply not be a CreatureSpawner on this implementation.
            plugin.getLogger().log(Level.FINE,
                    "Could not fully configure spawner block at " + spawner.location.toVector(), e);
        }
    }

    /** How many spawners are still standing for a team. Exposed for tests and logging. */
    public int getActiveSpawnerCount(@NotNull UUID teamId) {
        List<Spawner> spawners = spawnersByTeam.get(teamId);
        return spawners == null ? 0 : spawners.size();
    }

    /** How many live mobs this manager is currently tracking for a team. */
    public int getTrackedMobCount(@NotNull UUID teamId) {
        Set<UUID> mobs = mobsByTeam.get(teamId);
        return mobs == null ? 0 : mobs.size();
    }

    // --- The spawn loop ---

    /**
     * Starts the repeating spawn check. Called by {@code GameManager.beginPlay}.
     *
     * <p>A task rather than a movement listener, because a spawner has to keep producing mobs at a
     * player who is standing still fighting it.
     */
    public void start() {
        if (spawnTask != null && !spawnTask.isCancelled()) {
            return;
        }
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick,
                TICK_PERIOD_TICKS, TICK_PERIOD_TICKS);
    }

    /** Stops the repeating spawn check. Called by {@code GameManager.tearDownRound}. */
    public void stop() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
    }

    /**
     * One pass over every online player, firing any of their team's spawners that are in range, off
     * cooldown and under their live-mob cap.
     *
     * <p>Package-private so the tests can drive it without a live scheduler.
     */
    void tick() {
        if (gameManager.getCurrentState() != GameState.RUNNING) {
            return;
        }
        long now = clock.getAsLong();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
            if (teamId == null) {
                continue;
            }
            List<Spawner> spawners = spawnersByTeam.get(teamId);
            if (spawners == null || spawners.isEmpty()) {
                continue;
            }
            Location playerLoc = player.getLocation();
            if (playerLoc.getWorld() == null) {
                continue;
            }

            for (Spawner spawner : new ArrayList<>(spawners)) {
                if (now < spawner.nextSpawnAt) {
                    continue;
                }
                if (spawner.liveMobs.size() >= MAX_LIVE_MOBS_PER_SPAWNER) {
                    continue;
                }
                if (!playerLoc.getWorld().equals(spawner.location.getWorld())) {
                    continue;
                }
                if (playerLoc.distanceSquared(spawner.location) > ACTIVATION_RADIUS_SQUARED) {
                    continue;
                }
                fire(spawner);
                spawner.nextSpawnAt = now + SPAWN_INTERVAL_TICKS;
            }
        }
    }

    /** Spawns one wave for a spawner, respecting its remaining headroom under the cap. */
    private void fire(@NotNull Spawner spawner) {
        World world = spawner.location.getWorld();
        if (world == null) {
            return;
        }

        List<Class<? extends Mob>> types = mobsForDepth(spawner.depth, random);
        int headroom = MAX_LIVE_MOBS_PER_SPAWNER - spawner.liveMobs.size();
        int wanted = Math.min(types.size(), headroom);

        for (int i = 0; i < wanted; i++) {
            Location at = spawnPointFor(spawner.location, i);
            try {
                Mob mob = world.spawn(at, types.get(i), created -> configure(created, spawner.teamId));
                spawner.liveMobs.add(mob.getUniqueId());
                mobsByTeam.computeIfAbsent(spawner.teamId, k -> ConcurrentHashMap.newKeySet())
                        .add(mob.getUniqueId());
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to spawn " + types.get(i).getSimpleName() + " at " + at.toVector()
                                + " for team " + spawner.teamId, e);
            }
        }
    }

    /**
     * Where the {@code index}-th mob of a wave stands.
     *
     * <p>The spawner occupies the marker cell itself, so every mob is nudged off it; the first mob
     * takes the cell in front by preference and later ones scatter. A nudge that lands in a wall
     * falls back to the cell above the spawner, which the template guarantees is open.
     */
    private Location spawnPointFor(@NotNull Location base, int index) {
        Location fallback = base.clone().add(0.5, 1, 0.5);
        int dx = random.nextInt(GROUP_SPREAD * 2 + 1) - GROUP_SPREAD;
        int dz = random.nextInt(GROUP_SPREAD * 2 + 1) - GROUP_SPREAD;
        if (dx == 0 && dz == 0) {
            dx = 1; // never inside the spawner block itself
        }
        Location offset = base.clone().add(0.5 + dx, 0, 0.5 + dz);
        try {
            return offset.getBlock().isPassable() ? offset : fallback;
        } catch (Exception e) {
            // Cannot tell whether the nudged cell is clear (an unloaded chunk, an implementation
            // that does not answer) — fall back to the cell above the spawner.
            return fallback;
        }
    }

    /** Tags and configures a freshly created mob. Runs before the entity joins the world. */
    private void configure(@NotNull Mob mob, @NotNull UUID teamId) {
        // The tag is the load-bearing part: it is what makes the mob trackable, cleanable and
        // countable, so it is set first and never guarded.
        mob.getPersistentDataContainer().set(mobTagKey, PersistentDataType.BYTE, (byte) 1);
        mob.getPersistentDataContainer().set(mobTeamKey, PersistentDataType.STRING, teamId.toString());

        // Everything below is best-effort hardening, so a setter an implementation does not support
        // downgrades the encounter rather than aborting the spawn and leaving the room empty.
        // (MockBukkit, for one, declares setRemoveWhenFarAway but throws on it.)
        try {
            // Opt out of both vanilla despawn paths, so a player who retreats and comes back for
            // their corpse still finds the fight they ran from.
            mob.setPersistent(true);
            mob.setRemoveWhenFarAway(false);

            // The dungeon is pasted at y=100 in the overworld and may see sky; an encounter that
            // burns away at dawn is not an encounter.
            if (mob instanceof Zombie zombie) {
                zombie.setShouldBurnInDay(false);
            } else if (mob instanceof AbstractSkeleton skeleton) {
                skeleton.setShouldBurnInDay(false);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE,
                    "Could not fully harden dungeon mob " + mob.getType() + "; it may despawn or burn", e);
        }
    }

    // --- Breaking a spawner ---

    /**
     * Destroying a spawner with a pickaxe stops it and pays out.
     *
     * <p>This is the only way to end an encounter: the block keeps producing waves until it is gone.
     * The payout matches an ordinary coin stack at the same depth
     * ({@link DungeonManager#coinBaseValueForDepth}) and is dropped as a tracked
     * {@link com.clarkson.sot.entities.CoinStack}, so it picks up the same ItemDisplay visual,
     * team-scoped proximity pickup, batched pickup notifier and instance cleanup as every other coin.
     *
     * <p>A bare-handed break is refused rather than allowed-without-payout: silently getting nothing
     * for destroying the spawner reads as a bug to the player.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Spawner spawner = spawnersByBlock.get(BlockKey.of(event.getBlock().getLocation()));
        if (spawner == null) {
            return;
        }

        Player player = event.getPlayer();
        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
        if (!spawner.teamId.equals(teamId)) {
            // Another team's instance (or a non-participant): leave the block alone entirely.
            event.setCancelled(true);
            return;
        }

        if (!isPickaxe(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "You need a pickaxe to destroy a mob spawner",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        forget(spawner);

        try {
            FloorItemManager floorItemManager = gameManager.getFloorItemManager();
            if (floorItemManager != null) {
                floorItemManager.spawnCoinStack(spawner.location.clone(),
                        DungeonManager.coinBaseValueForDepth(spawner.depth),
                        spawner.teamId, spawner.instanceId, spawner.depth);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to drop coins for a spawner broken at "
                    + spawner.location.toVector() + " by " + player.getName(), e);
        }
    }

    /**
     * Whether the held item can destroy a spawner.
     *
     * <p>Static and material-based rather than a tag lookup so it is testable without a server.
     */
    static boolean isPickaxe(ItemStack held) {
        if (held == null) {
            return false;
        }
        return switch (held.getType()) {
            case WOODEN_PICKAXE, STONE_PICKAXE, IRON_PICKAXE,
                 GOLDEN_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE -> true;
            default -> false;
        };
    }

    /** Drops a spawner from both indexes. The mobs it already produced are left to be fought. */
    private void forget(@NotNull Spawner spawner) {
        spawnersByBlock.remove(BlockKey.of(spawner.location));
        List<Spawner> spawners = spawnersByTeam.get(spawner.teamId);
        if (spawners != null) {
            spawners.remove(spawner);
        }
    }

    // --- Kill tracking ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        // The tag alone is the gate: a tagged mob only ever exists inside a live round, and reading
        // the game state here would miss kills landing in the same tick the round ends.
        if (!dead.getPersistentDataContainer().has(mobTagKey, PersistentDataType.BYTE)) {
            return;
        }
        untrack(dead.getUniqueId());

        Player killer = dead.getKiller();
        if (killer == null) {
            return; // Fall damage, suffocation, another mob — nobody to credit.
        }
        SoTPlayerData data = gameManager.getPlayerManager().getPlayerData(killer.getUniqueId());
        if (data != null) {
            data.incrementMonstersKilled();
        }
    }

    /** Forgets a dead mob, freeing headroom under its spawner's cap. */
    private void untrack(@NotNull UUID entityId) {
        for (Set<UUID> mobs : mobsByTeam.values()) {
            mobs.remove(entityId);
        }
        for (List<Spawner> spawners : spawnersByTeam.values()) {
            for (Spawner spawner : new ArrayList<>(spawners)) {
                spawner.liveMobs.remove(entityId);
            }
        }
    }

    // --- Cleanup ---

    /**
     * Removes a team's spawners and every mob they produced.
     *
     * <p>Called from {@link DungeonManager#cleanupInstance()}. That method already sweeps every
     * non-player entity inside the dungeon bounds, but a mob that pathed out through an open doorway
     * is no longer in those bounds — tracking each one by UUID is what makes the removal complete.
     */
    public void clearTeamState(UUID teamId) {
        List<Spawner> spawners = spawnersByTeam.remove(teamId);
        if (spawners != null) {
            for (Spawner spawner : spawners) {
                spawnersByBlock.remove(BlockKey.of(spawner.location));
            }
        }

        Set<UUID> mobs = mobsByTeam.remove(teamId);
        if (mobs == null || mobs.isEmpty()) {
            return;
        }
        int removed = 0;
        for (UUID mobId : mobs) {
            try {
                Entity entity = Bukkit.getEntity(mobId);
                if (entity != null) {
                    entity.remove();
                    removed++;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error removing dungeon mob " + mobId
                        + " for team " + teamId, e);
            }
        }
        plugin.getLogger().fine("Removed " + removed + " of " + mobs.size()
                + " tracked dungeon mobs for team " + teamId + ".");
    }

    /** Clears every team's spawners and mobs, and stops the spawn loop. */
    public void clearAllTeamStates() {
        for (UUID teamId : new ArrayList<>(spawnersByTeam.keySet())) {
            clearTeamState(teamId);
        }
        for (UUID teamId : new ArrayList<>(mobsByTeam.keySet())) {
            clearTeamState(teamId);
        }
        spawnersByTeam.clear();
        spawnersByBlock.clear();
        mobsByTeam.clear();
        stop();
    }

    // --- Difficulty ---

    /**
     * The mobs one wave produces at the given depth, in spawn order.
     *
     * <p>GAME_RULES asks for mobs that get more dangerous "especially in deeper segments", so both
     * the wave size and the pool widen with depth: one mob near the hub, three at the bottom.
     * Depth runs 0 (hub) to 10.
     *
     * <p>Static and side-effect free on purpose — this is the part of mob spawning worth testing
     * exhaustively, and it is the only part that needs no server (compare
     * {@code DungeonManager.spawnsRustyKey}). The {@link Random} is a parameter so tests can seed it.
     *
     * @param depth  Segment depth; values below zero are treated as the hub.
     * @param random Source of randomness for the type choice.
     * @return A non-empty, immutable list of mob classes to spawn.
     */
    static List<Class<? extends Mob>> mobsForDepth(int depth, @NotNull Random random) {
        final List<Class<? extends Mob>> pool;
        final int count;
        if (depth < MID_DEPTH) {
            pool = List.of(Zombie.class, Skeleton.class);
            count = 1;
        } else if (depth < DEEP_DEPTH) {
            pool = List.of(Zombie.class, Skeleton.class, Spider.class);
            count = 2;
        } else {
            pool = List.of(Zombie.class, Skeleton.class, Spider.class, CaveSpider.class);
            count = 3;
        }

        List<Class<? extends Mob>> chosen = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            chosen.add(pool.get(random.nextInt(pool.size())));
        }
        return Collections.unmodifiableList(chosen);
    }
}
