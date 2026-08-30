package com.clarkson.sot.main;

// Required Imports (ensure all needed imports are present)
import com.clarkson.sot.dungeon.*; // Includes Dungeon, DungeonBlueprint, DeathCage, VaultColor, VaultManager
import com.clarkson.sot.dungeon.DoorManager;
import com.clarkson.sot.events.FloorItemManager; // Import FloorItemManager
import com.clarkson.sot.player.SoTPlayerManager;
import com.clarkson.sot.scoring.BankingManager;
import com.clarkson.sot.scoring.ScoreManager;
import com.clarkson.sot.timer.VisualTimerLayout;
import com.clarkson.sot.ui.GameScoreboardManager;
import com.clarkson.sot.utils.*; // PlayerStateManager, PlayerStatus, SandManager, SoTTeam, TeamDefinition, TeamManager

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import java.time.Duration;
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
    private final MobManager mobManager;
    private final SoTPlayerManager playerManager;
    private final GameScoreboardManager scoreboardManager; // Live standings sidebar
    private final Map<UUID, DungeonManager> teamDungeonManagers; // TeamID -> Manager for their instance
    private final Map<UUID, SoTTeam> activeTeamsInGame; // TeamID -> Active team object
    private DungeonBlueprint dungeonLayoutBlueprint; // Shared blueprint for this game run
    /**
     * Incremented every time a round starts or ends. The countdown ticker only checks the state once
     * a second, so without this a round aborted during its countdown leaves a task that can still
     * see COUNTDOWN when a *new* round starts within that same second — and then finish the new
     * round's countdown early, off its own stale counter. Tasks capture the epoch and stop when it
     * moves on.
     */
    private int roundEpoch;

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
        // playerManager first: MobManager credits kills through getPlayerManager().
        this.playerManager = new SoTPlayerManager(plugin);
        this.mobManager = new MobManager(plugin, this);
        this.dungeonGenerator = new DungeonGenerator(plugin);
        this.scoreboardManager = new GameScoreboardManager(plugin, this); // Reads the managers above

        // Initialize maps
        this.activeTeamsInGame = new ConcurrentHashMap<>(); // Use concurrent maps if accessed by events/tasks
        this.teamDungeonManagers = new ConcurrentHashMap<>();

        // Set initial state
        this.currentState = GameState.SETUP;

        // Load dungeon segment templates. A template failure is deliberately NOT expressed as a
        // game state: it used to set ENDED here, conflating "this server cannot play at all" with
        // "a round just finished". Those need to stay distinct now that /sot reset clears the
        // latter — otherwise a reset would launder a condition that is still fatal and let
        // /sot start run with no templates. setupGame()/startGame() check hasHubTemplate() instead,
        // which is also re-evaluated after /sotreloadsegments rather than latched at boot.
        if (!this.dungeonGenerator.loadSegmentTemplates(plugin.getDataFolder())) {
            plugin.getLogger().severe("Failed to load dungeon segments into DungeonGenerator."
                    + " /sot start will refuse until a HUB template exists (see /sotreloadsegments).");
        }

        plugin.getLogger().info("GameManager initialized.");
    }

    /**
     * Sets up the participating teams for the current game instance.
     * Creates SoTTeam objects and stores them.
     *
     * <p>Teams start without a visual sand column: it is anchored on the hub's TIMER marker in
     * {@link #startGame()}, once each team's dungeon has been pasted.
     *
     * @param participatingTeamIds List of UUIDs for teams participating.
     * @param allPlayersInGame     List of all players involved in the game.
     * @return true if teams were set up and the game is ready for {@link #startGame()}. Callers must
     *         check this rather than assume success — every refusal below leaves the state untouched
     *         so the operator can fix the cause and try again.
     */
    public boolean setupGame(List<UUID> participatingTeamIds, List<Player> allPlayersInGame) {
        if (currentState != GameState.SETUP) { /* ... warning ... */ return false; }
        // startGame derives the dungeon world and origin from these, so refuse now rather than
        // generating a round somewhere nobody chose. State is left alone so the admin can fix config
        // and retry.
        if (!areLocationsConfigured()) {
            plugin.getLogger().severe("Cannot set up game: unconfigured location(s) "
                    + getUnconfiguredLocationNames() + ". Use /sot set <lobby|trapped>.");
            return false;
        }
        // Checked here as well as in startGame so a hub-less server refuses at the first step, rather
        // than reporting a successful setup for a round that can never actually start.
        if (!dungeonGenerator.hasHubTemplate()) {
            plugin.getLogger().severe("Cannot set up game: no HUB segment template loaded."
                    + " Save one with /sotsavesegment <name> HUB, then /sotreloadsegments.");
            return false;
        }
        if (participatingTeamIds == null || participatingTeamIds.isEmpty()) { /* ... warning ... */ return false; }
        plugin.getLogger().info("Setting up game with " + participatingTeamIds.size() + " teams.");

        // Clear state from previous games. tearDownRound() is idempotent, so this is a no-op after
        // a clean /sot end + /sot reset and a genuine rescue after a crash or a failed start — the
        // important part is that it air-fills any dungeon still standing rather than just dropping
        // the DungeonManagers that know where it is.
        tearDownRound();

        // A player who logged out mid-round (or rejoined after a crash) can still be carrying last
        // round's vault keys, which would open this round's vaults on sight.
        stripGameKeys(allPlayersInGame);

        // ... (Validate player assignments - same as before) ...

        // Create SoTTeam instances for each participating team
        for (UUID teamId : participatingTeamIds) {
            TeamDefinition definition = teamManager.getTeamDefinition(teamId);
            if (definition == null) { /* ... warning ... */ continue; }

            SoTTeam activeTeam = new SoTTeam(definition, plugin, this);
            activeTeamsInGame.put(teamId, activeTeam);
            plugin.getLogger().info("Initialized SoTTeam for: " + definition.getName());

            // Add members and initialize state
             Set<UUID> memberUUIDs = teamManager.getTeamMemberUUIDs(teamId);
             for (UUID memberId : memberUUIDs) {
                 Player p = Bukkit.getPlayer(memberId);
                 if (p != null && p.isOnline()) {
                     activeTeam.addMember(p);
                     playerStateManager.initializePlayer(p);
                     playerManager.initializePlayer(p);
                 } else { /* ... warning ... */ }
             }
        }
        plugin.getLogger().info("Game setup complete. " + activeTeamsInGame.size() + " active teams created. Ready to start.");
        return !activeTeamsInGame.isEmpty();
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
        // Generation needs at least one HUB template on disk. This used to be expressed by the
        // constructor parking the state at ENDED, which could never be recovered from; checking it
        // here instead means /sotreloadsegments can fix a hub-less server without a restart. Like
        // the location check above, this deliberately leaves the state at SETUP.
        if (!dungeonGenerator.hasHubTemplate()) {
            plugin.getLogger().severe("Cannot start game: no HUB segment template loaded."
                    + " Save one with /sotsavesegment <name> HUB, then /sotreloadsegments.");
            return;
        }

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
        cleanupDungeonInstances(); // Never drop a DungeonManager without air-filling what it pasted

        // Sorted so a team lands on the same origin every round; activeTeamsInGame is a
        // ConcurrentHashMap, whose iteration order is not part of its contract.
        List<SoTTeam> orderedTeams = new ArrayList<>(activeTeamsInGame.values());
        orderedTeams.sort(Comparator.comparing(t -> t.getTeamId().toString()));
        for (SoTTeam team : orderedTeams) {
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

            // 2b. Anchor the team's visual sand column on its hub's TIMER marker. This is the only
            //     place the column ever gets a location: with no marker the team plays without one
            //     rather than having a stray pillar of sand appear at the lobby.
            Location timerBase = teamDungeon.getTimerBaseLocation();
            if (timerBase != null) {
                Location timerTop = timerBase.clone().add(0, VisualTimerLayout.COLUMN_HEIGHT_BLOCKS, 0);
                team.relocateVisualTimer(timerBase, timerTop);
            } else {
                plugin.getLogger().warning("Hub defines no TIMER marker; visual sand column disabled"
                        + " for team " + team.getTeamName() + " this round.");
            }

            // 3. Assign each player to their own death cage
            assignPlayerCages(teamId, team);

            // 4. Teleport Team Members to their spawn points. Each player gets a distinct PLAYER_SPAWN
            //    marker (round-robin if there are more players than markers); segments without markers
            //    fall back to the single hub location for everyone.
            Location teamHubLocation = getTeamHubLocation(teamId); // Get instance-specific hub
            List<Location> spawnPoints = getTeamPlayerSpawnLocations(teamId);
            if (teamHubLocation != null || !spawnPoints.isEmpty()) {
                 int spawnIndex = 0;
                 for (UUID memberId : team.getMemberUUIDs()) {
                     Player player = Bukkit.getPlayer(memberId);
                     if (player != null && player.isOnline()) {
                         Location base = spawnPoints.isEmpty()
                                 ? teamHubLocation
                                 : spawnPoints.get(spawnIndex % spawnPoints.size());
                         spawnIndex++;
                         final Location teleportTarget = base.clone().add(0.5, 0.1, 0.5);
                         teleportTarget.setYaw(player.getLocation().getYaw());
                         teleportTarget.setPitch(0);
                         Bukkit.getScheduler().runTask(plugin, () -> { if (player.isValid()) player.teleport(teleportTarget); });
                     }
                 }
            } else { /* ... warning ... */ }
            teamIndex++;
        }

        // 4. Freeze players for a countdown, then begin play. Timers must NOT tick during the
        //    countdown, so team.startTimer() is deferred to beginPlay() when the count hits zero.
        //    The COUNTDOWN state gates the freeze listener (see CountdownFreezeListener).
        this.currentState = GameState.COUNTDOWN;
        roundEpoch++;
        startCountdown(roundEpoch);
        plugin.getLogger().info("Sands of Time dungeons generated; countdown started.");
    }

    /** Seconds players are frozen at the hub before movement and timers begin. */
    private static final int COUNTDOWN_SECONDS = 10;

    /**
     * Runs a {@value #COUNTDOWN_SECONDS}-second on-screen countdown while players are frozen
     * (movement blocked by CountdownFreezeListener while the state is COUNTDOWN), then calls
     * {@link #beginPlay(int)}. Aborts silently if the game is no longer in COUNTDOWN (e.g.
     * force-ended), or if {@code epoch} no longer matches {@link #roundEpoch} because another round
     * has since started — the state alone is not enough, since the new round is also in COUNTDOWN.
     *
     * @param epoch the {@link #roundEpoch} this countdown belongs to.
     */
    private void startCountdown(final int epoch) {
        final int[] remaining = { COUNTDOWN_SECONDS };
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (currentState != GameState.COUNTDOWN || epoch != roundEpoch) { task.cancel(); return; }
            if (remaining[0] > 0) {
                Title title = Title.title(
                        Component.text(String.valueOf(remaining[0]), NamedTextColor.GOLD, TextDecoration.BOLD),
                        Component.text("Get ready...", NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(200)));
                for (Player p : getParticipatingPlayers()) {
                    p.showTitle(title);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                }
                remaining[0]--;
            } else {
                task.cancel();
                beginPlay(epoch);
            }
        }, 0L, 20L);
    }

    /** Releases the freeze, starts every team timer, and announces the game has begun. */
    private void beginPlay(int epoch) {
        if (currentState != GameState.COUNTDOWN || epoch != roundEpoch) return;
        this.currentState = GameState.RUNNING;
        scoreboardManager.start(); // Live standings sidebar, refreshed once a second
        mobManager.start(); // Mob spawners produce waves while a team member stands near them
        for (SoTTeam team : activeTeamsInGame.values()) { team.startTimer(); }
        Title go = Title.title(
                Component.text("GO!", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(200)));
        for (Player p : getParticipatingPlayers()) {
            p.showTitle(go);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
        }
        Bukkit.getServer().broadcast(Component.text("Sands of Time has begun!", NamedTextColor.GOLD, TextDecoration.BOLD));
        plugin.getLogger().info("Sands of Time game started with per-team dungeons.");
    }

    /** True if the player belongs to an active team in the current game. Used by the freeze gate. */
    public boolean isParticipant(UUID playerId) {
        for (SoTTeam team : activeTeamsInGame.values()) {
            if (team.getMemberUUIDs().contains(playerId)) return true;
        }
        return false;
    }

    /** All online players across every active team. Used for countdown titles and the freeze gate. */
    public List<Player> getParticipatingPlayers() {
        List<Player> players = new ArrayList<>();
        for (SoTTeam team : activeTeamsInGame.values()) {
            for (UUID memberId : team.getMemberUUIDs()) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null && p.isOnline()) players.add(p);
            }
        }
        return players;
    }

    /**
     * Forcefully ends the current Sands of Time game.
     *
     * <p>COUNTDOWN counts as a live round: a round started by mistake has to be abortable without
     * waiting out the {@value #COUNTDOWN_SECONDS}-second freeze. No team timer has started yet at
     * that point, so the stop loop below is simply a no-op, and the countdown ticker cancels itself
     * on its next tick once the state is no longer COUNTDOWN (see {@link #startCountdown}).
     */
    public void endGame() {
        if (currentState != GameState.RUNNING && currentState != GameState.PAUSED
                && currentState != GameState.COUNTDOWN) { /* ... warning ... */ return; }
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

    /**
     * Whether a team member should be pulled back to the lobby when the round ends.
     *
     * <p>Everyone except the players locked in by their timer. {@link #handleTeamTimerEnd} only
     * <i>queues</i> the trapped teleport ({@code runTask} = next tick) and then calls
     * {@link #checkGameEndCondition} synchronously, so on the last team's expiry
     * {@link #endGameInternal} runs in that same tick and queues its lobby teleport <i>behind</i>
     * the trapped one. Teleporting a trapped player here would therefore silently undo the
     * trapping — which is exactly what used to happen in a single-team round, where the game
     * always ends on the tick the team is trapped.
     */
    static boolean returnsToLobbyAtGameEnd(PlayerStatus status) {
        return status != PlayerStatus.TRAPPED_TIMER_OUT;
    }

    /** Checks if all active teams' timers have expired. */
    private void checkGameEndCondition() {
        if (currentState != GameState.RUNNING) return;
        for (SoTTeam team : activeTeamsInGame.values()) { if (team.isTimerRunning()) return; } // Game continues if any timer runs
        plugin.getLogger().info("Game end condition met: All team timers have expired.");
        endGameInternal("All timers expired.");
    }

    /**
     * Internal method containing the logic to actually end the game.
     *
     * <p>Ends at {@link GameState#ENDED}, which is deliberately terminal: nothing rearms the game
     * to {@link GameState#SETUP} on its own, so the final standings stay readable even with
     * teleports still queued for the next tick. {@link #resetGame()} (via {@code /sot reset}) is
     * the only way back.
     */
    private void endGameInternal(String reason) {
        // Positive guard: only a live round can end. Stronger than the old "not already ENDED"
        // check, which stopped meaning anything useful once /sot reset made SETUP reachable again —
        // this also refuses to run a teardown against a freshly reset game.
        if (currentState != GameState.RUNNING && currentState != GameState.PAUSED
                && currentState != GameState.COUNTDOWN) {
            return;
        }
        plugin.getLogger().info("Executing internal game end sequence. Reason: " + reason);
        // A round aborted during its countdown never actually began: no timer ticked and nobody
        // scored, so the standings ceremony would just be a wall of zeroes.
        final boolean roundBegan = currentState != GameState.COUNTDOWN;
        this.currentState = GameState.ENDED;
        roundEpoch++; // Retire any countdown ticker still scheduled for this round

        // --- Final score calculations & display (needs activeTeamsInGame still populated) ---
        if (roundBegan) {
            displayFinalScores();
        }

        // Teleport remaining players to lobby (snapshot once; the field is mutable).
        // The status must be read here rather than inside the task below — tearDownRound() clears
        // every status further down in this same tick, so by the time the task fires they are gone.
        final Location lobbyDestination = getLobbyLocation();
        for (SoTTeam team : activeTeamsInGame.values()) {
            for (UUID memberId : team.getMemberUUIDs()) {
                if (!returnsToLobbyAtGameEnd(playerStateManager.getStatus(memberId))) continue;
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

        // Don't let this round's vault keys walk back to the lobby and into the next round. Also
        // read while the teams are still populated.
        stripGameKeysFromActiveTeams();

        // MUST stay last: tearDownRound() clears player statuses and the team map, both of which the
        // blocks above read. In particular the status read that keeps TRAPPED_TIMER_OUT players out
        // of the lobby teleport (see returnsToLobbyAtGameEnd) is only correct while statuses live.
        tearDownRound();

        Bukkit.getServer().broadcast(Component.text("Sands of Time has ended!", NamedTextColor.GOLD, TextDecoration.BOLD));
        plugin.getLogger().info("Sands of Time game ended and state cleared.");
    }

    /**
     * Tears the round's state down to nothing: timers, sand columns, the sidebar, every pasted
     * dungeon, and all of the per-round manager maps.
     *
     * <p>Deliberately idempotent — every step is a no-op on already-empty state — because three
     * callers need it: {@link #endGameInternal} at the end of a round, {@link #resetGame()} to
     * rescue the failed-start paths that set ENDED without ever cleaning up, and {@link #setupGame}
     * as a last line of defence after a crash or a {@code /reload}.
     *
     * <p>Does <b>not</b> touch {@link #currentState}; the caller owns that.
     */
    private void tearDownRound() {
        for (SoTTeam team : activeTeamsInGame.values()) {
            if (team.isTimerRunning()) team.stopTimer();
            // Tear down the visual sand column too. cleanupInstance below air-fills the dungeon
            // bounds, which covers it, but this also disarms the display and keeps its own block
            // count honest for the next round.
            team.clearVisualTimer();
            // Carried sand is real inventory now, so it has to be taken off the players themselves:
            // activeTeamsInGame is the only source of member lists, and anything left undeposited
            // would otherwise cross into the next round and buy free time at the next hub.
            sandManager.clearSandItems(team.getMemberUUIDs());
        }

        // Take the sidebar down before the teams are cleared below, so every player gets the
        // server's own scoreboard back rather than a frozen one.
        scoreboardManager.stop();

        cleanupDungeonInstances();
        activeTeamsInGame.clear();
        playerStateManager.clearAllStates();
        scoreManager.clearAllUnbankedScores();
        // cleanupInstance() clears these per team; repeat it globally so a team whose dungeon was
        // never successfully created is covered too.
        vaultManager.clearAllTeamStates();
        doorManager.clearAllTeamStates();
        floorItemManager.clearAllTeamStates();
        mobManager.clearAllTeamStates();
        // Drops the strong Player references SoTPlayerData holds, as well as last round's stats.
        playerManager.clearAll();
        dungeonLayoutBlueprint = null;
    }

    /**
     * Air-fills and forgets every dungeon instance this round pasted.
     *
     * <p>Always use this rather than clearing {@link #teamDungeonManagers} directly: the
     * DungeonManager is the only thing that knows where its blocks are, so dropping it without
     * calling {@code cleanupInstance()} strands a dungeon in the world that the next round then
     * pastes into (the paste uses {@code ignoreAirBlocks}, so it cannot clear the leftovers itself).
     */
    private void cleanupDungeonInstances() {
        for (DungeonManager dm : teamDungeonManagers.values()) {
            dm.cleanupInstance(); // Calls clearTeamState on Vault/Door/FloorItem managers
        }
        teamDungeonManagers.clear();
    }

    /**
     * Whether {@link #resetGame()} is allowed to run from the given state.
     *
     * <p>A live round has players in a dungeon and timers on the clock, so it must be ended
     * ({@code /sot end}) before it can be reset. Everything else — a finished round, or a SETUP
     * that an operator wants to re-clear — is fair game.
     */
    public static boolean canResetFrom(GameState state) {
        return state == GameState.ENDED || state == GameState.SETUP;
    }

    /**
     * Whether a round is on the clock — the states in which players are inside a dungeon and the
     * world has to be protected from them ({@code BlockProtectionListener}).
     *
     * <p>The exact complement of {@link #canResetFrom}, but written as its own explicit whitelist
     * rather than a negation, so that a new {@link GameState} constant is classified by neither and
     * fails {@code GameManagerTest} instead of silently defaulting to "unprotected".
     *
     * <p>{@code COUNTDOWN} counts: the hub segment's baked sand shaft is real sand in the world from
     * the moment the dungeon is pasted, well before the visual timer arms itself.
     */
    public static boolean isRoundLive(GameState state) {
        return state == GameState.COUNTDOWN || state == GameState.RUNNING || state == GameState.PAUSED;
    }

    /**
     * Clears a finished round away and returns to {@link GameState#SETUP} so another game can be
     * set up and started, without restarting the server.
     *
     * <p>This is the only transition out of the terminal {@code ENDED} state, and it is deliberately
     * explicit ({@code /sot reset}) rather than automatic at the end of a round: the final standings
     * stay on screen until an operator decides the round is done with.
     *
     * @return false if a round is still live, in which case nothing was changed.
     */
    public boolean resetGame() {
        if (!canResetFrom(currentState)) {
            plugin.getLogger().warning("Refusing to reset: a game is still " + currentState
                    + ". End it first.");
            return false;
        }
        plugin.getLogger().info("Resetting Sands of Time back to setup.");

        // Normally a no-op straight after endGameInternal, but startGame's failure paths park the
        // state at ENDED without any cleanup at all, so this is what rescues them.
        tearDownRound();

        // Team assignments outlive the round otherwise, so a player who was on a team last round
        // but is not set up this round would still resolve to it.
        teamManager.clearAssignments();
        stripGameKeys(Bukkit.getOnlinePlayers());

        this.currentState = GameState.SETUP;
        plugin.getLogger().info("Sands of Time reset. Ready for /sot setup.");
        return true;
    }

    /** Strips round keys from every member of every active team. Teams must still be populated. */
    private void stripGameKeysFromActiveTeams() {
        List<Player> members = new ArrayList<>();
        for (SoTTeam team : activeTeamsInGame.values()) {
            for (UUID memberId : team.getMemberUUIDs()) {
                Player player = Bukkit.getPlayer(memberId);
                if (player != null && player.isOnline()) members.add(player);
            }
        }
        stripGameKeys(members);
    }

    /**
     * Removes Sands of Time keys (rusty and vault) from the given players' inventories.
     *
     * <p>Vault keys are handed out as real items ({@code Key.giveTo}), so without this a player
     * carries last round's gold key into the next one and opens that vault on sight. Only key items
     * are taken: the builder tools carry a different tag and an admin keeps theirs across rounds.
     *
     * <p>Note this can only reach players who are online now — someone who logged out mid-round
     * keeps the key in their saved inventory, which is why {@link #setupGame} strips again on the
     * way into the next round rather than trusting the end-of-round pass.
     *
     * <p>Best-effort by design: this is cleanup, and it must never be the reason a reset or a setup
     * fails. {@code ItemManager}'s tag lookups throw if its keys were never initialized, and that
     * would otherwise leave the game stuck in ENDED — the exact dead end {@link #resetGame()} exists
     * to clear.
     */
    private void stripGameKeys(Collection<? extends Player> players) {
        if (players == null) return;
        int removed = 0;
        try {
            for (Player player : players) {
                if (player == null || !player.isOnline()) continue;
                ItemStack[] contents = player.getInventory().getContents();
                boolean changed = false;
                for (int i = 0; i < contents.length; i++) {
                    ItemStack item = contents[i];
                    if (ItemManager.isRustyKey(item) || ItemManager.isVaultKey(item)) {
                        contents[i] = null;
                        changed = true;
                        removed++;
                    }
                }
                if (changed) {
                    player.getInventory().setContents(contents);
                    player.updateInventory();
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Could not strip leftover Sands of Time keys; continuing anyway.", e);
        }
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " leftover Sands of Time key stack(s).");
        }
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

        // Everything they carried out leaves with them. Done synchronously rather than in the teleport
        // task below: that task is a tick away and is skipped entirely if they log out, and the gap is
        // a tick in which an escaped player could drop sand back into the dungeon for a teammate.
        // Unbanked coins are numeric state in ScoreManager, not items, so this does not touch scoring.
        player.getInventory().clear();
        player.updateInventory();

        // Out of the dungeon and out of the round: escaping is irreversible, so send them back to
        // the lobby rather than leaving them standing at the exit block inside the dungeon.
        // ESCAPED_SAFE already bars them from escaping again (EscapeListener) and from spending
        // sand (SandManager); the lobby puts them physically out of reach of the dungeon too.
        final Location lobbyDestination = getLobbyLocation();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isValid()) {
                player.teleport(lobbyDestination);
                player.sendMessage(Component.text("You escaped safely!", NamedTextColor.GREEN));
            }
        });

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
     * Absolute per-player spawn points for a team's instance (from PLAYER_SPAWN markers). Empty when
     * the segment templates define none — callers then fall back to {@link #getTeamHubLocation}.
     */
    public List<Location> getTeamPlayerSpawnLocations(UUID teamId) {
        DungeonManager teamDungeonManager = teamDungeonManagers.get(teamId);
        if (teamDungeonManager == null) return Collections.emptyList();
        Dungeon teamDungeonData = teamDungeonManager.getDungeonData();
        if (teamDungeonData == null) return Collections.emptyList();
        return teamDungeonData.getPlayerSpawnLocations();
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
     * Whether the location is part of <em>any</em> active team's visual sand timer column.
     *
     * <p>Every team, not just the one the player belongs to: {@link #getTeamIdForLocation} is still
     * a stub, team dungeons are thousands of blocks apart so a cross-team hit cannot happen by
     * accident, and an operator who teleported into someone else's hub is exactly the case worth
     * covering. Costs one coordinate comparison per active team.
     */
    public boolean isVisualTimerBlock(@Nullable Location location) {
        if (location == null) return false;
        for (SoTTeam team : activeTeamsInGame.values()) {
            if (team.isVisualTimerBlock(location)) return true;
        }
        return false;
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

    /**
     * True if the given block location is one of a team's sand deposit cells — the TIMER_DEPOSIT
     * marker positions where carried sand is spent to add time to that team's timer.
     */
    public boolean isTeamSandTimerDepositAt(UUID teamId, Location location) {
        DungeonManager teamDungeonManager = teamDungeonManagers.get(teamId);
        if (teamDungeonManager == null || teamDungeonManager.getDungeonData() == null) return false;
        return teamDungeonManager.getDungeonData().isSandTimerDepositAt(location);
    }

    /**
     * True if the given block location is the team's coin bank (the ender chest built at the
     * BANK marker). False when the team has no dungeon, or the dungeon defines no bank.
     */
    public boolean isTeamBankAt(UUID teamId, Location location) {
        DungeonManager teamDungeonManager = teamDungeonManagers.get(teamId);
        if (teamDungeonManager == null || teamDungeonManager.getDungeonData() == null) return false;
        return teamDungeonManager.getDungeonData().isBankAt(location);
    }

    /** Finds the team ID associated with a given world location. */
    @Nullable
    public UUID getTeamIdForLocation(Location location) { /* ... (Implementation remains the same) ... */ return null;}
    /** Gets the DungeonManager instance for a specific team. */
    @Nullable public DungeonManager getTeamDungeonManager(UUID teamId) { return teamDungeonManagers.get(teamId); }

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
    public MobManager getMobManager() { return mobManager; }
    public SoTPlayerManager getPlayerManager() { return playerManager; }
    public GameScoreboardManager getScoreboardManager() { return scoreboardManager; }
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

    // --- Dungeon seed (see /sot seed and config.yml) ---

    /**
     * Fixes the seed future rounds generate from, or clears it with {@code null} so each round rolls
     * its own. Only read when {@link #startGame} generates a layout, so this is safe to change
     * between rounds; changing it mid-round has no effect on the dungeon already standing, which is
     * why {@code /sot seed} refuses it rather than silently doing nothing.
     */
    public void setDungeonSeed(@Nullable Long seed) {
        dungeonGenerator.setSeed(seed);
    }

    /** The configured fixed seed, or null when each round rolls its own. */
    @Nullable
    public Long getConfiguredDungeonSeed() {
        return dungeonGenerator.getSeed();
    }

    /**
     * The seed the current (or most recent) round's dungeon was generated from, or null before any
     * round has generated one. This is what the population RNGs derive from, so that every team's
     * dungeon is populated identically, and what an operator reads back to replay a random layout.
     */
    @Nullable
    public Long getRoundSeed() {
        return dungeonGenerator.getLastUsedSeed();
    }

}
