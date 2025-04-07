package com.clarkson.sot.utils;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.timer.TeamTimer; // Import the Timer class
import com.clarkson.sot.timer.VisualSandTimerDisplay; // Import Visual Display

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.logging.Level;

/**
 * Represents an active team participating in a Sands of Time game instance.
 * Holds team definition, member UUIDs, and game-specific state like sand count and banked score.
 * Manages the team's timer via a dedicated TeamTimer object.
 */
public class SoTTeam {

    // --- Dependencies & Static Info ---
    private final Plugin plugin;
    private final GameManager gameManager;
    private final TeamDefinition teamDefinition;

    // --- Members ---
    private final Set<UUID> memberUUIDs;

    // --- Game State ---
    private int teamSandCount;
    private int bankedScore;

    // --- Timer Control ---
    private transient VisualSandTimerDisplay visualTimerDisplay;
    private final TeamTimer teamTimer;

    // --- Constants ---
    // Default start time for this team
    private static final int DEFAULT_START_SECONDS = 150; // Example value

    /**
     * Constructor for an active SoTTeam instance.
     * Creates and initializes the dedicated TeamTimer and other team state.
     * Dependencies like Plugin, GameManager, TeamDefinition, and visual timer locations are required.
     *
     * @param teamDefinition    The static definition of the team (ID, Name, Color).
     * @param plugin            The main plugin instance (for scheduling, logging).
     * @param gameManager       The GameManager instance (for callbacks, context).
     * @param visualTimerBottom The location below the lowest visual sand block for the timer display.
     * @param visualTimerTop    The location of the highest possible visual sand block for the timer display.
     */
    public SoTTeam(TeamDefinition teamDefinition, Plugin plugin, GameManager gameManager, Location visualTimerBottom, Location visualTimerTop) {
        this.teamDefinition = Objects.requireNonNull(teamDefinition, "TeamDefinition cannot be null");
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.gameManager = Objects.requireNonNull(gameManager, "GameManager cannot be null");

        this.memberUUIDs = new HashSet<>();

        // 1. Create Visual Timer Display first
        if (visualTimerBottom != null && visualTimerTop != null && visualTimerBottom.getWorld() != null && visualTimerTop.getWorld() != null) {
            this.visualTimerDisplay = new VisualSandTimerDisplay(plugin, this, visualTimerBottom, visualTimerTop);
        } else {
            plugin.getLogger().log(Level.WARNING, "Visual timer locations invalid for team " + teamDefinition.getName() + ". Visual timer disabled.");
            this.visualTimerDisplay = null;
        }

        // 2. Create the TeamTimer instance using constants from TeamTimer class
        this.teamTimer = new TeamTimer(
                plugin,
                // Lambda callback: When timer expires, call GameManager's handler
                () -> this.gameManager.handleTeamTimerEnd(this, true),
                this.visualTimerDisplay, // Pass the visual display (can be null)
                DEFAULT_START_SECONDS, // Use SoTTeam's constant for starting time
                TeamTimer.DEFAULT_MAX_TIMER_SECONDS, // Use TeamTimer's constant
                TeamTimer.DEFAULT_TIMER_INTERVAL_TICKS // Use TeamTimer's constant
        );

        // Initialize other game state
        resetForNewGame();
    }

    /**
     * Resets the team's game-specific state (sand, score) for a new game
     * and resets the associated TeamTimer to its default starting time.
     */
    public void resetForNewGame() {
        this.teamSandCount = 0;
        this.bankedScore = 0;
        // Reset the dedicated timer object to the default start time
        this.teamTimer.reset(DEFAULT_START_SECONDS);
        plugin.getLogger().log(Level.INFO, "Reset game state for team: " + getTeamName());
    }

    // --- Member Management ---

    /**
     * Adds a player to this team instance by storing their UUID.
     * Should be called by GameManager during game setup.
     *
     * @param player The player to add to the team roster.
     */
    public void addMember(Player player) {
        if (player != null) {
            if (memberUUIDs.add(player.getUniqueId())) {
                plugin.getLogger().fine("Added player " + player.getName() + " to SoTTeam " + getTeamName());
            }
        } else {
            plugin.getLogger().warning("Attempted to add null player to SoTTeam " + getTeamName());
        }
    }

