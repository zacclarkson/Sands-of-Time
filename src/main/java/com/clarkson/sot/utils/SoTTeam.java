package com.clarkson.sot.utils; // Or your chosen package

import com.clarkson.sot.main.GameManager; // Assuming GameManager is in .main package
import com.clarkson.sot.visuals.VisualSandTimerDisplay; // Import the visual display class

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Represents a team playing the Sands of Time game.
 * Includes general team info, game-specific state (sand, score),
 * and manages its own independent timer (logic and visual link).
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
    private int remainingSeconds; // Authoritative timer value for this team

    // --- Timer Control ---
    // transient: BukkitTask is not serializable and specific to a runtime instance
    private transient BukkitTask logicTimerTask; // Task for decrementing remainingSeconds
    private transient Plugin plugin; // Reference to the main plugin for scheduling
    private transient GameManager gameManager; // Reference to notify on timer end

    // --- Visual Timer Link ---
    private transient VisualSandTimerDisplay visualTimerDisplay; // Handles physical sand blocks

    // Constants for timer
    private static final int MAX_TIMER_SECONDS = 150; // 2.5 minutes (example, can be configurable)
    private static final int DEFAULT_START_SECONDS = 150; // Example start time
    private static final int SECONDS_PER_BLOCK_VISUAL = 10; // How many seconds one visual block represents

    /**
     * Constructor for SoTTeam.
     *
     * @param teamId      Unique ID for the team.
     * @param teamName    Display name of the team.
     * @param teamColor   Color identifier for the team.
     * @param plugin      The main plugin instance (for scheduling tasks).
     * @param gameManager The central GameManager (for notifications).
     * @param visualBottom Location below the lowest sand block for the visual timer.
     * @param visualTop    Location of the highest sand block for the visual timer.
     */
    public SoTTeam(UUID teamId, String teamName, String teamColor, Plugin plugin, GameManager gameManager, Location visualBottom, Location visualTop) {
        // General Info
        this.teamId = teamId;
        this.teamName = teamName;
        this.teamColor = teamColor;
        this.memberUUIDs = ConcurrentHashMap.newKeySet();

        // Dependencies
        this.plugin = plugin;
        this.gameManager = gameManager;

        // Create the visual display manager instance for this team
        if (visualBottom != null && visualTop != null && plugin != null) {
             this.visualTimerDisplay = new VisualSandTimerDisplay(plugin, this, visualBottom, visualTop);
        } else {
             plugin.getLogger().log(Level.WARNING, "Visual timer locations/plugin not provided correctly for team " + teamName + ". Visual timer disabled.");
             this.visualTimerDisplay = null;
        }

        resetForNewGame(); // Initialize game state (sand, score, timer)
    }

    // --- Timer Control Methods ---

    /**
     * Starts the logical timer countdown and visual updates for this team.
     */
    public void startTimer() {
        // Prevent starting if already running or dependencies are missing
        if (logicTimerTask != null && !logicTimerTask.isCancelled()) {
            plugin.getLogger().log(Level.INFO, "Logical timer already running for team: " + teamName);
            return;
        }
        if (plugin == null || gameManager == null) {
             plugin.getLogger().log(Level.SEVERE, "Cannot start timer for team " + teamName + ". Plugin or GameManager reference missing.");
             return;
        }

        plugin.getLogger().log(Level.INFO, "Starting logical timer for team: " + teamName + " with " + remainingSeconds + "s");
        // Schedule the logical timer task to run every second (20 ticks)
        this.logicTimerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickLogicTimer, 20L, 20L); // Start after 1s, repeat every 1s

        // Start the visual timer updates as well
        if (visualTimerDisplay != null) {
            visualTimerDisplay.startVisualUpdates();
        }
    }

    /**
     * Stops the logical timer countdown and visual updates for this team.
     */
    public void stopTimer() {
        // Stop the logical timer task
        if (logicTimerTask != null && !logicTimerTask.isCancelled()) {
            logicTimerTask.cancel();
            plugin.getLogger().log(Level.INFO, "Stopped logical timer for team: " + teamName);
        }
        logicTimerTask = null;

        // Stop the visual timer updates
        if (visualTimerDisplay != null) {
            visualTimerDisplay.stopVisualUpdates();
        }
    }

    /**
     * Called every second by the BukkitTask to decrement the logical timer.
     * Notifies GameManager when the timer reaches zero.
     */
    private void tickLogicTimer() {
        if (remainingSeconds > 0) {
            remainingSeconds--;
            // Optional: Update team scoreboard/display with remaining time
            // Example: gameManager.getScoreboardManager().updateTeamTime(teamId, remainingSeconds);
        }

        // Check if timer has run out
        if (remainingSeconds <= 0) {
            // Stop self (both logic and visual tasks via stopTimer)
            stopTimer();
            plugin.getLogger().log(Level.WARNING, "Logical timer expired for team: " + teamName);
            // Notify the GameManager to handle consequences for this team
            gameManager.handleTeamTimerEnd(this);
        }
    }

    /**
     * Adds seconds to this team's logical timer, capped at the maximum.
     * Also triggers synchronization of the visual timer display.
     * @param secondsToAdd The number of seconds to add (must be positive).
     */
    public void addSeconds(int secondsToAdd) {
        if (secondsToAdd <= 0 || remainingSeconds >= MAX_TIMER_SECONDS) {
             // Don't add time if input is invalid or timer is already full
             return;
        }

        int oldSeconds = this.remainingSeconds;
        // Calculate new time, ensuring it doesn't exceed the maximum
        this.remainingSeconds = Math.min(oldSeconds + secondsToAdd, MAX_TIMER_SECONDS);
        int actualSecondsAdded = this.remainingSeconds - oldSeconds; // How many seconds were actually added

        // If time was actually added, update the visual display
        if (actualSecondsAdded > 0 && visualTimerDisplay != null) {
            // Sync the visuals to the new time. This handles adding the correct number of blocks.
            visualTimerDisplay.syncVisualState();
        }

        plugin.getLogger().log(Level.INFO, "Added " + actualSecondsAdded + "s to team " + teamName + " timer. New time: " + this.remainingSeconds + "s");
    }

    /**
     * Gets the remaining seconds on this team's logical timer.
     * @return Remaining seconds.
     */
    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    /**
     * Resets the game-specific state (sand, score, timer) for a new Sands of Time game.
     * Stops any existing timer task and resets the visual display.
     */
     public void resetForNewGame() {
         stopTimer(); // Ensure logic and visual tasks are stopped
         this.teamSandCount = 0;
         this.bankedScore = 0;
         this.remainingSeconds = DEFAULT_START_SECONDS; // Reset timer duration

         // Reset the visual display to match the starting time
         if (visualTimerDisplay != null) {
             // Ensure visual state reflects the reset time
             // Bukkit actions should be synced if called from async later
             Bukkit.getScheduler().runTask(plugin, visualTimerDisplay::syncVisualState);
         }
         plugin.getLogger().log(Level.INFO, "Reset game state for team: " + teamName);
     }

    // --- Getters for General Info ---
    public UUID getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public String getTeamColor() { return teamColor; }
    public Set<UUID> getMemberUUIDs() { return memberUUIDs; } // Consider returning an unmodifiable view

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

    @Override
    public String toString() {
        return "SoTTeam{" +
                "teamId=" + teamId +
                ", teamName='" + teamName + '\'' +
                ", teamColor='" + teamColor + '\'' +
                ", members=" + memberUUIDs.size() +
                ", sand=" + teamSandCount +
                ", score=" + bankedScore +
                ", secondsLeft=" + remainingSeconds + // Added timer state
                '}';
    }
}
