package com.clarkson.sot.main; // Assuming GameManager is in .main package

// Import necessary classes from your project structure
import com.clarkson.sot.dungeon.DungeonManager;
import com.clarkson.sot.dungeon.VaultManager;
import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.scoring.BankingManager;
import com.clarkson.sot.scoring.ScoreManager;
import com.clarkson.sot.utils.PlayerStateManager;
import com.clarkson.sot.utils.PlayerStatus;
import com.clarkson.sot.utils.SandManager;
import com.clarkson.sot.utils.SoTTeam;
import com.clarkson.sot.utils.TeamDefinition;
import com.clarkson.sot.utils.TeamManager;

// Import Bukkit API classes
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

// Import Adventure API classes
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages the overall state, lifecycle, and coordination of a Sands of Time game instance.
 * Responsible for creating game-specific team instances (SoTTeam) and managing per-team timers.
 * Uses Adventure API for messages.
 */
public class GameManager {

    private final Plugin plugin; // Reference to the main plugin (SoT instance)
    private final TeamManager teamManager; // Manages team definitions and assignments
    private final PlayerStateManager playerStateManager;
    private final SandManager sandManager;
    private final ScoreManager scoreManager;
    private final BankingManager bankingManager;
    private final VaultManager vaultManager;
    private final DungeonManager dungeonManager; // For generating the dungeon

    // Stores the ACTIVE SoTTeam objects (with timers, sand count etc.) for the CURRENT game instance
    private final Map<UUID, SoTTeam> activeTeamsInGame;

    private GameState currentState;
    private Location dungeonHubLocation; // Central timer/sphinx area - needs to be set
    private Location safeExitLocation; // Where players go when they leave - needs to be set
    private Location deathCageLocation; // Where dead/trapped players are sent - needs to be set
    private List<PlacedSegment> currentDungeonLayout; // Store the generated layout

    /**
     * Constructor for GameManager. Initializes all necessary sub-managers.
     * @param plugin The main SoT plugin instance.
     * @param hubLocation Location of the central hub/timer area.
     * @param safeExit Location players teleport to upon safe exit.
     * @param deathCage Location players teleport to upon death/trapping.
     */
    public GameManager(Plugin plugin, Location hubLocation, Location safeExit, Location deathCage) {
        this.plugin = plugin;
        this.dungeonHubLocation = hubLocation; // TODO: Load from config or command
        this.safeExitLocation = safeExit;     // TODO: Load from config or command
        this.deathCageLocation = deathCage;   // TODO: Load from config or command

        // Initialize managers
        this.teamManager = new TeamManager();
        this.playerStateManager = new PlayerStateManager();
        this.scoreManager = new ScoreManager(teamManager, this, plugin);
        this.bankingManager = new BankingManager(scoreManager);
        this.sandManager = new SandManager(this);
        // Ensure SoT constructor exists or cast plugin appropriately
        if (plugin instanceof SoT) {
             this.vaultManager = new VaultManager((SoT) plugin, this);
             this.dungeonManager = new DungeonManager((SoT) plugin);
        } else {
             // Handle error - plugin type mismatch or provide alternative initialization
             plugin.getLogger().severe("Plugin instance is not of type SoT! Cannot initialize VaultManager or DungeonManager correctly.");
             this.vaultManager = null; // Or throw exception
             this.dungeonManager = null; // Or throw exception
        }


        this.activeTeamsInGame = new HashMap<>();
        this.currentDungeonLayout = new ArrayList<>();
        this.currentState = GameState.SETUP;

        // TODO: Load segment templates for DungeonManager if dungeonManager initialized successfully
        // if (this.dungeonManager != null) { ... }
    }

