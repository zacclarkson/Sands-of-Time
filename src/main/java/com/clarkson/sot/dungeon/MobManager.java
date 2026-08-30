package com.clarkson.sot.dungeon;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.player.SoTPlayerData;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Spawns the hostile mobs a segment template asked for, at its {@code MOB_SPAWNER} markers.
 *
 * <p>Spawners are <b>armed, not fired</b>, at instance setup: {@link DungeonManager} calls
 * {@link #armSpawner} for every marker once the dungeon is pasted, and the mobs themselves only
 * appear when a member of the owning team first walks within {@link #ACTIVATION_RADIUS} blocks.
 * Firing at setup instead would leave every segment's worth of mobs ticking and pathing for the
 * whole round before anyone entered, and vanilla would despawn most of them during the countdown.
 *
 * <p>Activation is <b>one-shot</b>: a fired spawner is dropped from the armed list, so a room that
 * has been cleared stays cleared. That matters for the corpse run — a player returning to their
 * death location should be racing the timer, not the same fight again.
 *
 * <p>Every mob this class creates is tagged in its PDC with {@link #MOB_TAG_KEY} and its owning
 * team, the same idiom {@link DungeonManager#removeBakedBuildMarkers()} uses for build markers.
 * The tag is what separates a designed encounter from the ambient mobs vanilla spawns in a dark
 * room: only tagged mobs are tracked, cleaned up, and counted toward
 * {@link SoTPlayerData#incrementMonstersKilled()}.
 *
 * <p>Registered as a listener by {@code SoT.onEnable()} only — like its sibling managers this
 * class deliberately never registers itself (see {@code ListenerRegistrationTest}).
 */
public class MobManager implements Listener {

    /** PDC key (BYTE) set on every mob this manager spawns. */
    public static final String MOB_TAG_KEY = "sot_dungeon_mob";
    /** PDC key (STRING) holding the UUID of the team whose instance the mob belongs to. */
    public static final String MOB_TEAM_KEY = "sot_mob_team";

    /**
     * How close a player must get before a spawner fires.
     *
     * <p>Ten blocks is roughly "the room you are entering" for the segment sizes in use, and it is
     * deliberately larger than vanilla's spawner range so the fight starts ahead of the player
     * rather than on top of them.
     */
    private static final double ACTIVATION_RADIUS = 10.0;
    private static final double ACTIVATION_RADIUS_SQUARED = ACTIVATION_RADIUS * ACTIVATION_RADIUS;

    /** Depth at which the second and third difficulty bands begin. */
    private static final int MID_DEPTH = 3;
    private static final int DEEP_DEPTH = 6;

    /** Horizontal spread applied to the second and later mobs of a group, in blocks. */
    private static final int GROUP_SPREAD = 1;

    private final Plugin plugin;
    private final GameManager gameManager;
    private final NamespacedKey mobTagKey;
    private final NamespacedKey mobTeamKey;
    private final Random random = new Random();

    /** teamId -> spawners still waiting to fire. An entry is removed the moment it fires. */
    private final Map<UUID, List<ArmedSpawner>> armedByTeam = new ConcurrentHashMap<>();
    /** teamId -> UUIDs of the mobs spawned for that team, so cleanup can find them again. */
    private final Map<UUID, Set<UUID>> mobsByTeam = new ConcurrentHashMap<>();

    /** A marker waiting to fire, with the depth of the segment it sits in. */
    private record ArmedSpawner(Location location, int depth) {}

    public MobManager(@NotNull Plugin plugin, @NotNull GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.mobTagKey = new NamespacedKey(plugin, MOB_TAG_KEY);
        this.mobTeamKey = new NamespacedKey(plugin, MOB_TEAM_KEY);
        // Deliberately NOT registering as a listener here; SoT.onEnable() is the single
        // registration point and registering in both places makes every handler run twice.
    }

    // --- Arming ---

    /**
     * Arms one {@code MOB_SPAWNER} marker. Nothing is spawned until a player of the team approaches.
     *
     * @param location Absolute location of the marker cell.
     * @param teamId   The team whose dungeon instance this marker belongs to.
     * @param depth    Depth of the segment containing the marker; drives difficulty.
     */
    public void armSpawner(@NotNull Location location, @NotNull UUID teamId, int depth) {
        if (location.getWorld() == null) {
            plugin.getLogger().warning("Ignoring mob spawner with no world for team " + teamId);
            return;
        }
        armedByTeam.computeIfAbsent(teamId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new ArmedSpawner(location.clone(), depth));
    }

    /** How many spawners are still waiting to fire for a team. Exposed for tests and logging. */
    public int getArmedSpawnerCount(@NotNull UUID teamId) {
        List<ArmedSpawner> armed = armedByTeam.get(teamId);
        return armed == null ? 0 : armed.size();
    }

    /** How many live mobs this manager is currently tracking for a team. */
    public int getTrackedMobCount(@NotNull UUID teamId) {
        Set<UUID> mobs = mobsByTeam.get(teamId);
        return mobs == null ? 0 : mobs.size();
    }

    // --- Activation (proximity) ---

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        if (gameManager.getCurrentState() != GameState.RUNNING) {
            return;
        }

        Player player = event.getPlayer();
        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
        if (teamId == null) {
            return;
        }

        List<ArmedSpawner> armed = armedByTeam.get(teamId);
        if (armed == null || armed.isEmpty()) {
            return;
        }

        Location playerLoc = event.getTo();
        if (playerLoc == null || playerLoc.getWorld() == null) {
            return;
        }

        // Only this team's spawners are considered, so a player can never trigger another team's
        // instance 5000 blocks away.
        List<ArmedSpawner> fired = new ArrayList<>();
        for (ArmedSpawner spawner : new ArrayList<>(armed)) {
            Location spawnerLoc = spawner.location();
            if (!playerLoc.getWorld().equals(spawnerLoc.getWorld())) {
                continue;
            }
            if (playerLoc.distanceSquared(spawnerLoc) <= ACTIVATION_RADIUS_SQUARED) {
                fired.add(spawner);
            }
        }

        for (ArmedSpawner spawner : fired) {
            // Remove before spawning so a failure part-way through cannot re-fire the spawner.
            if (armed.remove(spawner)) {
                fireSpawner(spawner, teamId);
            }
        }
    }

    /** Spawns the group for one marker and tracks the result. */
    private void fireSpawner(@NotNull ArmedSpawner spawner, @NotNull UUID teamId) {
        Location base = spawner.location();
        World world = base.getWorld();
        if (world == null) {
            return;
        }

        List<Class<? extends Mob>> types = mobsForDepth(spawner.depth(), random);
        int spawned = 0;
        for (int i = 0; i < types.size(); i++) {
            Location at = spawnPointFor(base, i);
            try {
                Mob mob = world.spawn(at, types.get(i), created -> configure(created, teamId));
                mobsByTeam.computeIfAbsent(teamId, k -> ConcurrentHashMap.newKeySet())
                        .add(mob.getUniqueId());
                spawned++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to spawn " + types.get(i).getSimpleName() + " at " + at.toVector()
                                + " for team " + teamId, e);
            }
        }
        plugin.getLogger().fine("Mob spawner fired at " + base.toVector() + " (depth " + spawner.depth()
                + "): spawned " + spawned + " of " + types.size() + " mobs for team " + teamId + ".");
    }

    /**
     * Where the {@code index}-th mob of a group stands.
     *
     * <p>The marker itself is a floor marker — {@code SaveSegmentCommand.isFloorMarker} warns the
     * builder if there is no solid ground under it — so the marker cell is known-safe and the first
     * mob always uses it. Later mobs are nudged aside so a group does not spawn inside itself, and
     * fall back to the marker cell if the nudge lands in a wall.
     */
    private Location spawnPointFor(@NotNull Location base, int index) {
        Location centre = base.clone().add(0.5, 0, 0.5);
        if (index == 0) {
            return centre;
        }
        int dx = random.nextInt(GROUP_SPREAD * 2 + 1) - GROUP_SPREAD;
        int dz = random.nextInt(GROUP_SPREAD * 2 + 1) - GROUP_SPREAD;
        Location offset = centre.clone().add(dx, 0, dz);
        try {
            return offset.getBlock().isPassable() ? offset : centre;
        } catch (Exception e) {
            // Cannot tell whether the nudged cell is clear (an unloaded chunk, an implementation
            // that does not answer) — fall back to the marker cell, which is known good.
            return centre;
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
            // Opt out of both vanilla despawn paths. Spawners are one-shot, so a mob that despawned
            // while its team was elsewhere would leave the room permanently empty — and the player
            // coming back for their corpse should still find the fight they ran from.
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

    private void untrack(@NotNull UUID entityId) {
        for (Set<UUID> mobs : mobsByTeam.values()) {
            if (mobs.remove(entityId)) {
                return;
            }
        }
    }

    // --- Cleanup ---

    /**
     * Disarms every remaining spawner for a team and removes the mobs it spawned.
     *
     * <p>Called from {@link DungeonManager#cleanupInstance()}. That method already sweeps every
     * non-player entity inside the dungeon bounds, but a mob that pathed out through an open
     * doorway is no longer in those bounds — tracking each one by UUID is what makes the removal
     * complete.
     */
    public void clearTeamState(UUID teamId) {
        armedByTeam.remove(teamId);

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

    /** Clears every team's spawners and mobs. Used by end-of-round teardown. */
    public void clearAllTeamStates() {
        for (UUID teamId : new ArrayList<>(mobsByTeam.keySet())) {
            clearTeamState(teamId);
        }
        // Teams that never had a mob spawn can still hold armed spawners.
        armedByTeam.clear();
    }

    // --- Difficulty ---

    /**
     * The mobs a spawner at the given depth produces, in spawn order.
     *
     * <p>GAME_RULES asks for mobs that get more dangerous "especially in deeper segments", so both
     * the group size and the pool widen with depth: one mob near the hub, three at the bottom.
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
