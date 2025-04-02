package com.clarkson.sot.main;

import com.clarkson.sot.dungeon.DungeonManager;
import com.clarkson.sot.dungeon.SandTimer;
import com.clarkson.sot.dungeon.VaultManager;
import com.clarkson.sot.dungeon.BankingManager;
import com.clarkson.sot.utils.PlayerStateManager;
import com.clarkson.sot.utils.SandManager;
import com.clarkson.sot.utils.ScoreManager;
import com.clarkson.sot.utils.TeamManager;
import com.clarkson.sot.utils.SoTTeam;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
public class GameManager {

    private final SoT plugin;
    private final DungeonManager dungeonManager; // Dependency
    private final TeamManager teamManager;
    private final PlayerStateManager playerStateManager;
    private final SandManager sandManager;
    private final VaultManager vaultManager;
    private final BankingManager bankingManager;
    private ScoreManager scoreManager = null;

    private Map<UUID, SoTTeam> teamsInGame; // Map of Team ID to Team object
    private GameState currentState;
    private Location dungeonHubLocation; // Central timer/sphinx area
    private Location safeExitLocation; // Where players go when they leave
    private Location deathCageLocation; // Where dead players are trapped

    // Assuming one central timer per game instance for now
    private SandTimer gameTimer; // Existing SandTimer class
    private int remainingSeconds;
    private BukkitTask timerTask;

    public GameManager(SoT plugin, DungeonManager dungeonManager, Location hubLocation, Location safeExit, Location deathCage) {
        this.plugin = plugin;
        this.dungeonManager = dungeonManager;
        this.dungeonHubLocation = hubLocation;
        this.safeExitLocation = safeExit;
        this.deathCageLocation = deathCage;

        // Initialize manager dependencies
        this.teamManager = new TeamManager();
        this.playerStateManager = new PlayerStateManager();
        this.sandManager = new SandManager(this); // Pass GameManager for time updates
        this.vaultManager = new VaultManager(plugin, this);
        this.bankingManager = new BankingManager(this.scoreManager);
        this.scoreManager = new ScoreManager(this.teamManager);
        // Initialize gameTimer here, potentially linking it to sandManager
        // ...
    }

    public void setupGame(List<Player> players, int playersPerTeam) {
        // Assign players to teams using TeamManager
        // Initialize player states using PlayerStateManager
    }

    public void startGame() {
        // Generate dungeon using DungeonManager
        // Place vaults/keys using VaultManager based on dungeon layout
        // Start the game timer (SandTimer adaptation needed)
        // Teleport players to the dungeon start/hub
        // Set GameState to RUNNING
    }

    public void endGame() {
        // Stop the timer
        // Handle players still inside (trap them, apply penalties via ScoreManager)
        // Calculate final scores
        // Teleport everyone out
        // Set GameState to ENDED
    }

    public void updateTimer(int seconds) {
        // Update remainingSeconds
        // Check if timer <= 0, then call handleTimerEnd()
    }

    private void handleTimerEnd() {
        // Identify players still inside (via PlayerStateManager)
        // Trap them (move to deathCageLocation)
        // Apply coin loss penalties via ScoreManager
        // Call endGame()
    }

    public void handlePlayerDeath(Player player) {
        // Use PlayerStateManager to mark player as dead
        // Use ScoreManager to apply coin loss penalty
        // Store player's inventory temporarily (if needed for revival)
        // Teleport player to deathCageLocation
        // Notify team about revival cost (via SandManager/TeamManager)
    }

    public void handlePlayerRevive(Player deadPlayer, Player reviver) {
        // Check if reviver's team has enough sand (via SandManager/TeamManager)
        // Consume sand
        // Use PlayerStateManager to mark player as alive
        // Teleport player back to hub or reviver's location
        // Restore inventory (if applicable)
    }

    public void handlePlayerLeave(Player player) {
        // Check if player is alive (via PlayerStateManager)
        // Mark player as escaped using PlayerStateManager
        // Finalize player's contribution via ScoreManager (no tax)
        // Teleport player to safeExitLocation
        // Prevent player from re-entering
    }

    // Getters for managers and state
    public PlayerStateManager getPlayerStateManager() { return playerStateManager; }
    public TeamManager getTeamManager() { return teamManager; }
    public SandManager getSandManager() { return sandManager; }
    public VaultManager getVaultManager() { return vaultManager; }
    public BankingManager getBankingManager() { return bankingManager; }
    public ScoreManager getScoreManager() { return scoreManager; }
    public GameState getCurrentState() { return currentState; }
    public int getRemainingSeconds() { return remainingSeconds; }
    public Location getDungeonHubLocation() { return dungeonHubLocation; }

    public void addSecondsToTimer(int timeBonusSeconds) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addSecondsToTimer'");
    }

    public void handleTeamTimerEnd(SoTTeam soTTeam) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'handleTeamTimerEnd'");
    }
}