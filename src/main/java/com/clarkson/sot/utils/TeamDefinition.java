package com.clarkson.sot.utils;

import java.util.UUID;

public class TeamDefinition {
    private final UUID teamId;
    private final String teamName;
    private final String teamColor;

    public TeamDefinition(UUID teamId, String teamName, String teamColor) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.teamColor = teamColor;
    }

    public UUID getId() { return teamId; }
    public String getName() { return teamName; }
    public String getColor() { return teamColor; }
}
