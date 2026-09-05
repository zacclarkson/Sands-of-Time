package com.clarkson.sot.player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

public class SoTPlayerManager {

    private final List<SoTPlayerData> players = new ArrayList<>();
    private final Logger logger;

    /**
     * Constructor for the player manager.
     * Initializes the player list and sets the plugin reference.
     *
     * @param plugin The plugin instance to associate with this manager.
     */
    public SoTPlayerManager(Plugin plugin) {
        this.logger = plugin.getLogger();
        logger.info("Player manager initialized with plugin: " + plugin.getName());
    }

    /**
     * Initializes all players in the manager.
     */
    public void initializePlayers() {
        for (SoTPlayerData playerData : players) {
            initializePlayer(playerData.getPlayer());
        }
        logger.info("Player manager initialized.");
    }

    /**
     * Initializes a specific player.
     * If the player is not already in the list, it adds a new entry.
     *
     * @param player The Player to initialize.
     */
    public void initializePlayer(Player player) {
        if (!hasPlayer(player)) {
            players.add(new SoTPlayerData(player));
        }
        logger.fine("Player initialized: " + player.getName());
    }

    /**
     * Adds a player to the manager.
     *
     * @param playerData The SoTPlayerData object to add.
     */
    public void addPlayer(SoTPlayerData playerData) {
        if (!hasPlayer(playerData.getPlayer())) {
            players.add(playerData);
        }
    }

    /**
     * Retrieves the player data for a specific player.
     *
     * @param player The Player.
     * @return The SoTPlayerData object, or null if not found.
     */
    public SoTPlayerData getPlayerData(Player player) {
        return getPlayerData(player.getUniqueId());
    }

    /**
     * Retrieves the player data for a player UUID.
     *
     * <p>UUID rather than {@link Player} identity is the lookup key throughout: a player who
     * reconnects mid-round is handed a fresh {@code Player} instance, and matching on the object
     * would silently stop finding their data.
     *
     * @param playerUUID The player's UUID.
     * @return The SoTPlayerData object, or null if not found.
     */
    public SoTPlayerData getPlayerData(UUID playerUUID) {
        for (SoTPlayerData playerData : players) {
            if (playerData.getPlayerUUID().equals(playerUUID)) {
                return playerData;
            }
        }
        return null;
    }

    /**
     * Removes a player from the manager.
     *
     * @param player The Player to remove.
     */
    public void removePlayer(Player player) {
        players.removeIf(playerData -> playerData.getPlayerUUID().equals(player.getUniqueId()));
    }

    /**
     * Forgets every player.
     *
     * <p>Called by end-of-round teardown: {@link SoTPlayerData} holds a strong reference to the
     * Bukkit {@link Player}, so carrying the list into the next round would both leak those and
     * report last round's stats.
     */
    public void clearAll() {
        players.clear();
    }

    /**
     * Checks if a player exists in the manager.
     *
     * @param player The Player to check.
     * @return True if the player exists, false otherwise.
     */
    public boolean hasPlayer(Player player) {
        return players.stream().anyMatch(playerData -> playerData.getPlayerUUID().equals(player.getUniqueId()));
    }

    /**
     * Gets the total number of players in the manager.
     *
     * @return The number of players.
     */
    public int getPlayerCount() {
        return players.size();
    }
}
