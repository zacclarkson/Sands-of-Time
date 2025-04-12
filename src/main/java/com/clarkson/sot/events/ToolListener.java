package com.clarkson.sot.events;

// Use consistent key definitions - ideally move these to a shared Constants class
import com.clarkson.sot.main.SoT;

// Adventure API Imports
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity; // Import base Entity class
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult; // Import for entity ray tracing
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.logging.Level;
import java.util.function.Predicate; // For ray trace filter

/**
 * Listens for interactions with SoT tools.
 * Right-click places markers (Coin Display or Item Spawn).
 * Left-click removes markers the player is looking at.
 */
public class ToolListener implements Listener {

    private final SoT plugin;

    // --- Constants for Coin Models ---
    private static final int COIN_STACK_SMALL_MODEL_ID = 1001;
    private static final int COIN_STACK_MEDIUM_MODEL_ID = 1002;
    private static final int COIN_STACK_LARGE_MODEL_ID = 1003;
    private static final Material COIN_BASE_MATERIAL = Material.GOLD_NUGGET;
    private static final Material ITEM_SPAWN_MARKER_ITEM_MATERIAL = Material.TORCH;

    // --- PDC Keys (Define consistently) ---
    private final NamespacedKey TOOL_TYPE_KEY;
    private final NamespacedKey TOOL_VALUE_KEY; // Used by coin tool
    private final NamespacedKey MARKER_TYPE_KEY; // Used for the marker entity (ItemDisplay or ArmorStand)
    private final NamespacedKey DIRECTION_KEY; // Used for entry point marker entity
    private final NamespacedKey VAULT_COLOR_KEY; // Used for vault/key marker entity
    private final NamespacedKey BUILD_MARKER_TAG; // Tag identifying ANY build-phase marker entity

    public ToolListener(SoT plugin) {
        this.plugin = plugin;
        // Initialize keys
        TOOL_TYPE_KEY = new NamespacedKey(plugin, "sot_tool_type");
        TOOL_VALUE_KEY = new NamespacedKey(plugin, "sot_tool_value");
        MARKER_TYPE_KEY = new NamespacedKey(plugin, "sot_marker_type");
        DIRECTION_KEY = new NamespacedKey(plugin, "sot_direction");
        VAULT_COLOR_KEY = new NamespacedKey(plugin, "sot_vault_color");
        BUILD_MARKER_TAG = new NamespacedKey(plugin, "sot_build_marker"); // Key for the general build marker tag
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // Only handle main hand

        Player player = event.getPlayer();
        ItemStack itemInHand = event.getItem();

        if (itemInHand == null || itemInHand.getType() == Material.AIR) return;
        ItemMeta meta = itemInHand.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(TOOL_TYPE_KEY, PersistentDataType.STRING)) return; // Not one of our tools

        String toolType = pdc.get(TOOL_TYPE_KEY, PersistentDataType.STRING);

