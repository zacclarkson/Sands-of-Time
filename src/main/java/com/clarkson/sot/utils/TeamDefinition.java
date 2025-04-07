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

    public TeamDefinition(UUID teamUUID, String teamName, String teamColor) {
        this.teamId = teamUUID;
        this.teamName = teamName;
        this.teamColor = teamColor;

        switch (teamColor.toLowerCase()) {
            case "red":
            this.adventureTextColor = TextColor.color(0xFF0000);
            break;
            case "blue":
            this.adventureTextColor = TextColor.color(0x0000FF);
            break;
            case "green":
            this.adventureTextColor = TextColor.color(0x00FF00);
            break;
            case "lime":
            this.adventureTextColor = TextColor.color(0x32CD32);
            break;
            case "magenta":
            this.adventureTextColor = TextColor.color(0xFF00FF);
            break;
            case "purple":
            this.adventureTextColor = TextColor.color(0x800080);
            break;
            case "pink":
            this.adventureTextColor = TextColor.color(0xFFC0CB);
            break;
            case "aqua":
            this.adventureTextColor = TextColor.color(0x00FFFF);
            break;
            case "yellow":
            this.adventureTextColor = TextColor.color(0xFFFF00);
            break;
            case "orange":
            this.adventureTextColor = TextColor.color(0xFFA500);
            break;
            default:
            this.adventureTextColor = TextColor.color(0xFFFFFF); // Default to white
            break;
        }
    }

    public UUID getId() { return teamId; }
    public String getName() { return teamName; }
    public String getColor() { return teamColor; }
    public TextColor getAdventureTextColor() { return adventureTextColor; }
}
