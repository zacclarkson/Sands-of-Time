package com.clarkson.sot.timer;

import com.clarkson.sot.timer.VisualSandTimerDisplay; // Assuming this path is correct
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Manages the countdown logic for a single team's timer in Sands of Time.
 * Handles starting, stopping, adding time, and notifying on expiry via a callback.
 * Also interacts with a VisualSandTimerDisplay to keep visuals synced.
 */
public class TeamTimer {

    /**
     * Functional interface for a callback action to be performed when the timer expires.
     */
    @FunctionalInterface
    public interface TimerCallback {
        /** Called when the timer reaches zero. */
        void onTimerExpire();
    }

    // --- Dependencies ---
    private final Plugin plugin;
    private final TimerCallback expiryCallback; // Called when timer hits 0
    private final VisualSandTimerDisplay visualNotifier; // To sync visual display (can be null)

    // --- Configuration ---
    private final int maxSeconds; // Maximum time allowed
    private final long timerIntervalTicks; // Ticks between each second decrement (e.g., 20L)

    // --- State ---
    private int remainingSeconds; // Current time left
    private BukkitTask timerTask; // Reference to the scheduled Bukkit task
    private boolean isRunning; // Flag indicating if the timer countdown is active

    // --- Constants (Example Defaults - Consider making these configurable) ---
    public static final int DEFAULT_MAX_TIMER_SECONDS = 150; // Default max time (2m 30s)
    public static final long DEFAULT_TIMER_INTERVAL_TICKS = 20L; // Default interval (1 second)

    /**
     * Constructor for TeamTimer.
     *
     * @param plugin           The Bukkit plugin instance for scheduling tasks.
     * @param expiryCallback   A callback function to execute when the timer expires.
     * @param visualNotifier   The VisualSandTimerDisplay associated with this timer (can be null).
     * @param startSeconds     The initial time in seconds.
     * @param maxSeconds       The maximum time the timer can hold in seconds.
     * @param intervalTicks    The number of server ticks between each second decrement (usually 20).
     */
    public TeamTimer(Plugin plugin, TimerCallback expiryCallback, VisualSandTimerDisplay visualNotifier,
                     int startSeconds, int maxSeconds, long intervalTicks) {

        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.expiryCallback = Objects.requireNonNull(expiryCallback, "Expiry callback cannot be null");
        this.visualNotifier = visualNotifier; // Allow null visual notifier

        // Validate and store configuration
        this.maxSeconds = Math.max(1, maxSeconds); // Ensure max is at least 1 second
        this.timerIntervalTicks = Math.max(1L, intervalTicks); // Ensure interval is at least 1 tick

        // Initialize state
        // Ensure startSeconds is within valid bounds (0 to maxSeconds)
        this.remainingSeconds = Math.max(0, Math.min(startSeconds, this.maxSeconds));
        this.isRunning = false;
        this.timerTask = null;

        plugin.getLogger().config("TeamTimer created: start=" + startSeconds + "s, max=" + this.maxSeconds + "s, interval=" + this.timerIntervalTicks + "t");
    }

    /**
     * Starts the timer countdown. Does nothing if already running.
     * Syncs the visual display before starting the task.
     */
    public void start() {
        // Prevent starting multiple tasks
        if (isRunning || (timerTask != null && !timerTask.isCancelled())) {
            plugin.getLogger().log(Level.FINE, "Timer start requested but already running.");
            return;
        }

        // Ensure visual state matches current time before starting countdown
        syncVisual();

        plugin.getLogger().log(Level.INFO, "Starting timer with " + remainingSeconds + "s remaining.");
        isRunning = true;
        // Schedule the repeating task using the plugin's scheduler
        this.timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, timerIntervalTicks, timerIntervalTicks);

