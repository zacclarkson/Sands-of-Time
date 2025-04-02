package com.clarkson.sot.dungeon;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import com.clarkson.sot.utils.ScoreManager;

public class BankingManager implements Listener {
    private final ScoreManager scoreManager; // To add banked score
    private Location sphinxLocation; // Set during game setup

    private static final double BANKING_TAX = 0.20; // 20% tax

    public BankingManager(ScoreManager scoreManager) {
        this.scoreManager = scoreManager;
        // Register listener if needed
    }

    public void setSphinxLocation(Location loc) {
        this.sphinxLocation = loc;
    }

    // Could be triggered by interaction event or command
    public void attemptBanking(Player player, int coinsToBank) {
        // Check if player is near sphinxLocation (optional)
        // Check if player actually has coinsToBank (needs PlayerData/Inventory tracking)
        // Calculate tax amount (coinsToBank * BANKING_TAX)
        // Calculate banked amount (coinsToBank - tax)
        // Remove coinsToBank from player's tracked coins
        // Add banked amount to team's score via ScoreManager
        // Provide feedback to player
    }

    // @EventHandler // Needs registration
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Check if interacted entity is the Sphinx
        // Check if player is holding coins or trigger banking UI/command
    }
}