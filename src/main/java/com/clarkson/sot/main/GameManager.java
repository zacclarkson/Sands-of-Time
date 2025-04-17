package com.clarkson.sot.main;

// Required Imports (ensure all needed imports are present)
import com.clarkson.sot.dungeon.*; // Includes Dungeon, DungeonBlueprint, VaultColor, VaultManager
import com.clarkson.sot.dungeon.DoorManager; // Import DoorManager
import com.clarkson.sot.events.FloorItemManager; // Import FloorItemManager
import com.clarkson.sot.scoring.BankingManager;
import com.clarkson.sot.scoring.ScoreManager;
import com.clarkson.sot.utils.*; // PlayerStateManager, PlayerStatus, SandManager, SoTTeam, TeamDefinition, TeamManager

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap; // Added for maps accessed by listeners

import javax.annotation.Nullable;

/**
 * Manages the overall state, lifecycle, and coordination of a Sands of Time game instance.
 * Hub, Safe Exit, and Death Cage locations are now instance-specific.
 * The game ends automatically when the last team's timer expires.
 */
public class GameManager {

    // --- Fields ---
    private final Plugin plugin;
    private GameState currentState;
    private final TeamManager teamManager;
    private final PlayerStateManager playerStateManager;
    private final SandManager sandManager;
    private final ScoreManager scoreManager;
    private final BankingManager bankingManager;
    private final VaultManager vaultManager;
    private final DungeonGenerator dungeonGenerator;
    private final FloorItemManager floorItemManager; // Added
    private final DoorManager doorManager; // Added
    private final Map<UUID, DungeonManager> teamDungeonManagers; // TeamID -> Manager for their instance
    private final Map<UUID, SoTTeam> activeTeamsInGame; // TeamID -> Active team object
    private DungeonBlueprint dungeonLayoutBlueprint; // Shared blueprint for this game run

    // --- Refactored Locations ---
    private final Location lobbyLocation; // Main world anchor (e.g., for visual timers)
    private final Location configTrappedLocation; // Universal location for trapped players

    // --- Constants ---
    private static final Vector DUNGEON_BASE_OFFSET = new Vector(10000, 100, 10000); // Offset from world spawn/anchor
    private static final Vector TEAM_DUNGEON_SPACING = new Vector(5000, 0, 0); // Space between team instances

    /**
     * Constructor for GameManager (Refactored).
     * Initializes managers and loads configuration. Takes lobby and trapped locations.
     *
     * @param plugin            The main plugin instance.
     * @param lobbyLocation     A central location in the main world (e.g., lobby) used as an anchor.
     * @param trappedLocation   The universal location where players are sent when trapped by the timer.
     */
    public GameManager(Plugin plugin, Location lobbyLocation, Location trappedLocation) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.lobbyLocation = Objects.requireNonNull(lobbyLocation, "Lobby location cannot be null");
        this.configTrappedLocation = Objects.requireNonNull(trappedLocation, "Trapped location cannot be null");

        // Initialize managers - ORDER MATTERS based on dependencies
        this.playerStateManager = new PlayerStateManager();
        this.teamManager = new TeamManager(this); // Pass self
        this.scoreManager = new ScoreManager(teamManager, this, plugin);
        this.bankingManager = new BankingManager(scoreManager);
        this.sandManager = new SandManager(this); // Pass self
        this.vaultManager = new VaultManager((SoT) plugin, this); // Pass SoT plugin, GameManager
        this.floorItemManager = new FloorItemManager((SoT) plugin, this, scoreManager); // Pass SoT plugin, GameManager, ScoreManager
        this.doorManager = new DoorManager((SoT) plugin, this); // Pass SoT plugin, GameManager
        this.dungeonGenerator = new DungeonGenerator(plugin);

        // Initialize maps
        this.activeTeamsInGame = new ConcurrentHashMap<>(); // Use concurrent maps if accessed by events/tasks
        this.teamDungeonManagers = new ConcurrentHashMap<>();

        // Set initial state
        this.currentState = GameState.SETUP;

