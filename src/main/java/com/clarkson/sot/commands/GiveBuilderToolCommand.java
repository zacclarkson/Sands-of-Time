package com.clarkson.sot.commands;

import com.clarkson.sot.main.SoT;
import com.clarkson.sot.utils.SegmentBuilderKeys;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * /sotbuilder — gives the player the master segment builder tool.
 * Mode is switched separately via /sotmode.
 */
public class GiveBuilderToolCommand implements CommandExecutor {

    private static final String TOOL_ID = "SEGMENT_BUILDER";

    private final SoT plugin;
    private final SegmentBuilderKeys keys;

    public GiveBuilderToolCommand(SoT plugin, SegmentBuilderKeys keys) {
        this.plugin = plugin;
        this.keys = keys;
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

        ItemStack tool = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = tool.getItemMeta();
        meta.displayName(Component.text("Segment Builder", NamedTextColor.GOLD));
        meta.lore(List.of(
            Component.text("Mode: ENTRY_POINT", NamedTextColor.YELLOW),
            Component.text("Right-click to place  |  Left-click to remove", NamedTextColor.GRAY),
            Component.text("Use /sotmode <mode> to switch", NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(keys.TOOL_TYPE, PersistentDataType.STRING, TOOL_ID);
        tool.setItemMeta(meta);

        player.getInventory().addItem(tool);
        player.sendMessage(Component.text("Segment Builder tool given. Use ", NamedTextColor.GREEN)
            .append(Component.text("/sotmode <mode>", NamedTextColor.YELLOW))
            .append(Component.text(" to switch modes.", NamedTextColor.GREEN)));
        return true;
    }
}