    /**
     * Sets up the participating teams for the current game instance.
     * Creates SoTTeam objects with necessary context.
     * @param participatingTeamIds List of Team IDs participating in this game.
     * @param allPlayersInGame List of all players intended to be in the game.
     */
    public void setupGame(List<UUID> participatingTeamIds, List<Player> allPlayersInGame) {
        if (currentState != GameState.SETUP) {
            plugin.getLogger().warning("Cannot setup game, already started or ended.");
            return;
        }
        plugin.getLogger().info("Setting up game with " + participatingTeamIds.size() + " teams.");

        activeTeamsInGame.clear();
        playerStateManager.clearAllStates();

        // 1. Assign players to teams
        for(Player p : allPlayersInGame) {
            UUID teamId = teamManager.getPlayerTeamId(p);
             if (teamId == null || !participatingTeamIds.contains(teamId)) {
                 plugin.getLogger().warning("Player " + p.getName() + " is not assigned to a participating team!");
                 // Handle assignment or removal
             }
        }

        // 2. Create game-specific SoTTeam instances
        for (UUID teamId : participatingTeamIds) {
            TeamDefinition definition = teamManager.getTeamDefinition(teamId);
            if (definition == null) {
                plugin.getLogger().log(Level.WARNING, "Cannot setup team: Definition not found for ID " + teamId);
                continue;
            }

            Location visualTimerBottom = determineVisualTimerBottomLocation(definition, dungeonHubLocation);
            Location visualTimerTop = determineVisualTimerTopLocation(definition, dungeonHubLocation);

            if (visualTimerBottom == null || visualTimerTop == null) {
                 plugin.getLogger().severe("Could not determine visual timer locations for team " + definition.getName() + "! Aborting team setup.");
                 continue;
            }

            SoTTeam activeTeam = new SoTTeam(
                definition.getId(), definition.getName(), definition.getColor(),
                plugin, this, visualTimerBottom, visualTimerTop
            );
            activeTeamsInGame.put(teamId, activeTeam);
            plugin.getLogger().log(Level.INFO, "Initialized SoTTeam for: " + definition.getName());

            Set<UUID> memberUUIDs = teamManager.getTeamMemberUUIDs(teamId);
            for(UUID memberId : memberUUIDs) {
                 Player p = Bukkit.getPlayer(memberId);
                 if(p != null && p.isOnline()) {
                    activeTeam.addMember(p);
                    playerStateManager.initializePlayer(p);
                 }
            }
        }
        plugin.getLogger().info("Game setup complete.");
    }

    /**
     * Starts the actual game timer and dungeon exploration.
     */
    public void startGame() {
        if (currentState != GameState.SETUP || activeTeamsInGame.isEmpty()) {
            plugin.getLogger().warning("Cannot start game: Not in SETUP state or no teams initialized.");
            return;
        }
        if (dungeonManager == null || vaultManager == null) {
             plugin.getLogger().severe("Cannot start game: DungeonManager or VaultManager not initialized!");
             return;
        }
        plugin.getLogger().info("Starting Sands of Time game...");

        // 1. Generate Dungeon
        if (dungeonHubLocation == null) {
             plugin.getLogger().severe("Cannot generate dungeon: Hub location not set!");
             return;
        }
        if (!dungeonManager.loadSegmentTemplates(plugin.getDataFolder())) {
             plugin.getLogger().severe("Failed to load dungeon segments. Cannot start game.");
             return;
        }
        dungeonManager.generateDungeon(dungeonHubLocation);
        // TODO: Get layout if needed: this.currentDungeonLayout = dungeonManager.getCurrentLayout();

        // 2. Place Vaults/Keys
        // vaultManager.placeVaultsAndKeys(this.currentDungeonLayout); // Needs valid layout

        // 3. Teleport Players
        // TODO: Implement teleport logic (teleport to hubLocation)

        // 4. Start Timers
        for (SoTTeam team : activeTeamsInGame.values()) {
            team.startTimer();
        }

        // 5. Set State & Announce
        this.currentState = GameState.RUNNING;
        Bukkit.getServer().broadcast(Component.text("Sands of Time has begun!", NamedTextColor.GOLD, TextDecoration.BOLD));
        plugin.getLogger().info("Sands of Time game started.");
    }

    /**
     * Ends the current Sands of Time game.
     */
     public void endGame() {
         if (currentState != GameState.RUNNING && currentState != GameState.PAUSED) { // Allow ending if paused
             plugin.getLogger().warning("Attempted to end game but it wasn't running or paused.");
             return;
         }
         plugin.getLogger().info("Ending Sands of Time game...");
         GameState previousState = this.currentState;
         this.currentState = GameState.ENDED;

         // Stop all team timers
         for (SoTTeam team : activeTeamsInGame.values()) {
             team.stopTimer();
             // If game was running, check consequences for remaining players
             if (previousState == GameState.RUNNING) {
                 handleTeamTimerEnd(team, false); // Pass false to indicate game end, not timer expiry
             }
         }

         // TODO: Final score calculations, display results, teleport players out

         activeTeamsInGame.clear();
         playerStateManager.clearAllStates();
         currentDungeonLayout.clear();

         Bukkit.getServer().broadcast(Component.text("Sands of Time has ended!", NamedTextColor.GOLD, TextDecoration.BOLD));
         plugin.getLogger().info("Sands of Time game ended.");
     }

