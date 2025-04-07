package com.clarkson.sot.main;

// Required Imports (ensure all needed imports are present)
import com.clarkson.sot.dungeon.Dungeon; // Needed for getting instance locations
import com.clarkson.sot.dungeon.DungeonGenerator;
import com.clarkson.sot.dungeon.DungeonManager;
import com.clarkson.sot.dungeon.VaultManager;
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
import java.util.logging.Level; // Added for logging

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
    private final Map<UUID, DungeonManager> teamDungeonManagers;
    private final Map<UUID, SoTTeam> activeTeamsInGame;
    private DungeonBlueprint dungeonLayoutBlueprint; // Use DungeonBlueprint

    // --- Refactored Locations ---
    private final Location lobbyLocation; // Main world anchor (e.g., for visual timers)
    private final Location configTrappedLocation; // Universal location for trapped players

    // --- Constants ---
    private static final Vector DUNGEON_BASE_OFFSET = new Vector(10000, 100, 10000);
    private static final Vector TEAM_DUNGEON_SPACING = new Vector(5000, 0, 0);

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

        // Initialize managers
        this.playerStateManager = new PlayerStateManager();
        this.teamManager = new TeamManager(this); // Pass self
        this.scoreManager = new ScoreManager(teamManager, this, plugin);
        this.bankingManager = new BankingManager(scoreManager);
        this.sandManager = new SandManager(this); // Pass self

        // Initialize VaultManager (check if plugin is SoT instance)
        if (plugin instanceof SoT) {
            this.vaultManager = new VaultManager((SoT) plugin, this);
        } else {
            plugin.getLogger().severe("Plugin instance is not of type SoT! VaultManager may not function correctly.");
            this.vaultManager = null; // Or throw an error
        }

        this.dungeonGenerator = new DungeonGenerator(plugin);

        // Initialize maps
        this.activeTeamsInGame = new HashMap<>();
        this.teamDungeonManagers = new HashMap<>();

        // Set initial state
        this.currentState = GameState.SETUP;

        // Load dungeon segment templates
        if (this.dungeonGenerator != null) {
            if (!this.dungeonGenerator.loadSegmentTemplates(plugin.getDataFolder())) {
                plugin.getLogger().severe("Failed to load dungeon segments into DungeonGenerator. Game cannot start.");
                // Consider setting state to ENDED or throwing an exception
                this.currentState = GameState.ENDED;
            }
        } else {
            plugin.getLogger().severe("DungeonGenerator failed to initialize.");
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
        if (currentState != GameState.SETUP) {
            plugin.getLogger().warning("Cannot setup game, current state is " + currentState);
            return;
        }
        if (participatingTeamIds == null || participatingTeamIds.isEmpty()) {
             plugin.getLogger().warning("Cannot setup game: No participating team IDs provided.");
             return;
        }
        plugin.getLogger().info("Setting up game with " + participatingTeamIds.size() + " teams.");

        // Clear state from previous games
        activeTeamsInGame.clear();
        teamDungeonManagers.clear();
        playerStateManager.clearAllStates();
        scoreManager.clearAllUnbankedScores();
        // vaultManager might need a clear method too: vaultManager.clearTeamStates();
        dungeonLayoutBlueprint = null;

        // Validate player assignments (ensure players are on a participating team)
        if (allPlayersInGame != null) {
            for (Player p : allPlayersInGame) {
                UUID teamId = teamManager.getPlayerTeamId(p);
                if (teamId == null || !participatingTeamIds.contains(teamId)) {
                    plugin.getLogger().warning("Player " + p.getName() + " is not assigned to a participating team!");
                    // Decide whether to proceed or abort setup
                }
            }
        }

        // Create SoTTeam instances for each participating team
        for (UUID teamId : participatingTeamIds) {
            TeamDefinition definition = teamManager.getTeamDefinition(teamId);
            if (definition == null) {
                plugin.getLogger().warning("Cannot setup team: Definition not found for ID " + teamId + ". Skipping team.");
                continue;
            }

            // Determine visual timer locations based on the main world lobbyLocation
            Location visualTimerBottom = determineVisualTimerBottomLocation(definition, this.lobbyLocation);
            Location visualTimerTop = determineVisualTimerTopLocation(definition, this.lobbyLocation);

            // Create the active team instance (SoTTeam handles its own timer)
            SoTTeam activeTeam = new SoTTeam(
                    definition, plugin, this, visualTimerBottom, visualTimerTop
            );
            activeTeamsInGame.put(teamId, activeTeam);
            plugin.getLogger().info("Initialized SoTTeam for: " + definition.getName());

            // Add assigned members to the SoTTeam instance and initialize their state
            Set<UUID> memberUUIDs = teamManager.getTeamMemberUUIDs(teamId);
            for (UUID memberId : memberUUIDs) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null && p.isOnline()) {
                    activeTeam.addMember(p);
                    playerStateManager.initializePlayer(p); // Set initial status (e.g., ALIVE_IN_DUNGEON)
                } else {
                    plugin.getLogger().warning("Player " + memberId + " assigned to team " + definition.getName() + " is offline or not found during setup.");
                }
            }
        }
        plugin.getLogger().info("Game setup complete. " + activeTeamsInGame.size() + " active teams created. Ready to start.");
    }

    /**
     * Starts the actual game: generates layout, creates dungeon instances,
     * teleports players to their instance-specific hubs, and starts timers.
     */
    public void startGame() {
        if (currentState != GameState.SETUP) {
            plugin.getLogger().severe("Cannot start game, current state is " + currentState);
            return;
        }
        if (activeTeamsInGame.isEmpty()) {
            plugin.getLogger().severe("Cannot start game: No active teams were set up.");
            return;
        }
        if (dungeonGenerator == null || vaultManager == null) {
             plugin.getLogger().severe("Cannot start game: Core components (DungeonGenerator/VaultManager) missing.");
             return;
        }
        plugin.getLogger().info("Starting Sands of Time game generation...");

        // 1. Generate Dungeon Layout Blueprint (Relative Structure)
        this.dungeonLayoutBlueprint = dungeonGenerator.generateDungeonLayout();
        if (this.dungeonLayoutBlueprint == null || this.dungeonLayoutBlueprint.getRelativeSegments().isEmpty()) {
            plugin.getLogger().severe("Failed to generate dungeon layout blueprint. Aborting game start.");
            currentState = GameState.ENDED; // Prevent further actions
            return;
        }

        // Determine the world for dungeon instances (using lobbyLocation's world as reference)
        World gameWorld = lobbyLocation.getWorld();
        if (gameWorld == null) {
            plugin.getLogger().severe("Cannot determine game world from lobby location. Aborting game start.");
            currentState = GameState.ENDED;
            return;
        }

        // 2. Create and Initialize Dungeon Instance for Each Team
        int teamIndex = 0;
        // Calculate base offset from world spawn or a fixed point
        Location currentDungeonBase = gameWorld.getSpawnLocation().clone().add(DUNGEON_BASE_OFFSET);
        teamDungeonManagers.clear(); // Ensure clean map

        for (SoTTeam team : activeTeamsInGame.values()) {
            UUID teamId = team.getTeamId();
            // Calculate the absolute origin for this team's dungeon instance
            Location teamOrigin = currentDungeonBase.clone().add(TEAM_DUNGEON_SPACING.clone().multiply(teamIndex));
            plugin.getLogger().info("Creating dungeon instance for team " + team.getTeamName() + " at " + teamOrigin.toVector());

            // Create the manager for this specific team's instance, passing the blueprint
            DungeonManager teamDungeon = new DungeonManager(plugin, this, dungeonGenerator, vaultManager, teamId, teamOrigin, dungeonLayoutBlueprint);

            // Initialize the instance (pastes segments, populates features, creates Dungeon data object)
            if (!teamDungeon.initializeInstance()) {
                plugin.getLogger().severe("Failed to initialize dungeon instance for team " + team.getTeamName() + ". This team may not be playable.");
                // Decide how to handle failure: skip team, abort game?
            }
            teamDungeonManagers.put(teamId, teamDungeon); // Store the manager

            // 3. Teleport Team Members to their specific Hub
            Location teamHubLocation = getTeamHubLocation(teamId); // Get instance-specific hub
            if (teamHubLocation != null) {
                for (UUID memberId : team.getMemberUUIDs()) {
                    Player player = Bukkit.getPlayer(memberId);
                    if (player != null && player.isOnline()) {
                        // Prepare safe teleport location within the hub
                        final Location teleportTarget = teamHubLocation.clone().add(0.5, 0.1, 0.5); // Center on block, slightly above floor
                        teleportTarget.setYaw(player.getLocation().getYaw()); // Keep player's facing direction
                        teleportTarget.setPitch(0); // Level pitch
                        // Schedule teleport task for safety
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isValid()) { // Check if player is still valid before teleporting
                                player.teleport(teleportTarget);
                            }
                        });
                    }
                }
            } else {
                plugin.getLogger().warning("Could not determine hub location for team " + team.getTeamName() + " for teleport. Players not teleported to hub.");
            }
            teamIndex++; // Increment for next team's spacing
        }

        // 4. Start All Team Timers
        for (SoTTeam team : activeTeamsInGame.values()) {
            team.startTimer();
        }

        // 5. Set Game State & Announce
        this.currentState = GameState.RUNNING;
        Bukkit.getServer().broadcast(Component.text("Sands of Time has begun!", NamedTextColor.GOLD, TextDecoration.BOLD));
        plugin.getLogger().info("Sands of Time game started with per-team dungeons.");
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
            checkGameEndCondition(); // Still check if game should end
            return;
        }

        plugin.getLogger().warning("Timer has run out for team: " + team.getTeamName() + "!");

        Set<UUID> memberUUIDs = team.getMemberUUIDs();
        boolean teamWiped = true; // Assume wiped unless someone escaped

        for (UUID memberUUID : memberUUIDs) {
            PlayerStatus currentStatus = playerStateManager.getStatus(memberUUID);

            // Skip players who already finished (escaped or previously trapped)
            if (currentStatus == PlayerStatus.ESCAPED_SAFE) {
                teamWiped = false; // Someone escaped, not a full wipe
                continue;
            }
            if (currentStatus == PlayerStatus.TRAPPED_TIMER_OUT) {
                continue; // Already trapped
            }

            // Process players who were actively inside (alive or dead awaiting revive)
            if (currentStatus == PlayerStatus.ALIVE_IN_DUNGEON || currentStatus == PlayerStatus.DEAD_AWAITING_REVIVE) {
                plugin.getLogger().info("Player " + memberUUID + " from team " + team.getTeamName() + " is trapped due to timer expiry!");

                // Update state and apply penalties
                playerStateManager.updateStatus(memberUUID, PlayerStatus.TRAPPED_TIMER_OUT);
                scoreManager.applyTimerEndPenalty(memberUUID); // Lose unbanked coins

                // Teleport online players to the universal trapped location
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
                        NamedTextColor teamColor = teamManager.getPlayerTeamColor(onlinePlayer); // Get team color
                        Component broadcastMessage = Component.text(onlinePlayer.getName(), teamColor)
                                .append(Component.text(" has been trapped!", NamedTextColor.RED));
                        Bukkit.getServer().broadcast(broadcastMessage);
                    } else {
                        plugin.getLogger().severe("Trap location (configTrappedLocation) is not set! Cannot teleport trapped player " + onlinePlayer.getName());
                    }
                }
            } else {
                // Player was in another state (e.g., NOT_IN_GAME), ignore for trapping but check for wipe status
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

        // TODO: Teleport all remaining players out (e.g., back to lobbyLocation)
        plugin.getLogger().info("Teleporting remaining players...");
        // Example: for (Player p : Bukkit.getOnlinePlayers()) { if (isPlayerInGame(p)) p.teleport(lobbyLocation); }

        // TODO: Consider dungeon cleanup (WorldEdit //set air or similar) - potentially intensive
        // for (DungeonManager dm : teamDungeonManagers.values()) { dm.cleanupInstance(); }

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


    // --- Player Action Handlers ---

    /**
     * Handles player death: applies penalties, updates status, teleports to instance-specific death cage.
     * @param player The player who died.
     */
    public void handlePlayerDeath(Player player) {
         if (currentState != GameState.RUNNING) return;
         SoTTeam team = getActiveTeamForPlayer(player);
         if (team == null) return; // Player not on an active team

         plugin.getLogger().info("Handling death for player " + player.getName() + " on team " + team.getTeamName());

         // Apply penalties and update status
         scoreManager.applyDeathPenalty(player.getUniqueId());
         playerStateManager.updateStatus(player, PlayerStatus.DEAD_AWAITING_REVIVE);

         // Get instance-specific death cage location
         Location cageLocation = getTeamDeathCageLocation(team.getTeamId());

         if (cageLocation != null) {
             final Component deathMessage = Component.text("You died! A teammate must use ", NamedTextColor.RED)
                     .append(Component.text(SandManager.REVIVE_COST, NamedTextColor.WHITE)) // Assuming REVIVE_COST is accessible
                     .append(Component.text(" sand to revive you.", NamedTextColor.RED));
             // Schedule teleport task
             Bukkit.getScheduler().runTask(plugin, () -> {
                 if (player.isValid()) {
                     player.teleport(cageLocation);
                     player.sendMessage(deathMessage);
                 }
             });
         } else {
             plugin.getLogger().severe("Could not determine Death Cage location for team " + team.getTeamName() + "! Cannot teleport player " + player.getName());
             // Consider teleporting to lobby or trapped location as a fallback?
             // player.teleport(configTrappedLocation);
         }
    }

    /**
     * Handles player revival: checks conditions, attempts revive via SandManager, teleports to instance-specific hub.
     * @param deadPlayer The player being revived.
     * @param reviver    The player performing the revive.
     */
    public void handlePlayerRevive(Player deadPlayer, Player reviver) {
         if (currentState != GameState.RUNNING) return;

         // Basic validation
         if (playerStateManager.getStatus(deadPlayer) != PlayerStatus.DEAD_AWAITING_REVIVE) {
             reviver.sendMessage(Component.text(deadPlayer.getName() + " does not need reviving.", NamedTextColor.YELLOW));
             return;
         }
         SoTTeam deadPlayerTeam = getActiveTeamForPlayer(deadPlayer);
         SoTTeam reviverTeam = getActiveTeamForPlayer(reviver);
         if (deadPlayerTeam == null || reviverTeam == null || !deadPlayerTeam.getTeamId().equals(reviverTeam.getTeamId())) {
             reviver.sendMessage(Component.text("You can only revive players on your own team!", NamedTextColor.RED));
             return;
         }

         // Attempt revive via SandManager (checks sand cost)
         if (sandManager.attemptRevive(reviver)) { // Assumes SandManager handles cost deduction
             plugin.getLogger().info(reviver.getName() + " revived " + deadPlayer.getName());

             // Update status and teleport to instance-specific hub
             playerStateManager.updateStatus(deadPlayer, PlayerStatus.ALIVE_IN_DUNGEON);
             Location teamHub = getTeamHubLocation(deadPlayerTeam.getTeamId()); // Get instance-specific hub

             if (teamHub != null) {
                 // Prepare safe teleport location
                 final Location targetHub = teamHub.clone().add(0.5, 0.1, 0.5);
                 targetHub.setYaw(deadPlayer.getLocation().getYaw()); // Keep direction
                 targetHub.setPitch(0);
                 // Schedule teleport task
                 Bukkit.getScheduler().runTask(plugin, () -> {
                     if(deadPlayer.isValid()) deadPlayer.teleport(targetHub);
                 });
                 // Send confirmation messages
                 deadPlayer.sendMessage(Component.text("You have been revived!", NamedTextColor.GREEN));
                 reviver.sendMessage(Component.text("You revived " + deadPlayer.getName() + "!", NamedTextColor.GREEN));
             } else {
                 plugin.getLogger().warning("Could not determine Hub location for team " + deadPlayerTeam.getTeamName() + " during revive. Player status updated but not teleported.");
                 // Consider fallback teleport?
             }
         } else {
             // SandManager.attemptRevive failed (likely insufficient sand)
             reviver.sendMessage(Component.text("Not enough sand to revive!", NamedTextColor.RED));
             // SandManager should ideally provide specific feedback if possible
         }
    }

    /**
     * Handles player leaving the dungeon safely: finalizes score, updates status, teleports to instance-specific safe exit.
     * @param player The player leaving.
     */
    public void handlePlayerLeave(Player player) {
         if (currentState != GameState.RUNNING) return;

         PlayerStatus status = playerStateManager.getStatus(player);
         SoTTeam team = getActiveTeamForPlayer(player);

         if (team == null) return; // Not in game

         // Only allow leaving if alive in dungeon
         if (status == PlayerStatus.ALIVE_IN_DUNGEON) {
             plugin.getLogger().info("Player " + player.getName() + " is leaving the dungeon safely.");

             // Finalize score and update status
             scoreManager.playerEscaped(player.getUniqueId()); // Adds unbanked score to team total
             playerStateManager.updateStatus(player, PlayerStatus.ESCAPED_SAFE);

             // Get instance-specific safe exit location
             Location safeExitLocation = getTeamSafeExitLocation(team.getTeamId());

             if (safeExitLocation != null) {
                 // Schedule teleport task
                 Bukkit.getScheduler().runTask(plugin, () -> {
                     if (player.isValid()) {
                         player.teleport(safeExitLocation);
                         player.sendMessage(Component.text("You escaped the dungeon safely!", NamedTextColor.GREEN));
                     }
                 });
             } else {
                 plugin.getLogger().severe("Could not determine Safe Exit location for team " + team.getTeamName() + "! Cannot teleport player " + player.getName());
                 // Fallback: teleport to lobby?
                 // Bukkit.getScheduler().runTask(plugin, () -> player.teleport(lobbyLocation));
             }
         } else {
             // Player is not in a state where they can leave (dead, already escaped, trapped)
             player.sendMessage(Component.text("You cannot leave the dungeon right now.", NamedTextColor.RED));
         }
    }

    // --- Utility Methods & Getters ---

    /** Gets the active SoTTeam instance for a given player, or null if not found/assigned. */
    public SoTTeam getActiveTeamForPlayer(Player player) {
        if (player == null) return null;
        UUID teamId = teamManager.getPlayerTeamId(player);
        return (teamId != null) ? activeTeamsInGame.get(teamId) : null;
    }

    /** Returns an unmodifiable view of the active teams map. */
    public Map<UUID, SoTTeam> getActiveTeams() {
        return Collections.unmodifiableMap(activeTeamsInGame);
    }

    /**
     * Gets the absolute world location of the Hub for a specific team's instance.
     * Calculates it based on the blueprint and the instance's origin.
     *
     * @param teamId The UUID of the team.
     * @return The absolute Location of the hub, or null if not found or instance doesn't exist.
     */
    public Location getTeamHubLocation(UUID teamId) {
        DungeonManager teamDungeonManager = teamDungeonManagers.get(teamId);
        // Check if the blueprint and the specific team's manager exist
        if (teamDungeonManager == null || dungeonLayoutBlueprint == null) {
            plugin.getLogger().log(Level.FINE, "Cannot get hub location: Team dungeon manager or blueprint is null for team " + teamId);
            return null;
        }

        // TODO: Optimization: Ideally, retrieve the pre-calculated absolute hub location
        // directly from the Dungeon object once DungeonManager.initializeInstance fully populates it.
        // Dungeon teamDungeonData = teamDungeonManager.getDungeonData();
        // if (teamDungeonData != null && teamDungeonData.getHubLocation() != null) {
        //     return teamDungeonData.getHubLocation();
        // }
        // plugin.getLogger().log(Level.FINE, "Falling back to calculating hub location from blueprint for team " + teamId);

        // Fallback: Calculate from blueprint (as per current structure)
        Location teamOrigin = teamDungeonManager.getDungeonOrigin();
        List<PlacedSegment> segmentsToCheck = dungeonLayoutBlueprint.getRelativeSegments();

        for (PlacedSegment blueprintSegment : segmentsToCheck) {
            // Check the template referenced by the PlacedSegment
            if (blueprintSegment.getSegmentTemplate().isHub()) {
                // The PlacedSegment's origin IS the relative origin in the blueprint
                Location relativeHubOrigin = blueprintSegment.getWorldOrigin(); // This is RELATIVE here
                // Calculate absolute location by adding relative origin to team's absolute origin
                return teamOrigin.clone().add(relativeHubOrigin.toVector());
            }
        }

        plugin.getLogger().warning("Hub segment not found in blueprint for team " + teamId);
        return null; // Hub segment not found in the blueprint
    }

    /**
     * Placeholder method to get the instance-specific Safe Exit location.
     * Needs implementation in DungeonManager/Dungeon.
     *
     * @param teamId The UUID of the team.
     * @return The absolute Location of the safe exit, or null if not found.
     */
    private Location getTeamSafeExitLocation(UUID teamId) {
        DungeonManager teamDungeonManager = teamDungeonManagers.get(teamId);
        if (teamDungeonManager == null) return null;
        Dungeon teamDungeonData = teamDungeonManager.getDungeonData();
        if (teamDungeonData == null) {
            plugin.getLogger().warning("Dungeon data not available for team " + teamId + " when getting safe exit location.");
            return null;
        }
        // TODO: Implement logic in Dungeon.java to store/retrieve the absolute safe exit location.
        // Example: return teamDungeonData.getSafeExitLocation();
        plugin.getLogger().warning("getTeamSafeExitLocation: Needs implementation in Dungeon.java");
        return null; // Placeholder
    }

    /**
     * Placeholder method to get the instance-specific Death Cage location.
     * Needs implementation in DungeonManager/Dungeon.
     * Allows for multiple cages, returning one randomly.
     *
     * @param teamId The UUID of the team.
     * @return An absolute Location for a death cage, or null if none found.
     */
    private Location getTeamDeathCageLocation(UUID teamId) {
        DungeonManager teamDungeonManager = teamDungeonManagers.get(teamId);
        if (teamDungeonManager == null) {
            plugin.getLogger().warning("No DungeonManager found for team ID: " + teamId);
            return null;
        }
    
        Dungeon teamDungeonData = teamDungeonManager.getDungeonData();
        if (teamDungeonData == null) {
            plugin.getLogger().warning("Dungeon data not available for team " + teamId + " when getting death cage location.");
            return null;
        }
    
        // Retrieve the list of death cage locations
        List<Location> cages = teamDungeonData.getDeathCageLocations();
        if (cages == null || cages.isEmpty()) {
            plugin.getLogger().warning("No death cage locations defined in Dungeon data for team " + teamId);
            return null;
        }
    
        // Return a random cage if multiple exist
        return cages.get(new Random().nextInt(cages.size()));
    }


    /** Finds the team ID associated with a given world location by checking dungeon bounds. */
    public UUID getTeamIdForLocation(Location location) {
        if (location == null || location.getWorld() == null) return null;
        // Iterate through the managers of active dungeon instances
        for (DungeonManager dm : teamDungeonManagers.values()) {
            // Quick world check
            if (!dm.getWorld().equals(location.getWorld())) continue;
            // Check if the location is within the bounds of any segment in this instance
            // This relies on DungeonManager having a way to check segment bounds efficiently
            if (dm.getSegmentAtLocation(location) != null) { // Assumes getSegmentAtLocation exists
                return dm.getTeamId();
            }
        }
        return null; // Location doesn't belong to any known active dungeon instance
    }

    /** Gets the DungeonManager instance for a specific team. */
    public DungeonManager getTeamDungeonManager(UUID teamId) {
        return teamDungeonManagers.get(teamId);
    }

    /** Determines the bottom location for a team's visual timer based on the lobby anchor. */
    private Location determineVisualTimerBottomLocation(TeamDefinition teamDef, Location anchorLocation) {
        // TODO: Implement robust logic for placing timers around the lobbyLocation.
        // This is a placeholder. Needs actual calculation based on team ID/index.
        if (anchorLocation == null) return null;
        int offset = Math.abs(teamDef.getId().hashCode() % 10); // Simple placeholder offset
        return anchorLocation.clone().add(offset * 5, 0, 0); // Example: Spread along X axis
    }

    /** Determines the top location for a team's visual timer. */
    private Location determineVisualTimerTopLocation(TeamDefinition teamDef, Location anchorLocation) {
        // TODO: Implement robust logic.
        Location bottom = determineVisualTimerBottomLocation(teamDef, anchorLocation);
        if (bottom == null) return null;
        // Example: 15 blocks high
        return bottom.clone().add(0, 15, 0); // Adjust height as needed
    }

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
    public Location getTrappedLocation() { return configTrappedLocation; }
    public Location getLobbyLocation() { return lobbyLocation; } // Getter for the new location

}
