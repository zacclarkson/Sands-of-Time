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

    // Key is no longer defined here, retrieved from GameManager
    private final NamespacedKey segmentIdKey;

    // TODO: Add tracking for player unbanked scores
    private Map<UUID, Integer> playerUnbankedScores = new HashMap<>();

    public ScoreManager(TeamManager teamManager, GameManager gameManager, Plugin plugin) {
        this.teamManager = teamManager;
        this.gameManager = gameManager;
        this.plugin = plugin; // Keep for logging

        // Get the key from GameManager instead of creating it here
        this.segmentIdKey = gameManager.getSegmentIdKey(); // Assumes GameManager has this getter
        if (this.segmentIdKey == null) {
             // Handle error: GameManager didn't provide the key
             plugin.getLogger().severe("ScoreManager could not retrieve segmentIdKey from GameManager!");
             // Throw an exception or handle appropriately
        }
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
        if (player == null || coinItem == null || baseCoinValue <= 0) {
            return;
        }

        int scaledValue = calculateScaledValue(coinItem, baseCoinValue);

        // Update player's unbanked score tracking
        updatePlayerUnbankedScore(player.getUniqueId(), scaledValue);

        // Optional: Send feedback message with scaled value
        // player.sendMessage(Component.text("+" + scaledValue + " Coins!", NamedTextColor.GOLD));

        plugin.getLogger().log(Level.FINE, player.getName() + " collected coin worth " + scaledValue + " (Base: " + baseCoinValue + ", Unbanked: " + getPlayerUnbankedScore(player.getUniqueId()) + ")");
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
         if (itemEntity != null) {
             playerCollectedCoin(player, itemEntity.getItemStack(), baseCoinValue);
         }
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
        // Ensure the key was initialized correctly
        if (segmentIdKey == null) {
             plugin.getLogger().severe("Segment ID Key is null in calculateScaledValue!");
             return baseValue;
        }

        if (coinItem == null || !coinItem.hasItemMeta()) {
            return baseValue; // Cannot scale without metadata
        }
        ItemMeta meta = coinItem.getItemMeta();
        if (meta == null) return baseValue;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Check if the segment ID tag exists using the key from GameManager
        if (pdc.has(segmentIdKey, PersistentDataType.STRING)) {
            String segmentIdString = pdc.get(segmentIdKey, PersistentDataType.STRING);
            try {
                UUID segmentId = UUID.fromString(segmentIdString);

                // Ask GameManager to find the PlacedSegment instance
                Optional<PlacedSegment> segmentOpt = gameManager.getPlacedSegmentById(segmentId);

                if (segmentOpt.isPresent()) {
                    Segment template = segmentOpt.get().getSegmentTemplate();
                    double multiplier = template.getCoinMultiplier(); // Get multiplier from template
                    int scaledValue = (int) Math.round(baseValue * multiplier);

                    // Optional: Add sanity check/cap for scaled value
                    // scaledValue = Math.max(baseValue, scaledValue); // Ensure it doesn't decrease value

                    return scaledValue;
                } else {
                    // Segment ID found on item, but GameManager couldn't find the segment instance
                    plugin.getLogger().log(Level.WARNING, "Coin item had segment ID " + segmentId + " but PlacedSegment instance was not found in GameManager.");
                }

            } catch (IllegalArgumentException e) {
                // The string stored in PDC was not a valid UUID
                plugin.getLogger().log(Level.WARNING, "Coin item had invalid segment UUID string in PDC: " + segmentIdString);
            }
        } else {
             // Coin item did not have the segment ID tag - maybe spawned incorrectly or is a different item?
             plugin.getLogger().log(Level.FINE, "Collected coin item did not have segment ID metadata.");
        }

        // Default to base value if scaling fails for any reason
        return baseValue;
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
        int currentUnbanked = getPlayerUnbankedScore(playerUUID);
        if (currentUnbanked > 0) {
            int penalty = (int) Math.floor(currentUnbanked * 0.20); // Example: 20% loss, floor
            int remaining = currentUnbanked - penalty;
            setPlayerUnbankedScore(playerUUID, remaining);
            plugin.getLogger().info("Applied death penalty to " + playerUUID + ". Lost " + penalty + " unbanked coins. Remaining: " + remaining);
            // Optional: Drop some physical coins?
        }
    }

    /** Finalizes score when player escapes safely (adds all unbanked to team score) */
    public void playerEscaped(UUID playerUUID) {
        int finalUnbanked = getPlayerUnbankedScore(playerUUID);
        if (finalUnbanked > 0) {
            Player player = Bukkit.getPlayer(playerUUID); // Need player object to get team
            if(player != null) {
                 SoTTeam team = gameManager.getActiveTeamForPlayer(player); // Use GameManager method
                 if (team != null) {
                     team.addBankedScore(finalUnbanked); // Add directly to team's banked score
                     plugin.getLogger().info("Player " + playerUUID + " escaped with " + finalUnbanked + " coins for team " + team.getTeamName());
                 }
            }
        }
        clearPlayerUnbankedScore(playerUUID); // Clear unbanked score after escaping
    }

    /** Clears unbanked score when player is trapped by timer */
    public void applyTimerEndPenalty(UUID playerUUID) {
        int lostCoins = getPlayerUnbankedScore(playerUUID);
        if (lostCoins > 0) {
             plugin.getLogger().info("Player " + playerUUID + " trapped by timer, losing " + lostCoins + " unbanked coins.");
        }
        clearPlayerUnbankedScore(playerUUID); // Lose all unbanked coins
    }

}