    /**
     * Removes a player from this team instance using their UUID.
     * Called if a player leaves mid-game or during cleanup.
     *
     * @param player The player to remove from the team roster.
     */
    public void removeMember(Player player) {
        if (player != null) {
            if (memberUUIDs.remove(player.getUniqueId())) {
                plugin.getLogger().fine("Removed player " + player.getName() + " from SoTTeam " + getTeamName());
            }
        } else {
            plugin.getLogger().warning("Attempted to remove null player from SoTTeam " + getTeamName());
        }
    }

    /**
     * Gets an unmodifiable set containing the UUIDs of all members
     * currently assigned to this team instance.
     *
     * @return An unmodifiable Set of member UUIDs.
     */
    public Set<UUID> getMemberUUIDs() {
        return Collections.unmodifiableSet(memberUUIDs);
    }

    /**
     * Gets a list of the currently online players who are members of this team.
     * Iterates through member UUIDs and retrieves online Player objects.
     *
     * @return A List of online Player objects who are members of this team.
     */
    public List<Player> getOnlineMembers() {
        List<Player> online = new ArrayList<>();
        for (UUID id : memberUUIDs) {
            Player p = Bukkit.getPlayer(id);
            // Check if player is online and still valid
            if (p != null && p.isOnline()) {
                online.add(p);
            }
        }
        return online;
    }

    /**
     * Checks if a specific player, identified by their UUID, is currently
     * a member of this team instance.
     *
     * @param playerUUID The UUID of the player to check.
     * @return true if the player's UUID is in the member set, false otherwise.
     */
    public boolean isMember(UUID playerUUID) {
        return memberUUIDs.contains(playerUUID);
    }


    // --- Timer Control (Delegation) ---

    /**
     * Starts the team's timer countdown by delegating the call
     * to the internal TeamTimer object.
     */
    public void startTimer() {
        this.teamTimer.start();
    }

    /**
     * Stops the team's timer countdown by delegating the call
     * to the internal TeamTimer object.
     */
    public void stopTimer() {
        this.teamTimer.stop();
    }

    /**
     * Adds seconds to the team's timer by delegating the call
     * to the internal TeamTimer object.
     *
     * @param secondsToAdd The number of seconds to add (must be positive).
     */
    public void addSeconds(int secondsToAdd) {
        this.teamTimer.addSeconds(secondsToAdd);
    }

    /**
     * Gets the number of seconds currently remaining on the team's timer
     * by delegating the call to the internal TeamTimer object.
     *
     * @return The number of seconds remaining.
     */
    public int getRemainingSeconds() {
        return this.teamTimer.getSecondsLeft();
    }

    /**
     * Checks if the team's timer is currently running by delegating
     * the call to the internal TeamTimer object.
     *
     * @return true if the timer is running, false otherwise.
     */
    public boolean isTimerRunning() {
        return this.teamTimer.isRunning();
    }


    // --- Sand Management ---

    /**
     * Gets the current amount of sand held collectively by the team.
     *
     * @return The team's current sand count.
     */
    public int getSandCount() {
        return this.teamSandCount;
    }

    /**
     * Adds a specified amount of sand to the team's total count.
     * Input amount should be positive.
     *
     * @param amount The amount of sand to add.
     */
    public void addSand(int amount) {
        if (amount > 0) {
            this.teamSandCount += amount;
            // Optional: Update scoreboard or notify team
        } else if (amount < 0) {
             plugin.getLogger().warning("Attempted to add negative sand amount: " + amount + " to team: " + getTeamName());
        }
    }

