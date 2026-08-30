package com.clarkson.sot.utils;

import com.clarkson.sot.dungeon.DeathCage;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Handles sand collection (breaking sand blocks), adding time to the team timer,
 * and sand sacrifice to revive dead teammates.
 *
 * Sand is placed as normal SAND blocks in the dungeon. Players break them with a shovel
 * to collect sand. Each sand collected adds 10 seconds to the team timer.
 * Sacrificing 1 sand at a sacrifice point revives a dead teammate.
 */
public class SandManager implements Listener {

    private final GameManager gameManager;
    private final Plugin plugin;

    public static final int SECONDS_PER_SAND = 10;
    public static final int REVIVE_COST = 1;

    // PDC key to identify sand sacrifice point blocks
    private static NamespacedKey SACRIFICE_POINT_KEY;

    // Track how much sand each player is carrying
    private final Map<UUID, Integer> playerSandCounts = new HashMap<>();

    public SandManager(GameManager gameManager, Plugin plugin) {
        this.gameManager = Objects.requireNonNull(gameManager);
        this.plugin = Objects.requireNonNull(plugin);
        SACRIFICE_POINT_KEY = new NamespacedKey(plugin, "sot_sand_sacrifice");
    }

    /**
     * Called when a player breaks a sand block in the dungeon.
     * Adds the sand to their personal sand count and time to the team timer.
     */
    public void collectSandItem(Player player, int amount) {
        UUID playerUUID = player.getUniqueId();
        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
        if (teamId == null) return;

        SoTTeam team = gameManager.getActiveTeams().get(teamId);
        if (team == null) return;

        // Add sand to player's count
        playerSandCounts.merge(playerUUID, amount, Integer::sum);

        // Add time to team timer
        int secondsToAdd = amount * SECONDS_PER_SAND;
        team.addSeconds(secondsToAdd);

        player.sendActionBar(Component.text("+" + secondsToAdd + "s to timer!", NamedTextColor.YELLOW));
        player.playSound(player.getLocation(), Sound.BLOCK_SAND_BREAK, SoundCategory.BLOCKS, 1.0f, 1.2f);
        plugin.getLogger().fine(player.getName() + " collected " + amount + " sand (+" + secondsToAdd + "s) for team " + team.getTeamName());
    }

    /**
     * Attempts to revive the specific player assigned to the given death cage.
     * The reviver must have at least 1 sand. The caged player is freed and teleported to the hub.
     *
     * @param reviver The player sacrificing sand at the sacrifice point.
     * @param cage The death cage whose sacrifice point was interacted with.
     * @return true if the assigned player was successfully revived.
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

        // Check if reviver has enough sand
        int sand = getPlayerSandCount(reviver.getUniqueId());
        if (sand < REVIVE_COST) {
            reviver.sendMessage(Component.text("You need at least " + REVIVE_COST + " sand to revive a teammate!", NamedTextColor.RED));
            return false;
        }

        // Consume sand
        removeSand(reviver.getUniqueId(), REVIVE_COST);

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

        plugin.getLogger().info(reviver.getName() + " revived " + deadPlayer.getName() + " (cost: " + REVIVE_COST + " sand)");
        return true;
    }

    /**
     * Directly use sand to add time to the timer (if needed outside block-break flow).
     */
    public void useSandForTimer(Player player, int amount) {
        UUID playerUUID = player.getUniqueId();
        int currentSand = getPlayerSandCount(playerUUID);
        if (currentSand < amount) {
            player.sendMessage(Component.text("Not enough sand!", NamedTextColor.RED));
            return;
        }
        removeSand(playerUUID, amount);

        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
        if (teamId == null) return;
        SoTTeam team = gameManager.getActiveTeams().get(teamId);
        if (team == null) return;

        int secondsToAdd = amount * SECONDS_PER_SAND;
        team.addSeconds(secondsToAdd);
        player.sendActionBar(Component.text("+" + secondsToAdd + "s to timer!", NamedTextColor.YELLOW));
    }

    // --- Sand tracking helpers ---

    public int getPlayerSandCount(UUID playerUUID) {
        return playerSandCounts.getOrDefault(playerUUID, 0);
    }

    private void removeSand(UUID playerUUID, int amount) {
        int current = getPlayerSandCount(playerUUID);
        playerSandCounts.put(playerUUID, Math.max(0, current - amount));
    }

    public void clearPlayerSand(UUID playerUUID) {
        playerSandCounts.remove(playerUUID);
    }

    public void clearAllSandCounts() {
        playerSandCounts.clear();
    }

    // --- Event Listeners ---

    /**
     * Detects when a player breaks a SAND block in a dungeon and collects it.
     *
     * <p>Relies on {@code BlockProtectionListener} running at {@code LOW} to have already cancelled
     * any break it disallows -- the team's own timer column above all, which is also made of SAND.
     * That is what {@code ignoreCancelled = true} at {@code NORMAL} buys us: a protected block never
     * reaches this method, so it can pay out unconditionally. Do not raise this priority.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (gameManager.getCurrentState() != GameState.RUNNING) return;

        Block block = event.getBlock();
        if (block.getType() != Material.SAND) return;

        Player player = event.getPlayer();
        PlayerStatus status = gameManager.getPlayerStateManager().getStatus(player);
        if (status != PlayerStatus.ALIVE_IN_DUNGEON) return;

        // Cancel the normal drop and collect the sand
        event.setDropItems(false);
        collectSandItem(player, 1);
    }

    /**
     * Detects interaction with sand sacrifice points to revive the specific caged teammate.
     * Each sacrifice point (LODESTONE) is paired with a specific death cage and player.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (gameManager.getCurrentState() != GameState.RUNNING) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // Sacrifice points are LODESTONE blocks
        if (clickedBlock.getType() != Material.LODESTONE) return;

        Player player = event.getPlayer();
        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
        if (teamId == null) return;

        // Look up which death cage this sacrifice point belongs to
        DeathCage cage = gameManager.getDeathCageAtSacrificePoint(teamId, clickedBlock.getLocation());
        if (cage == null) return; // Not a registered sacrifice point

        event.setCancelled(true);
        attemptRevive(player, cage);
    }

    public static NamespacedKey getSacrificePointKey() {
        return SACRIFICE_POINT_KEY;
    }
}