package com.clarkson.sot.commands;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.utils.TeamDefinition;
import com.clarkson.sot.utils.TeamManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /sot &lt;setup|start|end&gt; — minimal admin control for a Sands of Time round.
 *
 * <p>This wires the existing {@link GameManager} lifecycle ({@code setupGame}/{@code startGame}/
 * {@code endGame}) to an in-game command so an operator can actually reach a running game on a
 * test/dev server. It is intentionally small: {@code setup} assigns all online players to teams
 * (round-robin), {@code start} generates the dungeon and starts timers, {@code end} tears the
 * round down.
 *
 * <p>Requires at least one {@code HUB} segment template on disk for {@code start} to succeed
 * (build one with the segment tools and {@code /sotsavesegment <name> HUB}).
 */
public class GameCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "sot.admin.control";
    private static final List<String> SUBCOMMANDS = List.of("setup", "start", "end");

    private final GameManager gameManager;

    public GameCommand(@NotNull GameManager gameManager) {
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
            default      -> sendUsage(sender);
        }
        return true;
    }

    private void handleSetup(@NotNull CommandSender sender, @NotNull String[] args) {
        if (gameManager.getCurrentState() != GameState.SETUP) {
            sender.sendMessage(Component.text("Cannot set up: game state is "
                    + gameManager.getCurrentState() + ". Reload the plugin/server to reset.", NamedTextColor.RED));
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

        gameManager.setupGame(teamIds, players);
        sender.sendMessage(Component.text("Game set up with " + numTeams + " team(s) and "
                + players.size() + " player(s). Run ", NamedTextColor.GREEN)
                .append(Component.text("/sot start", NamedTextColor.YELLOW))
                .append(Component.text(" to begin.", NamedTextColor.GREEN)));
    }

    private void handleStart(@NotNull CommandSender sender) {
        if (gameManager.getCurrentState() != GameState.SETUP) {
            sender.sendMessage(Component.text("Cannot start: run /sot setup first (state is "
                    + gameManager.getCurrentState() + ").", NamedTextColor.RED));
            return;
        }
        gameManager.startGame();
        // startGame() sets state to RUNNING on success, or ENDED if generation failed.
        if (gameManager.getCurrentState() == GameState.RUNNING) {
            sender.sendMessage(Component.text("Game started.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Start failed (likely no HUB segment template loaded). "
                    + "Check the console.", NamedTextColor.RED));
        }
    }

    private void handleEnd(@NotNull CommandSender sender) {
        GameState state = gameManager.getCurrentState();
        if (state != GameState.RUNNING && state != GameState.PAUSED) {
            sender.sendMessage(Component.text("No active game to end (state is " + state + ").", NamedTextColor.RED));
            return;
        }
        gameManager.endGame();
        sender.sendMessage(Component.text("Game ended.", NamedTextColor.GREEN));
    }

    private void sendUsage(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /sot <setup|start|end>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /sot setup [numTeams]  - assign online players to teams",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /sot start             - generate the dungeon and start timers",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /sot end               - force-end the current game", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        return List.of();
    }
}
