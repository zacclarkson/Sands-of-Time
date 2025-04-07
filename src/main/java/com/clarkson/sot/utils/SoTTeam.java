package com.clarkson.sot.utils;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.timer.TeamTimer; // Import the new Timer class
import com.clarkson.sot.timer.VisualSandTimerDisplay; // Import Visual Display

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

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
     */
    public SoTTeam(TeamDefinition teamDefinition, Plugin plugin, GameManager gameManager, Location visualTimerBottom, Location visualTimerTop) {
        this.teamDefinition = Objects.requireNonNull(teamDefinition, "TeamDefinition cannot be null");
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.gameManager = Objects.requireNonNull(gameManager, "GameManager cannot be null");
        this.memberUUIDs = new HashSet<>();

        // Create Visual Timer Display
        if (visualTimerBottom != null && visualTimerTop != null && visualTimerBottom.getWorld() != null && visualTimerTop.getWorld() != null) {
            this.visualTimerDisplay = new VisualSandTimerDisplay(plugin, this, visualTimerBottom, visualTimerTop);
        } else {
            plugin.getLogger().log(Level.WARNING, "Visual timer locations invalid for team " + teamDefinition.getName() + ". Visual timer disabled.");
            this.visualTimerDisplay = null;
        }

        // Create the TeamTimer instance
        this.teamTimer = new TeamTimer(
                plugin,
                () -> this.gameManager.handleTeamTimerEnd(this), // Updated callback
                this.visualTimerDisplay,
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
