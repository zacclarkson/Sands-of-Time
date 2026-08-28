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
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

/**
 * Listens for players interacting with their team's safe exit to escape the dungeon safely.
 *
 * <p>The exit is the absolute location derived from the SAFE_EXIT marker in a segment template.
 * Segment templates built before that marker existed define no exit, and those fall back to the
 * older heuristic: any End Portal Frame near the team's hub.
 */
public class EscapeListener implements Listener {

    /** Squared radius around the hub within which the legacy escape block is accepted. */
    private static final double LEGACY_HUB_RADIUS_SQUARED = 900; // 30 blocks

    private final GameManager gameManager;

    public EscapeListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (gameManager.getCurrentState() != GameState.RUNNING) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return; // Ignore the off-hand half of the event

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        Player player = event.getPlayer();
        PlayerStatus status = gameManager.getPlayerStateManager().getStatus(player);
        if (status != PlayerStatus.ALIVE_IN_DUNGEON) return;

        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
        if (teamId == null) return;

        boolean escaping = gameManager.hasTeamSafeExit(teamId)
                ? isAtSafeExit(teamId, clickedBlock)
                : isAtLegacyExit(teamId, clickedBlock);
        if (!escaping) return;

        event.setCancelled(true);
        gameManager.handlePlayerLeave(player);
    }

    /**
     * The builder tool records the air block in front of the face the builder clicked, so a marker
     * placed on the exit block's top face sits one block above it. Accept the marked block itself
     * and the block directly beneath it, and nothing else — escaping is irreversible, so a radius
     * would arm the whole floor around the exit.
     */
    private boolean isAtSafeExit(UUID teamId, Block clickedBlock) {
        Location clicked = clickedBlock.getLocation();
        return gameManager.isTeamSafeExitAt(teamId, clicked)
                || gameManager.isTeamSafeExitAt(teamId, clicked.clone().add(0, 1, 0));
    }

    /** Pre-SAFE_EXIT behaviour: any End Portal Frame within 30 blocks of the team's hub. */
    private boolean isAtLegacyExit(UUID teamId, Block clickedBlock) {
        if (clickedBlock.getType() != Material.END_PORTAL_FRAME) return false;

        Location hubLocation = gameManager.getTeamHubLocation(teamId);
        if (hubLocation == null) return false;

        Location blockLoc = clickedBlock.getLocation();
        if (!blockLoc.getWorld().equals(hubLocation.getWorld())) return false;
        return blockLoc.distanceSquared(hubLocation) <= LEGACY_HUB_RADIUS_SQUARED;
    }
}
