package com.clarkson.sot.events;

import com.clarkson.sot.commands.GiveCoinToolCommand; // To access PDC keys
import com.clarkson.sot.main.SoT;

// Adventure API Imports
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.logging.Level;

/**
 * Listens for interactions with SoT tools, like the Coin Placer Tool.
 */
public class ToolListener implements Listener {

    private final SoT plugin;

    // --- Constants for Custom Models (Should match CoinStack and PlaceCoinDisplayCommand) ---
    private static final int COIN_STACK_SMALL_MODEL_ID = 1001; // Example ID
    private static final int COIN_STACK_MEDIUM_MODEL_ID = 1002; // Example ID
    private static final int COIN_STACK_LARGE_MODEL_ID = 1003; // Example ID
    private static final Material COIN_BASE_MATERIAL = Material.GOLD_NUGGET; // Base item for model

    public ToolListener(SoT plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Check for right-click action
        if (!(event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = event.getItem();

        // Check if player is holding an item
        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            return;
        }

        // Check if the item has meta and the correct PDC tag for our tool
        ItemMeta meta = itemInHand.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(GiveCoinToolCommand.TOOL_TYPE_KEY, PersistentDataType.STRING)) {
            return;
        }

        String toolType = pdc.get(GiveCoinToolCommand.TOOL_TYPE_KEY, PersistentDataType.STRING);

        // --- Handle Coin Placer Tool ---
        if ("COIN_PLACER".equals(toolType)) {
            // Cancel the event to prevent default item usage (like Blaze Rod placing fire)
            event.setCancelled(true);

            // Check permission again (optional, but good practice)
            if (!player.hasPermission("sot.admin.placedisplay")) {
                 player.sendMessage(Component.text("You do not have permission to use this tool.", NamedTextColor.RED));
                 return;
            }

            // Get the value stored on the tool
            if (!pdc.has(GiveCoinToolCommand.TOOL_VALUE_KEY, PersistentDataType.INTEGER)) {
                player.sendMessage(Component.text("Error: This tool is missing its value data.", NamedTextColor.RED));
                return;
            }
            int baseValue = pdc.get(GiveCoinToolCommand.TOOL_VALUE_KEY, PersistentDataType.INTEGER);

            // --- Re-use logic from PlaceCoinDisplayCommand ---
            spawnCoinDisplayVisual(player, baseValue);
        }
        // --- Add handlers for other tool types here ---
        // else if ("OTHER_TOOL".equals(toolType)) { ... }
    }

    /**
     * Spawns the visual ItemDisplay for a coin stack.
     * Extracted from PlaceCoinDisplayCommand.
     * @param player Player using the tool.
     * @param baseValue The coin value determining the model.
     */
    private void spawnCoinDisplayVisual(Player player, int baseValue) {
        // Determine Model ID
        int modelIdToUse;
        if (baseValue >= 50) modelIdToUse = COIN_STACK_LARGE_MODEL_ID;
        else if (baseValue >= 20) modelIdToUse = COIN_STACK_MEDIUM_MODEL_ID;
        else modelIdToUse = COIN_STACK_SMALL_MODEL_ID;

        // Create ItemStack with CustomModelData
        ItemStack displayStack = new ItemStack(COIN_BASE_MATERIAL);
        ItemMeta meta = displayStack.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(modelIdToUse);
            displayStack.setItemMeta(meta);
        } else {
            player.sendMessage(Component.text("Error: Could not get ItemMeta for " + COIN_BASE_MATERIAL + ". Cannot apply custom model.", NamedTextColor.RED));
            return;
        }

        // Spawn Location (adjust as needed)
        Location targetBlockLoc = player.getTargetBlockExact(5) != null ? player.getTargetBlockExact(5).getLocation() : null;
        Location eyeLoc = player.getEyeLocation();
        Location spawnLocation;

        if (targetBlockLoc != null) {
             // Try placing on top of the target block if space allows
             Location above = targetBlockLoc.clone().add(0.5, 1.1, 0.5);
             if (above.getBlock().getType().isAir()) {
                 spawnLocation = above;
             } else {
                 // If block above is solid, place in front of the target block
                 spawnLocation = targetBlockLoc.clone().add(player.getFacing().getDirection()).add(0.5, 0.1, 0.5);
                 // Adjust Y slightly if needed based on block type
                 if (!spawnLocation.getBlock().getType().isAir()){
                    spawnLocation = eyeLoc.clone().add(player.getFacing().getDirection().multiply(1.5)); // Fallback further in front
                    spawnLocation.setY(Math.floor(spawnLocation.getY()) + 0.1); // Align Y to floor + offset
                 }
             }
        } else {
             // If no target block, place 1.5 blocks in front of player's eyes, aligned to floor
             spawnLocation = eyeLoc.clone().add(player.getFacing().getDirection().multiply(1.5));
             spawnLocation.setY(Math.floor(spawnLocation.getY()) + 0.1); // Align Y to floor + offset
        }
        spawnLocation.setPitch(0);
        spawnLocation.setYaw(player.getLocation().getYaw()); // Face same way as player


        // Spawn and Configure ItemDisplay
        try {
            player.getWorld().spawn(spawnLocation, ItemDisplay.class, display -> {
                display.setItemStack(displayStack);
                display.setGravity(false);
                // display.setPersistent(true); // Make persistent for building?
                display.setInvulnerable(true);
                display.setBillboard(Display.Billboard.CENTER);

                float scale = 1.5f; // Adjust scale as desired
                Transformation transformation = new Transformation(
                        new Vector3f(0f, 0f, 0f), new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(scale, scale, scale), new AxisAngle4f(0f, 0f, 0f, 1f)
                );
                display.setTransformation(transformation);

                // Add build marker tag
                display.getPersistentDataContainer().set(new NamespacedKey(plugin, "sot_build_marker"), PersistentDataType.BYTE, (byte)1);
                // display.customName(Component.text("Coin Display (Value: " + baseValue + ")", NamedTextColor.YELLOW));
                // display.setCustomNameVisible(false);
            });
            // Feedback can be simpler now, maybe action bar?
            player.sendActionBar(Component.text("Placed Coin Display (Value: " + baseValue + ")", NamedTextColor.GREEN));

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn ItemDisplay via tool", e);
            player.sendMessage(Component.text("An error occurred while spawning the display entity.", NamedTextColor.RED));
        }
    }
}
