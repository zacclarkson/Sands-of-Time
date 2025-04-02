package com.clarkson.sot.utils; // Or your chosen package

import org.bukkit.Bukkit; // For BukkitTask
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin; // Needed to schedule tasks
import org.bukkit.scheduler.BukkitTask; // For the timer task

import com.clarkson.sot.main.GameManager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a team playing the Sands of Time game.
 * Now includes its own independent timer state and controls.
 */
public class SoTTeam {
    // --- General Team Info ---
    private final UUID teamId;
    private final String teamName;
    private final String teamColor;
    private final Set<UUID> memberUUIDs;

    // --- Sands of Time Specific State ---
    private int teamSandCount;
    private int bankedScore;
    private int remainingSeconds; // Each team has its own timer!
    private transient BukkitTask timerTask; // The task ticking this team's timer
    private transient Plugin plugin; // Reference to the main plugin for scheduling
    private transient GameManager gameManager; // Reference to notify on timer end

    // Constants for timer
    private static final int MAX_TIMER_SECONDS = 150; // 2.5 minutes (example)
    private static final int DEFAULT_START_SECONDS = 150; // 2.5 minutes (example)


    public SoTTeam(UUID teamId, String teamName, String teamColor, Plugin plugin, GameManager gameManager) {
        // General Info
        this.teamId = teamId;
        this.teamName = teamName;
        this.teamColor = teamColor;
        this.memberUUIDs = ConcurrentHashMap.newKeySet();

        // SoT Specific State
        this.teamSandCount = 0;
        this.bankedScore = 0;
        this.remainingSeconds = DEFAULT_START_SECONDS; // Start with default time

        // Dependencies for timer task
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    // --- Timer Control Methods ---

    /**
     * Starts the timer countdown for this specific team.
     */
    public void startTimer() {
        if (timerTask != null && !timerTask.isCancelled()) {
            return; // Timer already running
        }
        if (plugin == null || gameManager == null) {
             System.err.println("Error: Cannot start timer for team " + teamName + ". Plugin or GameManager reference missing.");
             return;
        }

        this.timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTimer, 0L, 20L); // Tick every second (20 ticks)
        // You might want an initial delay based on game rules
    }

    /**
     * Stops the timer countdown for this team.
     */
    public void stopTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    /**
     * Called every second by the BukkitTask to decrement the timer.
     */
    private void tickTimer() {
        if (remainingSeconds > 0) {
            remainingSeconds--;
            // Optional: Update team scoreboard/display with remaining time
        }

        if (remainingSeconds <= 0) {
            stopTimer(); // Stop the task
            // Notify the GameManager that this specific team's timer has ended
            gameManager.handleTeamTimerEnd(this);
        }
    }

    /**
     * Adds seconds to this team's timer, capped at the maximum.
     * @param secondsToAdd The number of seconds to add.
     */
    public void addSeconds(int secondsToAdd) {
        if (secondsToAdd <= 0) return;
        this.remainingSeconds = Math.min(this.remainingSeconds + secondsToAdd, MAX_TIMER_SECONDS);
        // Optional: Update display immediately
    }

    /**
     * Gets the remaining seconds on this team's timer.
     * @return Remaining seconds.
     */
    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    // --- Getters for General Info ---
    public UUID getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public String getTeamColor() { return teamColor; }
    public Set<UUID> getMemberUUIDs() { return memberUUIDs; }

    // --- Methods for Team Members ---
    public void addMember(Player player) { memberUUIDs.add(player.getUniqueId()); }
    public void removeMember(Player player) { memberUUIDs.remove(player.getUniqueId()); }

    // --- Methods for SoT Sand Management ---
    public int getSandCount() { return teamSandCount; }
    public void addSand(int amount) { if (amount > 0) this.teamSandCount += amount; }
    public boolean tryUseSand(int amount) {
        if (amount > 0 && this.teamSandCount >= amount) {
            this.teamSandCount -= amount;
            return true;
        }
        return false;
    }

    // --- Methods for SoT Score Management ---
    public int getBankedScore() { return bankedScore; }
    public void addBankedScore(int score) { if (score > 0) this.bankedScore += score; }

    /**
     * Resets the game-specific state for a new Sands of Time game.
     */
     public void resetForNewGame() {
         stopTimer(); // Make sure the old timer task is cancelled
         this.teamSandCount = 0;
         this.bankedScore = 0;
         this.remainingSeconds = DEFAULT_START_SECONDS; // Reset timer duration
         // Should plugin/gameManager references be cleared or reused? Depends on lifecycle.
     }

    // toString method...
}