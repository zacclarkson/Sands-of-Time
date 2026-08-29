package com.clarkson.sot.events;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Holds players in place during the pre-game countdown. While the game is in
 * {@link GameState#COUNTDOWN}, any positional movement by a participating player is undone by
 * snapping them back to where they were, so they cannot walk or jump away from their spawn.
 * Looking around is preserved (yaw/pitch are copied through), and the listener is inert in every
 * other game state, so it costs nothing during normal play.
 */
public class CountdownFreezeListener implements Listener {

    private final GameManager gameManager;

    public CountdownFreezeListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (gameManager.getCurrentState() != GameState.COUNTDOWN) return;

        // PlayerTeleportEvent extends PlayerMoveEvent; let teleports through, otherwise this would
        // cancel the very teleport that puts players on their spawn at the start of the countdown.
        if (event instanceof PlayerTeleportEvent) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        // Only care about actual position changes; a pure head turn leaves the block/coords equal.
        if (to == null
                || (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())) {
            return;
        }

        Player player = event.getPlayer();
        if (!gameManager.isParticipant(player.getUniqueId())) return;

        // Pin them to the from-position but keep the new look direction so they can glance around.
        Location held = from.clone();
        held.setYaw(to.getYaw());
        held.setPitch(to.getPitch());
        event.setTo(held);
    }
}
