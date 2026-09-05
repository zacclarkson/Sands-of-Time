package com.clarkson.sot.commands;

import com.clarkson.sot.dungeon.VaultColor;
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.events.BuilderMode;
import com.clarkson.sot.events.BuilderSessionManager;
import com.clarkson.sot.events.PlayerBuilderSession;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /sotmode <mode> [color|value|cost]
 * Switches the builder tool mode for the calling player.
 *
 * Examples:
 *   /sotmode ENTRY_POINT
 *   /sotmode VAULT_DOOR BLUE
 *   /sotmode KEY_SPAWN GOLD
 *   /sotmode COIN_SPAWN 50
 *   /sotmode SAND_SACRIFICE 3   (sand price of a gate sacrifice chest; ignored on a HUB)
 */
public class SetBuilderModeCommand implements CommandExecutor, TabCompleter {

    private final BuilderSessionManager sessionManager;

    // Modes that require a VaultColor second argument.
    private static final List<BuilderMode> COLOR_MODES = Arrays.asList(
            BuilderMode.VAULT_DOOR, BuilderMode.VAULT_MARKER, BuilderMode.KEY_SPAWN);

    public SetBuilderModeCommand(@NotNull BuilderSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission("sot.admin.builder")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            sendUsage((Player) sender, label);
            return true;
        }

        Player player = (Player) sender;
        PlayerBuilderSession session = sessionManager.getSession(player);

        // --- Parse mode ---
        BuilderMode newMode;
        try {
            newMode = BuilderMode.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Unknown mode: " + args[0], NamedTextColor.RED));
            sendUsage(player, label);
            return true;
        }

        // --- If switching away from a bound mode that has a pending corner, cancel it ---
        if (session.getMode().isBoundMode() && session.getPendingBoundCorner() != null) {
            // Remove the temp first-corner entity if it still exists
            if (session.getPendingCornerEntityId() != null) {
                player.getWorld().getEntities().stream()
                        .filter(e -> e.getUniqueId().equals(session.getPendingCornerEntityId()))
                        .forEach(Entity::remove);
            }
            session.clearPendingBound();
            player.sendMessage(Component.text("Pending bound selection cancelled.", NamedTextColor.YELLOW));
        }

        // --- Parse optional second argument (VaultColor or coin value) ---
        VaultColor newColor = null;
        if (COLOR_MODES.contains(newMode)) {
            if (args.length < 2) {
                player.sendMessage(Component.text(newMode.getDisplayName()
                        + " requires a color: BLUE, RED, GREEN, GOLD", NamedTextColor.RED));
                return true;
            }
            try {
                newColor = VaultColor.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage(Component.text("Invalid color: " + args[1]
                        + ". Use BLUE, RED, GREEN, or GOLD.", NamedTextColor.RED));
                return true;
            }
        }

        int newCoinValue = session.getCoinValue();
        if (newMode == BuilderMode.COIN_SPAWN && args.length >= 2) {
            try {
                newCoinValue = Integer.parseInt(args[1]);
                if (newCoinValue < 1) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Invalid coin value: " + args[1]
                        + ". Must be a positive integer.", NamedTextColor.RED));
                return true;
            }
        }

        int newSacrificeCost = session.getSacrificeCost();
        if (newMode == BuilderMode.SAND_SACRIFICE && args.length >= 2) {
            try {
                newSacrificeCost = Integer.parseInt(args[1]);
                if (newSacrificeCost < 1 || newSacrificeCost > Segment.MAX_SACRIFICE_COST) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Invalid sacrifice cost: " + args[1]
                        + ". Must be 1-" + Segment.MAX_SACRIFICE_COST + " sand.", NamedTextColor.RED));
                return true;
            }
        }

        // --- Apply to session ---
        session.setMode(newMode);
        session.setVaultColor(newColor);
        session.setCoinValue(newCoinValue);
        session.setSacrificeCost(newSacrificeCost);

        // --- Feedback ---
        Component msg = Component.text("Mode: ", NamedTextColor.GREEN)
                .append(Component.text(newMode.getDisplayName(), NamedTextColor.YELLOW));
        if (newColor != null) {
            msg = msg.append(Component.text(" (" + newColor.name() + ")", NamedTextColor.AQUA));
        }
        if (newMode == BuilderMode.COIN_SPAWN) {
            msg = msg.append(Component.text(" value=" + newCoinValue, NamedTextColor.GOLD));
        }
        if (newMode == BuilderMode.SAND_SACRIFICE) {
            msg = msg.append(Component.text(" cost=" + newSacrificeCost + " sand", NamedTextColor.GOLD));
        }
        player.sendActionBar(msg);
        player.sendMessage(msg);
        player.sendMessage(Component.text(newMode.getHint(), NamedTextColor.GRAY));
        return true;
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.stream(BuilderMode.values())
                    .map(BuilderMode::name)
                    .filter(n -> n.startsWith(args[0].toUpperCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            BuilderMode mode = null;
            try { mode = BuilderMode.valueOf(args[0].toUpperCase()); } catch (IllegalArgumentException ignored) {}
            if (mode != null && COLOR_MODES.contains(mode)) {
                return Arrays.stream(VaultColor.values())
                        .map(Enum::name)
                        .filter(n -> n.startsWith(args[1].toUpperCase()))
                        .collect(Collectors.toList());
            }
            if (mode == BuilderMode.COIN_SPAWN) {
                return List.of("1", "5", "10", "25", "50", "100");
            }
            if (mode == BuilderMode.SAND_SACRIFICE) {
                return List.of("1", "2", "3", "4", "5", "10");
            }
        }
        return new ArrayList<>();
    }

    private void sendUsage(Player player, String label) {
        player.sendMessage(Component.text("Usage: /" + label + " <mode> [color|value|cost]", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Modes: " +
                Arrays.stream(BuilderMode.values()).map(BuilderMode::name)
                        .collect(Collectors.joining(", ")), NamedTextColor.GRAY));
    }
}
