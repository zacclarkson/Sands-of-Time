package com.clarkson.sot.scoring;

import com.clarkson.sot.entities.CoinStack;
import com.clarkson.sot.entities.FloorItem;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.ui.CoinPickupNotifier;
import com.clarkson.sot.utils.TeamManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class ScoreManager {
    private final TeamManager teamManager;
    private final GameManager gameManager;
    private final Plugin plugin;

    private final Map<UUID, Integer> playerUnbankedScores = new HashMap<>();
    /** Combines coins picked up in quick succession into one action-bar message. */
    private final CoinPickupNotifier pickupNotifier;

    // Depth scaling: 100% at depth 0, up to 120% at max depth
    private static final int MAX_DUNGEON_DEPTH = 10;
    private static final double MAX_DEPTH_MULTIPLIER = 1.20;

    public ScoreManager(TeamManager teamManager, GameManager gameManager, Plugin plugin) {
        this(teamManager, gameManager, plugin, new CoinPickupNotifier());
    }

    /** Constructor for tests, which need to drive the pickup notifier's clock. */
    public ScoreManager(TeamManager teamManager, GameManager gameManager, Plugin plugin,
                        CoinPickupNotifier pickupNotifier) {
        this.teamManager = teamManager;
        this.gameManager = gameManager;
        this.plugin = plugin;
        this.pickupNotifier = pickupNotifier;
    }

    /**
     * Main entry point: called by FloorItemManager when a player picks up a floor item.
     * Dispatches to the appropriate handler based on item type.
     */
    public void collectFloorItem(Player player, FloorItem item) {
        if (item instanceof CoinStack) {
            CoinStack coin = (CoinStack) item;
            int scaledValue = awardDepthScaledCoins(player, coin.getBaseValue(), coin.getDepth());
            plugin.getLogger().fine(player.getName() + " collected coin worth " + scaledValue
                    + " (base: " + coin.getBaseValue() + ", depth: " + coin.getDepth() + ")");
        }
        // Future: handle FloorLoot (add to inventory), SandPile (add sand to team), etc.
    }

    /**
     * Adds depth-scaled coins to a player's <em>unbanked</em> score and folds the amount into their
     * running pickup message.
     *
     * <p>The path a coin stack takes, and the single place the depth multiplier is applied, so any
     * other source of coins (a broken mob spawner, for one) shares it by calling here. The coins land
     * unbanked on purpose: they are lost on death and on a timer-out like any other unbanked coin.
     *
     * @return the scaled amount actually added.
     */
    public int awardDepthScaledCoins(Player player, int baseValue, int depth) {
        if (baseValue <= 0) return 0;
        int scaledValue = calculateScaledCoinValue(baseValue, depth);
        updatePlayerUnbankedScore(player.getUniqueId(), scaledValue);
        pickupNotifier.notifyPickup(player, scaledValue);
        return scaledValue;
    }

    /**
     * Processes a collected coin from an ItemStack (e.g., recovering dropped coins).
     */
    public void playerCollectedCoin(Player player, ItemStack coinItem, int baseCoinValue) {
        // When recovering dropped coins, no depth scaling — use base value directly
        updatePlayerUnbankedScore(player.getUniqueId(), baseCoinValue);
        pickupNotifier.notifyPickup(player, baseCoinValue);
        plugin.getLogger().fine(player.getName() + " recovered coin worth " + baseCoinValue);
    }

    /**
     * Processes a collected coin from a dropped Item entity.
     */
    public void playerCollectedCoin(Player player, Item itemEntity, int baseCoinValue) {
        if (itemEntity == null) return;
        playerCollectedCoin(player, itemEntity.getItemStack(), baseCoinValue);
    }

    /**
     * Calculates the depth-scaled value of a coin.
     * Multiplier ranges from 100% (depth 0) to 120% (max depth).
     */
    private int calculateScaledCoinValue(int baseValue, int depth) {
        double depthRatio = Math.min((double) depth / MAX_DUNGEON_DEPTH, 1.0);
        double multiplier = 1.0 + depthRatio * (MAX_DEPTH_MULTIPLIER - 1.0);
        return (int) Math.round(baseValue * multiplier);
    }

    // --- Unbanked Score Tracking ---

    public void updatePlayerUnbankedScore(UUID playerUUID, int delta) {
        playerUnbankedScores.put(playerUUID, getPlayerUnbankedScore(playerUUID) + delta);
    }

    public int getPlayerUnbankedScore(UUID playerUUID) {
        return playerUnbankedScores.getOrDefault(playerUUID, 0);
    }

    public void setPlayerUnbankedScore(UUID playerUUID, int amount) {
        playerUnbankedScores.put(playerUUID, Math.max(0, amount));
    }

    public void clearPlayerUnbankedScore(UUID playerUUID) {
        playerUnbankedScores.remove(playerUUID);
        // Banking/death ends the burst: the next coin picked up starts a fresh message.
        pickupNotifier.reset(playerUUID);
    }

    public void clearAllUnbankedScores() {
        playerUnbankedScores.clear();
        pickupNotifier.resetAll();
    }

    // --- Penalty & Escape Methods ---

    /**
     * Death penalty: the player's unbanked coins are cleared outright.
     *
     * <p>Unbanked coins are a number here, not items in an inventory, so unlike the sand and gear that
     * drop at the death location there is nothing for the corpse run to recover — dying loses them.
     * Banked coins are untouched.
     *
     * @return the amount lost, for the caller to report.
     */
    public int applyDeathPenalty(UUID playerUUID) {
        int lostCoins = getPlayerUnbankedScore(playerUUID);
        clearPlayerUnbankedScore(playerUUID);
        if (lostCoins > 0) {
            plugin.getLogger().info("Death penalty: " + playerUUID + " lost " + lostCoins + " unbanked coins");
        }
        return lostCoins;
    }

    /**
     * Player escaped safely. Unbanked coins are kept but only banked coins count
     * toward the team's final score. No score changes needed here.
     */
    public void playerEscaped(UUID playerUUID) {
        plugin.getLogger().info("Player " + playerUUID + " escaped safely with "
                + getPlayerUnbankedScore(playerUUID) + " unbanked coins (must bank to count)");
    }

    /**
     * Timer expiry: ALL unbanked coins are lost — complete wipeout.
     */
    public void applyTimerEndPenalty(UUID playerUUID) {
        int lostCoins = getPlayerUnbankedScore(playerUUID);
        clearPlayerUnbankedScore(playerUUID);
        if (lostCoins > 0) {
            plugin.getLogger().info("Timer expiry: " + playerUUID + " lost " + lostCoins + " unbanked coins (trapped)");
            Player player = org.bukkit.Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                player.sendMessage(Component.text("You lost " + lostCoins + " unbanked coins!", NamedTextColor.RED));
            }
        }
    }

}