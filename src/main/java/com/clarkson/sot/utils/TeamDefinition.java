package com.clarkson.sot.utils;

import java.util.UUID;
import net.kyori.adventure.text.format.TextColor;

public class TeamDefinition {
    private final UUID teamId;
    private final String teamName;
    private final String teamColor;
    private final TextColor adventureTextColor;

    public TeamDefinition(UUID teamId, String teamName, String teamColor, TextColor adventureTextColor) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.teamColor = teamColor;
        this.adventureTextColor = adventureTextColor;
    }

    public UUID getId() { return teamId; }
    public String getName() { return teamName; }
    public String getColor() { return teamColor; }
    public TextColor getAdventureTextColor() { return adventureTextColor; }
}
