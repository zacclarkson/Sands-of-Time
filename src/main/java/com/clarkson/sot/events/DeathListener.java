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

        // Items drop at the death location for the corpse run to recover; the unbanked coin penalty
        // is handled by GameManager. XP dropping stays off to avoid clutter.
        event.setDroppedExp(0);

        // Carried sand drops whether or not this death keeps the inventory: it is the round's
        // currency for timer seconds and revives, so losing it is a rule of the game, not a server
        // setting. Runs before handlePlayerDeath, which queues the teleport to the death cage.
        // Priority is HIGH rather than MONITOR because this mutates state, so a plugin that turns
        // keep-inventory on at HIGHEST would still slip past — acceptable, and the vanilla default
        // needs nothing from us anyway.
        gameManager.getSandManager().dropCarriedSandOnDeath(event);

        gameManager.handlePlayerDeath(player);
    }
}
