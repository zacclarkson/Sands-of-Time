package com.clarkson.sot.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds per-player builder sessions. Shared between ToolListener and SetBuilderModeCommand.
 */
public class BuilderSessionManager {

    private final Map<UUID, PlayerBuilderSession> sessions = new HashMap<>();

    /** Gets or creates the session for a player. */
    public PlayerBuilderSession getSession(UUID playerId) {
        return sessions.computeIfAbsent(playerId, id -> new PlayerBuilderSession());
    }

    public void clearSession(UUID playerId) {
        sessions.remove(playerId);
    }
}
