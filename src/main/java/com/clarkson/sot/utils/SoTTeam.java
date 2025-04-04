package com.clarkson.sot.utils;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.visuals.VisualSandTimerDisplay;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class SoTTeam {
    // --- General Team Info ---
    

    // --- Sands of Time Specific State ---
    private int teamSandCount;
    private int bankedScore;
    private int remainingSeconds;

    // --- Timer Control ---
    private transient BukkitTask logicTimerTask;
    private transient Plugin plugin;
    private transient GameManager gameManager;

    // --- Visual Timer Link ---
    private transient VisualSandTimerDisplay visualTimerDisplay;

    public SoTTeam(TeamDefinition team) {

        
    }

    // --- Getters for General Info ---
    public UUID getTeamId() {
        return teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getTeamColor() {
        return teamColor;
    }

    public Set<UUID> getMemberUUIDs() {
        return Collections.unmodifiableSet(memberUUIDs);
    }

    // --- Methods for SoT Sand Management ---
    public int getSandCount() {
        return teamSandCount;
    }

    public void addSand(int amount) {
        if (amount > 0) {
            this.teamSandCount += amount;
        } else {
            plugin.getLogger().log(Level.WARNING, "Attempted to add invalid sand amount: " + amount + " to team: " + teamName);
        }
    }

    public boolean tryUseSand(int amount) {
        if (amount > 0 && this.teamSandCount >= amount) {
            this.teamSandCount -= amount;
            return true;
        }
        plugin.getLogger().log(Level.WARNING, "Failed to use sand. Requested: " + amount + ", Available: " + teamSandCount + " for team: " + teamName);
        return false;
    }

    // --- Methods for SoT Score Management ---
    public int getBankedScore() {
        return bankedScore;
    }

    public void addBankedScore(int score) {
        if (score > 0) {
            this.bankedScore += score;
        } else {
            plugin.getLogger().log(Level.WARNING, "Attempted to add invalid score: " + score + " to team: " + teamName);
        }
    }

    @Override
    public String toString() {
        return "SoTTeam{" +
                "teamId=" + teamId +
                ", teamName='" + teamName + '\'' +
                ", teamColor='" + teamColor + '\'' +
                ", members=" + memberUUIDs.size() +
                ", sand=" + teamSandCount +
                ", score=" + bankedScore +
                ", secondsLeft=" + remainingSeconds +
                '}';
    }
}