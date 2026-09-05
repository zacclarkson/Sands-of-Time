package com.clarkson.sot.utils;

import com.clarkson.sot.dungeon.DeathCage;
import com.clarkson.sot.dungeon.GateSacrificePoint;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.timer.TeamTimer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Handles sand collection, spending sand at a timer deposit point, and sand sacrifice to revive
 * dead teammates.
 *
 * <p>Sand is placed as normal SAND blocks in the dungeon. Players break them with a shovel to pick
 * the sand up as a real {@link Material#SAND} item, then carry it back to a deposit point in the
 * hub and place it there: the placement is consumed and the team timer gains
 * {@link #SECONDS_PER_SAND} seconds. Breaking sand deliberately gives <em>no</em> time on its own —
 * the trip back to the timer is the risk the game is built around.
 *
 * <p>Carried sand lives in the player's inventory and nowhere else; there is no parallel counter to
 * drift out of sync with it. Sand is also the price of a revive: a caged teammate is bought out by
 * right-clicking their sacrifice chest, one sand per click, until their death count (capped at
 * {@link DeathCage#MAX_REVIVE_COST}) has been paid.
 *
 * <p>Sand is also how gated coins are bought: a sacrifice chest out in a branch stands in front of a
 * gate, and paying its price (set by the builder, one sand per click) opens the gate onto whatever the
 * segment put behind it. It is the same chest block as a cage sacrifice point and is handled by the
 * same interact listener — see {@link #attemptGateSacrifice(Player, GateSacrificePoint)}. A
 * sacrifice point never hands out coins itself.
 *
 * <p>Dying drops undeposited sand on the floor where you fell, like any other item, so the corpse run
 * can win it back — see {@link #dropCarriedSandOnDeath(PlayerDeathEvent)}.
 */
public class SandManager implements Listener {

    private final GameManager gameManager;
    private final Plugin plugin;

    public static final int SECONDS_PER_SAND = 10;

    public SandManager(GameManager gameManager, Plugin plugin) {
        this.gameManager = Objects.requireNonNull(gameManager);
        this.plugin = Objects.requireNonNull(plugin);
    }

    /**
     * Gives a player sand they just collected. Anything that does not fit in the inventory is dropped
     * at their feet rather than silently destroyed, matching how keys and floor loot are handed out.
     */
    public void collectSandItem(Player player, int amount) {
        if (amount <= 0) return;

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(Material.SAND, amount));
        leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));

        player.sendActionBar(Component.text("+" + amount + " sand — take it to the timer", NamedTextColor.YELLOW));
        player.playSound(player.getLocation(), Sound.BLOCK_SAND_BREAK, SoundCategory.BLOCKS, 1.0f, 1.2f);
        plugin.getLogger().fine(player.getName() + " collected " + amount + " sand");
    }

    /**
     * Puts one sand toward freeing the player assigned to the given death cage, and frees them once
     * the full price is paid.
     *
     * <p>The price escalates with the caged player's death count (see {@link DeathCage}), and is paid
     * a sand at a time, so any number of teammates can chip in on the same revive. Each accepted click
     * consumes exactly one sand; a reviver who is short simply pays nothing.
     *
     * @param reviver The player sacrificing sand at the chest.
     * @param cage The death cage whose sacrifice point was interacted with.
     * @return true if this sacrifice completed the price and the caged player was revived.
     */
    public boolean attemptRevive(Player reviver, DeathCage cage) {
        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(reviver);
        if (teamId == null) return false;

        // Check that the cage has an assigned player
        UUID cagedPlayerId = cage.getAssignedPlayerUUID();
        if (cagedPlayerId == null) {
            reviver.sendMessage(Component.text("No one is assigned to this cage.", NamedTextColor.YELLOW));
            return false;
        }

        // Check the assigned player is actually dead and awaiting revive
        if (gameManager.getPlayerStateManager().getStatus(cagedPlayerId) != PlayerStatus.DEAD_AWAITING_REVIVE) {
            reviver.sendMessage(Component.text("This teammate doesn't need reviving!", NamedTextColor.YELLOW));
            return false;
        }

        Player deadPlayer = Bukkit.getPlayer(cagedPlayerId);
        if (deadPlayer == null || !deadPlayer.isOnline()) {
            reviver.sendMessage(Component.text("That teammate is not online.", NamedTextColor.RED));
            return false;
        }

        // One sand per click. Refused without cost when they are carrying none.
        if (getPlayerSandCount(reviver) < 1) {
            reviver.sendMessage(Component.text("You need sand to free a teammate!", NamedTextColor.RED));
            return false;
        }

        removeSand(reviver, 1);
        boolean complete = cage.depositSand();
        gameManager.getSacrificeIndicatorManager().update(cage);

        if (!complete) {
            int remaining = cage.getRemainingSand();
            reviver.sendActionBar(Component.text(
                    "Sacrificed 1 sand — " + remaining + " more to free " + deadPlayer.getName(),
                    NamedTextColor.YELLOW));
            reviver.playSound(reviver.getLocation(), Sound.BLOCK_SAND_PLACE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            deadPlayer.sendActionBar(Component.text(
                    reviver.getName() + " paid 1 sand — " + remaining + " more to free you",
                    NamedTextColor.YELLOW));
            plugin.getLogger().fine(reviver.getName() + " paid 1 sand toward reviving "
                    + deadPlayer.getName() + " (" + remaining + " remaining)");
            return false;
        }

        int totalCost = cage.getRequiredSand();
        cage.clearProgress();

        // Revive the dead player
        gameManager.getPlayerStateManager().updateStatus(deadPlayer, PlayerStatus.ALIVE_IN_DUNGEON);

        // Teleport them to the hub
        Location hubLocation = gameManager.getTeamHubLocation(teamId);
        if (hubLocation != null) {
            final Player target = deadPlayer;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (target.isValid()) target.teleport(hubLocation.clone().add(0.5, 0.1, 0.5));
            });
        }

        // Effects
        reviver.sendMessage(Component.text("You revived " + deadPlayer.getName() + "!", NamedTextColor.GREEN));
        deadPlayer.sendMessage(Component.text("You have been revived by " + reviver.getName() + "!", NamedTextColor.GREEN));
        reviver.playSound(reviver.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.5f);
        deadPlayer.playSound(deadPlayer.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.5f);

        plugin.getLogger().info(reviver.getName() + " revived " + deadPlayer.getName() + " (cost: " + totalCost + " sand)");
        return true;
    }

    /**
     * Puts one sand toward opening the gates a sacrifice chest fronts, and opens them once the full
     * price is paid.
     *
     * <p>This is the sand-for-money trade: the chest hands out nothing itself, the reward is whatever
     * the segment put behind the gate. The price is per chest, set by the builder, and paid a sand at
     * a time so any number of teammates can chip in; each accepted click consumes exactly one sand
     * and the gates open on the click that completes the total. Sand paid into a chest whose gates
     * then open by lever is spent, not refunded. A chest whose gates are already open refuses without
     * taking anything.
     *
     * @param payer The player sacrificing sand at the chest.
     * @param point The gate sacrifice point they clicked.
     * @return true if this sacrifice completed the price and opened the gates.
     */
    public boolean attemptGateSacrifice(Player payer, GateSacrificePoint point) {
        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(payer);
        if (teamId == null) return false;

        // Opening gates is an exploring player's move: escaped, trapped and caged players have no
        // business spending sand, and an escaped player's inventory has already been wiped anyway.
        if (gameManager.getPlayerStateManager().getStatus(payer) != PlayerStatus.ALIVE_IN_DUNGEON) return false;

        if (point.isOpen()) {
            payer.sendMessage(Component.text("These gates are already open.", NamedTextColor.YELLOW));
            return false;
        }

        if (getPlayerSandCount(payer) < 1) {
            payer.sendMessage(Component.text("You need sand to open these gates! ("
                    + point.getRemainingSand() + " more)", NamedTextColor.RED));
            return false;
        }

        removeSand(payer, 1);
        boolean complete = point.depositSand();
        gameManager.getSacrificeIndicatorManager().update(point);

        if (!complete) {
            payer.sendActionBar(Component.text("Sacrificed 1 sand — " + point.getRemainingSand()
                    + " more to open the gates", NamedTextColor.YELLOW));
            payer.playSound(payer.getLocation(), Sound.BLOCK_SAND_PLACE, SoundCategory.BLOCKS, 1.0f, 1.0f);
            return false;
        }

        gameManager.getDoorManager().openGatesForSacrifice(teamId, point, payer);
        payer.sendMessage(Component.text("The gates grind open!", NamedTextColor.GREEN));
        payer.playSound(payer.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 1.0f, 0.8f);
        plugin.getLogger().fine(payer.getName() + " paid the last of " + point.getCost()
                + " sand at the sacrifice chest in " + point.getSegmentName());
        return true;
    }

    /**
     * Spends carried sand to add time to the player's team timer. This is the single path from sand to
     * seconds — the deposit-point handler below routes through it.
     *
     * <p>Refused, with the sand left untouched, when the timer is already at
     * {@link TeamTimer#DEFAULT_MAX_TIMER_SECONDS}: {@code addSeconds} clamps, so accepting the deposit
     * there would destroy the sand and give nothing back.
     *
     * @return true if the sand was spent and time was added.
     */
    public boolean useSandForTimer(Player player, int amount) {
        if (amount <= 0) return false;

        if (getPlayerSandCount(player) < amount) {
            player.sendMessage(Component.text("Not enough sand!", NamedTextColor.RED));
            return false;
        }

        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
        if (teamId == null) return false;
        SoTTeam team = gameManager.getActiveTeams().get(teamId);
        if (team == null) return false;

        if (team.getRemainingSeconds() >= TeamTimer.DEFAULT_MAX_TIMER_SECONDS) {
            player.sendActionBar(Component.text("The timer is already full!", NamedTextColor.RED));
            return false;
        }

        removeSand(player, amount);

        int secondsToAdd = amount * SECONDS_PER_SAND;
        team.addSeconds(secondsToAdd);
        player.sendActionBar(Component.text("+" + secondsToAdd + "s to timer!", NamedTextColor.YELLOW));
        player.playSound(player.getLocation(), Sound.BLOCK_SAND_PLACE, SoundCategory.BLOCKS, 1.0f, 1.4f);
        plugin.getLogger().fine(player.getName() + " deposited " + amount + " sand (+" + secondsToAdd
                + "s) for team " + team.getTeamName());
        return true;
    }

    // --- Sand tracking helpers ---

    /**
     * How much sand the player is carrying. The inventory is the only store of carried sand.
     *
     * <p>Walks the storage slots and the off hand explicitly rather than going through
     * {@code Inventory.all}/{@code removeItem}: which slots those cover differs between CraftBukkit
     * and MockBukkit, and sand held only in the off hand must still count.
     */
    public int getPlayerSandCount(Player player) {
        PlayerInventory inventory = player.getInventory();
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (isSand(item)) total += item.getAmount();
        }
        if (isSand(inventory.getItemInOffHand())) total += inventory.getItemInOffHand().getAmount();
        return total;
    }

    /**
     * Removes up to {@code amount} sand from the player, off hand last so the stack they are holding
     * is spent first.
     *
     * @return how many were actually removed.
     */
    private int removeSand(Player player, int amount) {
        if (amount <= 0) return 0;
        PlayerInventory inventory = player.getInventory();
        int remaining = amount;

        ItemStack[] storage = inventory.getStorageContents();
        for (int i = 0; i < storage.length && remaining > 0; i++) {
            ItemStack item = storage[i];
            if (!isSand(item)) continue;
            int taken = Math.min(remaining, item.getAmount());
            remaining -= taken;
            if (item.getAmount() == taken) {
                storage[i] = null;
            } else {
                item.setAmount(item.getAmount() - taken);
            }
        }
        inventory.setStorageContents(storage);

        ItemStack offHand = inventory.getItemInOffHand();
        if (remaining > 0 && isSand(offHand)) {
            int taken = Math.min(remaining, offHand.getAmount());
            remaining -= taken;
            if (offHand.getAmount() == taken) {
                inventory.setItemInOffHand(null);
            } else {
                offHand.setAmount(offHand.getAmount() - taken);
                inventory.setItemInOffHand(offHand);
            }
        }

        player.updateInventory();
        return amount - remaining;
    }

    private static boolean isSand(ItemStack item) {
        return item != null && item.getType() == Material.SAND && item.getAmount() > 0;
    }

    /**
     * Puts the sand a player was carrying on the floor at the spot where they died, so a teammate on
     * the corpse run can recover it.
     *
     * <p>Nearly always there is nothing to do: a death that drops the inventory already scatters the
     * sand with everything else, and that path is deliberately left alone so the pile lands, merges
     * and despawns by the same vanilla rules as the rest of the corpse. This only steps in when the
     * death is <em>keeping</em> the inventory — the {@code keepInventory} gamerule, or another plugin
     * — because sand is the round's currency for both timer seconds and revives, so losing it on
     * death is a rule of the game rather than a server setting.
     *
     * @return how much sand this dropped; 0 when the sand is already going to drop on its own.
     */
    public int dropCarriedSandOnDeath(PlayerDeathEvent event) {
        if (!event.getKeepInventory()) return 0;

        Player player = event.getEntity();
        int carried = getPlayerSandCount(player);
        if (carried <= 0) return 0;

        int dropped = removeSand(player, carried);
        dropSandAt(player.getLocation(), dropped);
        plugin.getLogger().fine("Dropped " + dropped + " sand carried by " + player.getName()
                + " at their death location (the death kept their inventory)");
        return dropped;
    }

    /**
     * Drops {@code amount} sand as ground items, split across as many stacks as the material's own
     * limit needs — a single over-sized ItemStack is not something the world will accept.
     */
    private void dropSandAt(Location location, int amount) {
        World world = location.getWorld();
        if (world == null || amount <= 0) return;

        int maxStack = Math.max(1, Material.SAND.getMaxStackSize());
        for (int remaining = amount; remaining > 0; ) {
            int stack = Math.min(remaining, maxStack);
            world.dropItemNaturally(location, new ItemStack(Material.SAND, stack));
            remaining -= stack;
        }
    }

    /**
     * Strips every sand item from the given players, skipping any who are offline.
     *
     * <p>Called at the end of a round. {@link GameManager#handlePlayerLeave} only fires for players who
     * escape, so trapped, dead and still-exploring players would otherwise carry sand into the next
     * round and deposit it for free time — which is exactly what the old in-memory counter reset
     * prevented. Only sand is removed, not the whole inventory: {@code /sot end} can be run mid-round
     * on players who never consented to losing their gear.
     */
    public void clearSandItems(Collection<UUID> playerUUIDs) {
        if (playerUUIDs == null) return;
        for (UUID playerUUID : playerUUIDs) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player == null || !player.isOnline()) continue;
            int carried = getPlayerSandCount(player);
            if (carried > 0) removeSand(player, carried);
        }
    }

    // --- Event Listeners ---

    /**
     * Detects when a player breaks a SAND block in a dungeon and hands them the sand as an item.
     * Deliberately adds no time: sand only becomes seconds at a deposit point (see
     * {@link #onBlockPlace(BlockPlaceEvent)}).
     *
     * <p>{@code BlockProtectionListener} runs at {@code LOW} and has already cancelled any break it
     * disallows, so with {@code ignoreCancelled = true} at {@code NORMAL} a protected block never
     * reaches this method and it can pay out unconditionally. <b>Do not raise this priority.</b>
     *
     * <p>This used to re-check the team's own timer column itself. That check is gone because the
     * listener subsumes it and then some — it covers every team's column, not just the breaker's,
     * and the whole live round rather than only {@code RUNNING}. The only case it did not subsume
     * was a participant in Creative, where it contradicted the listener's deliberate
     * Creative/Spectator bypass by refusing the break anyway.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (gameManager.getCurrentState() != GameState.RUNNING) return;

        Block block = event.getBlock();
        if (block.getType() != Material.SAND) return;

        Player player = event.getPlayer();
        PlayerStatus status = gameManager.getPlayerStateManager().getStatus(player);
        if (status != PlayerStatus.ALIVE_IN_DUNGEON) return;

        // No team lookup: it existed only for the timer-column check above, and ALIVE_IN_DUNGEON is
        // already only ever set on players who were assigned to a team at setup.

        // Cancel the normal drop; collectSandItem hands the sand over instead.
        event.setDropItems(false);
        collectSandItem(player, 1);
    }

    /**
     * Detects a player placing sand on one of their team's timer deposit points and converts it into
     * time. The deposit cell always ends up empty, so the event is cancelled rather than placed and
     * cleared a tick later — sand has gravity, and a block that turns into a falling entity before the
     * cleanup runs would land somewhere as a duplicate.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (gameManager.getCurrentState() != GameState.RUNNING) return;

        Block placed = event.getBlockPlaced();
        if (placed.getType() != Material.SAND) return;

        Player player = event.getPlayer();
        PlayerStatus status = gameManager.getPlayerStateManager().getStatus(player);
        if (status != PlayerStatus.ALIVE_IN_DUNGEON) return;

        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
        if (teamId == null) return;

        if (!gameManager.isTeamSandTimerDepositAt(teamId, placed.getLocation())) return;

        // Cancelling puts the sand back in the inventory, which is where useSandForTimer spends it
        // from — so a refused deposit (timer already full) costs the player nothing.
        event.setCancelled(true);
        useSandForTimer(player, 1);
    }

    /**
     * Handles a right-click on one of the two chests a sand can be spent at: a <em>cage</em>
     * sacrifice point in the hub, which pays toward freeing the teammate caged in front of it, or a
     * <em>gate</em> sacrifice point out in a branch, which pays toward opening the gates it stands in
     * front of. The two are the same block and the same marker — which one a chest is comes from the
     * segment it was saved on — so one handler has to tell them apart, and the cage meaning wins
     * where a cell somehow carries both (a caged teammate is the meaning with a deadline).
     *
     * <p>The click is cancelled before anything else, because both kinds are real chests and an
     * uncancelled right-click opens the inventory. That has to happen ahead of the team lookup too:
     * bailing out early for a player with no team would leave them able to open the chest.
     *
     * <p>{@link PlayerInteractEvent} fires once per hand, and cancelling the main-hand pass does not
     * stop the off-hand one — so the off-hand pass is cancelled as well (or the chest opens on it) but
     * returns before paying, otherwise a single right-click would spend two sand.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (gameManager.getCurrentState() != GameState.RUNNING) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // Both kinds of sacrifice point are CHEST blocks
        if (clickedBlock.getType() != Material.CHEST) return;

        Location clicked = clickedBlock.getLocation();
        // Checked against every team's dungeon so a player with no team (or on another team) still
        // cannot open someone's chest, whichever kind it is.
        boolean cagePoint = gameManager.isAnySacrificePointAt(clicked);
        boolean gatePoint = !cagePoint && gameManager.isAnyGateSacrificePointAt(clicked);
        if (!cagePoint && !gatePoint) return;

        event.setCancelled(true);
        // Cancelled above for both hands; only the main hand actually pays.
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
        if (teamId == null) return;

        if (cagePoint) {
            DeathCage cage = gameManager.getDeathCageAtSacrificePoint(teamId, clicked);
            if (cage == null) return; // Someone else's team's chest
            attemptRevive(player, cage);
        } else {
            GateSacrificePoint point = gameManager.getGateSacrificePointAt(teamId, clicked);
            if (point == null) return; // Someone else's team's chest
            attemptGateSacrifice(player, point);
        }
    }
}
