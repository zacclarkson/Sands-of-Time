package com.clarkson.sot.events;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.utils.PlayerStatus;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Listens for player deaths during the Sands of Time game.
 * Delegates handling to GameManager.handlePlayerDeath().
 */
public class DeathListener implements Listener {

    private final GameManager gameManager;

    public DeathListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (gameManager.getCurrentState() != GameState.RUNNING) return;

        Player player = event.getEntity();
        PlayerStatus status = gameManager.getPlayerStateManager().getStatus(player);
        if (status != PlayerStatus.ALIVE_IN_DUNGEON) return;

        // Suppress default death message — we'll send our own
        event.deathMessage(null);

        // Keep inventory is OFF — items drop naturally at death location.
        // The unbanked coin penalty is handled by GameManager.
        // Keep XP dropping disabled to avoid clutter
        event.setDroppedExp(0);

        gameManager.handlePlayerDeath(player);
    }
}
