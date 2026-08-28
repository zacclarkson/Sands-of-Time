package com.clarkson.sot.main;

// Required Imports (ensure all needed imports are present)
import com.clarkson.sot.dungeon.*; // Includes Dungeon, DungeonBlueprint, DeathCage, VaultColor, VaultManager
import com.clarkson.sot.dungeon.DoorManager; // Import DoorManager
import com.clarkson.sot.events.FloorItemManager; // Import FloorItemManager
import com.clarkson.sot.scoring.BankingManager;
import com.clarkson.sot.scoring.ScoreManager;
import com.clarkson.sot.timer.VisualTimerLayout;
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
    // Not final: an admin can move these at runtime with /sot set <lobby|trapped>. Both are
    // always non-null, but until they have been set from config.yml or that command they hold a
    // fallback (the main world's spawn) and the "configured" flags below stay false, which is what
    // makes setupGame/startGame refuse to run rather than drop a round somewhere nobody chose.
    private volatile Location lobbyLocation; // Main world anchor (e.g., for visual timers)
    private volatile Location configTrappedLocation; // Universal location for trapped players
    private volatile boolean lobbyConfigured;
    private volatile boolean trappedConfigured;

    // --- Constants ---
    private static final Vector DUNGEON_BASE_OFFSET = new Vector(10000, 100, 10000); // Offset from world spawn/anchor
    private static final Vector TEAM_DUNGEON_SPACING = new Vector(5000, 0, 0); // Space between team instances

    /**
     * Constructor for GameManager (Refactored).
     * Initializes managers and loads configuration. Takes lobby and trapped locations.
     *
     * <p>Both locations start out flagged as <i>unconfigured</i>: pass whatever fallback you like
     * (the caller usually passes the main world's spawn) and then call {@link #setLobbyLocation} /
     * {@link #setTrappedLocation} with the real values from {@code config.yml}. Until that happens
     * {@link #setupGame} and {@link #startGame} refuse to run.
     *
     * @param plugin            The main plugin instance.
     * @param lobbyLocation     A central location in the main world (e.g., lobby) used as an anchor.
     * @param trappedLocation   The universal location where players are sent when trapped by the timer.
     */
    public GameManager(Plugin plugin, Location lobbyLocation, Location trappedLocation) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.lobbyLocation = Objects.requireNonNull(lobbyLocation, "Lobby location cannot be null").clone();
        this.configTrappedLocation = Objects.requireNonNull(trappedLocation, "Trapped location cannot be null").clone();
        this.lobbyConfigured = false;
        this.trappedConfigured = false;

        // Initialize managers - ORDER MATTERS based on dependencies
        this.playerStateManager = new PlayerStateManager();
        this.teamManager = new TeamManager(this); // Pass self
        this.scoreManager = new ScoreManager(teamManager, this, plugin);
        this.bankingManager = new BankingManager(scoreManager, this, plugin);
        this.sandManager = new SandManager(this, plugin); // Pass self and plugin
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
        // Visual sand timers get anchored on the lobby here, so refuse before placing them at a
        // fallback location. State is left alone so the admin can fix config and retry.
        if (!areLocationsConfigured()) {
            plugin.getLogger().severe("Cannot set up game: unconfigured location(s) "
                    + getUnconfiguredLocationNames() + ". Use /sot set <lobby|trapped>.");
            return;
        }
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
        int visualTimerIndex = 0; // Column slot in the lobby, one per participating team
        for (UUID teamId : participatingTeamIds) {
            TeamDefinition definition = teamManager.getTeamDefinition(teamId);
            if (definition == null) { /* ... warning ... */ continue; }

            // Via the getter: it hands out a copy, so the team's timer display cannot end up holding
            // a reference to the live lobby field.
            Location anchor = getLobbyLocation();
            Location visualTimerBottom = determineVisualTimerBottomLocation(definition, anchor, visualTimerIndex);
            Location visualTimerTop = determineVisualTimerTopLocation(definition, anchor, visualTimerIndex);
            visualTimerIndex++;

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
        // Defence in depth for non-command callers; GameCommand reports this more helpfully.
        // Deliberately leaves currentState at SETUP (unlike the generation failures below) so the
        // admin can set the locations and start again without a restart.
        if (!areLocationsConfigured()) {
            plugin.getLogger().severe("Cannot start game: unconfigured location(s) "
                    + getUnconfiguredLocationNames() + ". Use /sot set <lobby|trapped>.");
            return;
        }
        // Removed check for vaultManager/dungeonGenerator null as constructor handles it

        plugin.getLogger().info("Starting Sands of Time game generation...");

        // 1. Generate Dungeon Layout Blueprint (Relative Structure)
        this.dungeonLayoutBlueprint = dungeonGenerator.generateDungeonLayout();
        if (this.dungeonLayoutBlueprint == null || this.dungeonLayoutBlueprint.getRelativeSegments().isEmpty()) {
             plugin.getLogger().severe("Failed to generate dungeon layout blueprint. Aborting game start.");
             currentState = GameState.ENDED; return;
        }

        World gameWorld = getLobbyLocation().getWorld();
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

            // 3. Assign each player to their own death cage
            assignPlayerCages(teamId, team);

            // 4. Teleport Team Members to their specific Hub
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
                     // Snapshot the destination now: /sot set trapped may move it before the task runs.
                     final Location trapDestination = getTrappedLocation();
                     if (trapDestination != null) {
                         final Component message = Component.text("Your team's timer ran out! You are trapped!", NamedTextColor.RED, TextDecoration.BOLD);
                         Bukkit.getScheduler().runTask(plugin, () -> { if (onlinePlayer.isValid()) { onlinePlayer.teleport(trapDestination); onlinePlayer.sendMessage(message); } });
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

        // --- Final score calculations & display ---
        displayFinalScores();

        // Teleport remaining players to lobby (snapshot once; the field is mutable)
        final Location lobbyDestination = getLobbyLocation();
        for (SoTTeam team : activeTeamsInGame.values()) {
            for (UUID memberId : team.getMemberUUIDs()) {
                Player player = Bukkit.getPlayer(memberId);
                if (player != null && player.isOnline()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isValid()) {
                            player.teleport(lobbyDestination);
                        }
                    });
                }
            }
        }

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

    /**
     * Handles a player dying in the dungeon.
     * - Clears unbanked coins (items drop naturally at death location)
     * - Updates status to DEAD_AWAITING_REVIVE
     * - Teleports to death cage
     */
    public void handlePlayerDeath(Player player) {
        if (player == null) return;
        UUID playerUUID = player.getUniqueId();
        UUID teamId = teamManager.getPlayerTeamId(player);
        if (teamId == null) return;

        // Apply death penalty — clears unbanked score (items already drop at death location)
        int lostCoins = scoreManager.applyDeathPenalty(playerUUID);

        // Update state
        playerStateManager.updateStatus(playerUUID, PlayerStatus.DEAD_AWAITING_REVIVE);

        // Teleport to player's assigned death cage
        Location cageLocation = getPlayerDeathCageLocation(teamId, playerUUID);
        if (cageLocation != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isValid()) {
                    player.teleport(cageLocation.clone().add(0.5, 0.1, 0.5));
                    player.sendMessage(Component.text("You died! A teammate must sacrifice sand to free you.", NamedTextColor.RED));
                }
            });
        }

        // Notify team
        NamedTextColor teamColor = teamManager.getTeamColor(teamId);
        Component deathMsg = Component.text(player.getName(), teamColor)
                .append(Component.text(" has died!", NamedTextColor.RED));
        SoTTeam team = activeTeamsInGame.get(teamId);
        if (team != null) {
            for (UUID memberId : team.getMemberUUIDs()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    member.sendMessage(deathMsg);
                }
            }
        }

        if (lostCoins > 0) {
            plugin.getLogger().info(player.getName() + " died, lost " + lostCoins + " unbanked coins");
        }
    }

    /**
     * Handles reviving a dead player (called from SandManager after sand is consumed).
     */
    public void handlePlayerRevive(Player deadPlayer, Player reviver) {
        if (deadPlayer == null || reviver == null) return;
        UUID teamId = teamManager.getPlayerTeamId(deadPlayer);
        if (teamId == null) return;

        playerStateManager.updateStatus(deadPlayer, PlayerStatus.ALIVE_IN_DUNGEON);

        Location hubLocation = getTeamHubLocation(teamId);
        if (hubLocation != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (deadPlayer.isValid()) {
                    deadPlayer.teleport(hubLocation.clone().add(0.5, 0.1, 0.5));
                }
            });
        }

        plugin.getLogger().info(reviver.getName() + " revived " + deadPlayer.getName());
    }

    /**
     * Handles a player escaping the dungeon safely.
     */
    public void handlePlayerLeave(Player player) {
        if (player == null) return;
        UUID playerUUID = player.getUniqueId();
        UUID teamId = teamManager.getPlayerTeamId(player);
        if (teamId == null) return;

        playerStateManager.updateStatus(playerUUID, PlayerStatus.ESCAPED_SAFE);
        scoreManager.playerEscaped(playerUUID);

        // Teleport to safe exit
        Location safeExit = getTeamSafeExitLocation(teamId);
        if (safeExit != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isValid()) {
                    player.teleport(safeExit.clone().add(0.5, 0.1, 0.5));
                    player.sendMessage(Component.text("You escaped safely!", NamedTextColor.GREEN));
                }
            });
        }

        // Notify team
        NamedTextColor teamColor = teamManager.getTeamColor(teamId);
        Component escapeMsg = Component.text(player.getName(), teamColor)
                .append(Component.text(" has escaped the dungeon!", NamedTextColor.GREEN));
        Bukkit.getServer().broadcast(escapeMsg);

        plugin.getLogger().info(player.getName() + " escaped the dungeon safely");
    }

    // --- Final Scores ---

    /**
     * Calculates and broadcasts final scores. The team with the highest banked score wins.
     */
    private void displayFinalScores() {
        Bukkit.getServer().broadcast(Component.text(""));
        Bukkit.getServer().broadcast(Component.text("=== FINAL SCORES ===", NamedTextColor.GOLD, TextDecoration.BOLD));

        // Sort teams by banked score descending
        List<SoTTeam> sortedTeams = new ArrayList<>(activeTeamsInGame.values());
        sortedTeams.sort(Comparator.comparingInt(SoTTeam::getBankedScore).reversed());

        int rank = 1;
        for (SoTTeam team : sortedTeams) {
            NamedTextColor teamColor = teamManager.getTeamColor(team.getTeamId());
            Component line = Component.text("#" + rank + " ", NamedTextColor.WHITE)
                    .append(Component.text(team.getTeamName(), teamColor))
                    .append(Component.text(" - " + team.getBankedScore() + " coins", NamedTextColor.YELLOW));
            Bukkit.getServer().broadcast(line);
            rank++;
        }

        // Announce winner
        if (!sortedTeams.isEmpty()) {
            SoTTeam winner = sortedTeams.get(0);
            if (winner.getBankedScore() > 0) {
                NamedTextColor winnerColor = teamManager.getTeamColor(winner.getTeamId());
                Bukkit.getServer().broadcast(Component.text(""));
                Bukkit.getServer().broadcast(
                        Component.text(winner.getTeamName(), winnerColor, TextDecoration.BOLD)
                                .append(Component.text(" wins Sands of Time!", NamedTextColor.GOLD, TextDecoration.BOLD)));
            }
        }
        Bukkit.getServer().broadcast(Component.text("====================", NamedTextColor.GOLD, TextDecoration.BOLD));
    }

    // --- Utility Methods & Getters ---

    public SoTTeam getActiveTeamForPlayer(Player player) {
        if (player == null) return null;
        UUID teamId = teamManager.getPlayerTeamId(player);
        if (teamId == null) return null;
        return activeTeamsInGame.get(teamId);
    }
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

    /**
     * Gets the instance-specific Safe Exit location for a team.
     * Falls back to the hub when the segment templates define no SAFE_EXIT marker; the generator
     * has already warned about that once, so this path stays quiet.
     */
    @Nullable
    public Location getTeamSafeExitLocation(UUID teamId) {
        DungeonManager teamDungeonManager = teamDungeonManagers.get(teamId);
        if (teamDungeonManager == null) return null;
        Dungeon teamDungeonData = teamDungeonManager.getDungeonData();
        if (teamDungeonData == null) return null;

        Location safeExit = teamDungeonData.getSafeExitLocation();
        if (safeExit != null) return safeExit;

        plugin.getLogger().fine("No safe exit defined for team " + teamId + "; using the hub location.");
        return teamDungeonData.getHubLocation();
    }

    /**
     * Checks whether a team's dungeon defines a safe exit at all.
     * Callers use this to decide whether to fall back to the legacy escape behaviour.
     */
    public boolean hasTeamSafeExit(UUID teamId) {
        DungeonManager teamDungeonManager = teamDungeonManagers.get(teamId);
        if (teamDungeonManager == null) return false;
        Dungeon teamDungeonData = teamDungeonManager.getDungeonData();
        return teamDungeonData != null && teamDungeonData.getSafeExitLocation() != null;
    }

    /**
     * Checks whether a location is a team's safe exit block.
     * Returns false when the team's dungeon defines no safe exit, so callers can fall back.
     */
    public boolean isTeamSafeExitAt(UUID teamId, Location location) {
        if (location == null) return false;
        DungeonManager teamDungeonManager = teamDungeonManagers.get(teamId);
        if (teamDungeonManager == null) return false;
        Dungeon teamDungeonData = teamDungeonManager.getDungeonData();
        return teamDungeonData != null && teamDungeonData.isSafeExitAt(location);
    }

    /**
     * Assigns each player on a team to their own death cage.
     * Called during startGame() after dungeon instances are created.
     */
    private void assignPlayerCages(UUID teamId, SoTTeam team) {
        DungeonManager teamDungeonManager = teamDungeonManagers.get(teamId);
        if (teamDungeonManager == null || teamDungeonManager.getDungeonData() == null) return;

        List<DeathCage> cages = teamDungeonManager.getDungeonData().getDeathCages();
        int cageIndex = 0;
        for (UUID memberId : team.getMemberUUIDs()) {
            if (cageIndex >= cages.size()) {
                plugin.getLogger().warning("Not enough death cages for team " + team.getTeamName()
                    + " (have " + cages.size() + ", need more). Player " + memberId + " unassigned.");
                break;
            }
            cages.get(cageIndex).assignPlayer(memberId);
            plugin.getLogger().fine("Assigned player " + memberId + " to death cage " + cageIndex + " for team " + teamId);
            cageIndex++;
        }
    }

    /**
     * Gets the death cage location assigned to a specific player.
     * @return The cage location, or the universal trapped location as fallback.
     */
    @Nullable
    private Location getPlayerDeathCageLocation(UUID teamId, UUID playerUUID) {
        DungeonManager teamDungeonManager = teamDungeonManagers.get(teamId);
        if (teamDungeonManager == null || teamDungeonManager.getDungeonData() == null) {
            return getTrappedLocation();
        }
        for (DeathCage cage : teamDungeonManager.getDungeonData().getDeathCages()) {
            if (playerUUID.equals(cage.getAssignedPlayerUUID())) {
                return cage.getCageLocation();
            }
        }
        plugin.getLogger().warning("No death cage assigned to player " + playerUUID + " on team " + teamId);
        return getTrappedLocation();
    }

    /**
     * Finds the DeathCage whose sacrifice point is at the given location for a team.
     * @return The DeathCage, or null if no match.
     */
    @Nullable
    public DeathCage getDeathCageAtSacrificePoint(UUID teamId, Location location) {
        DungeonManager teamDungeonManager = teamDungeonManagers.get(teamId);
        if (teamDungeonManager == null || teamDungeonManager.getDungeonData() == null) return null;
        for (DeathCage cage : teamDungeonManager.getDungeonData().getDeathCages()) {
            if (cage.isSacrificePointAt(location)) {
                return cage;
            }
        }
        return null;
    }

    /** Finds the team ID associated with a given world location. */
    @Nullable
    public UUID getTeamIdForLocation(Location location) { /* ... (Implementation remains the same) ... */ return null;}
    /** Gets the DungeonManager instance for a specific team. */
    @Nullable public DungeonManager getTeamDungeonManager(UUID teamId) { return teamDungeonManagers.get(teamId); }
    /**
     * Determines the bottom location (the block below the sand column) for a team's visual timer.
     * Returns null if the anchor has no loaded world, which disables that team's display.
     */
    @Nullable
    private Location determineVisualTimerBottomLocation(TeamDefinition teamDef, Location anchorLocation, int teamIndex) {
        if (!hasVisualTimerWorld(teamDef, anchorLocation)) return null;
        return VisualTimerLayout.bottomLocation(anchorLocation, teamIndex);
    }

    /**
     * Determines the top location (the highest sand block) for a team's visual timer.
     * Returns null if the anchor has no loaded world, which disables that team's display.
     */
    @Nullable
    private Location determineVisualTimerTopLocation(TeamDefinition teamDef, Location anchorLocation, int teamIndex) {
        if (!hasVisualTimerWorld(teamDef, anchorLocation)) return null;
        return VisualTimerLayout.topLocation(anchorLocation, teamIndex);
    }

    private boolean hasVisualTimerWorld(TeamDefinition teamDef, Location anchorLocation) {
        if (anchorLocation != null && anchorLocation.getWorld() != null) return true;
        plugin.getLogger().warning("Lobby anchor has no loaded world; visual timer disabled for team "
                + (teamDef != null ? teamDef.getName() : "unknown"));
        return false;
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
    public FloorItemManager getFloorItemManager() { return floorItemManager; } // Added Getter
    public DoorManager getDoorManager() { return doorManager; } // Added Getter
    /** The universal trapped location. Returns a copy: {@link Location} is mutable. */
    public Location getTrappedLocation() { return configTrappedLocation.clone(); }
    /** The lobby anchor. Returns a copy: {@link Location} is mutable. */
    public Location getLobbyLocation() { return lobbyLocation.clone(); }

    // --- Location configuration (see /sot set and config.yml) ---

    /**
     * Sets the lobby anchor and marks it configured. Callers must not do this mid-round:
     * {@link #startGame} derives the dungeon world and origin from it, so moving it under a live
     * game would strand players and orphan the generated dungeons.
     */
    public void setLobbyLocation(Location location) {
        this.lobbyLocation = Objects.requireNonNull(location, "Lobby location cannot be null").clone();
        this.lobbyConfigured = true;
    }

    /**
     * Sets the universal trapped location and marks it configured. Safe at any time: it is only
     * read at teleport time and nothing caches it.
     */
    public void setTrappedLocation(Location location) {
        this.configTrappedLocation = Objects.requireNonNull(location, "Trapped location cannot be null").clone();
        this.trappedConfigured = true;
    }

    public boolean isLobbyConfigured() { return lobbyConfigured; }
    public boolean isTrappedConfigured() { return trappedConfigured; }

    /** True once both universal locations have been set from config.yml or {@code /sot set}. */
    public boolean areLocationsConfigured() { return lobbyConfigured && trappedConfigured; }

    /** Names of the locations still unset, for operator-facing messages. Empty when all are set. */
    public List<String> getUnconfiguredLocationNames() {
        List<String> names = new ArrayList<>(2);
        if (!lobbyConfigured) names.add("lobby");
        if (!trappedConfigured) names.add("trapped");
        return names;
    }

}
