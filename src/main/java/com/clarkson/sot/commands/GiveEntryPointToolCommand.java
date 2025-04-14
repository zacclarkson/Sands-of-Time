package com.clarkson.sot.commands;

import com.clarkson.sot.main.SoT;
// Removed Direction import as it's no longer an argument

// Adventure API Imports
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to give the player a generic Entry Point Placer Tool.
 * Usage: /sotgetentrytool
 * Gives an item used to place and rotate entry point markers.
 */
public class GiveEntryPointToolCommand implements CommandExecutor {

    private final SoT plugin;
    // Key to identify the tool type
    public static final NamespacedKey TOOL_TYPE_KEY = new NamespacedKey("sot", "tool_type");
    // Direction key is no longer needed ON THE TOOL ITSELF

    public GiveEntryPointToolCommand(SoT plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be run by a player.", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        // --- Permission Check ---
        if (!player.hasPermission("sot.admin.givetool")) {
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        // --- Argument Parsing (No arguments needed now) ---
        if (args.length > 0) {
             player.sendMessage(Component.text("Usage: /" + label, NamedTextColor.RED));
             return true;
        }


        // --- Create Tool ItemStack ---
        ItemStack entryTool = new ItemStack(Material.ARROW); // Still using an arrow
        ItemMeta meta = entryTool.getItemMeta();

        if (meta != null) {
            // Set Name
            meta.displayName(MiniMessage.miniMessage().deserialize("<gradient:light_purple:dark_purple><bold>Entry Point Placer</bold></gradient>"));

            // Set Lore
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Right-click block face to place.", NamedTextColor.LIGHT_PURPLE));
            lore.add(Component.text("Right-click existing marker to rotate.", NamedTextColor.LIGHT_PURPLE));
            lore.add(Component.text("Left-click marker to remove.", NamedTextColor.YELLOW)); // Add reminder for removal
            meta.lore(lore);

            // Set Persistent Data
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(TOOL_TYPE_KEY, PersistentDataType.STRING, "ENTRY_POINT_PLACER"); // Tool type identifier

            entryTool.setItemMeta(meta);
        } else {
             player.sendMessage(Component.text("Error: Could not get ItemMeta for tool.", NamedTextColor.RED));
             return true;
        }

        // --- Give Tool to Player ---
        player.getInventory().addItem(entryTool);
        player.sendMessage(Component.text("Received Entry Point Placer tool.", NamedTextColor.GREEN));

        return true;
    }
}
