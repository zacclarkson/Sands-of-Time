package com.clarkson.sot.events;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

/**
 * Suppresses Nether-portal travel server-wide.
 * <p>
 * The safe exit in Sands of Time is a nether portal that players walk through to escape the dungeon;
 * the actual vanilla teleport to the Nether must never fire, or it would yank players out of the game
 * world. Cancelling {@link PlayerPortalEvent} (and the entity equivalent) leaves the portal blocks
 * standing and harmless — players can stand in them freely. The escape behaviour itself is handled
 * elsewhere (a later pass); this listener only removes the vanilla teleport.
 */
public class NetherPortalListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() == TeleportCause.NETHER_PORTAL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        // EntityPortalEvent fires for non-player entities entering any portal; block it outright so
        // mobs / items don't vanish into the Nether either.
        event.setCancelled(true);
    }
}
