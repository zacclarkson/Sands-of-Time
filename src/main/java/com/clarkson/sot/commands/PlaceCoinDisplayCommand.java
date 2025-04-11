package com.clarkson.sot.commands;

import com.clarkson.sot.main.SoT; // Your main plugin class

// Adventure API Imports
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.logging.Level;
import org.jetbrains.annotations.NotNull; // Standard annotation

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display; // Import Display for Billboard
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation; // For scaling/rotating display
import org.joml.AxisAngle4f; // For rotation
import org.joml.Vector3f; // For translation/scale

/**
 * Command to place a visual-only ItemDisplay representing a CoinStack for segment design.
 * Usage: /sotplacecoindisplay <value>
 * Spawns an ItemDisplay with CustomModelData based on the value at the player's location.
 * This entity is purely visual and has no game logic attached during building.
 */
public class PlaceCoinDisplayCommand implements CommandExecutor {

    private final SoT plugin;

    // --- Constants for Custom Models (Mirrored from CoinStack or a shared Constants class) ---
    private static final int COIN_STACK_SMALL_MODEL_ID = 1001; // Example ID
    private static final int COIN_STACK_MEDIUM_MODEL_ID = 1002; // Example ID
    private static final int COIN_STACK_LARGE_MODEL_ID = 1003; // Example ID
    private static final Material COIN_BASE_MATERIAL = Material.GOLD_NUGGET; // Base item for model

    public PlaceCoinDisplayCommand(SoT plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            // Use Adventure Component for messages
            sender.sendMessage(Component.text("This command can only be run by a player.", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        // --- Permission Check ---
        if (!player.hasPermission("sot.admin.placedisplay")) {
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        // --- Argument Parsing ---
        if (args.length != 1) {
            // Build multi-line messages with Adventure
            player.sendMessage(Component.text("Usage: /" + label + " <value>", NamedTextColor.RED));
            player.sendMessage(Component.text("Example: /" + label + " 25", NamedTextColor.GRAY));
            return true;
        }

        int baseValue;
        try {
            baseValue = Integer.parseInt(args[0]);
            if (baseValue <= 0) {
                player.sendMessage(Component.text("Value must be a positive integer.", NamedTextColor.RED));
                return true;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid number provided for value: " + args[0], NamedTextColor.RED));
            return true;
        }

        // --- Determine Model ID ---
        int modelIdToUse;
        if (baseValue >= 50) {
            modelIdToUse = COIN_STACK_LARGE_MODEL_ID;
        } else if (baseValue >= 20) {
            modelIdToUse = COIN_STACK_MEDIUM_MODEL_ID;
        } else {
            modelIdToUse = COIN_STACK_SMALL_MODEL_ID;
        }

        // --- Create ItemStack with CustomModelData ---
        ItemStack displayStack = new ItemStack(COIN_BASE_MATERIAL);
        ItemMeta meta = displayStack.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(modelIdToUse);
            displayStack.setItemMeta(meta);
        } else {
            player.sendMessage(Component.text("Error: Could not get ItemMeta for " + COIN_BASE_MATERIAL + ". Cannot apply custom model.", NamedTextColor.RED));
            return true;
        }

        // --- Spawn Location ---
        Location spawnLocation = player.getTargetBlockExact(5) != null
            ? player.getTargetBlockExact(5).getLocation().add(0.5, 1.1, 0.5)
            : player.getLocation().add(0, 0.1, 0);
        spawnLocation.setPitch(0);
        spawnLocation.setYaw(player.getLocation().getYaw());


        // --- Spawn and Configure ItemDisplay ---
        try {
            player.getWorld().spawn(spawnLocation, ItemDisplay.class, display -> {
                display.setItemStack(displayStack);
                display.setGravity(false);
                // display.setPersistent(true); // Consider if needed for build markers
                display.setInvulnerable(true);
                display.setBillboard(Display.Billboard.CENTER);

                float scale = 1.5f;
                Transformation transformation = new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(scale, scale, scale),
                        new AxisAngle4f(0f, 0f, 0f, 1f)
                );
                display.setTransformation(transformation);

                // Add PDC tag
                 display.getPersistentDataContainer().set(new NamespacedKey(plugin, "sot_build_marker"), PersistentDataType.BYTE, (byte)1);

                 // Set custom name using Adventure Component
                 display.customName(Component.text("Coin Display (Value: " + baseValue + ")", NamedTextColor.YELLOW));
                 display.setCustomNameVisible(false);

            });
            // Send success message using Adventure Component
            player.sendMessage(
                Component.text("Spawned coin display (Value: ", NamedTextColor.GREEN)
                    .append(Component.text(baseValue, NamedTextColor.WHITE))
                    .append(Component.text(", ModelID: ", NamedTextColor.GREEN))
                    .append(Component.text(modelIdToUse, NamedTextColor.WHITE))
                    .append(Component.text(") at your location.", NamedTextColor.GREEN))
            );
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn ItemDisplay via command", e);
            player.sendMessage(Component.text("An error occurred while spawning the display entity.", NamedTextColor.RED));
        }

        return true;
    }
}
