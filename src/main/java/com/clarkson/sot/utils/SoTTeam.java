package com.clarkson.sot.utils; // Or your chosen package for team-related classes

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap; // For thread-safe player set

/**
 * Represents a team playing the Sands of Time game.
 * Includes both general team info and game-specific state.
 */
public class SoTTeam {
    // --- General Team Info ---
    private final UUID teamId;
    private final String teamName;
    private final String teamColor;
    private final Set<UUID> memberUUIDs;

    // --- Sands of Time Specific State ---
    private int teamSandCount;  // Sand collected/held by the team during the current game
    private int bankedScore;    // Score banked via Sphinx or by escaping

    public SoTTeam(UUID teamId, String teamName, String teamColor) {
        // General Info
        this.teamId = teamId;
        this.teamName = teamName;
        this.teamColor = teamColor;
        this.memberUUIDs = ConcurrentHashMap.newKeySet(); // Thread-safe set

        // Initialize SoT Specific State for a new game
        this.teamSandCount = 0;
        this.bankedScore = 0;
        // NOTE: You might need a method like 'resetForNewGame()' to clear these
        // if the same SoTTeam objects are reused across multiple game instances.
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

    public void addSand(int amount) {
        if (amount > 0) {
            this.teamSandCount += amount;
            // Optional: Send update message to team members?
        }
    }

    /**
     * Attempts to use a specified amount of sand.
     * @param amount The amount of sand to use.
     * @return true if the team had enough sand and it was consumed, false otherwise.
     */
    public boolean tryUseSand(int amount) {
        if (amount > 0 && this.teamSandCount >= amount) {
            this.teamSandCount -= amount;
            // Optional: Send update message to team members?
            return true;
        }
        return false;
    }

    // --- Methods for SoT Score Management ---
    public int getBankedScore() { return bankedScore; }

    public void addBankedScore(int score) {
        if (score > 0) {
            this.bankedScore += score;
            // Optional: Update scoreboard / send message?
        }
    }

    /**
     * Resets the game-specific state for a new Sands of Time game.
     */
    public void resetForNewGame() {
        this.teamSandCount = 0;
        this.bankedScore = 0;
        // Don't clear members here unless intended
    }


    @Override
    public String toString() {
        return "SoTTeam{" +
                "teamId=" + teamId +
                ", teamName='" + teamName + '\'' +
                ", teamColor='" + teamColor + '\'' +
                ", members=" + memberUUIDs.size() +
                ", sand=" + teamSandCount + // Added SoT specific state
                ", score=" + bankedScore + // Added SoT specific state
                '}';
    }
}