        // Load dungeon segment templates
        if (!this.dungeonGenerator.loadSegmentTemplates(plugin.getDataFolder())) {
            plugin.getLogger().severe("Failed to load dungeon segments into DungeonGenerator. Game cannot start.");
            this.currentState = GameState.ENDED; // Prevent starting
        }

        plugin.getLogger().info("GameManager initialized.");
    }

    /**
     * Sets up the participating teams for the current game instance.
     * Creates SoTTeam objects and stores them.
     * Uses lobbyLocation to determine visual timer placement.
     *
     * @param participatingTeamIds List of UUIDs for teams participating.
     * @param allPlayersInGame     List of all players involved in the game.
     */
    public void setupGame(List<UUID> participatingTeamIds, List<Player> allPlayersInGame) {
        if (currentState != GameState.SETUP) { /* ... warning ... */ return; }
        if (participatingTeamIds == null || participatingTeamIds.isEmpty()) { /* ... warning ... */ return; }
        plugin.getLogger().info("Setting up game with " + participatingTeamIds.size() + " teams.");

        // Clear state from previous games (call clear methods on managers)
        activeTeamsInGame.clear();
        teamDungeonManagers.clear();
        playerStateManager.clearAllStates();
        scoreManager.clearAllUnbankedScores();
        vaultManager.clearAllTeamStates(); // Assuming VaultManager has this
        doorManager.clearAllTeamStates(); // Assuming DoorManager has this
        floorItemManager.clearAllTeamStates(); // Assuming FloorItemManager has this
        dungeonLayoutBlueprint = null;

        // ... (Validate player assignments - same as before) ...

        // Create SoTTeam instances for each participating team
        for (UUID teamId : participatingTeamIds) {
            TeamDefinition definition = teamManager.getTeamDefinition(teamId);
            if (definition == null) { /* ... warning ... */ continue; }

            Location visualTimerBottom = determineVisualTimerBottomLocation(definition, this.lobbyLocation);
            Location visualTimerTop = determineVisualTimerTopLocation(definition, this.lobbyLocation);

            SoTTeam activeTeam = new SoTTeam(definition, plugin, this, visualTimerBottom, visualTimerTop);
            activeTeamsInGame.put(teamId, activeTeam);
            plugin.getLogger().info("Initialized SoTTeam for: " + definition.getName());

            // Add members and initialize state
             Set<UUID> memberUUIDs = teamManager.getTeamMemberUUIDs(teamId);
             for (UUID memberId : memberUUIDs) {
                 Player p = Bukkit.getPlayer(memberId);
                 if (p != null && p.isOnline()) {
                     activeTeam.addMember(p);
                     playerStateManager.initializePlayer(p);
                 } else { /* ... warning ... */ }
             }
        }
        plugin.getLogger().info("Game setup complete. " + activeTeamsInGame.size() + " active teams created. Ready to start.");
    }

    /**
     * Starts the actual game: generates layout, creates dungeon instances,
     * teleports players to their instance-specific hubs, and starts timers.
     */
    public void startGame() {
        if (currentState != GameState.SETUP) { /* ... error ... */ return; }
        if (activeTeamsInGame.isEmpty()) { /* ... error ... */ return; }
        // Removed check for vaultManager/dungeonGenerator null as constructor handles it

        plugin.getLogger().info("Starting Sands of Time game generation...");

        // 1. Generate Dungeon Layout Blueprint (Relative Structure)
        this.dungeonLayoutBlueprint = dungeonGenerator.generateDungeonLayout();
        if (this.dungeonLayoutBlueprint == null || this.dungeonLayoutBlueprint.getRelativeSegments().isEmpty()) {
             plugin.getLogger().severe("Failed to generate dungeon layout blueprint. Aborting game start.");
             currentState = GameState.ENDED; return;
        }

        World gameWorld = lobbyLocation.getWorld();
        if (gameWorld == null) { /* ... error ... */ currentState = GameState.ENDED; return; }

        // 2. Create and Initialize Dungeon Instance for Each Team
        int teamIndex = 0;
        Location currentDungeonBase = gameWorld.getSpawnLocation().clone().add(DUNGEON_BASE_OFFSET); // Or use lobbyLocation as base?
        teamDungeonManagers.clear();

        for (SoTTeam team : activeTeamsInGame.values()) {
            UUID teamId = team.getTeamId();
            Location teamOrigin = currentDungeonBase.clone().add(TEAM_DUNGEON_SPACING.clone().multiply(teamIndex));
            plugin.getLogger().info("Creating dungeon instance for team " + team.getTeamName() + " at " + teamOrigin.toVector());

            // *** CORRECTED: Instantiate DungeonManager correctly ***
            // It retrieves other managers via 'this' (GameManager) passed to its constructor
            DungeonManager teamDungeon = new DungeonManager(plugin, this, teamId, teamOrigin, dungeonLayoutBlueprint);

            // Initialize the instance (pastes segments, populates features, creates Dungeon data object)
            if (!teamDungeon.initializeInstance()) {
                plugin.getLogger().severe("Failed to initialize dungeon instance for team " + team.getTeamName() + ". This team may not be playable.");
                // Handle failure? Skip team? Abort? For now, just log.
            }
            teamDungeonManagers.put(teamId, teamDungeon); // Store the manager

            // 3. Teleport Team Members to their specific Hub
            Location teamHubLocation = getTeamHubLocation(teamId); // Get instance-specific hub
            if (teamHubLocation != null) {
                 for (UUID memberId : team.getMemberUUIDs()) {
                     Player player = Bukkit.getPlayer(memberId);
                     if (player != null && player.isOnline()) {
                         final Location teleportTarget = teamHubLocation.clone().add(0.5, 0.1, 0.5);
                         teleportTarget.setYaw(player.getLocation().getYaw());
                         teleportTarget.setPitch(0);
                         Bukkit.getScheduler().runTask(plugin, () -> { if (player.isValid()) player.teleport(teleportTarget); });
                     }
                 }
            } else { /* ... warning ... */ }
            teamIndex++;
        }

        // 4. Start All Team Timers
        for (SoTTeam team : activeTeamsInGame.values()) { team.startTimer(); }

        // 5. Set Game State & Announce
        this.currentState = GameState.RUNNING;
        Bukkit.getServer().broadcast(Component.text("Sands of Time has begun!", NamedTextColor.GOLD, TextDecoration.BOLD));
        plugin.getLogger().info("Sands of Time game started with per-team dungeons.");
    }

    /** Forcefully ends the current Sands of Time game */
    public void endGame() {
        if (currentState != GameState.RUNNING && currentState != GameState.PAUSED) { /* ... warning ... */ return; }
        plugin.getLogger().info("Forcefully ending Sands of Time game...");
        for (SoTTeam team : activeTeamsInGame.values()) { if (team.isTimerRunning()) team.stopTimer(); }
        endGameInternal("Game forcefully ended.");
    }

    /** Handles consequences when a specific team's timer expires. */
    public void handleTeamTimerEnd(SoTTeam team) {
        // ... (Implementation remains mostly the same, trapping players, checking game end) ...
         if (team == null) { /* ... warning ... */ return; }
         if (currentState != GameState.RUNNING) { /* ... warning ... */ checkGameEndCondition(); return; }
         plugin.getLogger().warning("Timer has run out for team: " + team.getTeamName() + "!");
         Set<UUID> memberUUIDs = team.getMemberUUIDs();
         boolean teamWiped = true;
         for (UUID memberUUID : memberUUIDs) {
             PlayerStatus currentStatus = playerStateManager.getStatus(memberUUID);
             if (currentStatus == PlayerStatus.ESCAPED_SAFE) { teamWiped = false; continue; }
             if (currentStatus == PlayerStatus.TRAPPED_TIMER_OUT) continue;
             if (currentStatus == PlayerStatus.ALIVE_IN_DUNGEON || currentStatus == PlayerStatus.DEAD_AWAITING_REVIVE) {
                 plugin.getLogger().info("Player " + memberUUID + " trapped due to timer expiry!");
                 playerStateManager.updateStatus(memberUUID, PlayerStatus.TRAPPED_TIMER_OUT);
                 scoreManager.applyTimerEndPenalty(memberUUID);
                 Player onlinePlayer = Bukkit.getPlayer(memberUUID);
                 if (onlinePlayer != null && onlinePlayer.isOnline()) {
                     if (configTrappedLocation != null) {
                         final Component message = Component.text("Your team's timer ran out! You are trapped!", NamedTextColor.RED, TextDecoration.BOLD);
                         Bukkit.getScheduler().runTask(plugin, () -> { if (onlinePlayer.isValid()) { onlinePlayer.teleport(configTrappedLocation); onlinePlayer.sendMessage(message); } });
                         NamedTextColor teamColor = teamManager.getTeamColor(team.getTeamId()); // Get team color
                         Component broadcastMessage = Component.text(onlinePlayer.getName(), teamColor).append(Component.text(" has been trapped!", NamedTextColor.RED));
                         Bukkit.getServer().broadcast(broadcastMessage);
                     } else { /* ... error ... */ }
                 }
             }
         }
         // Announce team wipe/timer end
         final Component message = teamWiped ? Component.text("Team " + team.getTeamName() + " got locked in!", NamedTextColor.RED)
                                             : Component.text("Team " + team.getTeamName() + "'s timer ran out!", NamedTextColor.YELLOW);
         Bukkit.getServer().broadcast(message);
         checkGameEndCondition(); // Check if game should end now
    }

    /** Checks if all active teams' timers have expired. */
    private void checkGameEndCondition() {
        if (currentState != GameState.RUNNING) return;
        for (SoTTeam team : activeTeamsInGame.values()) { if (team.isTimerRunning()) return; } // Game continues if any timer runs
        plugin.getLogger().info("Game end condition met: All team timers have expired.");
        endGameInternal("All timers expired.");
    }

    /** Internal method containing the logic to actually end the game */
    private void endGameInternal(String reason) {
        if (currentState == GameState.ENDED) return;
        plugin.getLogger().info("Executing internal game end sequence. Reason: " + reason);
        this.currentState = GameState.ENDED;

        for (SoTTeam team : activeTeamsInGame.values()) { if (team.isTimerRunning()) team.stopTimer(); }

        // TODO: Final score calculations & display

        // TODO: Teleport remaining players out

        // Cleanup dungeon instances and manager states
        for (DungeonManager dm : teamDungeonManagers.values()) {
            dm.cleanupInstance(); // Calls clearTeamState on Vault/Door/FloorItem managers
        }
        activeTeamsInGame.clear();
        teamDungeonManagers.clear();
        playerStateManager.clearAllStates();
        scoreManager.clearAllUnbankedScores();
        // No need to call clearTeamState here again, cleanupInstance does it
        dungeonLayoutBlueprint = null;

        Bukkit.getServer().broadcast(Component.text("Sands of Time has ended!", NamedTextColor.GOLD, TextDecoration.BOLD));
        plugin.getLogger().info("Sands of Time game ended and state cleared.");
    }


    // --- Player Action Handlers ---
    public void handlePlayerDeath(Player player) { /* ... (Implementation mostly same, ensure getTeamDeathCageLocation works) ... */ }
    public void handlePlayerRevive(Player deadPlayer, Player reviver) { /* ... (Implementation mostly same, ensure getTeamHubLocation works) ... */ }
    public void handlePlayerLeave(Player player) { /* ... (Implementation mostly same, ensure getTeamSafeExitLocation works) ... */ }

    // --- Utility Methods & Getters ---
    public SoTTeam getActiveTeamForPlayer(Player player) { /* ... */ return null;}
    public Map<UUID, SoTTeam> getActiveTeams() { return Collections.unmodifiableMap(activeTeamsInGame); }

    /** Gets the absolute world location of the Hub for a specific team's instance. */
    @Nullable
    public Location getTeamHubLocation(UUID teamId) {
        DungeonManager teamDungeonManager = teamDungeonManagers.get(teamId);
        if (teamDungeonManager == null) return null;
        Dungeon teamDungeonData = teamDungeonManager.getDungeonData();
        // Prioritize getting pre-calculated location from Dungeon object
        if (teamDungeonData != null && teamDungeonData.getHubLocation() != null) {
            return teamDungeonData.getHubLocation();
        }
        // Fallback (should ideally not be needed after init)
        plugin.getLogger().warning("Falling back to calculating hub location from blueprint for team " + teamId);
        Location teamOrigin = teamDungeonManager.getDungeonOrigin();
        if (dungeonLayoutBlueprint == null || teamOrigin == null) return null;
        Vector hubRelative = dungeonLayoutBlueprint.getHubRelativeLocation();
        return (hubRelative != null) ? teamOrigin.clone().add(hubRelative) : null;
    }

    /** Placeholder method to get the instance-specific Safe Exit location. */
    @Nullable
    private Location getTeamSafeExitLocation(UUID teamId) {
        DungeonManager teamDungeonManager = teamDungeonManagers.get(teamId);
        if (teamDungeonManager == null) return null;
        Dungeon teamDungeonData = teamDungeonManager.getDungeonData();
        if (teamDungeonData == null) return null;
        // TODO: Implement logic in Dungeon.java to store/retrieve the absolute safe exit location.
        // return teamDungeonData.getSafeExitLocation();
        plugin.getLogger().warning("getTeamSafeExitLocation: Needs implementation in Dungeon.java");
        return teamDungeonData.getHubLocation(); // Placeholder: return hub for now
    }

    /** Placeholder method to get the instance-specific Death Cage location. */
     @Nullable
     private Location getTeamDeathCageLocation(UUID teamId) {
         DungeonManager teamDungeonManager = teamDungeonManagers.get(teamId);
         if (teamDungeonManager == null) return null;
         Dungeon teamDungeonData = teamDungeonManager.getDungeonData();
         if (teamDungeonData == null) return null;
         List<Location> cages = teamDungeonData.getDeathCageLocations();
         if (cages == null || cages.isEmpty()) {
             plugin.getLogger().warning("No death cage locations defined in Dungeon data for team " + teamId);
             // Fallback to universal trapped location if no specific cage found
             return this.configTrappedLocation;
         }
         return cages.get(new Random().nextInt(cages.size()));
     }

    /** Finds the team ID associated with a given world location. */
    @Nullable
    public UUID getTeamIdForLocation(Location location) { /* ... (Implementation remains the same) ... */ return null;}
    /** Gets the DungeonManager instance for a specific team. */
    @Nullable public DungeonManager getTeamDungeonManager(UUID teamId) { return teamDungeonManagers.get(teamId); }
    /** Determines the bottom location for a team's visual timer. */
    private Location determineVisualTimerBottomLocation(TeamDefinition teamDef, Location anchorLocation) { /* ... Placeholder ... */ return anchorLocation; }
    /** Determines the top location for a team's visual timer. */
    private Location determineVisualTimerTopLocation(TeamDefinition teamDef, Location anchorLocation) { /* ... Placeholder ... */ return anchorLocation; }

    // --- Standard Getters ---
    public GameState getCurrentState() { return currentState; }
    public Plugin getPlugin() { return plugin; }
    public TeamManager getTeamManager() { return teamManager; }
    public PlayerStateManager getPlayerStateManager() { return playerStateManager; }
    public SandManager getSandManager() { return sandManager; }
    public ScoreManager getScoreManager() { return scoreManager; }
    public BankingManager getBankingManager() { return bankingManager; }
    public VaultManager getVaultManager() { return vaultManager; }
    public DungeonGenerator getDungeonGenerator() { return dungeonGenerator; }
    public FloorItemManager getFloorItemManager() { return floorItemManager; } // Added Getter
    public DoorManager getDoorManager() { return doorManager; } // Added Getter
    public Location getTrappedLocation() { return configTrappedLocation; }
    public Location getLobbyLocation() { return lobbyLocation; }

}