    /**
     * Attempts to use (consume) a specified amount of sand from the team's total.
     * Checks if sufficient sand is available before deducting.
     *
     * @param amount The amount of sand to try and use (must be positive).
     * @return true if the team had enough sand and it was consumed, false otherwise.
     */
    public boolean tryUseSand(int amount) {
        if (amount <= 0) {
             plugin.getLogger().warning("Attempted to use invalid sand amount: " + amount + " for team: " + getTeamName());
             return false;
        }
        if (this.teamSandCount >= amount) {
            this.teamSandCount -= amount;
            plugin.getLogger().fine("Team " + getTeamName() + " used " + amount + " sand. Remaining: " + this.teamSandCount);
            // Optional: Update scoreboard or notify team
            return true;
        } else {
            plugin.getLogger().fine("Team " + getTeamName() + " failed to use " + amount + " sand. Available: " + this.teamSandCount);
            return false;
        }
    }


    // --- Score Management ---

    /**
     * Gets the total score that has been successfully banked by the team
     * (e.g., via the Sphinx or by escaping).
     *
     * @return The team's current banked score.
     */
    public int getBankedScore() {
        return this.bankedScore;
    }

    /**
     * Adds a specified amount of score to the team's banked total.
     * Input amount should be positive.
     *
     * @param scoreToAdd The amount of score to add to the banked total.
     */
    public void addBankedScore(int scoreToAdd) {
        if (scoreToAdd > 0) {
            this.bankedScore += scoreToAdd;
            plugin.getLogger().fine("Added " + scoreToAdd + " to banked score for team " + getTeamName() + ". New total: " + this.bankedScore);
            // Optional: Update scoreboard or notify team
        } else if (scoreToAdd < 0) {
            plugin.getLogger().warning("Attempted to add negative score: " + scoreToAdd + " to team: " + getTeamName());
        }
    }


    // --- Getters for Team Definition Info ---

    /**
     * Gets the unique identifier (UUID) for this team, derived from its TeamDefinition.
     *
     * @return The team's unique UUID.
     */
    public UUID getTeamId() {
        return teamDefinition.getId();
    }

    /**
     * Gets the display name of this team (e.g., "Red Rabbits"), derived from its TeamDefinition.
     *
     * @return The team's name string.
     */
    public String getTeamName() {
        return teamDefinition.getName();
    }

    /**
     * Gets the color identifier string for this team (e.g., "RED"), derived from its TeamDefinition.
     * Used potentially for formatting or team identification.
     *
     * @return The team's color string.
     */
    public String getTeamColor() {
        return teamDefinition.getColor();
    }

    /**
     * Gets the underlying static TeamDefinition object containing the team's
     * immutable properties (ID, Name, Color).
     *
     * @return The TeamDefinition object.
     */
    public TeamDefinition getTeamDefinition() {
        return teamDefinition;
    }


    // --- Standard Java Object Methods ---

    /**
     * Returns a string representation of the SoTTeam instance, including key state information.
     *
     * @return A string summary of the team's state.
     */
    @Override
    public String toString() {
        // Updated to use getters which delegate for timer info
        return "SoTTeam{" +
                "teamId=" + getTeamId() +
                ", teamName='" + getTeamName() + '\'' +
                ", members=" + memberUUIDs.size() +
                ", sand=" + teamSandCount +
                ", score=" + bankedScore +
                ", secondsLeft=" + getRemainingSeconds() +
                ", timerRunning=" + isTimerRunning() +
                '}';
    }

    /**
     * Compares this SoTTeam instance to another object for equality.
     * Equality is based solely on the unique Team ID (derived from TeamDefinition).
     *
     * @param o The object to compare with.
     * @return true if the other object is an SoTTeam with the same Team ID, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SoTTeam soTTeam = (SoTTeam) o;
        // Compare based on the unique ID from the TeamDefinition
        return Objects.equals(this.getTeamId(), soTTeam.getTeamId());
    }

    /**
     * Generates a hash code for this SoTTeam instance.
     * The hash code is based solely on the unique Team ID (derived from TeamDefinition).
     *
     * @return The hash code.
     */
    @Override
    public int hashCode() {
        // Hash based on the unique ID from the TeamDefinition
        return Objects.hash(this.getTeamId());
    }
}
