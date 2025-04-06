package com.clarkson.sot.player;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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
    public SoTPlayerManager(JavaPlugin plugin) {
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
        logger.info("Player initialized: " + player.getName());
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
        for (SoTPlayerData playerData : players) {
            if (playerData.getPlayer().equals(player)) {
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
        players.removeIf(playerData -> playerData.getPlayer().equals(player));
    }

    /**
     * Checks if a player exists in the manager.
     *
     * @param player The Player to check.
     * @return True if the player exists, false otherwise.
     */
    public boolean hasPlayer(Player player) {
        return players.stream().anyMatch(playerData -> playerData.getPlayer().equals(player));
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