    /**
     * Handles consequences when a team's timer ends OR the game ends forcefully.
     * @param team The team to process.
     * @param naturalExpiry True if timer hit 0 naturally, false if game ended otherwise.
     */
    public void handleTeamTimerEnd(SoTTeam team, boolean naturalExpiry) {
        if (team == null) return;
        // Only process if game is ending or was running when timer expired naturally
        if (currentState != GameState.ENDED && !(currentState == GameState.RUNNING && naturalExpiry)) return;

        if (naturalExpiry) {
             plugin.getLogger().log(Level.WARNING, "Timer has run out for team: " + team.getTeamName() + "!");
        }

        Set<UUID> memberUUIDs = team.getMemberUUIDs();
        boolean teamWiped = true;

        for (UUID memberUUID : memberUUIDs) {
            PlayerStatus currentStatus = playerStateManager.getStatus(memberUUID);

            if (currentStatus == PlayerStatus.ESCAPED_SAFE) {
                teamWiped = false;
                continue;
            }

            if (currentStatus == PlayerStatus.ALIVE_IN_DUNGEON || currentStatus == PlayerStatus.DEAD_AWAITING_REVIVE) {
                plugin.getLogger().log(Level.INFO, "Player " + memberUUID + " from team " + team.getTeamName() + " is trapped due to " + (naturalExpiry ? "timer expiry!" : "game end!"));

                playerStateManager.updateStatus(memberUUID, PlayerStatus.TRAPPED_TIMER_OUT);
                scoreManager.applyTimerEndPenalty(memberUUID); // Lose unbanked coins

                Player onlinePlayer = Bukkit.getPlayer(memberUUID);
                if (onlinePlayer != null && onlinePlayer.isOnline()) {
                    if (deathCageLocation != null) {
                         final Component message = naturalExpiry ?
                             Component.text("Your team's timer ran out! You are trapped!", NamedTextColor.RED, TextDecoration.BOLD) :
                             Component.text("The game ended while you were inside! You are trapped!", NamedTextColor.RED);
                         Bukkit.getScheduler().runTask(plugin, () -> {
                              onlinePlayer.teleport(deathCageLocation);
                              onlinePlayer.sendMessage(message);
                         });
                    } else {
                         plugin.getLogger().log(Level.SEVERE, "Death cage location is not set in GameManager!");
                    }
                }
            } else {
                 if(currentStatus != PlayerStatus.TRAPPED_TIMER_OUT) {
                    teamWiped = false;
                 }
            }
        }

        if (naturalExpiry) {
             final Component message = teamWiped ?
                 Component.text("Team " + team.getTeamName() + " got locked in!", NamedTextColor.RED) :
                 Component.text("Team " + team.getTeamName() + "'s timer ran out!", NamedTextColor.YELLOW);
             Bukkit.getServer().broadcast(message);
        }
    }
     // Overload for calls from SoTTeam internal timer
     public void handleTeamTimerEnd(SoTTeam team) {
        handleTeamTimerEnd(team, true); // Assume natural expiry if called without boolean
     }


    // --- Player Action Handlers (Called by GameListener) ---

    public void handlePlayerDeath(Player player) {
        if (currentState != GameState.RUNNING) return;
        SoTTeam team = getActiveTeamForPlayer(player);
        if (team == null) return;

        plugin.getLogger().info("Handling death for player " + player.getName() + " on team " + team.getTeamName());
        scoreManager.applyDeathPenalty(player.getUniqueId());
        playerStateManager.updateStatus(player, PlayerStatus.DEAD_AWAITING_REVIVE);

        if (deathCageLocation != null) {
             // Use final variable for lambda/runnable
             final Component deathMessage = Component.text("You died! A teammate must use ", NamedTextColor.RED)
                 .append(Component.text(SandManager.REVIVE_COST, NamedTextColor.WHITE)) // Use constant
                 .append(Component.text(" sand to revive you.", NamedTextColor.RED));
             // Ensure teleport happens on main thread
             Bukkit.getScheduler().runTask(plugin, () -> {
                player.teleport(deathCageLocation);
                player.sendMessage(deathMessage);
             });
        } else {
             plugin.getLogger().severe("Death cage location not set!");
        }
        // TODO: Notify team members
    }