        // Start the visual updates if a notifier is attached
        if (visualNotifier != null) {
            visualNotifier.startVisualUpdates();
        }
    }

    /**
     * Stops the timer countdown task and visual updates.
     */
    public void stop() {
        boolean wasRunning = isRunning; // Track if it was considered running

        // Cancel the Bukkit task if it exists and is active
        if (timerTask != null) {
             if (!timerTask.isCancelled()) {
                 timerTask.cancel();
             }
             timerTask = null; // Clear the reference
        }
        isRunning = false; // Set running state to false

        // Stop visual updates if a notifier is attached
        if (visualNotifier != null) {
            visualNotifier.stopVisualUpdates();
        }

        if (wasRunning) {
             plugin.getLogger().log(Level.INFO, "Stopped timer.");
        }
    }

    /**
     * Resets the timer to a specific starting value and ensures it is stopped.
     * Also syncs the visual display to the new time.
     *
     * @param startSeconds The number of seconds to reset the timer to.
     */
    public void reset(int startSeconds) {
        stop(); // Ensure any existing task is stopped first
        // Set remaining time, respecting bounds 0 to maxSeconds
        this.remainingSeconds = Math.max(0, Math.min(startSeconds, this.maxSeconds));
        plugin.getLogger().log(Level.INFO, "Timer reset to " + remainingSeconds + "s.");
        // Update the visual display immediately to reflect the reset time
        syncVisual();
    }

    /**
     * Internal method called periodically by the Bukkit scheduler task.
     * Decrements remaining time and triggers the expiry callback if time runs out.
     */
    private void tick() {
        // Safety check: If something external set isRunning to false, stop the task.
        if (!isRunning) {
            plugin.getLogger().warning("Timer tick called while isRunning is false. Stopping task.");
            stop();
            return;
        }

        // Decrement time if positive
        if (remainingSeconds > 0) {
            remainingSeconds--;
            // Optional: Add a TickCallback here if external components need second-by-second updates
            // tickCallback.onTick(remainingSeconds);
        }

        // Check if timer has reached zero
        if (remainingSeconds <= 0) {
            plugin.getLogger().log(Level.WARNING, "Timer expired!");
            stop(); // Stop the timer task
            // Execute the provided callback function
            try {
                 expiryCallback.onTimerExpire();
            } catch (Exception e) {
                 plugin.getLogger().log(Level.SEVERE, "Error executing timer expiry callback", e);
            }
        }
    }

    /**
     * Adds seconds to the timer's remaining time, up to the configured maximum.
     * Triggers a visual sync if time was successfully added.
     *
     * @param amount The number of seconds to add (must be positive).
     */
    public void addSeconds(int amount) {
        if (amount <= 0) {
            plugin.getLogger().log(Level.WARNING, "Attempted to add non-positive seconds: " + amount);
            return;
        }

        int oldSeconds = this.remainingSeconds;
        // Add time, but ensure it doesn't exceed maxSeconds
        this.remainingSeconds = Math.min(oldSeconds + amount, maxSeconds);
        int actualAdded = this.remainingSeconds - oldSeconds;

        if (actualAdded > 0) {
            plugin.getLogger().log(Level.INFO, "Added " + actualAdded + "s. New time: " + remainingSeconds + "s");
            // Sync visual display to show the added time
            syncVisual();
        } else {
            plugin.getLogger().log(Level.FINE, "Timer already at max (" + maxSeconds + "s). Cannot add " + amount + "s.");
            // Optional: Provide feedback to player who added sand (e.g., "Timer full!")
        }
    }

    /**
     * Gets the number of seconds currently remaining on the timer.
     *
     * @return The remaining time in seconds.
     */
    public int getSecondsLeft() {
        return remainingSeconds;
    }

    /**
     * Checks if the timer's countdown task is currently scheduled and running.
     *
     * @return True if the timer is actively counting down, false otherwise.
     */
    public boolean isRunning() {
        // Check both the flag and the task state for robustness
        // A task might exist but be cancelled, or isRunning might be false unexpectedly.
        return isRunning && timerTask != null && !timerTask.isCancelled();
    }

    /**
     * Helper method to trigger synchronization of the visual display.
     * Ensures the visual update happens on the main server thread if called asynchronously.
     */
    private void syncVisual() {
        if (visualNotifier != null) {
            // Check if we are already on the main thread
            if (!Bukkit.isPrimaryThread()) {
                // If not, schedule the sync task to run on the main thread
                Bukkit.getScheduler().runTask(plugin, visualNotifier::syncVisualState);
            } else {
                // If already on the main thread, call directly
                visualNotifier.syncVisualState();
            }
        }
    }
}
