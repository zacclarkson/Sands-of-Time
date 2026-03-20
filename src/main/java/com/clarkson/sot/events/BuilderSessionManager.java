package com.clarkson.sot.events;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maintains a PlayerBuilderSession for each builder currently using the segment tool.
 * Sessions are created lazily when first accessed and persist until the server restarts.
 */
public class BuilderSessionManager {

    private final Map<UUID, PlayerBuilderSession> sessions = new HashMap<>();

    /**
     * Gets the session for the given player, creating a new one if it doesn't exist.
     */
    @NotNull
    public PlayerBuilderSession getSession(@NotNull Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), uuid -> new PlayerBuilderSession());
    }

    /**
     * Removes the session for the given player (e.g. on disconnect).
     */
    public void removeSession(@NotNull Player player) {
        sessions.remove(player.getUniqueId());
    }
}
