package com.clarkson.sot.main;

// Required Imports
import com.clarkson.sot.dungeon.DungeonGenerator; // Renamed from DungeonManager
import com.clarkson.sot.dungeon.DungeonManager;   // New instance manager class
import com.clarkson.sot.dungeon.VaultManager;
import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.scoring.BankingManager;
import com.clarkson.sot.scoring.ScoreManager;
import com.clarkson.sot.utils.*; // Assuming PlayerStateManager, PlayerStatus, SandManager, SoTTeam, TeamDefinition, TeamManager are here

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
 * Uses DungeonGenerator to create a blueprint, then creates and manages per-team
 * DungeonManager instances for isolated, identical dungeons.
 * Uses Adventure API for messages.
 */
public class GameManager {

    // --- Core Plugin & State ---
    private final Plugin plugin;
    private GameState currentState;

    // --- Managers ---
    private final TeamManager teamManager;
    private final PlayerStateManager playerStateManager;
    private final SandManager sandManager;
    private final ScoreManager scoreManager;
    private final BankingManager bankingManager;
    private final VaultManager vaultManager;
    private final DungeonGenerator dungeonGenerator; // Renamed class for blueprint generation
    // Map to hold the manager for each team's specific dungeon instance
    private final Map<UUID, DungeonManager> teamDungeonManagers;

    // --- Game Instance Data ---
    private final Map<UUID, SoTTeam> activeTeamsInGame;
    private List<PlacedSegment> dungeonLayoutBlueprint; // Stores the relative layout blueprint

    // --- Configurable Locations ---
    private final Location configHubLocation; // Base location reference (e.g., for world)
    private final Location configSafeExitLocation;
    private final List<Location> configDeathCageLocations; // List of potential cage spots
    private final Location configTrappedLocation; // Where players go if trapped by timer

    // --- Constants for Dungeon Placement ---
    // Base offset from world spawn (or other anchor) for the first dungeon copy.
    // Ensure this is far away and potentially configurable. Y-level might need adjustment.
    private static final Vector DUNGEON_BASE_OFFSET = new Vector(10000, 100, 10000);
    // Offset between each team's dungeon instance. Should be large enough to prevent overlap.
    private static final Vector TEAM_DUNGEON_SPACING = new Vector(5000, 0, 0); // Example: 5000 blocks apart on X-axis

    /**
     * Constructor for GameManager. Initializes all necessary sub-managers.
     *
     * @param plugin The main SoT plugin instance.
     * @param hubLocation Location reference (primarily for world context and maybe visual timer placement).
     * @param safeExit Location players teleport to upon safe exit.
     * @param deathCageLocations List of possible locations players teleport to upon death.
     * @param trappedLocation Location players teleport to when trapped by the timer.
     */
    public GameManager(Plugin plugin, Location hubLocation, Location safeExit, List<Location> deathCageLocations, Location trappedLocation) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        // Store configured locations
        this.configHubLocation = Objects.requireNonNull(hubLocation, "Hub location cannot be null");
        this.configSafeExitLocation = Objects.requireNonNull(safeExit, "Safe exit location cannot be null");
        this.configDeathCageLocations = Objects.requireNonNull(deathCageLocations, "Death cage locations cannot be null");
        this.configTrappedLocation = Objects.requireNonNull(trappedLocation, "Trapped location cannot be null");
        if (deathCageLocations.isEmpty()) {
            plugin.getLogger().warning("Death cage locations list is empty!");
            // Consider adding a default fallback location or throwing an error
        }

        // Initialize managers, injecting dependencies
        this.playerStateManager = new PlayerStateManager();
        this.teamManager = new TeamManager(this); // TeamManager needs GameManager
        this.scoreManager = new ScoreManager(teamManager, this, plugin); // ScoreManager needs TeamManager, GameManager, Plugin
        this.bankingManager = new BankingManager(scoreManager); // BankingManager needs ScoreManager
        this.sandManager = new SandManager(this); // SandManager needs GameManager

