package com.clarkson.sot.utils;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;

/**
 * Manages teams and team-related data for Sands of Time.
 */
public class TeamManager {
    // Stores the basic definitions of available teams
    private final Map<UUID, TeamDefinition> teamDefinitions;
    // Still maps players to team *IDs*
    private final Map<UUID, UUID> playerTeamMap;

    private static final TeamDefinition[] STANDARD_TEAMS_DATA = {
        // {UUID Suffix, Name, Color}
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

    // Helper to create consistent UUIDs for the definitions
    private static UUID createTeamUUID(int suffix) {
        String uuidSuffix = String.format("%12s", suffix).replace(' ', '0');
        return UUID.fromString("00000000-0000-0000-0000-" + uuidSuffix);
    }

    public TeamManager() {
        this.teamDefinitions = new HashMap<>();
        this.playerTeamMap = new HashMap<>();
        initializeStandardTeamDefinitions();
    }

    private void initializeStandardTeamDefinitions() {
        for (TeamDefinition def : STANDARD_TEAMS_DATA) {
            this.teamDefinitions.put(def.getId(), def);
        }
        // In a real scenario, load these definitions from a config file
    }

    public TeamDefinition getTeamDefinition(UUID teamId) {
        return teamDefinitions.get(teamId);
    }

    public Map<UUID, TeamDefinition> getAllTeamDefinitions() {
        return Collections.unmodifiableMap(teamDefinitions);
    }

    // Player assignment logic remains similar, mapping Player UUID to Team UUID
    public void assignPlayerToTeam(Player player, UUID teamId) {
         if (player == null || teamId == null) return;
         // Check if teamId exists in definitions
         if (teamDefinitions.containsKey(teamId)) {
             // Remove from old team if necessary
             UUID oldTeamId = playerTeamMap.remove(player.getUniqueId());
             // ... (optional: notify old team if needed) ...

             playerTeamMap.put(player.getUniqueId(), teamId);
             System.out.println("Assigned " + player.getName() + " to team " + teamDefinitions.get(teamId).getName());
         } else {
             System.err.println("Error: Cannot assign player " + player.getName() + " to non-existent team ID: " + teamId);
         }
    }

    public UUID getPlayerTeamId(Player player) {
        return player != null ? playerTeamMap.get(player.getUniqueId()) : null;
    }

     public Set<UUID> getTeamMemberUUIDs(UUID teamId) {
         // Find all players mapped to this teamId
         return playerTeamMap.entrySet().stream()
                 .filter(entry -> teamId.equals(entry.getValue()))
                 .map(Map.Entry::getKey)
                 .collect(Collectors.toSet());
     }

     public SoTTeam getPlayerTeam(Player player) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPlayerTeam'");
     }
}