package com.clarkson.sot.utils;

import java.util.Set;
import java.util.UUID;

public class Team {
    private final UUID teamId;
    private final String teamName;
    private final String teamColor;
    private final Set<UUID> memberUUIDs;

    public Team(String teamName, String teamColor) {


    }

    public String getTeamName() {
        return teamName;
    }

    public String getTeamColor() {
        return teamColor;
    }
}
