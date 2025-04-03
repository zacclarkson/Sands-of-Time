package com.clarkson.sot.scoring; // Example package

import com.clarkson.sot.main.GameManager; // Need GameManager access
import com.clarkson.sot.dungeon.segment.PlacedSegment; // Need PlacedSegment
import com.clarkson.sot.dungeon.segment.Segment; // Need Segment template
import com.clarkson.sot.utils.SoTTeam;
import com.clarkson.sot.utils.TeamManager; // Assuming dependency

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item; // If using dropped items
import org.bukkit.entity.Player; // Import Player
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin; // ScoreManager might not need Plugin directly anymore

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.HashMap; // Import HashMap
import java.util.Map; // Import Map


public class ScoreManager {
    private final TeamManager teamManager;
    private final GameManager gameManager; // Dependency to access dungeon layout and the key
    private final Plugin plugin; // Still useful for logging


    // TODO: Add tracking for player unbanked scores
    private Map<UUID, Integer> playerUnbankedScores = new HashMap<>();

    public ScoreManager(TeamManager teamManager, GameManager gameManager, Plugin plugin) {
        this.teamManager = teamManager;
        this.gameManager = gameManager;
        this.plugin = plugin; // Keep for logging
    }

    /**
     * Processes a collected coin, calculates its scaled value based on its origin segment,
     * and updates the player's unbanked score.
     *
     * @param player The player who collected the coin.
     * @param coinItem The ItemStack representing the collected coin.
     * @param baseCoinValue The base value of this coin type before scaling.
     */
    public void playerCollectedCoin(Player player, ItemStack coinItem, int baseCoinValue) {
    }

     /**
      * Processes a collected coin from a dropped Item entity.
      * Extracts the ItemStack and calls the primary method.
      *
      * @param player The player who collected the coin.
      * @param itemEntity The Item entity that was picked up.
      * @param baseCoinValue The base value of this coin type.
      */
     public void playerCollectedCoin(Player player, Item itemEntity, int baseCoinValue) {

     }


    /**
     * Calculates the scaled value of a coin based on metadata attached to the ItemStack.
     * It retrieves the originating PlacedSegment ID from the item's metadata,
     * finds that segment via GameManager, and uses the segment's coinMultiplier.
     *
     * @param coinItem The coin ItemStack containing the segment ID in its PDC.
     * @param baseValue The base value of the coin before scaling.
     * @return The calculated scaled value, or the baseValue if scaling fails.
     */
    private int calculateScaledValue(ItemStack coinItem, int baseValue) {
        return -1;
    }

    // --- Unbanked Score Tracking ---
    // Basic implementation - consider thread safety if accessed async
    public void updatePlayerUnbankedScore(UUID playerUUID, int delta) {
        playerUnbankedScores.put(playerUUID, getPlayerUnbankedScore(playerUUID) + delta);
    }

    public int getPlayerUnbankedScore(UUID playerUUID) {
        return playerUnbankedScores.getOrDefault(playerUUID, 0);
    }

    public void setPlayerUnbankedScore(UUID playerUUID, int amount) {
         playerUnbankedScores.put(playerUUID, Math.max(0, amount)); // Ensure score doesn't go below 0
    }

    public void clearPlayerUnbankedScore(UUID playerUUID) {
        playerUnbankedScores.remove(playerUUID);
    }

    public void clearAllUnbankedScores() {
        playerUnbankedScores.clear();
    }


    // --- Other ScoreManager methods ---

    /** Applies death penalty (e.g., lose 20% of unbanked coins) */
    public void applyDeathPenalty(UUID playerUUID) {

    }

    /** Finalizes score when player escapes safely (adds all unbanked to team score) */
    public void playerEscaped(UUID playerUUID) {

    }

    /** Clears unbanked score when player is trapped by timer */
    public void applyTimerEndPenalty(UUID playerUUID) {

    }

}