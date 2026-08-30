package com.clarkson.sot.utils;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.timer.TeamTimer; // Import the new Timer class
import com.clarkson.sot.timer.VisualSandTimerDisplay; // Import Visual Display

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Level;

/**
 * Represents an active team participating in a Sands of Time game instance.
 * Holds team definition, member UUIDs, and game-specific state like banked score.
 * Manages the team's timer via a dedicated TeamTimer object.
 * Sand is managed via player inventory and block placement events.
 */
public class SoTTeam {

    // --- Dependencies & Static Info ---
    private final Plugin plugin;
    private final GameManager gameManager;
    private final TeamDefinition teamDefinition;

    // --- Members ---
    private final Set<UUID> memberUUIDs;

    // --- Game State ---
    // Removed: private int teamSandCount;
    private int bankedScore;

    // --- Timer Control ---
    private transient VisualSandTimerDisplay visualTimerDisplay;
    private final TeamTimer teamTimer;

    // --- Constants ---
    private static final int DEFAULT_START_SECONDS = 150; // Example value

    /**
     * Constructor for an active SoTTeam instance.
     *
     * <p>The team starts with no visual sand column: teams are created at {@code /sot setup}, before
     * the dungeon (and therefore the hub's TIMER marker) exists, and a column created here would have
     * to stand at some placeholder location. {@link #relocateVisualTimer} builds it once the team's
     * hub is known.
     */
    public SoTTeam(TeamDefinition teamDefinition, Plugin plugin, GameManager gameManager) {
        this.teamDefinition = Objects.requireNonNull(teamDefinition, "TeamDefinition cannot be null");
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.gameManager = Objects.requireNonNull(gameManager, "GameManager cannot be null");
        this.memberUUIDs = new HashSet<>();
        this.visualTimerDisplay = null;

        // Create the TeamTimer instance. The visual display is attached later, if the team's hub
        // defines a TIMER marker.
        this.teamTimer = new TeamTimer(
                plugin,
                () -> this.gameManager.handleTeamTimerEnd(this), // Updated callback
                null,
                DEFAULT_START_SECONDS,
                TeamTimer.DEFAULT_MAX_TIMER_SECONDS,
                TeamTimer.DEFAULT_TIMER_INTERVAL_TICKS
        );

        resetForNewGame();
    }

    /**
     * Resets the team's game-specific state for a new game.
     */
    public void resetForNewGame() {
        // Removed: this.teamSandCount = 0;
        this.bankedScore = 0;
        this.teamTimer.reset(DEFAULT_START_SECONDS);
        plugin.getLogger().log(Level.INFO, "Reset game state for team: " + getTeamName());
    }

    // --- Member Management ---
    // (addMember, removeMember, getMemberUUIDs, getOnlineMembers, isMember methods remain the same)
    public void addMember(Player player) { if (player != null) { if (memberUUIDs.add(player.getUniqueId())) { plugin.getLogger().fine("Added player " + player.getName() + " to SoTTeam " + getTeamName()); } } else { plugin.getLogger().warning("Attempted to add null player to SoTTeam " + getTeamName()); } }
    public void removeMember(Player player) { if (player != null) { if (memberUUIDs.remove(player.getUniqueId())) { plugin.getLogger().fine("Removed player " + player.getName() + " from SoTTeam " + getTeamName()); } } else { plugin.getLogger().warning("Attempted to remove null player from SoTTeam " + getTeamName()); } }
    public Set<UUID> getMemberUUIDs() { return Collections.unmodifiableSet(memberUUIDs); }
    public List<Player> getOnlineMembers() { List<Player> online = new ArrayList<>(); for (UUID id : memberUUIDs) { Player p = Bukkit.getPlayer(id); if (p != null && p.isOnline()) { online.add(p); } } return online; }
    public boolean isMember(UUID playerUUID) { return memberUUIDs.contains(playerUUID); }


