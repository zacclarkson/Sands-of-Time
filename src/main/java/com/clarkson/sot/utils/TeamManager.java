package com.clarkson.sot.utils;

import com.clarkson.sot.main.GameManager; // Import GameManager
import net.kyori.adventure.text.format.NamedTextColor; // For team colors
import org.bukkit.Bukkit; // Needed for logging potentially
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin; // Keep import if createTeamUUID needs it (though it doesn't currently)

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Manages team definitions and player assignments for Sands of Time.
 * Provides access to static team definitions and uses GameManager
 * to retrieve active SoTTeam instances during a game.
 */
public class TeamManager {

    // Stores the basic definitions of available teams (ID -> Definition)
    private final Map<UUID, TeamDefinition> teamDefinitions;
    // Maps player UUIDs to their assigned team ID
    private final Map<UUID, UUID> playerTeamMap;
    // Reference to the main GameManager to access active teams map
    private final GameManager gameManager; // Required dependency for getPlayerTeam

    // Static data for standard MCC teams (Restored)
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
     * @param suffix The numeric suffix (1-10 for standard teams).
     * @return A generated UUID for the team definition.
     */
    private static UUID createTeamUUID(int suffix) {
        String uuidSuffix = String.format("%012d", suffix); // Use %012d for zero padding
        return UUID.fromString("00000000-0000-0000-0000-" + uuidSuffix);
    }

    /**
     * Constructor for TeamManager.
     * Initializes team definitions and requires a GameManager instance to access active teams.
     * @param gameManager The main GameManager instance.
     */
    public TeamManager(GameManager gameManager) {
        // Store required GameManager dependency
        this.gameManager = Objects.requireNonNull(gameManager, "GameManager cannot be null");
        // Initialize internal maps
        this.teamDefinitions = new HashMap<>();
        this.playerTeamMap = new HashMap<>();
        // Load standard team definitions
        initializeStandardTeamDefinitions();
    }

