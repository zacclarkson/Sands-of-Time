package com.clarkson.sot.commands;

import com.clarkson.sot.main.SoT;
import com.clarkson.sot.utils.BuilderSessionManager;
import com.clarkson.sot.utils.PlayerBuilderSession;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /sotmode <mode> — switches the segment builder tool's current placement mode.
 */
public class SetBuilderModeCommand implements CommandExecutor, TabCompleter {

    private final SoT plugin;
    private final BuilderSessionManager sessionManager;

    public SetBuilderModeCommand(SoT plugin, BuilderSessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be run by a player.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("sot.builder.tool")) {
            player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            sendModeList(player);
            return true;
        }

        BuilderMode mode;
        try {
            mode = BuilderMode.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Unknown mode: " + args[0], NamedTextColor.RED));
            sendModeList(player);
            return true;
        }

        PlayerBuilderSession session = sessionManager.getSession(player.getUniqueId());
        session.setMode(mode);

        player.sendMessage(Component.text("Builder mode: ", NamedTextColor.GRAY)
            .append(Component.text(mode.getDisplayName(), NamedTextColor.YELLOW)));
        player.sendActionBar(Component.text("[" + mode.getDisplayName() + "] " + mode.getHint(), NamedTextColor.YELLOW));
        return true;
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toUpperCase();
            return Arrays.stream(BuilderMode.values())
                .map(Enum::name)
                .filter(n -> n.startsWith(partial))
                .collect(Collectors.toList());
        }
        return List.of();
    }

    private void sendModeList(Player player) {
        player.sendMessage(Component.text("Available modes:", NamedTextColor.GRAY));
        for (BuilderMode m : BuilderMode.values()) {
            player.sendMessage(Component.text("  " + m.name(), NamedTextColor.YELLOW)
                .append(Component.text(" — " + m.getHint(), NamedTextColor.DARK_GRAY)));
        }
    }
}
