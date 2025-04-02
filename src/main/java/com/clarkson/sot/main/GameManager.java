package com.clarkson.sot.main;

import com.clarkson.sot.dungeon.DungeonManager;
import com.clarkson.sot.dungeon.SandTimer;
import com.clarkson.sot.dungeon.VaultManager;
import com.clarkson.sot.dungeon.BankingManager;
import com.clarkson.sot.utils.PlayerStateManager;
import com.clarkson.sot.utils.PlayerStatus;
import com.clarkson.sot.utils.SandManager;
import com.clarkson.sot.utils.ScoreManager;
import com.clarkson.sot.utils.TeamManager;
import com.clarkson.sot.utils.SoTTeam;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class GameManager {

    private final Plugin plugin;
    private final TeamManager teamManager;
    private final PlayerStateManager playerStateManager;
    private final ScoreManager scoreManager;

    private GameState currentState;
    private Location deathCageLocation;

    public GameManager(Plugin plugin, TeamManager teamManager, PlayerStateManager playerStateManager, ScoreManager scoreManager, Location deathCageLocation) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.playerStateManager = playerStateManager;
        this.scoreManager = scoreManager;
        this.deathCageLocation = deathCageLocation;
    }

    public void startGame() {
        if (currentState == GameState.RUNNING) {
            plugin.getLogger().log(Level.WARNING, "Attempted to start the game, but it is already running.");
            return;
        }

        plugin.getLogger().log(Level.INFO, "Starting Sands of Time game...");
        Map<UUID, SoTTeam> teams = teamManager.getAllTeams();

        if (teams.isEmpty()) {
            plugin.getLogger().log(Level.SEVERE, "Cannot start the game: No teams found in TeamManager!");
            return;
        }

        for (SoTTeam team : teams.values()) {
            team.resetForNewGame();
            team.startTimer();
        }

        currentState = GameState.RUNNING;
        plugin.getLogger().log(Level.INFO, "Sands of Time game started.");
    }

    public void endGame() {
        if (currentState != GameState.RUNNING) {
            plugin.getLogger().log(Level.WARNING, "Attempted to end the game, but it is not running.");
            return;
        }

        plugin.getLogger().log(Level.INFO, "Ending Sands of Time game...");
        currentState = GameState.ENDED;

        Map<UUID, SoTTeam> teams = teamManager.getAllTeams();
        for (SoTTeam team : teams.values()) {
            team.stopTimer();
        }

        plugin.getLogger().log(Level.INFO, "Sands of Time game ended.");
    }

    public void handleTeamTimerEnd(SoTTeam team) {
        if (team == null || currentState != GameState.RUNNING) {
            return;
        }

        plugin.getLogger().log(Level.WARNING, "Timer has run out for team: " + team.getTeamName() + "!");
        Set<UUID> memberUUIDs = team.getMemberUUIDs();

        for (UUID memberUUID : memberUUIDs) {
            PlayerStatus currentStatus = playerStateManager.getStatus(memberUUID);

            if (currentStatus == PlayerStatus.ALIVE_IN_DUNGEON || currentStatus == PlayerStatus.DEAD_AWAITING_REVIVE) {
                plugin.getLogger().log(Level.INFO, "Player " + memberUUID + " from team " + team.getTeamName() + " is trapped due to timer!");

                playerStateManager.updateStatus(memberUUID, PlayerStatus.TRAPPED_TIMER_OUT);
                scoreManager.applyTimerEndPenalty(memberUUID);

                Player onlinePlayer = Bukkit.getPlayer(memberUUID);
                if (onlinePlayer != null && onlinePlayer.isOnline()) {
                    if (deathCageLocation != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            onlinePlayer.teleport(deathCageLocation);
                            onlinePlayer.sendMessage(Component.text("Your team's timer ran out! You are trapped!")
                                    .color(NamedTextColor.RED)
                                    .decorate(TextDecoration.BOLD));
                        });
                    } else {
                        plugin.getLogger().log(Level.SEVERE, "Death cage location is not set in GameManager!");
                    }
                }
            }
        }
    }

    public GameState getCurrentState() {
        return currentState;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public PlayerStateManager getPlayerStateManager() {
        return playerStateManager;
    }

    public ScoreManager getScoreManager() {
        return scoreManager;
    }

    public void addSecondsToTimer(int timeBonusSeconds) {
        throw new UnsupportedOperationException("Unimplemented method 'addSecondsToTimer'");
    }

    public void handlePlayerDeath(Player player) {
        throw new UnsupportedOperationException("Unimplemented method 'handlePlayerDeath'");
    }

    public VaultManager getVaultManager() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getVaultManager'");
    }
}