    /**
     * Anchors this team's visual sand-timer column at the given bottom/top, creating the display on
     * first call and moving it on any later one. Called from {@code GameManager.startGame} once the
     * team's dungeon hub has been pasted and its TIMER marker resolved to a world location — a hub
     * with no TIMER marker never calls this, so the team simply plays without a sand column rather
     * than getting one somewhere arbitrary.
     */
    public void relocateVisualTimer(Location bottom, Location top) {
        if (bottom == null || top == null || bottom.getWorld() == null || top.getWorld() == null) {
            plugin.getLogger().log(Level.WARNING, "Invalid visual timer location for team "
                    + getTeamName() + ". Visual timer disabled.");
            return;
        }
        if (visualTimerDisplay == null) {
            visualTimerDisplay = new VisualSandTimerDisplay(plugin, this, bottom, top);
            teamTimer.setVisualNotifier(visualTimerDisplay);
        } else {
            visualTimerDisplay.relocate(bottom, top);
        }
    }

    /**
     * Whether this team's visual sand column stands at the given block.
     *
     * <p>False when the team has no column this round: a hub segment with no {@code TIMER} marker
     * never calls {@link #relocateVisualTimer}, so the display is never created.
     */
    public boolean isVisualTimerBlock(@Nullable Location location) {
        return visualTimerDisplay != null && visualTimerDisplay.isColumnBlock(location);
    }

    /** Stops the visual timer task and clears its sand column. Used by end-of-game cleanup. */
    public void clearVisualTimer() {
        if (visualTimerDisplay != null) {
            visualTimerDisplay.stopAndClear();
        }
    }

    // --- Timer Control (Delegation) ---
    // (startTimer, stopTimer, addSeconds, getRemainingSeconds, isTimerRunning methods remain the same)
    public void startTimer() { this.teamTimer.start(); }
    public void stopTimer() { this.teamTimer.stop(); }
    public void addSeconds(int secondsToAdd) { this.teamTimer.addSeconds(secondsToAdd); }
    public int getRemainingSeconds() { return this.teamTimer.getSecondsLeft(); }
    public boolean isTimerRunning() { return this.teamTimer.isRunning(); }

    // --- Score Management ---
    // (getBankedScore, addBankedScore methods remain the same)
    public int getBankedScore() { return this.bankedScore; }
    public void addBankedScore(int scoreToAdd) { if (scoreToAdd > 0) { this.bankedScore += scoreToAdd; plugin.getLogger().fine("Added " + scoreToAdd + " to banked score for team " + getTeamName() + ". New total: " + this.bankedScore); } else if (scoreToAdd < 0) { plugin.getLogger().warning("Attempted to add negative score: " + scoreToAdd + " to team: " + getTeamName()); } }


    // --- Getters for Team Definition Info ---
    // (getTeamId, getTeamName, getTeamColor, getTeamDefinition methods remain the same)
    public UUID getTeamId() { return teamDefinition.getId(); }
    public String getTeamName() { return teamDefinition.getName(); }
    public String getTeamColor() { return teamDefinition.getColor(); }
    public TeamDefinition getTeamDefinition() { return teamDefinition; }


    // --- Standard Java Object Methods ---
    // (toString, equals, hashCode methods remain the same, but update toString)
    @Override
    public String toString() {
        return "SoTTeam{" +
                "teamId=" + getTeamId() +
                ", teamName='" + getTeamName() + '\'' +
                ", members=" + memberUUIDs.size() +
                // Removed: ", sand=" + teamSandCount +
                ", score=" + bankedScore +
                ", secondsLeft=" + getRemainingSeconds() +
                ", timerRunning=" + isTimerRunning() +
                '}';
    }
    @Override
    public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; SoTTeam soTTeam = (SoTTeam) o; return Objects.equals(this.getTeamId(), soTTeam.getTeamId()); }
    @Override
    public int hashCode() { return Objects.hash(this.getTeamId()); }
}