    public void handlePlayerRevive(Player deadPlayer, Player reviver) {
         if (currentState != GameState.RUNNING) return;
         if (playerStateManager.getStatus(deadPlayer) != PlayerStatus.DEAD_AWAITING_REVIVE) {
             reviver.sendMessage(Component.text(deadPlayer.getName() + " doesn't need reviving right now.", NamedTextColor.YELLOW));
             return;
         }
         SoTTeam deadPlayerTeam = getActiveTeamForPlayer(deadPlayer);
         SoTTeam reviverTeam = getActiveTeamForPlayer(reviver);
         if(deadPlayerTeam == null || reviverTeam == null || !deadPlayerTeam.getTeamId().equals(reviverTeam.getTeamId())) {
             reviver.sendMessage(Component.text("You can only revive players on your own team!", NamedTextColor.RED));
             return;
         }

         if (sandManager.attemptRevive(reviver)) {
             plugin.getLogger().info(reviver.getName() + " revived " + deadPlayer.getName());
             playerStateManager.updateStatus(deadPlayer, PlayerStatus.ALIVE_IN_DUNGEON);
             if (dungeonHubLocation != null) {
                 // Ensure teleport happens on main thread
                 Bukkit.getScheduler().runTask(plugin, () -> deadPlayer.teleport(dungeonHubLocation));
                 deadPlayer.sendMessage(Component.text("You have been revived!", NamedTextColor.GREEN));
                 reviver.sendMessage(Component.text("You revived " + deadPlayer.getName() + "!", NamedTextColor.GREEN));
             }
             // restorePlayerInventory(deadPlayer);
         } else {
             reviver.sendMessage(Component.text("Your team doesn't have enough sand ("+ SandManager.REVIVE_COST +") to revive!", NamedTextColor.RED));
         }
    }

    public void handlePlayerLeave(Player player) {
         if (currentState != GameState.RUNNING) return;
         PlayerStatus status = playerStateManager.getStatus(player);
         if (status == PlayerStatus.ALIVE_IN_DUNGEON) {
             plugin.getLogger().info("Player " + player.getName() + " is leaving the dungeon.");
             scoreManager.playerEscaped(player.getUniqueId());
             playerStateManager.updateStatus(player, PlayerStatus.ESCAPED_SAFE);

             if (safeExitLocation != null) {
                  // Ensure teleport happens on main thread
                  Bukkit.getScheduler().runTask(plugin, () -> {
                     player.teleport(safeExitLocation);
                     player.sendMessage(Component.text("You escaped the dungeon safely!", NamedTextColor.GREEN));
                  });
             } else {
                  plugin.getLogger().severe("Safe exit location not set!");
             }
             // TODO: Prevent re-entry, check if last player out
         } else {
             player.sendMessage(Component.text("You cannot leave right now (Status: " + status + ").", NamedTextColor.RED));
         }
    }

    // --- Utility Methods ---
    public SoTTeam getActiveTeamForPlayer(Player player) {
        if (player == null) return null;
        UUID teamId = teamManager.getPlayerTeamId(player);
        return (teamId != null) ? activeTeamsInGame.get(teamId) : null;
    }

     public Map<UUID, SoTTeam> getActiveTeams() {
         return Collections.unmodifiableMap(activeTeamsInGame);
     }

    // --- Placeholder Location Methods ---
    private Location determineVisualTimerBottomLocation(TeamDefinition teamDef, Location hubCenter) {
        plugin.getLogger().log(Level.WARNING, "Placeholder: Determine visual timer bottom location for " + teamDef.getName());
        if (hubCenter == null) return Bukkit.getWorlds().get(0).getSpawnLocation(); // BAD PLACEHOLDER
        int offset = Math.abs(teamDef.getId().hashCode() % 10); // Use abs
        return hubCenter.clone().add(offset * 5, 0, 0); // Example offset
    }
     private Location determineVisualTimerTopLocation(TeamDefinition teamDef, Location hubCenter) {
         Location bottom = determineVisualTimerBottomLocation(teamDef, hubCenter);
         if (bottom == null) return null;
         return bottom.clone().add(0, 15, 0); // Example height
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
     public DungeonManager getDungeonManager() { return dungeonManager; }
     public Location getDungeonHubLocation() { return dungeonHubLocation; }
     public Location getSafeExitLocation() { return safeExitLocation; }
     public Location getDeathCageLocation() { return deathCageLocation; }

     public void addSecondsToTimer(int timeBonusSeconds) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addSecondsToTimer'");
     }

     public NamespacedKey getSegmentIdKey() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSegmentIdKey'");
     }

     public Optional<PlacedSegment> getPlacedSegmentById(UUID segmentId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPlacedSegmentById'");
     }
}
