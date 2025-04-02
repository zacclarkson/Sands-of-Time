package com.clarkson.sot.utils;

import org.bukkit.entity.Player;

import com.clarkson.sot.main.GameManager;

public class SandManager {
    private final GameManager gameManager; // To interact with timer and teams

    public SandManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void collectSandItem(Player player, int amount) {
        // Add sand to the player's team using TeamManager
    }

    public void useSandForTimer(Player player, int amount) {
        // Check if team has enough sand
        // Consume sand via TeamManager
        // Calculate time bonus (amount * 10 seconds)
        // Add time via GameManager.updateTimer() or directly to SandTimer (needs adaptation)
        // Ensure time doesn't exceed max (e.g., 2 minutes)
    }

    public boolean attemptRevive(Player reviver) {
        // Check if team has >= 1 sand
        // If yes, consume 1 sand via TeamManager and return true
        return false;
    }

    public boolean attemptSandSacrifice(Player player, int amountRequired) {
        // Check if team has enough sand
        // If yes, consume sand via TeamManager and return true
        return false;
    }
}