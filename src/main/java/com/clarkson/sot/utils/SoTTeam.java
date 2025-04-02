package com.clarkson.sot.utils; // Or your chosen package

import org.bukkit.Bukkit; // For BukkitTask
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin; // Needed to schedule tasks
import org.bukkit.scheduler.BukkitTask; // For the timer task

import com.clarkson.sot.main.GameManager;

import java.util.logging.Level;
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
    // transient: BukkitTask is not serializable and specific to a runtime instance
    private transient BukkitTask timerTask;
    private transient Plugin plugin; // Reference to the main plugin for scheduling
    private transient GameManager gameManager; // Reference to notify on timer end

    // Constants for timer
    private static final int MAX_TIMER_SECONDS = 150; // 2.5 minutes (example, can be configurable)
    private static final int DEFAULT_START_SECONDS = 150; // Example start time

    public SoTTeam(UUID teamId, String teamName, String teamColor, Plugin plugin, GameManager gameManager) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.teamColor = teamColor;
        this.memberUUIDs = ConcurrentHashMap.newKeySet();
        this.plugin = plugin;
        this.gameManager = gameManager;
        resetForNewGame(); // Initialize state
    }

    // --- Timer Control Methods ---

    /**
     * Starts the timer countdown for this specific team.
     * Ensures plugin and gameManager references are available.
     */
    public void startTimer() {
        // Prevent starting if already running or dependencies are missing
        if (timerTask != null && !timerTask.isCancelled()) {
            plugin.getLogger().log(Level.INFO, "Timer already running for team: " + teamName);
            return;
        }
        if (plugin == null || gameManager == null) {
             plugin.getLogger().log(Level.SEVERE, "Cannot start timer for team " + teamName + ". Plugin or GameManager reference missing.");
             return;
        }

        plugin.getLogger().log(Level.INFO, "Starting timer for team: " + teamName + " with " + remainingSeconds + "s");
        // Schedule a task to run every second (20 ticks)
        this.timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTimer, 20L, 20L); // Start after 1s, repeat every 1s
    }

    /**
     * Stops the timer countdown for this team if it's running.
     */
    public void stopTimer() {
        if (timerTask != null && !timerTask.isCancelled()) {
            timerTask.cancel();
            plugin.getLogger().log(Level.INFO, "Stopped timer for team: " + teamName);
        }
        timerTask = null; // Clear the task reference
    }

    /**
     * Called every second by the BukkitTask to decrement the timer.
     * Notifies GameManager when the timer reaches zero.
     */
    private void tickTimer() {
        if (remainingSeconds > 0) {
            remainingSeconds--;
            // TODO: Update team scoreboard/display with remaining time if applicable
            // Example: gameManager.getScoreboardManager().updateTeamTime(teamId, remainingSeconds);
        }

        // Check if timer has run out
        if (remainingSeconds <= 0) {
            stopTimer(); // Stop this task
            plugin.getLogger().log(Level.INFO, "Timer expired for team: " + teamName);
            // Notify the GameManager to handle consequences
            gameManager.handleTeamTimerEnd(this);
        }
    }

    /**
     * Adds seconds to this team's timer, capped at the maximum allowed time.
     * @param secondsToAdd The number of seconds to add (must be positive).
     */
    public void addSeconds(int secondsToAdd) {
        if (secondsToAdd <= 0) return;
        int oldSeconds = this.remainingSeconds;
        // Calculate new time, ensuring it doesn't exceed the maximum
        this.remainingSeconds = Math.min(oldSeconds + secondsToAdd, MAX_TIMER_SECONDS);
        plugin.getLogger().log(Level.INFO, "Added " + (this.remainingSeconds - oldSeconds) + "s to team " + teamName + " timer. New time: " + this.remainingSeconds + "s");
        // TODO: Update display immediately if needed
    }

    /**
     * Gets the remaining seconds on this team's timer.
     * @return Remaining seconds.
     */
    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    /**
     * Resets the game-specific state for a new Sands of Time game.
     * Stops any existing timer task.
     */
     public void resetForNewGame() {
         stopTimer(); // Ensure the old timer task is cancelled
         this.teamSandCount = 0;
         this.bankedScore = 0;
         this.remainingSeconds = DEFAULT_START_SECONDS; // Reset timer duration
     }

    // --- Other SoTTeam methods (Getters, Sand/Score management) ---
    public UUID getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    // ... other getters ...
    public int getSandCount() { return teamSandCount; }
    public void addSand(int amount) { if (amount > 0) this.teamSandCount += amount; }
    public boolean tryUseSand(int amount) {
        if (amount > 0 && this.teamSandCount >= amount) {
            this.teamSandCount -= amount;
            return true;
        }
        return false;
    }
    public int getBankedScore() { return bankedScore; }
    public void addBankedScore(int score) { if (score > 0) this.bankedScore += score; }
    // ... addMember, removeMember etc ...

    public Set<UUID> getMemberUUIDs() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMemberUUIDs'");
    }

    public void removeMember(Player player) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'removeMember'");
    }

}
