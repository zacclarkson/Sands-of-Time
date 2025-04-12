package com.clarkson.sot.commands;
// Adventure API Imports
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * Command to give the player a Coin Display Placer Tool.
 * Usage: /sotgetcointool <value>
 * Gives an item configured to place coin displays of the specified value on right-click.
 */
public class GiveCoinToolCommand implements CommandExecutor {
    // Key to identify the tool itself
    public static final NamespacedKey TOOL_TYPE_KEY = new NamespacedKey("sot", "tool_type"); // Made public static
    // Key to store the value on the tool
    public static final NamespacedKey TOOL_VALUE_KEY = new NamespacedKey("sot", "tool_value"); // Made public static

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be run by a player.", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        // --- Permission Check ---
        if (!player.hasPermission("sot.admin.givetool")) { // Changed permission node
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        // --- Argument Parsing ---
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /" + label + " <value>", NamedTextColor.RED));
            player.sendMessage(Component.text("Gives a tool to place coin displays with the specified value.", NamedTextColor.GRAY));
            return true;
        }

        int valueToSet;
        try {
            valueToSet = Integer.parseInt(args[0]);
            if (valueToSet <= 0) {
                player.sendMessage(Component.text("Value must be a positive integer.", NamedTextColor.RED));
                return true;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid number provided for value: " + args[0], NamedTextColor.RED));
            return true;
        }

        // --- Create Tool ItemStack ---
        ItemStack coinTool = new ItemStack(Material.BLAZE_ROD); // Or another item like STICK
        ItemMeta meta = coinTool.getItemMeta();

        if (meta != null) {
            // Set Name (Using MiniMessage for easy formatting)
            meta.displayName(MiniMessage.miniMessage().deserialize("<gradient:gold:yellow><bold>Coin Placer Tool</bold></gradient>"));

            // Set Lore
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Value: ", NamedTextColor.GRAY).append(Component.text(valueToSet, NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Right-click to place a visual", NamedTextColor.YELLOW));
            lore.add(Component.text("coin display for segment design.", NamedTextColor.YELLOW));
            meta.lore(lore);

            // Set Persistent Data
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(TOOL_TYPE_KEY, PersistentDataType.STRING, "COIN_PLACER"); // Identify the tool type
            pdc.set(TOOL_VALUE_KEY, PersistentDataType.INTEGER, valueToSet); // Store the value

            coinTool.setItemMeta(meta);
        } else {
             player.sendMessage(Component.text("Error: Could not get ItemMeta for tool. Cannot create tool.", NamedTextColor.RED));
             return true;
        }

        // --- Give Tool to Player ---
        player.getInventory().addItem(coinTool);
        player.sendMessage(
            Component.text("Received Coin Placer Tool (Value: ", NamedTextColor.GREEN)
                .append(Component.text(valueToSet, NamedTextColor.WHITE))
                .append(Component.text(").", NamedTextColor.GREEN))
        );

        return true;
    }
}
