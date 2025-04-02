package com.clarkson.sot.utils; // Or your chosen package

import org.bukkit.Bukkit; // Needed for getting Player objects
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap; // Thread-safe option
import java.util.stream.Collectors;

// Define the PlayerStatus enum needed by the manager
enum PlayerStatus {
    ALIVE_IN_DUNGEON,
    DEAD_AWAITING_REVIVE,
    ESCAPED_SAFE,
    TRAPPED_TIMER_OUT,
    NOT_IN_GAME // Default or for players not participating
}

/**
 * Tracks the state of individual players within the Sands of Time game.
 */
public class PlayerStateManager {

    // Using ConcurrentHashMap for thread safety, especially if accessed by async events
    private final Map<UUID, PlayerStatus> playerStates;

    public PlayerStateManager() {
        // Initialize the map
        this.playerStates = new ConcurrentHashMap<>();
    }

    /**
     * Adds a player to the state manager with an initial status.
     * Should be called when a player joins the game instance.
     *
     * @param player The player to initialize.
     */
    public void initializePlayer(Player player) {
        if (player != null) {
            // Set initial status, assuming they start alive in the dungeon
            playerStates.put(player.getUniqueId(), PlayerStatus.ALIVE_IN_DUNGEON);
        }
    }

    /**
     * Removes a player from tracking.
     * Should be called when a player leaves the game instance entirely.
     *
     * @param player The player to remove.
     */
    public void removePlayer(Player player) {
        if (player != null) {
            playerStates.remove(player.getUniqueId());
        }
    }

     /**
      * Removes a player from tracking by UUID.
      * @param playerUUID The UUID of the player to remove.
      */
     public void removePlayer(UUID playerUUID) {
         if (playerUUID != null) {
             playerStates.remove(playerUUID);
         }
     }

    /**
     * Updates the status of a tracked player.
     *
     * @param player The player whose status is changing.
     * @param status The new PlayerStatus.
     */
    public void updateStatus(Player player, PlayerStatus status) {
        if (player != null && status != null) {
            // Use computeIfPresent or simply put to update only if player is tracked
            // Using put assumes initializePlayer was called, overwrites previous status.
            playerStates.put(player.getUniqueId(), status);
        }
    }

    /**
     * Updates the status of a tracked player by UUID.
     *
     * @param playerUUID The UUID of the player whose status is changing.
     * @param status The new PlayerStatus.
     */
     public void updateStatus(UUID playerUUID, PlayerStatus status) {
         if (playerUUID != null && status != null) {
             // Only update if the player is currently tracked
             playerStates.computeIfPresent(playerUUID, (uuid, oldStatus) -> status);
             // Or use put if you want to add them if they weren't tracked (less safe)
             // playerStates.put(playerUUID, status);
         }
     }

    /**
     * Gets the current status of a player.
     *
     * @param player The player to check.
     * @return The PlayerStatus, or PlayerStatus.NOT_IN_GAME if not tracked.
     */
    public PlayerStatus getStatus(Player player) {
        if (player == null) {
            return PlayerStatus.NOT_IN_GAME;
        }
        // Return NOT_IN_GAME if the player isn't in the map
        return playerStates.getOrDefault(player.getUniqueId(), PlayerStatus.NOT_IN_GAME);
    }

    /**
     * Gets the current status of a player by UUID.
     *
     * @param playerUUID The UUID of the player to check.
     * @return The PlayerStatus, or PlayerStatus.NOT_IN_GAME if not tracked.
     */
     public PlayerStatus getStatus(UUID playerUUID) {
         if (playerUUID == null) {
             return PlayerStatus.NOT_IN_GAME;
         }
         return playerStates.getOrDefault(playerUUID, PlayerStatus.NOT_IN_GAME);
     }


    /**
     * Checks if a player is considered actively playing inside the dungeon.
     * This typically means they are alive or potentially dead but awaiting revive.
     * Excludes players who have escaped or been trapped by the timer ending.
     *
     * @param player The player to check.
     * @return true if the player is considered actively in the dungeon, false otherwise.
     */
    public boolean isPlayerInDungeon(Player player) {
        PlayerStatus status = getStatus(player);
        // Define which statuses mean "actively participating inside"
        return status == PlayerStatus.ALIVE_IN_DUNGEON || status == PlayerStatus.DEAD_AWAITING_REVIVE;
    }

    /**
     * Finds all tracked players currently having a specific status.
     * Note: This returns online Player objects. Players who logged off might
     * still be in the map with a certain status but won't be included here.
     *
     * @param status The PlayerStatus to filter by.
     * @return A List of online Player objects with the specified status.
     */
    public List<Player> getPlayersWithStatus(PlayerStatus status) {
        List<Player> players = new ArrayList<>();
        for (Map.Entry<UUID, PlayerStatus> entry : playerStates.entrySet()) {
            if (entry.getValue() == status) {
                Player player = Bukkit.getPlayer(entry.getKey());
                // Add only if the player is currently online
                if (player != null && player.isOnline()) {
                    players.add(player);
                }
            }
        }
        return players;
    }

    /**
     * Gets the UUIDs of all players with a specific status, regardless of online state.
     * @param status The PlayerStatus to filter by.
     * @return A List of UUIDs.
     */
     public List<UUID> getPlayerUUIDsWithStatus(PlayerStatus status) {
         return playerStates.entrySet().stream()
                 .filter(entry -> entry.getValue() == status)
                 .map(Map.Entry::getKey)
                 .collect(Collectors.toList());
     }

    /**
    * Gets all tracked player UUIDs and their current status.
    * @return A copy of the internal state map.
    */
    public Map<UUID, PlayerStatus> getAllPlayerStates() {
        return new HashMap<>(playerStates); // Return a copy to prevent external modification
    }

    /**
    * Clears all tracked player states.
    * Useful when resetting the game.
    */
    public void clearAllStates() {
        playerStates.clear();
    }
}