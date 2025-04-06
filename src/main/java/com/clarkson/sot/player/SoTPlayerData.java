package com.clarkson.sot.player; // Example package

import org.bukkit.entity.Player;
import java.util.UUID;

/**
 * Holds custom data specific to a player's participation
 * in the Sands of Time game.
 */
public class SoTPlayerData {

    private final UUID playerUUID;
    private final String playerName; // Store name for convenience (UUID is primary key)
    private final Player player; // Reference to the Bukkit Player object

    // --- Game Specific Stats ---
    private int unbankedCoins;
    private int totalCoinsCollected; // Could track total before banking/death loss
    private int sandCollected;
    private int sandUsedForTimer;
    private int revivesPerformed;
    private int timesDied;
    private int monstersKilled;
    private int bankedCoins;
    private long timeEnteredDungeon = -1; // Timestamp when they entered
    private long timeExitedDungeon = -1; // Timestamp when they escaped/trapped
    // Add stats for vaults opened, keys found, etc.
    // private Set<VaultColor> keysFound = new HashSet<>();
    // private Set<VaultColor> vaultsOpenedByPlayer = new HashSet<>();


    /**
     * Constructor for player game data.
     * @param player The Bukkit Player object.
     */
    public SoTPlayerData(Player player) {

        this.playerUUID = player.getUniqueId();
        this.playerName = player.getName();
        this.player = player; // Store the reference to the player object
        resetStats(); // Initialize stats to default values
    }

    /**
     * Resets game-specific stats to their initial values.
     * Useful when starting a new game instance.
     */
    public void resetStats() {
        this.unbankedCoins = 0;
        this.totalCoinsCollected = 0;
        this.sandCollected = 0;
        this.sandUsedForTimer = 0;
        this.revivesPerformed = 0;
        this.timesDied = 0;
        this.timeEnteredDungeon = -1;
        this.timeExitedDungeon = -1;
        this.bankedCoins = 0;
        this.monstersKilled = 0;
        // Reset other stats like keysFound.clear();
    }

    // --- Getters ---
    public UUID getPlayerUUID() { return playerUUID; }
    public String getPlayerName() { return playerName; }
    public int getUnbankedCoins() { return unbankedCoins; }
    public int getTotalCoinsCollected() { return totalCoinsCollected; }
    public int getSandCollected() { return sandCollected; }
    public int getTimesDied() { return timesDied; }
    public int getBankedScore() { return bankedCoins; }
    public int getMonstersKilled() { return monstersKilled; }
    public int getSandUsedForTimer() {return sandUsedForTimer;}
    public int getRevivesPerformed() { return revivesPerformed;}
    public Player getPlayer() { return player; } // Return the Bukkit Player object




    // --- Setters / Modifiers ---
    public void addUnbankedCoins(int amount) {
        if (amount > 0) {
            this.unbankedCoins += amount;
            this.totalCoinsCollected += amount; // Also track total collected if desired
        }
    }

    public void setUnbankedCoins(int amount) {
        this.unbankedCoins = Math.max(0, amount); // Ensure it doesn't go below zero
    }

    public int takeUnbankedCoins(int amount) {
        int taken = Math.min(this.unbankedCoins, Math.max(0, amount));
        this.unbankedCoins -= taken;
        return taken;
    }

    public void incrementSandCollected(int amount) {
        if (amount > 0) this.sandCollected += amount;
    }

    public void incrementSandUsedForTimer(int amount) {
         if (amount > 0) this.sandUsedForTimer += amount;
    }

     public void incrementRevivesPerformed() {
         this.revivesPerformed++;
    }

    public void incrementDeaths() {
        this.timesDied++;
    }
    public void incrementMonstersKilled() {
        this.monstersKilled++;
    }
    public void addBankedCoins(int coins) {
        this.bankedCoins = this.bankedCoins + coins;
    }

    public void markDungeonEntry() {
        this.timeEnteredDungeon = System.currentTimeMillis();
    }

     public void markDungeonExit() {
         this.timeExitedDungeon = System.currentTimeMillis();
     }

     public long getTimeSurvivedMillis() {
         if (timeEnteredDungeon == -1) return 0;
         long end = (timeExitedDungeon != -1) ? timeExitedDungeon : System.currentTimeMillis(); // Use current time if still inside
         return Math.max(0, end - timeEnteredDungeon);
     }
}
