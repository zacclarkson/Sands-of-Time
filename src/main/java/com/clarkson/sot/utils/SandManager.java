package com.clarkson.sot.utils; // Or your chosen package

import com.clarkson.sot.main.GameManager; // Assuming GameManager is in .main package
// Assuming SoTTeam and TeamManager are accessible (e.g., same package or imported)

import org.bukkit.entity.Player;

/**
 * Handles sand collection, usage for timer, revivals, and sacrifices
 * within the Sands of Time game context.
 */
public class SandManager {

    private final GameManager gameManager; // To interact with timer and teams

    // Constants for game balance
    private static final int SECONDS_PER_SAND = 10;
    public static final int REVIVE_COST = 1;

    public SandManager(GameManager gameManager) {
        // Validate GameManager isn't null if necessary
        if (gameManager == null) {
            throw new IllegalArgumentException("GameManager cannot be null for SandManager!");
        }
        this.gameManager = gameManager;
    }

    /**
     * Adds collected sand to the player's team total.
     * Typically called when a player picks up a sand item.
     *
     * @param player The player who collected the sand.
     * @param amount The amount of sand collected.
     */
    public void collectSandItem(Player player, int amount) {
        if (player == null || amount <= 0) {
            return; // Ignore invalid input
        }

        SoTTeam team = gameManager.getTeamManager().getPlayerTeam(player);
        if (team != null) {
            team.addSand(amount);
            // Optional: Provide feedback to player/team
            // player.sendMessage("Your team gained " + amount + " sand!");
        } else {
            // Log error or handle case where player isn't on a team
             System.err.println("Warning: Player " + player.getName() + " collected sand but is not on a team.");
        }
    }

    /**
     * Attempts to use sand from the player's team to add time to the central timer.
     *
     * @param player The player attempting to use the sand.
     * @param amount The amount of sand blocks to use.
     */
    public void useSandForTimer(Player player, int amount) {
        if (player == null || amount <= 0) {
            return; // Ignore invalid input
        }

        SoTTeam team = gameManager.getTeamManager().getPlayerTeam(player);
        if (team == null) {
            player.sendMessage("You are not on a team!"); // Feedback
            return;
        }

        // Check if the team has enough sand *before* trying to use it
        if (team.getSandCount() >= amount) {
            // Attempt to consume the sand
            if (team.tryUseSand(amount)) {
                // Calculate time bonus
                int timeBonusSeconds = amount * SECONDS_PER_SAND;

                // Add time via the GameManager (which handles caps etc.)
                gameManager.addSecondsToTimer(timeBonusSeconds);

                // Provide feedback
                player.sendMessage("Added " + timeBonusSeconds + " seconds to the timer!");

            } else {
                // This case should ideally not happen if the initial check passed,
                // but could occur with concurrency issues if not handled carefully.
                player.sendMessage("Failed to use sand (Concurrency issue?).");
            }
        } else {
            // Not enough sand feedback
            player.sendMessage("Your team doesn't have enough sand! (Need " + amount + ", Have " + team.getSandCount() + ")");
        }
    }

    /**
     * Attempts to consume the required sand cost for reviving a player.
     * The actual revival logic (teleporting, changing status) is handled by GameManager.
     *
     * @param reviver The player initiating the revive (whose team pays).
     * @return true if the sand cost was successfully paid, false otherwise.
     */
    public boolean attemptRevive(Player reviver) {
        if (reviver == null) {
            return false;
        }

        SoTTeam team = gameManager.getTeamManager().getPlayerTeam(reviver);
        if (team == null) {
             // reviver.sendMessage("You must be on a team to revive someone!");
            return false;
        }

        // Attempt to use the sand cost for revival
        if (team.tryUseSand(REVIVE_COST)) {
            // reviver.sendMessage("Used " + REVIVE_COST + " sand to revive teammate!");
            return true; // Sand cost paid successfully
        } else {
            // reviver.sendMessage("Your team doesn't have enough sand to revive! (Need " + REVIVE_COST + ")");
            return false; // Not enough sand
        }
    }

    /**
     * Attempts to consume the required sand cost for a sand sacrifice point.
     * The actual reward/effect logic is handled elsewhere (e.g., GameListener or GameManager).
     *
     * @param player         The player initiating the sacrifice.
     * @param amountRequired The amount of sand needed for this specific sacrifice.
     * @return true if the sand cost was successfully paid, false otherwise.
     */
    public boolean attemptSandSacrifice(Player player, int amountRequired) {
        if (player == null || amountRequired <= 0) {
            return false;
        }

        SoTTeam team = gameManager.getTeamManager().getPlayerTeam(player);
        if (team == null) {
            // player.sendMessage("You must be on a team to use a sand sacrifice!");
            return false;
        }

        // Attempt to use the required amount of sand
        if (team.tryUseSand(amountRequired)) {
             // player.sendMessage("Used " + amountRequired + " sand for the sacrifice!");
            return true; // Sand cost paid successfully
        } else {
            // player.sendMessage("Your team doesn't have enough sand! (Need " + amountRequired + ", Have " + team.getSandCount() + ")");
            return false; // Not enough sand
        }
    }
}