        // Initialize VaultManager (handle potential plugin type mismatch)
        if (plugin instanceof SoT) {
            this.vaultManager = new VaultManager((SoT) plugin, this); // VaultManager needs Plugin, GameManager
        } else {
            plugin.getLogger().severe("Plugin instance is not of type SoT! VaultManager may not function correctly.");
            this.vaultManager = null; // Or throw exception / use dummy
        }

        // Initialize the renamed DungeonGenerator
        this.dungeonGenerator = new DungeonGenerator(plugin); // Pass seed if needed

        // Initialize maps for game instance data
        this.activeTeamsInGame = new HashMap<>();
        this.teamDungeonManagers = new HashMap<>(); // Map for the new instance managers

        this.currentState = GameState.SETUP; // Initial game state

        // Load segment templates into the DungeonGenerator upon initialization
        if (this.dungeonGenerator != null) {
            if (!this.dungeonGenerator.loadSegmentTemplates(plugin.getDataFolder())) {
                plugin.getLogger().severe("Failed to load dungeon segments into DungeonGenerator during GameManager init. Generation will likely fail.");
                // Consider preventing game start if loading fails
            }
        } else {
            plugin.getLogger().severe("DungeonGenerator failed to initialize.");
            // Prevent game start
            this.currentState = GameState.ENDED; // Set to ended if critical component missing
        }
    }

    /**
     * Sets up the participating teams for the current game instance.
     * Creates SoTTeam objects with necessary context (like visual timers).
     *
     * @param participatingTeamIds List of Team IDs participating in this game.
     * @param allPlayersInGame List of all players intended to be in the game (used for validation).
     */
    public void setupGame(List<UUID> participatingTeamIds, List<Player> allPlayersInGame) {
        if (currentState != GameState.SETUP) {
            plugin.getLogger().warning("Cannot setup game, current state is " + currentState);
            return;
        }
        if (dungeonGenerator == null) {
             plugin.getLogger().severe("Cannot setup game: DungeonGenerator is not initialized.");
             return;
        }
        plugin.getLogger().info("Setting up game with " + participatingTeamIds.size() + " teams.");

        // Clear state from any previous game
        activeTeamsInGame.clear();
        teamDungeonManagers.clear();
        playerStateManager.clearAllStates();
        scoreManager.clearAllUnbankedScores();
        // vaultManager might need a clear method too: vaultManager.clearTeamStates();
        dungeonLayoutBlueprint = null;

        // 1. Validate player assignments (optional but recommended)
        for (Player p : allPlayersInGame) {
            UUID teamId = teamManager.getPlayerTeamId(p);
            if (teamId == null || !participatingTeamIds.contains(teamId)) {
                plugin.getLogger().warning("Player " + p.getName() + " is not assigned to a participating team! Ensure assignments are correct before starting.");
                // Consider preventing setup if assignments are wrong
            }
        }

        // 2. Create game-specific SoTTeam instances
        for (UUID teamId : participatingTeamIds) {
            TeamDefinition definition = teamManager.getTeamDefinition(teamId);
            if (definition == null) {
                plugin.getLogger().warning("Cannot setup team: Definition not found for ID " + teamId);
                continue;
            }

            // Determine visual timer locations (relative to the main hub/lobby, not the dungeon instances)
            Location visualTimerBottom = determineVisualTimerBottomLocation(definition, configHubLocation);
            Location visualTimerTop = determineVisualTimerTopLocation(definition, configHubLocation);

            if (visualTimerBottom == null || visualTimerTop == null) {
                plugin.getLogger().severe("Could not determine visual timer locations for team " + definition.getName() + "! Aborting team setup for this team.");
                continue; // Skip this team if visual timer setup fails
            }

            // Create the active team object
            SoTTeam activeTeam = new SoTTeam(
                    definition.getId(), definition.getName(), definition.getColor(),
                    plugin, this, visualTimerBottom, visualTimerTop
            );
            activeTeamsInGame.put(teamId, activeTeam);
            plugin.getLogger().info("Initialized SoTTeam for: " + definition.getName());

            // Add members and initialize their state
            Set<UUID> memberUUIDs = teamManager.getTeamMemberUUIDs(teamId);
            for (UUID memberId : memberUUIDs) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null && p.isOnline()) {
                    activeTeam.addMember(p); // Add member to SoTTeam object
                    playerStateManager.initializePlayer(p); // Set initial state (e.g., ALIVE_IN_DUNGEON)
                } else {
                    plugin.getLogger().warning("Player " + memberId + " assigned to team " + definition.getName() + " is offline or not found.");
                    // Handle offline players if necessary (e.g., prevent game start, allow joining later)
                }
            }
        }
        plugin.getLogger().info("Game setup complete. Ready to start.");
    }

    /**
     * Starts the actual game: generates layout once, creates a DungeonManager instance
     * for each team to build its copy, places team-specific features, teleports players,
     * and starts timers.
     */
    public void startGame() {
        // Validate state and necessary components
        if (currentState != GameState.SETUP) {
            plugin.getLogger().warning("Cannot start game: Not in SETUP state (Current: " + currentState + ")");
            return;
        }
        if (activeTeamsInGame.isEmpty()) {
             plugin.getLogger().warning("Cannot start game: No active teams setup.");
             return;
        }
        if (dungeonGenerator == null || vaultManager == null) {
            plugin.getLogger().severe("Cannot start game: DungeonGenerator or VaultManager not initialized!");
            return;
        }
        plugin.getLogger().info("Starting Sands of Time game generation...");

        // 1. Generate Dungeon Layout Blueprint (Relative)
        this.dungeonLayoutBlueprint = dungeonGenerator.generateDungeonLayout();
        if (this.dungeonLayoutBlueprint == null || this.dungeonLayoutBlueprint.isEmpty()) {
            plugin.getLogger().severe("Failed to generate dungeon layout blueprint. Aborting game start.");
            currentState = GameState.ENDED; // Mark as ended due to critical error
            // TODO: Notify admins/players
            return;
        }

        // Determine the world to place dungeons in (use the world from the configured hub location)
        World gameWorld = configHubLocation.getWorld();
        if (gameWorld == null) {
            plugin.getLogger().severe("Cannot place dungeons: Game world is invalid (from configHubLocation).");
            currentState = GameState.ENDED;
            return;
        }

        // 2. Create and Initialize Instance for Each Team
        int teamIndex = 0;
        // Calculate the starting point for the first dungeon instance
        Location currentDungeonBase = gameWorld.getSpawnLocation().clone().add(DUNGEON_BASE_OFFSET); // Use world spawn + offset

        teamDungeonManagers.clear(); // Ensure map is clear before populating

        for (SoTTeam team : activeTeamsInGame.values()) {
            UUID teamId = team.getTeamId();
            // Calculate the absolute world origin for this team's dungeon copy
            Location teamOrigin = currentDungeonBase.clone().add(TEAM_DUNGEON_SPACING.clone().multiply(teamIndex));

            plugin.getLogger().info("Creating dungeon instance for team " + team.getTeamName() + " at " + teamOrigin.toVector());

            // Create a new DungeonManager instance for this team
            // Pass all required dependencies
            DungeonManager teamDungeon = new DungeonManager(plugin, this, dungeonGenerator, vaultManager, teamId, teamOrigin, dungeonLayoutBlueprint);

            // Initialize the instance (pastes segments, places vaults/keys, populates features)
            if (!teamDungeon.initializeInstance()) {
                plugin.getLogger().severe("Failed to initialize dungeon instance for team " + team.getTeamName() + ". This team may not have a playable dungeon.");
                // Consider how to handle this failure case (e.g., remove team, notify admins)
            }
            teamDungeonManagers.put(teamId, teamDungeon); // Store the instance manager regardless of partial failure for now

            // 3. Teleport Team Members to their instance's hub
            Location teamHubLocation = getTeamHubLocation(teamId); // Get hub relative to teamOrigin
            if (teamHubLocation != null) {
                for (UUID memberId : teamManager.getTeamMemberUUIDs(teamId)) {
                    Player player = Bukkit.getPlayer(memberId);
                    if (player != null && player.isOnline()) {
                        // Ensure teleport happens on the main server thread
                        final Location teleportTarget = teamHubLocation.clone().add(0.5, 0, 0.5); // Center player in block
                        teleportTarget.setYaw(player.getLocation().getYaw()); // Keep player's facing direction
                        teleportTarget.setPitch(player.getLocation().getPitch());

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isValid()) { // Double-check player validity before teleport
                                player.teleport(teleportTarget);
                            }
                        });
                        // Initial status should be set during setupGame, confirm here if needed
                        // playerStateManager.updateStatus(player, PlayerStatus.ALIVE_IN_DUNGEON);
                    }
                }
            } else {
                plugin.getLogger().warning("Could not determine hub location for team " + team.getTeamName() + " for teleport. Players not teleported.");
            }

            teamIndex++; // Increment index for the next team's position offset
        }

        // 4. Start Timers (after all generation/placement is done)
        for (SoTTeam team : activeTeamsInGame.values()) {
            team.startTimer();
        }

        // 5. Set State & Announce
        this.currentState = GameState.RUNNING;
        Bukkit.getServer().broadcast(Component.text("Sands of Time has begun!", NamedTextColor.GOLD, TextDecoration.BOLD));
        plugin.getLogger().info("Sands of Time game started with per-team dungeons.");
    }

    /**
     * Ends the current Sands of Time game.
     * Stops timers, handles final player states, and clears instance data.
     */
    public void endGame() {
        if (currentState != GameState.RUNNING && currentState != GameState.PAUSED) { // Allow ending if paused
            plugin.getLogger().warning("Attempted to end game but it wasn't running or paused (Current: " + currentState + ")");
            return;
        }
        plugin.getLogger().info("Ending Sands of Time game...");
        GameState previousState = this.currentState;
        this.currentState = GameState.ENDED;

        // Stop all team timers FIRST
        for (SoTTeam team : activeTeamsInGame.values()) {
            team.stopTimer();
        }

        // Handle consequences for players still inside if game was running
        if (previousState == GameState.RUNNING) {
            for (SoTTeam team : activeTeamsInGame.values()) {
                handleTeamTimerEnd(team, false); // Pass false to indicate game end, not natural timer expiry
            }
        }

        // TODO: Perform final score calculations and display results

        // TODO: Teleport all remaining players out (e.g., back to lobby/safeExitLocation)

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

    /**
     * Handles consequences when a team's timer ends naturally OR the game ends forcefully.
     * Updates player status, applies penalties, and teleports trapped players.
     *
     * @param team The team to process.
     * @param naturalExpiry True if the timer ran out naturally, false if the game was ended manually/forcefully.
     */
    public void handleTeamTimerEnd(SoTTeam team, boolean naturalExpiry) {
        if (team == null) return;
        // Only process if game is ending or was running when timer expired naturally
        if (currentState != GameState.ENDED && !(currentState == GameState.RUNNING && naturalExpiry)) {
            // Avoid processing if game is already ended and this is called again,
            // or if game is paused/setup when a timer somehow tries to expire.
            plugin.getLogger().fine("Skipping handleTeamTimerEnd for team " + team.getTeamName() + " in state " + currentState);
            return;
        }

        String reason = naturalExpiry ? "timer expiry" : "game end";
        if (naturalExpiry) {
            plugin.getLogger().warning("Timer has run out for team: " + team.getTeamName() + "!");
        } else {
             plugin.getLogger().info("Processing players for team " + team.getTeamName() + " due to game end.");
        }

        Set<UUID> memberUUIDs = team.getMemberUUIDs();
        boolean teamWiped = true; // Assume wiped until proven otherwise

        for (UUID memberUUID : memberUUIDs) {
            PlayerStatus currentStatus = playerStateManager.getStatus(memberUUID);

            // Skip players who already escaped or were already trapped
            if (currentStatus == PlayerStatus.ESCAPED_SAFE) {
                teamWiped = false; // Someone escaped
                continue;
            }
            if (currentStatus == PlayerStatus.TRAPPED_TIMER_OUT) {
                // Already handled, potentially by a previous call if timer ended exactly at game end
                continue;
            }

            // Process players who were actively inside (alive or dead)
            if (currentStatus == PlayerStatus.ALIVE_IN_DUNGEON || currentStatus == PlayerStatus.DEAD_AWAITING_REVIVE) {
                plugin.getLogger().info("Player " + memberUUID + " from team " + team.getTeamName() + " is trapped due to " + reason + "!");

                // Update state and apply score penalty
                playerStateManager.updateStatus(memberUUID, PlayerStatus.TRAPPED_TIMER_OUT);
                scoreManager.applyTimerEndPenalty(memberUUID); // Lose unbanked coins

                // Teleport the player if they are online
                Player onlinePlayer = Bukkit.getPlayer(memberUUID);
                if (onlinePlayer != null && onlinePlayer.isOnline()) {
                    if (configTrappedLocation != null) {
                        final Component message = naturalExpiry ?
                                Component.text("Your team's timer ran out! You are trapped!", NamedTextColor.RED, TextDecoration.BOLD) :
                                Component.text("The game ended while you were inside! You are trapped!", NamedTextColor.RED);

                        // Ensure teleport and message happen on main thread
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (onlinePlayer.isValid()) { // Check validity before teleport
                                onlinePlayer.teleport(configTrappedLocation);
                                onlinePlayer.sendMessage(message);
                            }
                        });

                        // Broadcast trap message
                        NamedTextColor teamColor = teamManager.getPlayerTeamColor(onlinePlayer); // Use helper
                        Component broadcastMessage = Component.text(onlinePlayer.getName(), teamColor)
                                .append(Component.text(" has been trapped!", NamedTextColor.RED));
                        Bukkit.getServer().broadcast(broadcastMessage);

                    } else {
                        plugin.getLogger().severe("Trap location (configTrappedLocation) is not set in GameManager!");
                    }
                }
            } else {
                 // If status is something else (like NOT_IN_GAME), they didn't escape
                 // but also weren't actively inside to be trapped by the timer ending.
                 // We might still consider the team not wiped if someone had an unusual status.
                 // For simplicity, only ESCAPED_SAFE prevents teamWiped status here.
            }
        }

        // Log/Broadcast if the whole team got trapped
        if (naturalExpiry && teamWiped) {
             plugin.getLogger().warning("All active players from team " + team.getTeamName() + " were trapped due to timer expiry!");
             final Component message = Component.text("Team " + team.getTeamName() + " got locked in!", NamedTextColor.RED);
             Bukkit.getServer().broadcast(message);
        } else if (naturalExpiry) {
             final Component message = Component.text("Team " + team.getTeamName() + "'s timer ran out!", NamedTextColor.YELLOW);
             Bukkit.getServer().broadcast(message);
        }
    }

    // Overload for calls from SoTTeam internal timer (assumes natural expiry)
    public void handleTeamTimerEnd(SoTTeam team) {
        handleTeamTimerEnd(team, true);
    }


    // --- Player Action Handlers (Called by GameListener or other managers) ---

    /** Handles player death during the game. */
    public void handlePlayerDeath(Player player) {
        if (currentState != GameState.RUNNING) return; // Only handle deaths while running
        SoTTeam team = getActiveTeamForPlayer(player);
        if (team == null) {
            plugin.getLogger().fine("Player " + player.getName() + " died but was not on an active SoT team.");
            return; // Player not in the game or team not active
        }

        plugin.getLogger().info("Handling death for player " + player.getName() + " on team " + team.getTeamName());
        scoreManager.applyDeathPenalty(player.getUniqueId()); // Apply score penalty
        playerStateManager.updateStatus(player, PlayerStatus.DEAD_AWAITING_REVIVE); // Update status

        // Teleport to a death cage location
        if (configDeathCageLocations != null && !configDeathCageLocations.isEmpty()) {
            // Choose a cage location (e.g., random, or based on team?)
            // For simplicity, using the first one for now.
            Location cageLocation = configDeathCageLocations.get(0); // Or use random index

            final Component deathMessage = Component.text("You died! A teammate must use ", NamedTextColor.RED)
                    .append(Component.text(SandManager.REVIVE_COST, NamedTextColor.WHITE)) // Use constant from SandManager
                    .append(Component.text(" sand to revive you.", NamedTextColor.RED));

            // Ensure teleport happens on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isValid()) { // Check player validity
                    player.teleport(cageLocation);
                    player.sendMessage(deathMessage);
                }
            });
        } else {
            plugin.getLogger().severe("Death cage location(s) not set or empty!");
        }
        // TODO: Notify team members about the death
    }

    /** Handles player revival attempt. */
    public void handlePlayerRevive(Player deadPlayer, Player reviver) {
        if (currentState != GameState.RUNNING) return; // Only handle revives while running

        // Validate statuses and team membership
        if (playerStateManager.getStatus(deadPlayer) != PlayerStatus.DEAD_AWAITING_REVIVE) {
            reviver.sendMessage(Component.text(deadPlayer.getName() + " doesn't need reviving right now.", NamedTextColor.YELLOW));
            return;
        }
        SoTTeam deadPlayerTeam = getActiveTeamForPlayer(deadPlayer);
        SoTTeam reviverTeam = getActiveTeamForPlayer(reviver);
        if (deadPlayerTeam == null || reviverTeam == null || !deadPlayerTeam.getTeamId().equals(reviverTeam.getTeamId())) {
            reviver.sendMessage(Component.text("You can only revive players on your own team!", NamedTextColor.RED));
            return;
        }

        // Attempt to use sand via SandManager
        if (sandManager.attemptRevive(reviver)) {
            plugin.getLogger().info(reviver.getName() + " revived " + deadPlayer.getName());
            playerStateManager.updateStatus(deadPlayer, PlayerStatus.ALIVE_IN_DUNGEON); // Update status

            // Teleport revived player back to their team's hub
            Location teamHub = getTeamHubLocation(deadPlayerTeam.getTeamId());
            if (teamHub != null) {
                final Location targetHub = teamHub.clone().add(0.5, 0, 0.5); // Center
                targetHub.setYaw(deadPlayer.getLocation().getYaw()); // Keep direction
                targetHub.setPitch(deadPlayer.getLocation().getPitch());

                Bukkit.getScheduler().runTask(plugin, () -> {
                     if(deadPlayer.isValid()) {
                         deadPlayer.teleport(targetHub);
                     }
                });
                deadPlayer.sendMessage(Component.text("You have been revived!", NamedTextColor.GREEN));
                reviver.sendMessage(Component.text("You revived " + deadPlayer.getName() + "!", NamedTextColor.GREEN));
            } else {
                 plugin.getLogger().warning("Could not find hub location for team " + deadPlayerTeam.getTeamName() + " to teleport revived player.");
                 // Consider teleporting to a default location or logging error more severely
            }
            // TODO: Restore inventory if needed (depends on death mechanics)
            // restorePlayerInventory(deadPlayer);
        } else {
            // Feedback if not enough sand
            reviver.sendMessage(Component.text("Your team doesn't have enough sand (" + SandManager.REVIVE_COST + ") to revive!", NamedTextColor.RED));
        }
    }

    /** Handles player attempting to leave the dungeon safely. */
    public void handlePlayerLeave(Player player) {
        if (currentState != GameState.RUNNING) return; // Only handle leaves while running

        PlayerStatus status = playerStateManager.getStatus(player);
        if (status == PlayerStatus.ALIVE_IN_DUNGEON) {
            plugin.getLogger().info("Player " + player.getName() + " is leaving the dungeon.");

            // Process score and update status
            scoreManager.playerEscaped(player.getUniqueId()); // Banks unbanked score
            playerStateManager.updateStatus(player, PlayerStatus.ESCAPED_SAFE); // Update status

            // Teleport player to the safe exit
            if (configSafeExitLocation != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isValid()) {
                        player.teleport(configSafeExitLocation);
                        player.sendMessage(Component.text("You escaped the dungeon safely!", NamedTextColor.GREEN));
                    }
                });
            } else {
                plugin.getLogger().severe("Safe exit location (configSafeExitLocation) is not set!");
            }
            // TODO: Prevent re-entry (maybe handled by PlayerStatus check elsewhere)
            // TODO: Check if this was the last player out for the team (optional)

        } else {
            // Player cannot leave if dead, already escaped, trapped, etc.
            player.sendMessage(Component.text("You cannot leave right now (Status: " + status + ").", NamedTextColor.RED));
        }
    }

    // --- Utility Methods ---

    /**
     * Gets the active SoTTeam instance for a given player.
     * Uses TeamManager to find the team assignment.
     * @param player The player.
     * @return The SoTTeam instance, or null if player is null, not assigned, or team not active.
     */
    public SoTTeam getActiveTeamForPlayer(Player player) {
        if (player == null) return null;
        // Use TeamManager to get the assigned team ID
        UUID teamId = teamManager.getPlayerTeamId(player);
        // Get the active SoTTeam object from the map using the ID
        return (teamId != null) ? activeTeamsInGame.get(teamId) : null;
    }

    /**
     * Gets an unmodifiable view of the map of active SoTTeam instances.
     * @return An unmodifiable map (Team UUID -> SoTTeam).
     */
    public Map<UUID, SoTTeam> getActiveTeams() {
        return Collections.unmodifiableMap(activeTeamsInGame);
    }

    /**
     * Gets the absolute world location of the hub for a specific team's dungeon instance.
     * @param teamId The ID of the team.
     * @return The absolute hub Location for the team's instance, or null if not found.
     */
    public Location getTeamHubLocation(UUID teamId) {
        DungeonManager teamDungeon = teamDungeonManagers.get(teamId);
        if (teamDungeon == null || dungeonLayoutBlueprint == null) {
            plugin.getLogger().fine("Cannot get hub location: No dungeon manager or blueprint for team " + teamId);
            return null;
        }
        Location teamOrigin = teamDungeon.getDungeonOrigin();

        // Find the hub segment in the blueprint (assumes only one segment has isHub() == true)
        for (PlacedSegment blueprintSegment : dungeonLayoutBlueprint) {
            if (blueprintSegment.getSegmentTemplate().isHub()) {
                Location relativeHubOrigin = blueprintSegment.getWorldOrigin(); // Relative origin in blueprint
                // Calculate absolute location: Team's Dungeon Origin + Hub's Relative Origin
                return teamOrigin.clone().add(relativeHubOrigin.toVector());
            }
        }
        plugin.getLogger().warning("Hub segment not found in blueprint when searching for team " + teamId);
        return null; // Hub segment not found in blueprint
    }

    /**
     * Determines which team's dungeon instance a given location falls within.
     * Iterates through the managed DungeonManager instances and checks their segments.
     * Requires DungeonManager.getSegmentAtLocation() to be implemented.
     * @param location The world location to check.
     * @return The UUID of the team whose dungeon contains the location, or null if none.
     */
    public UUID getTeamIdForLocation(Location location) {
        if (location == null || location.getWorld() == null) return null;

        for (DungeonManager dm : teamDungeonManagers.values()) {
            // Quick world check
            if (!dm.getWorld().equals(location.getWorld())) {
                continue;
            }
            // Check if location is within any segment managed by this instance
            // This relies on DungeonManager having an efficient way to check containment
            // (e.g., using its list of PlacedSegments and their Area bounds)
            if (dm.getSegmentAtLocation(location) != null) { // Assumes this method exists and works
                return dm.getTeamId(); // Found the team instance
            }
        }
        return null; // Location not within any managed dungeon instance
    }

     /**
      * Gets the DungeonManager instance for a specific team.
      * @param teamId The UUID of the team.
      * @return The DungeonManager instance, or null if the team is not active or has no instance.
      */
     public DungeonManager getTeamDungeonManager(UUID teamId) {
         return teamDungeonManagers.get(teamId);
     }


    // --- Placeholder Location Methods (For Visual Timers in Lobby/Hub) ---
    // These determine locations relative to the MAIN hub (configHubLocation),
    // not the per-team dungeon instances.
    private Location determineVisualTimerBottomLocation(TeamDefinition teamDef, Location hubCenter) {
        // TODO: Implement robust logic to find/calculate visual timer locations
        // This likely involves checking blocks around hubCenter or using predefined offsets.
        plugin.getLogger().log(Level.FINE, "Placeholder: Determine visual timer bottom location for " + teamDef.getName());
        if (hubCenter == null) return null; // Cannot determine without hub center
        // Example placeholder offset logic (Needs proper implementation)
        int offset = Math.abs(teamDef.getId().hashCode() % 10); // Simple offset based on hash
        return hubCenter.clone().add(offset * 5, 0, 0);
    }

    private Location determineVisualTimerTopLocation(TeamDefinition teamDef, Location hubCenter) {
        Location bottom = determineVisualTimerBottomLocation(teamDef, hubCenter);
        if (bottom == null) return null;
        // TODO: Determine actual height needed based on max timer / seconds per block
        int height = 15; // Example height
        return bottom.clone().add(0, height, 0);
    }

    // --- Getters for Managers and State ---
    public GameState getCurrentState() { return currentState; }
    public Plugin getPlugin() { return plugin; }
    public TeamManager getTeamManager() { return teamManager; }
    public PlayerStateManager getPlayerStateManager() { return playerStateManager; }
    public SandManager getSandManager() { return sandManager; }
    public ScoreManager getScoreManager() { return scoreManager; }
    public BankingManager getBankingManager() { return bankingManager; }
    public VaultManager getVaultManager() { return vaultManager; }
    public DungeonGenerator getDungeonGenerator() { return dungeonGenerator; } // Expose generator
    public Location getSafeExitLocation() { return configSafeExitLocation; }
    public List<Location> getDeathCageLocations() { return configDeathCageLocations; } // Return list
    public Location getTrappedLocation() { return configTrappedLocation; }

    // --- Methods required by other classes (ensure implementations exist or remove if unused) ---

    // This seems like it should be handled within SoTTeam or SandManager based on team state
    // public void addSecondsToTimer(UUID teamId, int timeBonusSeconds) {
    //     SoTTeam team = activeTeamsInGame.get(teamId);
    //     if (team != null) {
    //         team.addSeconds(timeBonusSeconds);
    //     } else {
    //         plugin.getLogger().warning("Attempted to add seconds to non-active team: " + teamId);
    //     }
    // }

    // This key is likely specific to how ScoreManager tags items/blocks, should be managed there
    // public NamespacedKey getSegmentIdKey() {
    //     // return scoreManager.getSegmentIdKey(); // Delegate or manage key elsewhere
    //     throw new UnsupportedOperationException("Segment ID Key management needs implementation (likely in ScoreManager).");
    // }

    // This requires iterating through the correct team's DungeonManager instance
    // public Optional<PlacedSegment> getPlacedSegmentById(UUID teamId, UUID segmentInstanceId) {
    //     DungeonManager dm = teamDungeonManagers.get(teamId);
    //     if (dm != null) {
    //         // Requires PlacedSegment to have a unique ID and DungeonManager to index them
    //         // return dm.getPlacedSegmentByInstanceId(segmentInstanceId);
    //          throw new UnsupportedOperationException("Getting segment by instance ID needs implementation in DungeonManager.");
    //     }
    //     return Optional.empty();
    // }
}
