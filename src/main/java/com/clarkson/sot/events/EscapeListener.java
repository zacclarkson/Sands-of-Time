package com.clarkson.sot.events;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.utils.PlayerStatus;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * Listens for players interacting with the safe exit point (e.g., an End Portal Frame block)
 * to escape the dungeon safely.
 */
public class EscapeListener implements Listener {

    private final GameManager gameManager;

    public EscapeListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (gameManager.getCurrentState() != GameState.RUNNING) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // Use END_PORTAL_FRAME as the escape point marker
        if (clickedBlock.getType() != Material.END_PORTAL_FRAME) return;

        Player player = event.getPlayer();
        PlayerStatus status = gameManager.getPlayerStateManager().getStatus(player);
        if (status != PlayerStatus.ALIVE_IN_DUNGEON) return;

        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
        if (teamId == null) return;

        // Verify the player is in their team's dungeon hub area
        // (the escape point should be in the hub)
        Location hubLocation = gameManager.getTeamHubLocation(teamId);
        if (hubLocation == null) return;

        // Only allow escape if within reasonable distance of the hub
        Location blockLoc = clickedBlock.getLocation();
        if (!blockLoc.getWorld().equals(hubLocation.getWorld())) return;
        if (blockLoc.distanceSquared(hubLocation) > 900) return; // Within 30 blocks of hub

        event.setCancelled(true);
        gameManager.handlePlayerLeave(player);
    }
}
