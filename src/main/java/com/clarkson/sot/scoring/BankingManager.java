package com.clarkson.sot.scoring;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.utils.SoTTeam;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Manages coin banking at the Sphinx NPC.
 * Players interact with the Sphinx to deposit unbanked coins with a 20% tax.
 */
public class BankingManager implements Listener {
    private final ScoreManager scoreManager;
    private final GameManager gameManager;
    private final Plugin plugin;

    private static final double BANKING_TAX = 0.20;
    private static final double BANKING_RADIUS_SQUARED = 3.0 * 3.0;

    // PDC key to identify the Sphinx entity
    private static NamespacedKey SPHINX_KEY;

    public BankingManager(ScoreManager scoreManager, GameManager gameManager, Plugin plugin) {
        this.scoreManager = scoreManager;
        this.gameManager = gameManager;
        this.plugin = plugin;
        SPHINX_KEY = new NamespacedKey(plugin, "sot_sphinx");
    }

    /**
     * Banks ALL of a player's unbanked coins with 20% tax.
     * Called when a player interacts with the Sphinx.
     */
    public void attemptBanking(Player player) {
        UUID playerUUID = player.getUniqueId();
        int unbankedCoins = scoreManager.getPlayerUnbankedScore(playerUUID);

        if (unbankedCoins <= 0) {
            player.sendMessage(Component.text("You have no coins to bank!", NamedTextColor.YELLOW));
            return;
        }

        // Calculate amounts
        int taxAmount = (int) Math.round(unbankedCoins * BANKING_TAX);
        int bankedAmount = unbankedCoins - taxAmount;

        // Clear unbanked score
        scoreManager.clearPlayerUnbankedScore(playerUUID);

        // Add to team's banked score
        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
        if (teamId != null) {
            SoTTeam team = gameManager.getActiveTeams().get(teamId);
            if (team != null) {
                team.addBankedScore(bankedAmount);
            }
        }

        // Feedback
        player.sendMessage(Component.text("Banked ", NamedTextColor.GREEN)
                .append(Component.text(bankedAmount + " coins", NamedTextColor.GOLD))
                .append(Component.text(" (tax: " + taxAmount + ")", NamedTextColor.GRAY)));
        plugin.getLogger().fine(player.getName() + " banked " + bankedAmount + " coins (tax: " + taxAmount
                + ", original: " + unbankedCoins + ")");
    }

    /**
     * Handles right-click interaction with the Sphinx entity.
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (gameManager.getCurrentState() != GameState.RUNNING) return;

        Entity clicked = event.getRightClicked();
        // Check if the entity is tagged as a Sphinx
        if (!clicked.getPersistentDataContainer().has(SPHINX_KEY, PersistentDataType.BYTE)) {
            return;
        }

        event.setCancelled(true);
        attemptBanking(event.getPlayer());
    }

    /** Returns the NamespacedKey used to tag Sphinx entities. */
    public static NamespacedKey getSphinxKey() {
        return SPHINX_KEY;
    }
}