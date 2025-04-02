package com.clarkson.sot.utils;

import java.util.*;
import java.util.logging.Logger;

import org.bukkit.entity.Player;

/**
 * Manages teams and team-related data for Sands of Time.
 */
public class TeamManager {
    private final Map<UUID, SoTTeam> teams; // Team ID -> Team Object
    private final Map<UUID, UUID> playerTeamMap; // Player UUID -> Team ID
    private final Logger logger;

    private static final String[][] STANDARD_TEAMS_DATA = {
        {"1", "Red Rabbits", "RED"},
        {"2", "Orange Ocelots", "ORANGE"},
        {"3", "Yellow Yaks", "YELLOW"},
        {"4", "Lime Llamas", "LIME"},
        {"5", "Green Geckos", "GREEN"},
        {"6", "Cyan Coyotes", "CYAN"},
        {"7", "Aqua Axolotls", "AQUA"},
        {"8", "Blue Bats", "BLUE"},
        {"9", "Purple Pandas", "PURPLE"},
        {"10", "Pink Parrots", "PINK"}
    };

    public TeamManager() {
        this.teams = new HashMap<>();
        this.playerTeamMap = new HashMap<>();
        this.logger = Logger.getLogger("TeamManager");
        initializeStandardTeams();
    }

    /**
     * Initializes the standard teams using predefined data.
     */
    private void initializeStandardTeams() {
        for (String[] teamData : STANDARD_TEAMS_DATA) {
            UUID teamId = createTeamUUID(Integer.parseInt(teamData[0]));
            String teamName = teamData[1];
            String teamColor = teamData[2];
            addTeam(new SoTTeam(teamId, teamName, teamColor, null, null));
        }
    }

    /**
     * Creates a UUID for a team based on a numeric suffix.
     * @param suffix The numeric suffix.
     * @return A UUID for the team.
     */
    private UUID createTeamUUID(int suffix) {
        String uuidSuffix = String.format("%012d", suffix);
        return UUID.fromString("00000000-0000-0000-0000-" + uuidSuffix);
    }

    /**
     * Adds a team to the manager.
     * @param team The SoTTeam object to add.
     */
    private void addTeam(SoTTeam team) {
        if (team == null) {
            logger.warning("Attempted to add a null team.");
            return;
        }
        if (teams.containsKey(team.getTeamId())) {
            logger.warning("Team with ID " + team.getTeamId() + " already exists. Skipping addition.");
            return;
        }
        teams.put(team.getTeamId(), team);
        logger.info("Added team: " + team.getTeamName());
    }

    /**
     * Assigns a player to a specific team.
     * @param player The player to assign.
     * @param teamId The UUID of the team to assign the player to.
     */
    public void assignPlayerToTeam(Player player, UUID teamId) {
        if (player == null) {
            logger.warning("Attempted to assign a null player to a team.");
            return;
        }
        if (teamId == null) {
            logger.warning("Attempted to assign player " + player.getName() + " to a null team ID.");
            return;
        }

        UUID currentTeamId = playerTeamMap.get(player.getUniqueId());
        if (teamId.equals(currentTeamId)) {
            logger.info("Player " + player.getName() + " is already on team " + teamId + ". No reassignment needed.");
            return;
        }

        SoTTeam newTeam = teams.get(teamId);
        if (newTeam == null) {
            logger.warning("Attempted to assign player " + player.getName() + " to a non-existent team ID: " + teamId);
            return;
        }

        // Remove player from the current team
        if (currentTeamId != null) {
            SoTTeam oldTeam = teams.get(currentTeamId);
            if (oldTeam != null) {
                oldTeam.removeMember(player);
                logger.info("Removed player " + player.getName() + " from team " + oldTeam.getTeamName());
            }
        }

        // Add player to the new team
        playerTeamMap.put(player.getUniqueId(), teamId);
        newTeam.removeMember(player);
        logger.info("Assigned player " + player.getName() + " to team " + newTeam.getTeamName());
    }

    /**
     * Retrieves a team by its UUID.
     * @param teamId The UUID of the team.
     * @return The SoTTeam object, or null if not found.
     */
    public SoTTeam getTeam(UUID teamId) {
        if (teamId == null) {
            logger.warning("Attempted to retrieve a team with a null ID.");
            return null;
        }
        return teams.get(teamId);
    }

    /**
     * Retrieves the team a specific player belongs to.
     * @param player The player.
     * @return The SoTTeam object the player belongs to, or null if not on a team.
     */
    public SoTTeam getPlayerTeam(Player player) {
        if (player == null) {
            logger.warning("Attempted to retrieve a team for a null player.");
            return null;
        }
        UUID teamId = playerTeamMap.get(player.getUniqueId());
        if (teamId == null) {
            logger.info("Player " + player.getName() + " is not assigned to any team.");
            return null;
        }
        return teams.get(teamId);
    }

    /**
     * Retrieves all players belonging to a specific team.
     * @param teamId The UUID of the team.
     * @return A Set of Player UUIDs on the team, or an empty set if the team is not found.
     */
    public Set<UUID> getTeamMemberUUIDs(UUID teamId) {
        SoTTeam team = teams.get(teamId);
        return (team != null) ? Set.copyOf(team.getMemberUUIDs()) : Set.of();
    }

    /**
     * Retrieves all defined teams.
     * @return An unmodifiable map of all teams managed.
     */
    public Map<UUID, SoTTeam> getAllTeams() {
        return Collections.unmodifiableMap(teams);
    }

    /**
     * Removes a team and unassigns all its players.
     * @param teamId The UUID of the team to remove.
     */
    public void removeTeam(UUID teamId) {
        if (teamId == null || !teams.containsKey(teamId)) {
            logger.warning("Attempted to remove a non-existent team with ID: " + teamId);
            return;
        }

        SoTTeam team = teams.remove(teamId);
        if (team != null) {
            for (UUID playerId : team.getMemberUUIDs()) {
                playerTeamMap.remove(playerId);
            }
            logger.info("Removed team: " + team.getTeamName());
        }
    }
}