package com.clarkson.sot.scoring;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.clarkson.sot.utils.SoTTeam;
import com.clarkson.sot.utils.TeamManager;

public class ScoreManager {
    private final TeamManager teamManager; // To update team banked scores
    // Potentially need reference to DungeonManager or PlacedSegments to check depth for scaling

    public ScoreManager(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    public void playerCollectedCoin(Player player, int baseCoinValue, Location collectionLocation) {
        // Calculate scaled value based on location/depth (needs logic)
        int scaledValue = calculateScaledValue(baseCoinValue, collectionLocation);
        // Track this value for the player (needs PlayerData system to hold unbanked coins)
        // updatePlayerUnbankedScore(player, scaledValue);
    }

    private int calculateScaledValue(int baseValue, Location location) {
        // TODO: Implement logic to determine depth/difficulty based on location
        // Example: distance from hub, segment tags?
        // Return baseValue * multiplier
        return baseValue;
    }

    public void applyDeathPenalty(Player player) {
        // Get player's current unbanked coin count (from PlayerData)
        // Calculate penalty amount (unbanked * 0.20)
        // Deduct penalty from player's unbanked count
        // Optionally, drop some coins? (As per wiki description implying item loss)
        // Reset player's unbanked count to 0 or remaining amount after penalty? (Clarify exact rule)
    }

    public void playerBankedCoins(Player player, int amountBanked) {
        // Add amountBanked to the player's team score via TeamManager
        SoTTeam team = teamManager.getPlayerTeam(player);
        if (team != null) {
            team.addBankedScore(amountBanked);
        }
    }

    public void playerEscaped(Player player) {
        // Get player's final unbanked coin count (from PlayerData)
        // Add this full amount to the player's team score (no tax)
         SoTTeam team = teamManager.getPlayerTeam(player);
         if (team != null) {
             // int finalCoins = getPlayerUnbankedScore(player);
             // team.addBankedScore(finalCoins);
         }
        // Clear player's unbanked score
    }

    public void applyTimerEndPenalty(Player player) {
        // Called by GameManager for players trapped inside
        // Clear player's unbanked score (they lose everything)
        // setPlayerUnbankedScore(player, 0);
    }

    public void applyTimerEndPenalty(UUID memberUUID) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'applyTimerEndPenalty'");
    }

    public void applyDeathPenalty(UUID uniqueId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'applyDeathPenalty'");
    }

    public void playerEscaped(UUID uniqueId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'playerEscaped'");
    }

    // --- Need methods to get/set player unbanked scores (requires PlayerData) ---
    // private Map<UUID, Integer> playerUnbankedScores;
    // public int getPlayerUnbankedScore(Player player) { ... }
    // public void updatePlayerUnbankedScore(Player player, int delta) { ... }
    // public void setPlayerUnbankedScore(Player player, int amount) { ... }

}