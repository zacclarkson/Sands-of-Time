package com.clarkson.sot.commands;

import com.clarkson.sot.main.SoT; // Your main plugin class

// Adventure API Imports
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage; // For easier formatting

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
 * Command to give the player an Item Spawn Placer Tool.
 * Usage: /sotgetitemtool
 * Gives an item used to place item spawn markers (Torch + hidden Armor Stand) on right-click.
 */
public class GiveItemToolCommand implements CommandExecutor {

    private final SoT plugin;
    // Use the same TOOL_TYPE_KEY, just a different value
    // Ensure GiveCoinToolCommand.TOOL_TYPE_KEY is accessible (e.g., public static or move to shared class)
    // Assuming it's public static in GiveCoinToolCommand for this example:
    // private final NamespacedKey TOOL_TYPE_KEY = GiveCoinToolCommand.TOOL_TYPE_KEY;

    // Or redefine if needed (make sure key name is identical)
     public static final NamespacedKey TOOL_TYPE_KEY = new NamespacedKey("sot", "tool_type");


    public GiveItemToolCommand(SoT plugin) {
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
        if (!player.hasPermission("sot.admin.givetool")) { // Can use the same permission or a new one
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        // --- Create Tool ItemStack ---
        ItemStack itemTool = new ItemStack(Material.IRON_SHOVEL); // Using a shovel as an example
        ItemMeta meta = itemTool.getItemMeta();

        if (meta != null) {
            // Set Name
            meta.displayName(MiniMessage.miniMessage().deserialize("<gradient:aqua:blue><bold>Item Spawn Placer</bold></gradient>"));

            // Set Lore
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Right-click block to place an", NamedTextColor.AQUA));
            lore.add(Component.text("item spawn marker (Torch).", NamedTextColor.AQUA));
            meta.lore(lore);

            // Set Persistent Data
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(TOOL_TYPE_KEY, PersistentDataType.STRING, "ITEM_SPAWN_PLACER"); // Identify the tool type

            itemTool.setItemMeta(meta);
        } else {
             player.sendMessage(Component.text("Error: Could not get ItemMeta for tool. Cannot create tool.", NamedTextColor.RED));
             return true;
        }

        // --- Give Tool to Player ---
        player.getInventory().addItem(itemTool);
        player.sendMessage(Component.text("Received Item Spawn Placer tool.", NamedTextColor.GREEN));

        return true;
    }
}
