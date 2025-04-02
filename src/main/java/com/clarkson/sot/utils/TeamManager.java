package com.clarkson.sot.utils;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;

public class TeamManager {
    private Map<UUID, SoTTeam> teams; // Team ID -> Team Object
    private Map<UUID, UUID> playerTeamMap; // Player UUID -> Team ID

    public TeamManager() {
        // Initialize maps
    }

    public void assignPlayerToTeam(Player player, UUID teamId) {
        // Add player to team map
        // Add player to SoTTeam object
    }

    public SoTTeam getTeam(UUID teamId) {
        // Return team object
        return null;
    }

     public SoTTeam getPlayerTeam(Player player) {
        // Get teamId from playerTeamMap, then get team object
        return null;
     }

    public Set<Player> getTeamMembers(UUID teamId) {
        // Get members from SoTTeam object
        return null;
    }

    // Could add methods for creating/removing teams
}