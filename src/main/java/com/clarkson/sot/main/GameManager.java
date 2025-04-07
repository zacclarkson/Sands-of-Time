package com.clarkson.sot.main;

// Required Imports (ensure all needed imports are present)
import com.clarkson.sot.dungeon.DungeonGenerator;
import com.clarkson.sot.dungeon.DungeonManager;
import com.clarkson.sot.dungeon.VaultManager;
// Assuming DungeonBlueprint is used now based on prior context
import com.clarkson.sot.dungeon.DungeonBlueprint;
import com.clarkson.sot.dungeon.segment.PlacedSegment;
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
import java.util.logging.Level;

/**
 * Manages the overall state, lifecycle, and coordination of a Sands of Time game instance.
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
    private final Map<UUID, DungeonManager> teamDungeonManagers;
    private final Map<UUID, SoTTeam> activeTeamsInGame;
    private DungeonBlueprint dungeonLayoutBlueprint; // Use DungeonBlueprint
    private final Location configHubLocation;
    private final Location configSafeExitLocation;
    private final List<Location> configDeathCageLocations;
    private final Location configTrappedLocation;
    private static final Vector DUNGEON_BASE_OFFSET = new Vector(10000, 100, 10000);
    private static final Vector TEAM_DUNGEON_SPACING = new Vector(5000, 0, 0);

    /**
     * Constructor for GameManager.
     * Initializes managers and loads configuration.
     */
    public GameManager(Plugin plugin, Location hubLocation, Location safeExit, List<Location> deathCageLocations, Location trappedLocation) {
        // --- Constructor Implementation (from GameManager_Refactored_Final_20250403) ---
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.configHubLocation = Objects.requireNonNull(hubLocation, "Hub location cannot be null");
        this.configSafeExitLocation = Objects.requireNonNull(safeExit, "Safe exit location cannot be null");
        this.configDeathCageLocations = Objects.requireNonNull(deathCageLocations, "Death cage locations cannot be null");
        this.configTrappedLocation = Objects.requireNonNull(trappedLocation, "Trapped location cannot be null");
        if (deathCageLocations.isEmpty()) {
            plugin.getLogger().warning("Death cage locations list is empty!");
        }

        // Initialize managers
        this.playerStateManager = new PlayerStateManager();
        this.teamManager = new TeamManager(this);
        this.scoreManager = new ScoreManager(teamManager, this, plugin);
        this.bankingManager = new BankingManager(scoreManager);
        this.sandManager = new SandManager(this);

        if (plugin instanceof SoT) {
             this.vaultManager = new VaultManager((SoT) plugin, this);
        } else {
             plugin.getLogger().severe("Plugin instance is not of type SoT! VaultManager may not function correctly.");
             this.vaultManager = null;
        }

        this.dungeonGenerator = new DungeonGenerator(plugin);

        this.activeTeamsInGame = new HashMap<>();
        this.teamDungeonManagers = new HashMap<>();

        this.currentState = GameState.SETUP;

        if (this.dungeonGenerator != null) {
            // Assuming loadSegmentTemplates is still relevant for the generator
            if (!this.dungeonGenerator.loadSegmentTemplates(plugin.getDataFolder())) {
                plugin.getLogger().severe("Failed to load dungeon segments into DungeonGenerator. Game cannot start.");
                this.currentState = GameState.ENDED;
            }
        } else {
            plugin.getLogger().severe("DungeonGenerator failed to initialize.");
            this.currentState = GameState.ENDED;
        }
         plugin.getLogger().info("GameManager initialized.");
         // --- End Constructor Implementation ---
    }

    /**
     * Sets up the participating teams for the current game instance.
     * Creates SoTTeam objects and stores them.
     */
    public void setupGame(List<UUID> participatingTeamIds, List<Player> allPlayersInGame) {
        // --- setupGame Implementation (from GameManager_Refactored_Final_20250403) ---
         if (currentState != GameState.SETUP) {
             plugin.getLogger().warning("Cannot setup game, current state is " + currentState);
             return;
         }
         if (participatingTeamIds == null || participatingTeamIds.isEmpty()) {
              plugin.getLogger().warning("Cannot setup game: No participating team IDs provided.");
              return;
         }
         plugin.getLogger().info("Setting up game with " + participatingTeamIds.size() + " teams.");

         activeTeamsInGame.clear();
         teamDungeonManagers.clear();
         playerStateManager.clearAllStates();
         scoreManager.clearAllUnbankedScores();
         // vaultManager might need a clear method too: vaultManager.clearTeamStates();
         dungeonLayoutBlueprint = null;

         // Validate player assignments
         if (allPlayersInGame != null) {
             for (Player p : allPlayersInGame) {
                 UUID teamId = teamManager.getPlayerTeamId(p);
                 if (teamId == null || !participatingTeamIds.contains(teamId)) {
                     plugin.getLogger().warning("Player " + p.getName() + " is not assigned to a participating team!");
                 }
             }
         }

         // Create SoTTeam instances
         for (UUID teamId : participatingTeamIds) {
             TeamDefinition definition = teamManager.getTeamDefinition(teamId);
             if (definition == null) {
                 plugin.getLogger().warning("Cannot setup team: Definition not found for ID " + teamId + ". Skipping team.");
                 continue;
             }

             Location visualTimerBottom = determineVisualTimerBottomLocation(definition, configHubLocation);
             Location visualTimerTop = determineVisualTimerTopLocation(definition, configHubLocation);

             // Use the SoTTeam constructor that takes dependencies and creates TeamTimer internally
             SoTTeam activeTeam = new SoTTeam(
                     definition, plugin, this, visualTimerBottom, visualTimerTop
             );
             activeTeamsInGame.put(teamId, activeTeam);
             plugin.getLogger().info("Initialized SoTTeam for: " + definition.getName());

             // Add members
             Set<UUID> memberUUIDs = teamManager.getTeamMemberUUIDs(teamId);
             for (UUID memberId : memberUUIDs) {
                 Player p = Bukkit.getPlayer(memberId);
                 if (p != null && p.isOnline()) {
                     activeTeam.addMember(p);
                     playerStateManager.initializePlayer(p);
                 } else {
                     plugin.getLogger().warning("Player " + memberId + " assigned to team " + definition.getName() + " is offline or not found during setup.");
                 }
             }
         }
         plugin.getLogger().info("Game setup complete. " + activeTeamsInGame.size() + " active teams created. Ready to start.");
         // --- End setupGame Implementation ---
    }

    /**
     * Starts the actual game: generates layout, creates dungeon instances,
     * teleports players, and starts timers.
     */
    public void startGame() {
        // --- startGame Implementation (from GameManager_Refactored_Final_20250403, using DungeonBlueprint) ---
        if (currentState != GameState.SETUP) { /* ... log error ... */ return; }
        if (activeTeamsInGame.isEmpty()) { /* ... log error ... */ return; }
        if (dungeonGenerator == null || vaultManager == null) { /* ... log error ... */ return; }
        plugin.getLogger().info("Starting Sands of Time game generation...");

        // 1. Generate Dungeon Layout Blueprint
        this.dungeonLayoutBlueprint = dungeonGenerator.generateDungeonLayout(); // Assume returns DungeonBlueprint
        if (this.dungeonLayoutBlueprint == null /* || this.dungeonLayoutBlueprint.getRelativeSegments().isEmpty() */) { // Adapt check
            plugin.getLogger().severe("Failed to generate dungeon layout blueprint. Aborting game start.");
            currentState = GameState.ENDED;
            return;
        }

        World gameWorld = configHubLocation.getWorld();
        if (gameWorld == null) { /* ... log error ... */ currentState = GameState.ENDED; return; }

        // 2. Create and Initialize Instance for Each Team
        int teamIndex = 0;
        Location currentDungeonBase = gameWorld.getSpawnLocation().clone().add(DUNGEON_BASE_OFFSET);
        teamDungeonManagers.clear();

        for (SoTTeam team : activeTeamsInGame.values()) {
            UUID teamId = team.getTeamId();
            Location teamOrigin = currentDungeonBase.clone().add(TEAM_DUNGEON_SPACING.clone().multiply(teamIndex));
            plugin.getLogger().info("Creating dungeon instance for team " + team.getTeamName() + " at " + teamOrigin.toVector());

            // Create DungeonManager instance, passing the blueprint
            DungeonManager teamDungeon = new DungeonManager(plugin, this, dungeonGenerator, vaultManager, teamId, teamOrigin, dungeonLayoutBlueprint); // Pass blueprint

            if (!teamDungeon.initializeInstance()) {
                plugin.getLogger().severe("Failed to initialize dungeon instance for team " + team.getTeamName());
            }
            teamDungeonManagers.put(teamId, teamDungeon);

            // 3. Teleport Team Members
            Location teamHubLocation = getTeamHubLocation(teamId);
            if (teamHubLocation != null) {
                for (UUID memberId : team.getMemberUUIDs()) {
                    Player player = Bukkit.getPlayer(memberId);
                    if (player != null && player.isOnline()) {
                        final Location teleportTarget = teamHubLocation.clone().add(0.5, 0.1, 0.5);
                        teleportTarget.setYaw(player.getLocation().getYaw());
                        teleportTarget.setPitch(0);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isValid()) player.teleport(teleportTarget);
                        });
                    }
                }
            } else {
                plugin.getLogger().warning("Could not determine hub location for team " + team.getTeamName() + " for teleport.");
            }
            teamIndex++;
        }

        // 4. Start Timers
        for (SoTTeam team : activeTeamsInGame.values()) {
            team.startTimer();
        }

        // 5. Set State & Announce
        this.currentState = GameState.RUNNING;
        Bukkit.getServer().broadcast(Component.text("Sands of Time has begun!", NamedTextColor.GOLD, TextDecoration.BOLD));
        plugin.getLogger().info("Sands of Time game started with per-team dungeons.");
         // --- End startGame Implementation ---
    }

    /**
     * Forcefully ends the current Sands of Time game (e.g., by admin command).
     * Stops all timers and triggers the internal end game sequence.
     */
    public void endGame() {
        if (currentState != GameState.RUNNING && currentState != GameState.PAUSED) {
            plugin.getLogger().warning("Attempted to end game but it wasn't running or paused (Current: " + currentState + ")");
            return;
        }
        plugin.getLogger().info("Forcefully ending Sands of Time game...");

        // Stop all active team timers immediately
        for (SoTTeam team : activeTeamsInGame.values()) {
            if (team.isTimerRunning()) {
                team.stopTimer();
            }
        }

        // Call the internal cleanup and state change logic
        endGameInternal("Game forcefully ended.");
    }

    /**
     * Handles the consequences when a specific team's timer expires.
     * Traps remaining players and checks if the overall game should end.
     * (Removed boolean parameter)
     *
     * @param team The team whose timer expired.
     */
    public void handleTeamTimerEnd(SoTTeam team) {
        if (team == null) {
            plugin.getLogger().warning("handleTeamTimerEnd called with null team.");
            return;
        }
        // Ensure game is actually running to process timer expiry consequences
        if (currentState != GameState.RUNNING) {
            plugin.getLogger().warning("Timer expired for team " + team.getTeamName() + " but game state was " + currentState + ". Not trapping players.");
            // Still check if game should end, in case this was the last one in a paused state? Unlikely.
            checkGameEndCondition();
            return;
        }

        plugin.getLogger().warning("Timer has run out for team: " + team.getTeamName() + "!");

        Set<UUID> memberUUIDs = team.getMemberUUIDs();
        boolean teamWiped = true; // Assume wiped

        for (UUID memberUUID : memberUUIDs) {
            PlayerStatus currentStatus = playerStateManager.getStatus(memberUUID);

            // Skip players who already finished (escaped or previously trapped)
            if (currentStatus == PlayerStatus.ESCAPED_SAFE) {
                teamWiped = false;
                continue;
            }
            if (currentStatus == PlayerStatus.TRAPPED_TIMER_OUT) {
                continue;
            }

            // Process players who were actively inside (alive or dead)
            if (currentStatus == PlayerStatus.ALIVE_IN_DUNGEON || currentStatus == PlayerStatus.DEAD_AWAITING_REVIVE) {
                plugin.getLogger().info("Player " + memberUUID + " from team " + team.getTeamName() + " is trapped due to timer expiry!");

                playerStateManager.updateStatus(memberUUID, PlayerStatus.TRAPPED_TIMER_OUT);
                scoreManager.applyTimerEndPenalty(memberUUID); // Lose unbanked coins

                Player onlinePlayer = Bukkit.getPlayer(memberUUID);
                if (onlinePlayer != null && onlinePlayer.isOnline()) {
                    if (configTrappedLocation != null) {
                        final Component message = Component.text("Your team's timer ran out! You are trapped!", NamedTextColor.RED, TextDecoration.BOLD);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (onlinePlayer.isValid()) {
                                onlinePlayer.teleport(configTrappedLocation);
                                onlinePlayer.sendMessage(message);
                            }
                        });
                        // Broadcast trap message
                        NamedTextColor teamColor = teamManager.getPlayerTeamColor(onlinePlayer);
                        Component broadcastMessage = Component.text(onlinePlayer.getName(), teamColor)
                                .append(Component.text(" has been trapped!", NamedTextColor.RED));
                        Bukkit.getServer().broadcast(broadcastMessage);
                    } else {
                        plugin.getLogger().severe("Trap location (configTrappedLocation) is not set!");
                    }
                }
            } else {
                 // Only ESCAPED_SAFE prevents teamWiped status.
            }
        }

        // Announce team wipe if applicable
        if (teamWiped) {
            plugin.getLogger().warning("All active players from team " + team.getTeamName() + " were trapped!");
            final Component message = Component.text("Team " + team.getTeamName() + " got locked in!", NamedTextColor.RED);
            Bukkit.getServer().broadcast(message);
        } else {
             final Component message = Component.text("Team " + team.getTeamName() + "'s timer ran out!", NamedTextColor.YELLOW);
             Bukkit.getServer().broadcast(message);
        }

        // *** Check if the game should end now ***
        checkGameEndCondition();
    }

    /**
     * Checks if all active teams' timers have expired. If so, triggers the internal game end sequence.
     */
    private void checkGameEndCondition() {
        // Don't end if already ended or not running
        if (currentState != GameState.RUNNING) {
            return;
        }

        // Check if any team still has a running timer
        for (SoTTeam team : activeTeamsInGame.values()) {
            if (team.isTimerRunning()) {
                // At least one timer is still going, game continues
                plugin.getLogger().fine("Game end check: Team " + team.getTeamName() + " timer still running. Game continues.");
                return;
            }
        }

        // If the loop completes, no timers are running
        plugin.getLogger().info("Game end condition met: All team timers have expired.");
        endGameInternal("All timers expired."); // Trigger internal end sequence
    }

    /**
     * Internal method containing the logic to actually end the game,
     * change state, perform cleanup, and announce the end.
     * Called by checkGameEndCondition() or the public endGame().
     * @param reason A string indicating why the game ended (for logging).
     */
    private void endGameInternal(String reason) {
        // Prevent double execution
        if (currentState == GameState.ENDED) {
            return;
        }
        plugin.getLogger().info("Executing internal game end sequence. Reason: " + reason);
        this.currentState = GameState.ENDED;

        // Ensure all timers are stopped (might be redundant but safe)
        for (SoTTeam team : activeTeamsInGame.values()) {
            if (team.isTimerRunning()) {
                team.stopTimer();
            }
        }

        // TODO: Perform final score calculations and display results (leaderboard, chat messages)
        plugin.getLogger().info("Calculating final scores...");
        // Example: Map<UUID, Integer> finalScores = calculateFinalScores(); displayLeaderboard(finalScores);

        // TODO: Teleport all remaining players out (e.g., back to lobby/safeExitLocation)
        plugin.getLogger().info("Teleporting remaining players...");
        // Example: for (Player p : Bukkit.getOnlinePlayers()) { if (isPlayerInGame(p)) p.teleport(configSafeExitLocation); }

        // Clear game state maps and references
        activeTeamsInGame.clear();
        teamDungeonManagers.clear();
        playerStateManager.clearAllStates();
        scoreManager.clearAllUnbankedScores();
        // vaultManager might need a clear method too: vaultManager.clearTeamStates();
        dungeonLayoutBlueprint = null; // Clear blueprint reference

        Bukkit.getServer().broadcast(Component.text("Sands of Time has ended!", NamedTextColor.GOLD, TextDecoration.BOLD));
        plugin.getLogger().info("Sands of Time game ended and state cleared.");
    }


    // --- Player Action Handlers (Implementations from GameManager_Refactored_Final_20250403) ---
    public void handlePlayerDeath(Player player) {
         if (currentState != GameState.RUNNING) return;
         SoTTeam team = getActiveTeamForPlayer(player);
         if (team == null) return;
         plugin.getLogger().info("Handling death for player " + player.getName() + " on team " + team.getTeamName());
         scoreManager.applyDeathPenalty(player.getUniqueId());
         playerStateManager.updateStatus(player, PlayerStatus.DEAD_AWAITING_REVIVE);
         if (configDeathCageLocations != null && !configDeathCageLocations.isEmpty()) {
             Location cageLocation = configDeathCageLocations.get(new Random().nextInt(configDeathCageLocations.size()));
             final Component deathMessage = Component.text("You died! A teammate must use ", NamedTextColor.RED)
                     .append(Component.text(SandManager.REVIVE_COST, NamedTextColor.WHITE))
                     .append(Component.text(" sand to revive you.", NamedTextColor.RED));
             Bukkit.getScheduler().runTask(plugin, () -> {
                 if (player.isValid()) { player.teleport(cageLocation); player.sendMessage(deathMessage); }
             });
         } else {
             plugin.getLogger().severe("Death cage location(s) not set or empty!");
         }
    }

    public void handlePlayerRevive(Player deadPlayer, Player reviver) {
         if (currentState != GameState.RUNNING) return;
         if (playerStateManager.getStatus(deadPlayer) != PlayerStatus.DEAD_AWAITING_REVIVE) { /* ... send msg ... */ return; }
         SoTTeam deadPlayerTeam = getActiveTeamForPlayer(deadPlayer);
         SoTTeam reviverTeam = getActiveTeamForPlayer(reviver);
         if (deadPlayerTeam == null || reviverTeam == null || !deadPlayerTeam.getTeamId().equals(reviverTeam.getTeamId())) { /* ... send msg ... */ return; }
         if (sandManager.attemptRevive(reviver)) {
             plugin.getLogger().info(reviver.getName() + " revived " + deadPlayer.getName());
             playerStateManager.updateStatus(deadPlayer, PlayerStatus.ALIVE_IN_DUNGEON);
             Location teamHub = getTeamHubLocation(deadPlayerTeam.getTeamId());
             if (teamHub != null) {
                 final Location targetHub = teamHub.clone().add(0.5, 0.1, 0.5);
                 targetHub.setYaw(deadPlayer.getLocation().getYaw()); targetHub.setPitch(0);
                 Bukkit.getScheduler().runTask(plugin, () -> { if(deadPlayer.isValid()) deadPlayer.teleport(targetHub); });
                 deadPlayer.sendMessage(Component.text("You have been revived!", NamedTextColor.GREEN));
                 reviver.sendMessage(Component.text("You revived " + deadPlayer.getName() + "!", NamedTextColor.GREEN));
             } else { /* ... log warning ... */ }
         } else { /* ... send no sand msg ... */ }
    }

    public void handlePlayerLeave(Player player) {
         if (currentState != GameState.RUNNING) return;
         PlayerStatus status = playerStateManager.getStatus(player);
         if (status == PlayerStatus.ALIVE_IN_DUNGEON) {
             plugin.getLogger().info("Player " + player.getName() + " is leaving the dungeon.");
             scoreManager.playerEscaped(player.getUniqueId());
             playerStateManager.updateStatus(player, PlayerStatus.ESCAPED_SAFE);
             if (configSafeExitLocation != null) {
                 Bukkit.getScheduler().runTask(plugin, () -> {
                     if (player.isValid()) { player.teleport(configSafeExitLocation); player.sendMessage(Component.text("You escaped the dungeon safely!", NamedTextColor.GREEN)); }
                 });
             } else { /* ... log error ... */ }
         } else { /* ... send cannot leave msg ... */ }
    }

    // --- Utility Methods & Getters (Implementations from GameManager_Refactored_Final_20250403) ---
    public SoTTeam getActiveTeamForPlayer(Player player) { /* ... */ if (player == null) return null; UUID teamId = teamManager.getPlayerTeamId(player); return (teamId != null) ? activeTeamsInGame.get(teamId) : null; }
    public Map<UUID, SoTTeam> getActiveTeams() { return Collections.unmodifiableMap(activeTeamsInGame); }
    public Location getTeamHubLocation(UUID teamId) {
        DungeonManager teamDungeon = teamDungeonManagers.get(teamId);
        if (teamDungeon == null || dungeonLayoutBlueprint == null) {
            return null;
        }

        Location teamOrigin = teamDungeon.getDungeonOrigin();
        List<PlacedSegment> segmentsToCheck = dungeonLayoutBlueprint.getRelativeSegments(); // Correctly retrieve the segments

        for (PlacedSegment blueprintSegment : segmentsToCheck) {
            if (blueprintSegment.getSegmentTemplate().isHub()) {
                Location relativeHubOrigin = blueprintSegment.getWorldOrigin();
                return teamOrigin.clone().add(relativeHubOrigin.toVector());
            }
        }

        return null;
    }
    public UUID getTeamIdForLocation(Location location) { /* ... */ if (location == null || location.getWorld() == null) return null; for (DungeonManager dm : teamDungeonManagers.values()) { if (!dm.getWorld().equals(location.getWorld())) continue; if (dm.getSegmentAtLocation(location) != null) { return dm.getTeamId(); } } return null; }
    public DungeonManager getTeamDungeonManager(UUID teamId) { return teamDungeonManagers.get(teamId); }
    private Location determineVisualTimerBottomLocation(TeamDefinition teamDef, Location hubCenter) { /* ... placeholder ... */ if (hubCenter == null) return null; int offset = Math.abs(teamDef.getId().hashCode() % 10); return hubCenter.clone().add(offset * 5, 0, 0); }
    private Location determineVisualTimerTopLocation(TeamDefinition teamDef, Location hubCenter) { /* ... placeholder ... */ Location bottom = determineVisualTimerBottomLocation(teamDef, hubCenter); if (bottom == null) return null; return bottom.clone().add(0, 15, 0); }
    public GameState getCurrentState() { return currentState; }
    public Plugin getPlugin() { return plugin; }
    public TeamManager getTeamManager() { return teamManager; }
    public PlayerStateManager getPlayerStateManager() { return playerStateManager; }
    public SandManager getSandManager() { return sandManager; }
    public ScoreManager getScoreManager() { return scoreManager; }
    public BankingManager getBankingManager() { return bankingManager; }
    public VaultManager getVaultManager() { return vaultManager; }
    public DungeonGenerator getDungeonGenerator() { return dungeonGenerator; }
    public Location getSafeExitLocation() { return configSafeExitLocation; }
    public List<Location> getDeathCageLocations() { return Collections.unmodifiableList(configDeathCageLocations); }
    public Location getTrappedLocation() { return configTrappedLocation; }

}
