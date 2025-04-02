package com.clarkson.sot.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;

/**
 * Manages teams and team-related data for Sands of Time.
 */
public class TeamManager {
    private final Map<UUID, SoTTeam> teams; // Team ID -> Team Object
    private final Map<UUID, UUID> playerTeamMap; // Player UUID -> Team ID

    // --- Option 1: Using a structured array for initialization ---
    private static final String[][] STANDARD_TEAMS_DATA = {
        // {UUID Suffix, Name, Color} - Using suffix for readability here
        {"1", "Red Rabbits",    "RED"},
        {"2", "Orange Ocelots", "ORANGE"},
        {"3", "Yellow Yaks",    "YELLOW"},
        {"4", "Lime Llamas",    "LIME"},
        {"5", "Green Geckos",   "GREEN"},
        {"6", "Cyan Coyotes",   "CYAN"},
        {"7", "Aqua Axolotls",  "AQUA"},
        {"8", "Blue Bats",      "BLUE"},
        {"9", "Purple Pandas",  "PURPLE"},
        {"10", "Pink Parrots",  "PINK"}
    };

    public TeamManager() {
        this.teams = new HashMap<>();
        this.playerTeamMap = new HashMap<>();
        initializeStandardTeams();
    }

    /**
     * Creates and stores the 10 standard MCC teams using predefined data.
     */
    private void initializeStandardTeams() {
        for (String[] teamData : STANDARD_TEAMS_DATA) {
            // Construct UUID - Replace with UUID.randomUUID() or a better scheme later
            String uuidSuffix = String.format("%12s", teamData[0]).replace(' ', '0');
            UUID teamId = UUID.fromString("00000000-0000-0000-0000-" + uuidSuffix);

            String teamName = teamData[1];
            String teamColor = teamData[2];

            addTeam(new SoTTeam(teamId, teamName, teamColor));
        }
    }

    // --- Option 2: Keeping direct initialization but improving formatting ---
    /*
    private void initializeStandardTeams_Formatted() {
        // Use comments and alignment for clarity
        // Team ID                                       Name              Color
        addTeam(new SoTTeam(createTeamUUID(1),  "Red Rabbits",    "RED"));
        addTeam(new SoTTeam(createTeamUUID(2),  "Orange Ocelots", "ORANGE"));
        addTeam(new SoTTeam(createTeamUUID(3),  "Yellow Yaks",    "YELLOW"));
        addTeam(new SoTTeam(createTeamUUID(4),  "Lime Llamas",    "LIME"));
        addTeam(new SoTTeam(createTeamUUID(5),  "Green Geckos",   "GREEN"));
        addTeam(new SoTTeam(createTeamUUID(6),  "Cyan Coyotes",   "CYAN"));
        addTeam(new SoTTeam(createTeamUUID(7),  "Aqua Axolotls",  "AQUA"));
        addTeam(new SoTTeam(createTeamUUID(8),  "Blue Bats",      "BLUE"));
        addTeam(new SoTTeam(createTeamUUID(9),  "Purple Pandas",  "PURPLE"));
        addTeam(new SoTTeam(createTeamUUID(10), "Pink Parrots",   "PINK"));
    }

    // Helper for Option 2 to keep UUID generation concise
    private UUID createTeamUUID(int suffix) {
        String uuidSuffix = String.format("%12s", suffix).replace(' ', '0');
        return UUID.fromString("00000000-0000-0000-0000-" + uuidSuffix);
    }
    */


    /**
     * Helper method to add a team to the manager.
     * @param team The SoTTeam object to add.
     */
    private void addTeam(SoTTeam team) {
        if (team != null) {
            this.teams.put(team.getTeamId(), team);
        }
    }

    // ... rest of the TeamManager methods (assignPlayerToTeam, getTeam, etc.) remain the same ...
     /**
     * Assigns a player to a specific team.
     * @param player The player to assign.
     * @param teamId The UUID of the team to assign the player to.
     */
    public void assignPlayerToTeam(Player player, UUID teamId) {
        if (player == null || teamId == null) return;

        SoTTeam team = teams.get(teamId);
        if (team != null) {
            // Remove player from any previous team first (optional, depends on logic)
            UUID oldTeamId = playerTeamMap.remove(player.getUniqueId());
            if (oldTeamId != null && !oldTeamId.equals(teamId)) {
                SoTTeam oldTeam = teams.get(oldTeamId);
                if (oldTeam != null) {
                    oldTeam.removeMember(player);
                }
            }

            // Add to new team
            playerTeamMap.put(player.getUniqueId(), teamId);
            team.addMember(player);
        } else {
            // Handle error: Team ID not found
            System.err.println("Error: Could not assign player " + player.getName() + " to non-existent team ID: " + teamId);
        }
    }

    /**
     * Retrieves a team by its UUID.
     * @param teamId The UUID of the team.
     * @return The SoTTeam object, or null if not found.
     */
    public SoTTeam getTeam(UUID teamId) {
        return teams.get(teamId);
    }

    /**
     * Retrieves the team a specific player belongs to.
     * @param player The player.
     * @return The SoTTeam object the player belongs to, or null if not on a team.
     */
     public SoTTeam getPlayerTeam(Player player) {
        if (player == null) return null;
        UUID teamId = playerTeamMap.get(player.getUniqueId());
        return (teamId != null) ? teams.get(teamId) : null;
     }

    /**
     * Retrieves all players belonging to a specific team.
     * Note: This currently returns UUIDs. Getting Player objects requires Bukkit lookup.
     * @param teamId The UUID of the team.
     * @return A Set of Player UUIDs on the team, or an empty set if team not found.
     */
    public Set<UUID> getTeamMemberUUIDs(UUID teamId) {
        SoTTeam team = teams.get(teamId);
        return (team != null) ? team.getMemberUUIDs() : Set.of(); // Return immutable empty set if not found
    }

    /**
    * Gets all defined teams.
    * @return A map of all teams managed. Consider returning an unmodifiable view.
    */
    public Map<UUID, SoTTeam> getAllTeams() {
        return teams; // Or Collections.unmodifiableMap(teams);
    }
}