package com.clarkson.sot.commands;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.main.SoTConfig;
import com.clarkson.sot.utils.TeamDefinition;
import com.clarkson.sot.utils.TeamManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /sot &lt;setup|start|end|reset|set|seed&gt; — minimal admin control for a Sands of Time round.
 *
 * <p>This wires the existing {@link GameManager} lifecycle ({@code setupGame}/{@code startGame}/
 * {@code endGame}) to an in-game command so an operator can actually reach a running game on a
 * test/dev server. It is intentionally small: {@code setup} assigns all online players to teams
 * (round-robin), {@code start} generates the dungeon and starts timers, {@code end} tears the
 * round down.
 *
 * <p>Requires at least one {@code HUB} segment template on disk for {@code start} to succeed
 * (build one with the segment tools and {@code /sotsavesegment <name> HUB}), and both universal
 * locations to be configured — {@code set} captures those from wherever the sender is standing,
 * writes them to {@code config.yml} and applies them without a restart.
 */
public class GameCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "sot.admin.control";
    private static final List<String> SUBCOMMANDS = List.of("setup", "start", "end", "reset", "set", "seed");
    private static final List<String> SET_TARGETS = List.of("lobby", "trapped");

    private final Plugin plugin;
    private final GameManager gameManager;

    public GameCommand(@NotNull Plugin plugin, @NotNull GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("You don't have permission to use this.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "setup" -> handleSetup(sender, args);
            case "start" -> handleStart(sender);
            case "end"   -> handleEnd(sender);
            case "reset" -> handleReset(sender);
            case "set"   -> handleSet(sender, args);
            case "seed"  -> handleSeed(sender, args);
            default      -> sendUsage(sender);
        }
        return true;
    }

    private void handleSetup(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!locationsConfigured(sender)) {
            return;
        }
        if (gameManager.getCurrentState() != GameState.SETUP) {
            sender.sendMessage(Component.text("Cannot set up: game state is "
                    + gameManager.getCurrentState() + ". Run /sot reset first.", NamedTextColor.RED));
            return;
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            sender.sendMessage(Component.text("No players online to assign to teams.", NamedTextColor.RED));
            return;
        }

        // Team definitions in a stable order (Red Rabbits .. Pink Parrots).
        TeamManager teamManager = gameManager.getTeamManager();
        List<TeamDefinition> definitions = teamManager.getAllTeamDefinitions().values().stream()
                .sorted(Comparator.comparing(def -> def.getId().toString()))
                .collect(Collectors.toList());

        // Number of teams: default 1 (works solo); optional arg spreads players across more.
        int requestedTeams = 1;
        if (args.length >= 2) {
            try {
                requestedTeams = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Usage: /sot setup [numTeams]", NamedTextColor.RED));
                return;
            }
        }
        int numTeams = Math.max(1, Math.min(requestedTeams, Math.min(players.size(), definitions.size())));

        // Round-robin assign online players to the first numTeams teams.
        List<UUID> teamIds = new ArrayList<>();
        for (int i = 0; i < numTeams; i++) {
            teamIds.add(definitions.get(i).getId());
        }
        for (int i = 0; i < players.size(); i++) {
            teamManager.assignPlayerToTeam(players.get(i), teamIds.get(i % numTeams));
        }

        if (!gameManager.setupGame(teamIds, players)) {
            // setupGame refuses on its own for a missing HUB template or an unconfigured location,
            // and used to do so silently behind an unconditional success message.
            if (!gameManager.getDungeonGenerator().hasHubTemplate()) {
                sender.sendMessage(Component.text("Setup failed: no HUB segment template loaded. Save "
                        + "one with /sotsavesegment <name> HUB, then /sotreloadsegments.", NamedTextColor.RED));
            } else {
                sender.sendMessage(Component.text("Setup failed. Check the console.", NamedTextColor.RED));
            }
            return;
        }
        sender.sendMessage(Component.text("Game set up with " + numTeams + " team(s) and "
                + players.size() + " player(s). Run ", NamedTextColor.GREEN)
                .append(Component.text("/sot start", NamedTextColor.YELLOW))
                .append(Component.text(" to begin.", NamedTextColor.GREEN)));
    }

    private void handleStart(@NotNull CommandSender sender) {
        if (!locationsConfigured(sender)) {
            return;
        }
        if (gameManager.getCurrentState() != GameState.SETUP) {
            sender.sendMessage(Component.text("Cannot start: run "
                    + (gameManager.getCurrentState() == GameState.ENDED ? "/sot reset" : "/sot setup")
                    + " first (state is " + gameManager.getCurrentState() + ").", NamedTextColor.RED));
            return;
        }
        gameManager.startGame();
        // startGame() hands off to the pre-game countdown, so COUNTDOWN is the success state here —
        // RUNNING only arrives once the countdown finishes. On failure it either leaves SETUP (no
        // HUB template, unconfigured locations) or lands on ENDED (generation failed).
        GameState after = gameManager.getCurrentState();
        if (after == GameState.COUNTDOWN || after == GameState.RUNNING) {
            sender.sendMessage(Component.text("Game started.", NamedTextColor.GREEN));
        } else if (!gameManager.getDungeonGenerator().hasHubTemplate()) {
            sender.sendMessage(Component.text("Start failed: no HUB segment template loaded. Save one "
                    + "with /sotsavesegment <name> HUB, then /sotreloadsegments.", NamedTextColor.RED));
        } else {
            sender.sendMessage(Component.text("Start failed (state is " + after + "). "
                    + "Check the console.", NamedTextColor.RED));
        }
    }

    private void handleEnd(@NotNull CommandSender sender) {
        GameState state = gameManager.getCurrentState();
        // COUNTDOWN counts as active: a round started by mistake must be abortable before it begins.
        if (state != GameState.RUNNING && state != GameState.PAUSED && state != GameState.COUNTDOWN) {
            sender.sendMessage(Component.text("No active game to end (state is " + state + ").", NamedTextColor.RED));
            return;
        }
        gameManager.endGame();
        sender.sendMessage(Component.text("Game ended. Run ", NamedTextColor.GREEN)
                .append(Component.text("/sot reset", NamedTextColor.YELLOW))
                .append(Component.text(" to play another round.", NamedTextColor.GREEN)));
    }

    /**
     * {@code /sot reset} — clears a finished round and returns the game to SETUP so another round
     * can be set up, without restarting the server. Refused while a round is still live.
     */
    private void handleReset(@NotNull CommandSender sender) {
        GameState state = gameManager.getCurrentState();
        if (!GameManager.canResetFrom(state)) {
            sender.sendMessage(Component.text("Cannot reset while a game is " + state
                    + ". Run /sot end first.", NamedTextColor.RED));
            return;
        }
        if (gameManager.resetGame()) {
            sender.sendMessage(Component.text("Game reset. Run ", NamedTextColor.GREEN)
                    .append(Component.text("/sot setup", NamedTextColor.YELLOW))
                    .append(Component.text(" to start another round.", NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(Component.text("Reset failed. Check the console.", NamedTextColor.RED));
        }
    }

    /**
     * {@code /sot set <lobby|trapped>} — stores the sender's current location, both in
     * {@code config.yml} and on the live {@link GameManager}, so it takes effect immediately.
     */
    private void handleSet(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only: /sot set uses your current location.",
                    NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /sot set <lobby|trapped>", NamedTextColor.RED));
            return;
        }

        String target = args[1].toLowerCase();
        Location location = player.getLocation().clone();
        String path;
        String label;

        switch (target) {
            case "lobby" -> {
                // startGame() derives the dungeon world and origin from the lobby, so moving it
                // mid-round would strand players and orphan the generated dungeons.
                // COUNTDOWN counts too: startGame has already derived the world and pasted every
                // dungeon from the lobby by the time the countdown is on screen.
                GameState state = gameManager.getCurrentState();
                if (state == GameState.RUNNING || state == GameState.PAUSED
                        || state == GameState.COUNTDOWN) {
                    sender.sendMessage(Component.text(
                            "Cannot move the lobby while a game is " + state + ". End it first.",
                            NamedTextColor.RED));
                    return;
                }
                gameManager.setLobbyLocation(location);
                path = SoTConfig.LOBBY_PATH;
                label = "Lobby anchor";
            }
            case "trapped" -> {
                // Only read at teleport time, so this is safe to change at any point.
                gameManager.setTrappedLocation(location);
                path = SoTConfig.TRAPPED_PATH;
                label = "Trapped location";
            }
            default -> {
                sender.sendMessage(Component.text("Unknown target '" + args[1] + "'. Use lobby or trapped.",
                        NamedTextColor.RED));
                return;
            }
        }

        SoTConfig.writeLocation(plugin.getConfig(), path, location);
        plugin.saveConfig();

        sender.sendMessage(Component.text(label + " set to " + SoTConfig.describe(location)
                + " and written to config.yml.", NamedTextColor.GREEN));
        if (!gameManager.areLocationsConfigured()) {
            sender.sendMessage(Component.text("Still unset: " + gameManager.getUnconfiguredLocationNames(),
                    NamedTextColor.YELLOW));
        }
    }

    /**
     * {@code /sot seed [<value>|random]} — reads or fixes the dungeon generation seed, both on the
     * live {@link GameManager} and in {@code config.yml}, so it survives a restart.
     *
     * <p>With no argument it reports the configured seed <i>and</i> the seed the last round actually
     * generated from, which is the point of the whole feature: an operator who just played a good
     * random dungeon can read the number off and pin it.
     */
    private void handleSeed(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            reportSeed(sender);
            return;
        }

        // The dungeon for a live round is already generated, so accepting a change now would be a
        // lie: it could not take effect until the next round. Mirrors the lobby guard above.
        GameState state = gameManager.getCurrentState();
        if (state == GameState.RUNNING || state == GameState.PAUSED || state == GameState.COUNTDOWN) {
            sender.sendMessage(Component.text("Cannot change the seed while a game is " + state
                    + " — this round's dungeon is already generated. End it first.", NamedTextColor.RED));
            return;
        }

        String raw = args[1];
        Long seed = raw.equalsIgnoreCase(SoTConfig.RANDOM_SEED_KEYWORD) || raw.equalsIgnoreCase("clear")
                ? null
                : SoTConfig.parseSeed(raw);

        gameManager.setDungeonSeed(seed);
        SoTConfig.writeSeed(plugin.getConfig(), SoTConfig.SEED_PATH, seed);
        plugin.saveConfig();

        if (seed == null) {
            sender.sendMessage(Component.text("Dungeon seed cleared: every round now rolls its own.",
                    NamedTextColor.GREEN));
            return;
        }
        Component message = Component.text("Dungeon seed set to " + seed, NamedTextColor.GREEN);
        if (!String.valueOf(seed).equals(raw.trim())) {
            // parseSeed hashed a non-numeric input; show what it became so it can be typed directly.
            message = message.append(Component.text(" (hashed from '" + raw + "')", NamedTextColor.GRAY));
        }
        sender.sendMessage(message.append(Component.text(" and written to config.yml. Every round will"
                + " now lay out identically.", NamedTextColor.GREEN)));
    }

    /** Reports the configured seed and the seed the most recent round generated from. */
    private void reportSeed(@NotNull CommandSender sender) {
        Long configured = gameManager.getConfiguredDungeonSeed();
        if (configured != null) {
            sender.sendMessage(Component.text("Dungeon seed: " + configured + " (fixed).", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Dungeon seed: random each round.", NamedTextColor.YELLOW));
        }

        Long lastUsed = gameManager.getRoundSeed();
        if (lastUsed == null) {
            sender.sendMessage(Component.text("No dungeon has been generated yet this session.",
                    NamedTextColor.GRAY));
        } else if (!lastUsed.equals(configured)) {
            sender.sendMessage(Component.text("Last generated from " + lastUsed + ". Run ", NamedTextColor.GRAY)
                    .append(Component.text("/sot seed " + lastUsed, NamedTextColor.YELLOW))
                    .append(Component.text(" to replay that layout.", NamedTextColor.GRAY)));
        }
    }

    /** Reports the missing locations and returns false when the game cannot run yet. */
    private boolean locationsConfigured(@NotNull CommandSender sender) {
        if (gameManager.areLocationsConfigured()) {
            return true;
        }
        sender.sendMessage(Component.text("Location(s) not configured: "
                + gameManager.getUnconfiguredLocationNames() + ".", NamedTextColor.RED));
        sender.sendMessage(Component.text("Stand where you want each one and run /sot set <lobby|trapped>.",
                NamedTextColor.GRAY));
        return false;
    }

    private void sendUsage(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /sot <setup|start|end|reset|set>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /sot setup [numTeams]  - assign online players to teams",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /sot start             - generate the dungeon and start timers",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /sot end               - force-end the current game", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /sot reset             - clear a finished round so another can be set up",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /sot set <lobby|trapped> - store your current location in config.yml",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /sot seed [<value>|random] - show or fix the dungeon generation seed",
                NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            String prefix = args[1].toLowerCase();
            return SET_TARGETS.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("seed")) {
            // Offer the last round's seed alongside "random", so replaying a layout is a keypress
            // rather than a trip to the console log.
            List<String> options = new ArrayList<>();
            options.add(SoTConfig.RANDOM_SEED_KEYWORD);
            Long lastUsed = gameManager.getRoundSeed();
            if (lastUsed != null) {
                options.add(String.valueOf(lastUsed));
            }
            String prefix = args[1].toLowerCase();
            return options.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        return List.of();
    }
}
