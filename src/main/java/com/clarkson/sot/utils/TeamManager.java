package com.clarkson.sot.utils;

import com.clarkson.sot.main.GameManager; // Import GameManager

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages team definitions and player assignments for Sands of Time.
 * Provides access to both static team definitions and active SoTTeam instances during a game.
 */
public class TeamManager {

    // Stores the basic definitions of available teams (ID -> Definition)
    private final Map<UUID, TeamDefinition> teamDefinitions;
    // Maps player UUIDs to their assigned team ID
    private final Map<UUID, UUID> playerTeamMap;
    // Reference to the main GameManager to access active teams
    private final GameManager gameManager; // Added dependency

    // Static data for standard MCC teams
    private static final TeamDefinition[] STANDARD_TEAMS_DATA = {
            new TeamDefinition(createTeamUUID(1), "Red Rabbits", "RED"),
            new TeamDefinition(createTeamUUID(2), "Orange Ocelots", "ORANGE"),
            new TeamDefinition(createTeamUUID(3), "Yellow Yaks", "YELLOW"),
            new TeamDefinition(createTeamUUID(4), "Lime Llamas", "LIME"),
            new TeamDefinition(createTeamUUID(5), "Green Geckos", "GREEN"),
            new TeamDefinition(createTeamUUID(6), "Cyan Coyotes", "CYAN"),
            new TeamDefinition(createTeamUUID(7), "Aqua Axolotls", "AQUA"),
            new TeamDefinition(createTeamUUID(8), "Blue Bats", "BLUE"),
            new TeamDefinition(createTeamUUID(9), "Purple Pandas", "PURPLE"),
            new TeamDefinition(createTeamUUID(10), "Pink Parrots", "PINK")
    };

    /**
     * Helper to create consistent UUIDs for team definitions based on a numeric suffix.
     * Ensures the first parts of the UUID are constant for easy identification.
     * @param suffix The numeric suffix (1-10 for standard teams).
     * @return A generated UUID for the team definition.
     */
    private static UUID createTeamUUID(int suffix) {
        // Format the suffix to be 12 characters long, padded with leading zeros
        String uuidSuffix = String.format("%012d", suffix); // Use %012d for zero padding
        return UUID.fromString("00000000-0000-0000-0000-" + uuidSuffix);
    }

    /**
     * Constructor for TeamManager.
     * Initializes team definitions and requires a GameManager instance to access active teams.
     * @param gameManager The main GameManager instance.
     */
    public TeamManager(GameManager gameManager) {
        this.gameManager = Objects.requireNonNull(gameManager, "GameManager cannot be null"); // Ensure GameManager is provided
        this.teamDefinitions = new HashMap<>();
        this.playerTeamMap = new HashMap<>();
        initializeStandardTeamDefinitions();
    }

    /**
     * Loads the standard MCC team definitions into the manager.
     * In a more advanced setup, this could load from a configuration file.
     */
    private void initializeStandardTeamDefinitions() {
        for (TeamDefinition def : STANDARD_TEAMS_DATA) {
            this.teamDefinitions.put(def.getId(), def);
        }
        // TODO: Consider loading team definitions from a config file for flexibility
    }

    /**
     * Gets the static definition (name, color, ID) for a given team ID.
     * @param teamId The UUID of the team definition.
     * @return The TeamDefinition object, or null if not found.
     */
    public TeamDefinition getTeamDefinition(UUID teamId) {
        return teamDefinitions.get(teamId);
    }

    /**
     * Gets an unmodifiable view of all loaded team definitions.
     * @return An unmodifiable Map of Team UUID to TeamDefinition.
     */
    public Map<UUID, TeamDefinition> getAllTeamDefinitions() {
        return Collections.unmodifiableMap(teamDefinitions);
    }

    /**
     * Assigns a player to a specific team ID.
     * Removes the player from any previous team assignment.
     * @param player The player to assign.
     * @param teamId The UUID of the team definition to assign the player to.
     */
    public void assignPlayerToTeam(Player player, UUID teamId) {
        if (player == null || teamId == null) {
            System.err.println("Error: Attempted to assign null player or team ID.");
            return;
        }
        // Check if the target team ID is valid
        if (teamDefinitions.containsKey(teamId)) {
            UUID playerUUID = player.getUniqueId();
            // Remove player from their old team mapping, if any
            UUID oldTeamId = playerTeamMap.remove(playerUUID);
            if (oldTeamId != null && !oldTeamId.equals(teamId)) {
                // Optional: Log or notify about the team change
                 System.out.println("Player " + player.getName() + " moved from team " + oldTeamId + " to " + teamId);
                 // If active SoTTeam instances exist, might need to update those too,
                 // but typically assignment happens before game start.
            }

            // Add the new mapping
            playerTeamMap.put(playerUUID, teamId);
            System.out.println("Assigned " + player.getName() + " to team " + teamDefinitions.get(teamId).getName() + " (ID: " + teamId + ")");
        } else {
            // Log an error if the team ID doesn't exist in the definitions
            System.err.println("Error: Cannot assign player " + player.getName() + " to non-existent team ID: " + teamId);
        }
    }

    /**
     * Gets the team ID assigned to a specific player.
     * @param player The player to check.
     * @return The UUID of the team the player is assigned to, or null if not assigned.
     */
    public UUID getPlayerTeamId(Player player) {
        return player != null ? playerTeamMap.get(player.getUniqueId()) : null;
    }

    /**
     * Gets the UUIDs of all players currently assigned to a specific team ID.
     * @param teamId The UUID of the team definition.
     * @return A Set containing the UUIDs of the team members. Returns an empty set if the team ID is invalid or has no members.
     */
    public Set<UUID> getTeamMemberUUIDs(UUID teamId) {
        // Filter the playerTeamMap entries for the given teamId and collect the player UUIDs (keys)
        return playerTeamMap.entrySet().stream()
                .filter(entry -> teamId.equals(entry.getValue())) // Find entries where the value matches the teamId
                .map(Map.Entry::getKey) // Extract the player UUID (the key)
                .collect(Collectors.toSet()); // Collect the UUIDs into a Set
    }

    /**
     * Gets the active SoTTeam instance for the given player.
     * This requires accessing the active teams managed by GameManager.
     *
     * @param player The player whose active team instance is needed.
     * @return The SoTTeam instance the player belongs to in the current game, or null if the player is not assigned,
     * the team is not active, or the game is not running/setup.
     */
    public SoTTeam getPlayerTeam(Player player) {
        // Get the Team ID assigned to the player
        UUID teamId = getPlayerTeamId(player);
        if (teamId == null) {
            // Player is not assigned to any team
            return null;
        }
        // Use the injected GameManager to get the map of active teams
        // and retrieve the SoTTeam instance using the teamId.
        // This returns null if the teamId is not found in the active teams map (e.g., game not started/setup).
        return gameManager.getActiveTeams().get(teamId);
    }

     /**
      * Removes a player's team assignment.
      * @param player The player to remove from their team.
      */
     public void removePlayerAssignment(Player player) {
         if (player != null) {
             UUID removedTeamId = playerTeamMap.remove(player.getUniqueId());
             if (removedTeamId != null) {
                 System.out.println("Removed " + player.getName() + " from team assignment (was team " + removedTeamId + ")");
             }
         }
     }

     /**
      * Clears all player-to-team assignments.
      * Useful when resetting between games.
      */
     public void clearAssignments() {
         playerTeamMap.clear();
         System.out.println("Cleared all player team assignments.");
     }

     public NamedTextColor getPlayerTeamColor(Player player) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPlayerTeamColor'");
     }
}
