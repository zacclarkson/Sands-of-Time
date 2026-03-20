package com.clarkson.sot.commands;

import com.clarkson.sot.events.BuilderMode;
import com.clarkson.sot.events.SegmentBuilderKeys;
import com.clarkson.sot.main.SoT;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /sotbuilder — gives the player the single master segment builder tool (BLAZE_ROD).
 * Modes are switched via /sotmode <mode> [args].
 */
public class GiveBuilderToolCommand implements CommandExecutor {

    private final SoT plugin;
    private final NamespacedKey toolTypeKey;

    public GiveBuilderToolCommand(@NotNull SoT plugin) {
        this.plugin = plugin;
        this.toolTypeKey = new NamespacedKey(plugin, SegmentBuilderKeys.TOOL_TYPE);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission("sot.admin.builder")) {
            sender.sendMessage(Component.text("You don't have permission to use this.", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        ItemStack tool = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = tool.getItemMeta();
        if (meta == null) {
            sender.sendMessage(Component.text("Error creating builder tool.", NamedTextColor.RED));
            return true;
        }

        meta.displayName(Component.text("Segment Builder", NamedTextColor.GOLD));

        List<Component> lore = Arrays.stream(BuilderMode.values())
                .map(m -> Component.text("  " + m.name(), NamedTextColor.GRAY))
                .collect(Collectors.toList());
        lore.add(0, Component.text("Switch mode: /sotmode <mode>", NamedTextColor.YELLOW));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(
                toolTypeKey, PersistentDataType.STRING, SegmentBuilderKeys.TOOL_TYPE_VALUE);

        tool.setItemMeta(meta);
        player.getInventory().addItem(tool);
        player.sendMessage(Component.text("Segment builder tool given. Use ", NamedTextColor.GREEN)
                .append(Component.text("/sotmode <mode>", NamedTextColor.YELLOW))
                .append(Component.text(" to switch modes.", NamedTextColor.GREEN)));
        return true;
    }
}