        // --- Handle Right-Click (Placement) ---
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if ("COIN_PLACER".equals(toolType)) {
                handleCoinPlacerTool(event, player, pdc);
            } else if ("ITEM_SPAWN_PLACER".equals(toolType)) {
                handleItemSpawnPlacerTool(event, player);
            }
            // Add other right-click tool handlers here
        }
        // --- Handle Left-Click (Removal) ---
        else if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Check if the tool held is *any* of our placement tools
            if ("COIN_PLACER".equals(toolType) || "ITEM_SPAWN_PLACER".equals(toolType)) {
                 // Add other tool types here if they should also remove markers
                 handleMarkerRemoval(event, player);
            }
        }
    }

    /** Handles logic for the Coin Placer tool (Right-Click) */
    private void handleCoinPlacerTool(PlayerInteractEvent event, Player player, PersistentDataContainer toolPdc) {
        event.setCancelled(true);
        if (!player.hasPermission("sot.admin.placedisplay")) { /* ... perm msg ... */ return; }
        if (!toolPdc.has(TOOL_VALUE_KEY, PersistentDataType.INTEGER)) { /* ... missing value msg ... */ return; }
        int baseValue = toolPdc.get(TOOL_VALUE_KEY, PersistentDataType.INTEGER);
        spawnCoinDisplayVisual(player, baseValue, event.getClickedBlock(), event.getBlockFace());
    }

    /** Handles logic for the Item Spawn Placer tool (Right-Click) */
    private void handleItemSpawnPlacerTool(PlayerInteractEvent event, Player player) {
        event.setCancelled(true);
        if (!player.hasPermission("sot.admin.placeitemspawn")) { /* ... perm msg ... */ return; }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getBlockFace() == null) {
             player.sendActionBar(Component.text("Right-click on the top face of a block to place marker.", NamedTextColor.YELLOW));
             return;
        }
        if (event.getBlockFace() != BlockFace.UP) {
            player.sendActionBar(Component.text("Please click on the top face of a block.", NamedTextColor.YELLOW));
            return;
        }
        placeItemSpawnMarker(player, event.getClickedBlock(), event.getBlockFace());
    }

    /** Handles logic for removing markers (Left-Click) */
    private void handleMarkerRemoval(PlayerInteractEvent event, Player player) {
        event.setCancelled(true); // Prevent block breaking / default left-click actions

        // Check permission for removing markers
        if (!player.hasPermission("sot.admin.removemarker")) { // Example permission
             player.sendActionBar(Component.text("You don't have permission to remove markers.", NamedTextColor.RED));
             return;
        }

        // Ray trace to find the entity the player is looking at
        double range = 6.0; // How far to check for markers
        // Create a filter to only hit entities that have our build marker tag
        Predicate<Entity> filter = entity -> {
            // Check if it's an ItemDisplay or ArmorStand (or other marker types you use)
            // AND check if it has the BUILD_MARKER_TAG
            return (entity instanceof ItemDisplay || entity instanceof org.bukkit.entity.ArmorStand) // Check relevant types
                   && entity.getPersistentDataContainer().has(BUILD_MARKER_TAG, PersistentDataType.BYTE);
        };

        RayTraceResult result = player.getWorld().rayTraceEntities(
            player.getEyeLocation(),
            player.getEyeLocation().getDirection(),
            range,
            filter // Apply the filter
        );

        if (result != null && result.getHitEntity() != null) {
            Entity hitEntity = result.getHitEntity();
            // Double-check the tag just in case the filter wasn't perfect (optional)
            if (hitEntity.getPersistentDataContainer().has(BUILD_MARKER_TAG, PersistentDataType.BYTE)) {
                String markerType = hitEntity.getPersistentDataContainer().getOrDefault(MARKER_TYPE_KEY, PersistentDataType.STRING, "Unknown");
                hitEntity.remove(); // Remove the marker entity
                player.sendActionBar(Component.text("Removed " + markerType + " marker.", NamedTextColor.YELLOW));
            } else {
                 // Should not happen if filter works correctly
                 player.sendActionBar(Component.text("Not a removable marker.", NamedTextColor.RED));
            }
        } else {
            player.sendActionBar(Component.text("No marker found in sight.", NamedTextColor.GRAY));
        }
    }


    /** Spawns the visual ItemDisplay for a coin stack. */
    private void spawnCoinDisplayVisual(Player player, int baseValue, Block clickedBlock, BlockFace clickedFace) {
        // ... (Implementation remains the same) ...
        int modelIdToUse;
        if (baseValue >= 50) modelIdToUse = COIN_STACK_LARGE_MODEL_ID;
        else if (baseValue >= 20) modelIdToUse = COIN_STACK_MEDIUM_MODEL_ID;
        else modelIdToUse = COIN_STACK_SMALL_MODEL_ID;

        ItemStack displayStack = new ItemStack(COIN_BASE_MATERIAL);
        ItemMeta meta = displayStack.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(modelIdToUse);
            displayStack.setItemMeta(meta);
        } else { player.sendMessage(Component.text("Error: Could not get ItemMeta for Coin Display.", NamedTextColor.RED)); return; }

        Location spawnLocation = calculatePlacementLocation(player, clickedBlock, clickedFace, 0.1);

        try {
            player.getWorld().spawn(spawnLocation, ItemDisplay.class, display -> {
                display.setItemStack(displayStack);
                display.setGravity(false);
                display.setInvulnerable(true);
                display.setBillboard(Display.Billboard.CENTER);
                float scale = 1.5f;
                Transformation transformation = new Transformation(
                        new Vector3f(0f, 0f, 0f), new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(scale, scale, scale), new AxisAngle4f(0f, 0f, 0f, 1f)
                );
                display.setTransformation(transformation);
                // Add the general build marker tag
                display.getPersistentDataContainer().set(BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte)1);
                // Also add the specific type tag if needed for removal feedback
                display.getPersistentDataContainer().set(MARKER_TYPE_KEY, PersistentDataType.STRING, "DISPLAY_COIN"); // Added this line
            });
            player.sendActionBar(Component.text("Placed Coin Display (Value: " + baseValue + ")", NamedTextColor.GREEN));
        } catch (Exception e) { /* ... error handling ... */ }
    }

    /** Places an ItemDisplay showing a flat torch item, tagged as an item spawn marker. */
    private void placeItemSpawnMarker(Player player, Block clickedBlock, BlockFace clickedFace) {
        // ... (Torch block placement removed) ...
        Block blockToPlaceOn = clickedBlock;
        Location spawnLocation = blockToPlaceOn.getLocation().add(0.5, 1.02, 0.5);
        spawnLocation.setYaw(player.getLocation().getYaw());
        spawnLocation.setPitch(0);

        try {
            ItemStack torchItem = new ItemStack(ITEM_SPAWN_MARKER_ITEM_MATERIAL); // Material.TORCH
            player.getWorld().spawn(spawnLocation, ItemDisplay.class, display -> {
                display.setItemStack(torchItem);
                display.setGravity(false);
                display.setInvulnerable(true);
                display.setPersistent(true);
                display.setBillboard(Display.Billboard.FIXED);

                float scale = 0.7f;
                AxisAngle4f rotation = new AxisAngle4f((float) Math.toRadians(90), 1f, 0f, 0f);
                Vector3f translation = new Vector3f(0f, -0.4f, 0f);
                Transformation transformation = new Transformation(
                        translation, rotation, new Vector3f(scale, scale, scale), new AxisAngle4f(0f, 0f, 0f, 1f)
                );
                display.setTransformation(transformation);

                PersistentDataContainer pdc = display.getPersistentDataContainer();
                // Add the specific marker type tag
                pdc.set(MARKER_TYPE_KEY, PersistentDataType.STRING, "SPAWN_ITEM"); // This identifies it as an item spawn
                // Add the general build marker tag
                pdc.set(BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte)1); // This identifies it as ANY build marker
            });
             player.sendActionBar(Component.text("Placed Item Spawn Marker (Torch Item).", NamedTextColor.GREEN));
        } catch (Exception e) { /* ... error handling ... */ }
    }

     /** Helper to calculate placement location */
     private Location calculatePlacementLocation(Player player, Block clickedBlock, BlockFace clickedFace, double yOffset) {
        // ... (Implementation remains the same) ...
         Location spawnLocation;
         Location eyeLoc = player.getEyeLocation();
         if (clickedBlock != null && clickedFace != null) {
             Block potentialBlock = clickedBlock.getRelative(clickedFace);
             if (!potentialBlock.getType().isSolid() || potentialBlock.isLiquid()) {
                 spawnLocation = potentialBlock.getLocation().add(0.5, 0, 0.5);
                 spawnLocation.setY(potentialBlock.getY() + yOffset);
             } else {
                 spawnLocation = eyeLoc.clone().add(player.getFacing().getDirection().multiply(1.5));
                 spawnLocation.setY(Math.floor(spawnLocation.getY()) + yOffset);
             }
         } else {
             spawnLocation = eyeLoc.clone().add(player.getFacing().getDirection().multiply(1.5));
             spawnLocation.setY(Math.floor(spawnLocation.getY()) + yOffset);
         }
         spawnLocation.setPitch(0);
         spawnLocation.setYaw(player.getLocation().getYaw());
         return spawnLocation;
     }
}