    /**
     * Loads the standard MCC team definitions into the manager.
     */
    private void initializeStandardTeamDefinitions() {
        teamDefinitions.clear(); // Ensure map is clear before loading
        for (TeamDefinition def : STANDARD_TEAMS_DATA) {
            this.teamDefinitions.put(def.getId(), def);
        }
        Bukkit.getLogger().info("[TeamManager] Initialized " + teamDefinitions.size() + " standard team definitions.");
        // TODO: Consider loading team definitions from a config file
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
     * Removes the player from any previous team assignment. Logs the assignment or errors.
     * @param player The player to assign.
     * @param teamId The UUID of the team definition to assign the player to.
     */
    public void assignPlayerToTeam(Player player, UUID teamId) {
        if (player == null || teamId == null) {
            Bukkit.getLogger().warning("[TeamManager] Attempted to assign null player or team ID.");
            return;
        }
        // Check if the target team ID is valid (exists in definitions)
        if (teamDefinitions.containsKey(teamId)) {
            UUID playerUUID = player.getUniqueId();
            // Put/replace the mapping. remove() is implicitly handled by put().
            UUID oldTeamId = playerTeamMap.put(playerUUID, teamId);

            String teamName = teamDefinitions.get(teamId).getName();
            // Log the change or assignment
            if (oldTeamId != null && !oldTeamId.equals(teamId)) {
                String oldTeamName = teamDefinitions.getOrDefault(oldTeamId, new TeamDefinition(null, "Unknown", null)).getName();
                Bukkit.getLogger().info("[TeamManager] Moved player " + player.getName() + " from team " + oldTeamName + " to " + teamName);
            } else if (oldTeamId == null) {
                Bukkit.getLogger().info("[TeamManager] Assigned player " + player.getName() + " to team " + teamName);
            }
            // Note: Further logic might be needed if assignments change mid-game
        } else {
            // Log an error if the team ID doesn't exist in the definitions
            Bukkit.getLogger().severe("[TeamManager] Error: Cannot assign player " + player.getName() + " to non-existent team ID: " + teamId);
        }
    }

    /**
     * Gets the team ID assigned to a specific player.
     * @param player The player to check.
     * @return The UUID of the team the player is assigned to, or null if not assigned or player is null.
     */
    public UUID getPlayerTeamId(Player player) {
        if (player == null) {
            return null;
        }
        return playerTeamMap.get(player.getUniqueId());
    }

    /**
     * Gets the UUIDs of all players currently assigned to a specific team ID.
     * @param teamId The UUID of the team definition.
     * @return A new Set containing the UUIDs of the team members. Returns an empty set if the team ID is invalid or has no members.
     */
    public Set<UUID> getTeamMemberUUIDs(UUID teamId) {
        if (teamId == null) {
            return Collections.emptySet(); // Return empty set for null teamId
        }
        // Filter the playerTeamMap entries for the given teamId and collect the player UUIDs (keys)
        return playerTeamMap.entrySet().stream()
                .filter(entry -> teamId.equals(entry.getValue())) // Find entries where the value matches the teamId
                .map(Map.Entry::getKey) // Extract the player UUID (the key)
                .collect(Collectors.toSet()); // Collect the UUIDs into a new Set
    }

    /**
     * Gets the active SoTTeam instance for the given player from the GameManager.
     *
     * @param player The player whose active team instance is needed.
     * @return The SoTTeam instance the player belongs to in the current game, or null if the player is not assigned,
     * the team is not active, or the game is not running/setup.
     */
    public SoTTeam getPlayerTeam(Player player) {
        // Get the Team ID assigned to the player using the internal map
        UUID teamId = getPlayerTeamId(player);
        if (teamId == null) {
            return null; // Player is not assigned to any team
        }
        // Use the injected GameManager to get the map of active teams
        // and retrieve the SoTTeam instance using the teamId.
        return gameManager.getActiveTeams().get(teamId);
    }

    /**
     * Removes a player's team assignment from the internal map.
     * @param player The player to remove from their team assignment.
     */
    public void removePlayerAssignment(Player player) {
        if (player != null) {
            UUID removedTeamId = playerTeamMap.remove(player.getUniqueId());
            if (removedTeamId != null) {
                 String teamName = teamDefinitions.getOrDefault(removedTeamId, new TeamDefinition(null, "Unknown", null)).getName();
                Bukkit.getLogger().info("[TeamManager] Removed " + player.getName() + " from team assignment (was team " + teamName + ")");
            }
        } else {
            Bukkit.getLogger().warning("[TeamManager] Attempted to remove assignment for null player.");
        }
    }

    /**
     * Clears all player-to-team assignments.
     * Useful when resetting between games or during shutdown.
     */
    public void clearAssignments() {
        playerTeamMap.clear();
        Bukkit.getLogger().info("[TeamManager] Cleared all player team assignments.");
    }

    /**
     * Gets the Adventure API NamedTextColor corresponding to the player's assigned team.
     * @param player The player.
     * @return The NamedTextColor for the team, or NamedTextColor.WHITE if the player or team is not found/assigned.
     */
    public NamedTextColor getPlayerTeamColor(Player player) {
        UUID teamId = getPlayerTeamId(player);
        if (teamId != null) {
            TeamDefinition definition = getTeamDefinition(teamId);
            if (definition != null && definition.getColor() != null) {
                return parseColor(definition.getColor());
            }
        }
        return NamedTextColor.WHITE; // Default color if no team or color found
    }

    /**
     * Helper method to convert team color strings (e.g., "RED", "LIGHT_PURPLE")
     * into Adventure API NamedTextColor objects.
     * @param colorName The name of the color (case-insensitive).
     * @return The corresponding NamedTextColor, or WHITE if not found or null.
     */
    private NamedTextColor parseColor(String colorName) {
        if (colorName == null) {
            return NamedTextColor.WHITE;
        }
        // Adventure API provides constants for standard Minecraft colors by name
        NamedTextColor color = NamedTextColor.NAMES.value(colorName.toLowerCase(Locale.ROOT));
        // Return the found color, or default to WHITE if the name was invalid
        return Objects.requireNonNullElse(color, NamedTextColor.WHITE);
    }
}
