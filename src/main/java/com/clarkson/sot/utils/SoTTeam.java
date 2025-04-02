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
    private final UUID teamId;
    private final String teamName;
    private final String teamColor;
    private final Set<UUID> memberUUIDs;

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

    // Constants for timer
    private static final int MAX_TIMER_SECONDS = 150;
    private static final int DEFAULT_START_SECONDS = 150;

    public SoTTeam(UUID teamId, String teamName, String teamColor, Plugin plugin, GameManager gameManager, Location visualBottom, Location visualTop) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.teamColor = teamColor;
        this.memberUUIDs = ConcurrentHashMap.newKeySet();

        this.plugin = plugin;
        this.gameManager = gameManager;

        if (visualBottom != null && visualTop != null && plugin != null) {
            this.visualTimerDisplay = new VisualSandTimerDisplay(plugin, this, visualBottom, visualTop);
        } else {
            if (plugin != null) {
                plugin.getLogger().log(Level.WARNING, "Visual timer locations/plugin not provided correctly for team " + teamName + ". Visual timer disabled.");
            }
            this.visualTimerDisplay = null;
        }

        resetForNewGame();
    }

    // --- Timer Control Methods ---
    public void startTimer() {
        if (logicTimerTask != null && !logicTimerTask.isCancelled()) {
            plugin.getLogger().log(Level.INFO, "Logical timer already running for team: " + teamName);
            return;
        }
        if (plugin == null || gameManager == null) {
            plugin.getLogger().log(Level.SEVERE, "Cannot start timer for team " + teamName + ". Plugin or GameManager reference missing.");
            return;
        }

        plugin.getLogger().log(Level.INFO, "Starting logical timer for team: " + teamName + " with " + remainingSeconds + "s");
        this.logicTimerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickLogicTimer, 20L, 20L);

        if (visualTimerDisplay != null) {
            visualTimerDisplay.startVisualUpdates();
        }
    }

    public void stopTimer() {
        if (logicTimerTask != null && !logicTimerTask.isCancelled()) {
            logicTimerTask.cancel();
            plugin.getLogger().log(Level.INFO, "Stopped logical timer for team: " + teamName);
        }
        logicTimerTask = null;

        if (visualTimerDisplay != null) {
            visualTimerDisplay.stopVisualUpdates();
        }
    }

    private void tickLogicTimer() {
        if (remainingSeconds > 0) {
            remainingSeconds--;
        }

        if (remainingSeconds <= 0) {
            stopTimer();
            plugin.getLogger().log(Level.WARNING, "Logical timer expired for team: " + teamName);
            gameManager.handleTeamTimerEnd(this);
        }
    }

    public void addSeconds(int secondsToAdd) {
        if (secondsToAdd <= 0) {
            plugin.getLogger().log(Level.WARNING, "Attempted to add invalid seconds: " + secondsToAdd + " to team: " + teamName);
            return;
        }
        if (remainingSeconds >= MAX_TIMER_SECONDS) {
            plugin.getLogger().log(Level.INFO, "Timer is already at maximum for team: " + teamName);
            return;
        }

        int oldSeconds = this.remainingSeconds;
        this.remainingSeconds = Math.min(oldSeconds + secondsToAdd, MAX_TIMER_SECONDS);
        int actualSecondsAdded = this.remainingSeconds - oldSeconds;

        if (actualSecondsAdded > 0 && visualTimerDisplay != null) {
            visualTimerDisplay.syncVisualState();
        }

        plugin.getLogger().log(Level.INFO, "Added " + actualSecondsAdded + "s to team " + teamName + " timer. New time: " + this.remainingSeconds + "s");
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void resetForNewGame() {
        stopTimer();
        this.teamSandCount = 0;
        this.bankedScore = 0;
        this.remainingSeconds = DEFAULT_START_SECONDS;

        if (visualTimerDisplay != null) {
            Bukkit.getScheduler().runTask(plugin, visualTimerDisplay::syncVisualState);
        }
        plugin.getLogger().log(Level.INFO, "Reset game state for team: " + teamName);
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

    // --- Methods for Team Members ---
    public void addMember(Player player) {
        if (player == null) {
            plugin.getLogger().log(Level.WARNING, "Attempted to add a null player to team: " + teamName);
            return;
        }
        if (!memberUUIDs.add(player.getUniqueId())) {
            plugin.getLogger().log(Level.WARNING, "Player " + player.getName() + " is already a member of team: " + teamName);
        }
    }

    public void removeMember(Player player) {
        if (player == null) {
            plugin.getLogger().log(Level.WARNING, "Attempted to remove a null player from team: " + teamName);
            return;
        }
        if (!memberUUIDs.remove(player.getUniqueId())) {
            plugin.getLogger().log(Level.WARNING, "Player " + player.getName() + " is not a member of team: " + teamName);
        }
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