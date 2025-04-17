package com.clarkson.sot.events;

// Use consistent key definitions - ideally move these to a shared Constants class
import com.clarkson.sot.main.SoT;
import com.clarkson.sot.dungeon.segment.Direction; // Import Direction

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
import org.joml.Quaternionf; // Using Quaternion for potentially easier rotation
import org.joml.Vector3f;

import java.util.logging.Level;
import java.util.function.Predicate; // For ray trace filter

/**
 * Listens for interactions with SoT tools.
 * Right-click places markers OR rotates existing entry point markers.
 * Left-click removes markers the player is looking at.
 */
public class ToolListener implements Listener {

    private final SoT plugin;

    // --- Constants ---
    private static final int COIN_STACK_SMALL_MODEL_ID = 1001;
    private static final int COIN_STACK_MEDIUM_MODEL_ID = 1002;
    private static final int COIN_STACK_LARGE_MODEL_ID = 1003;
    private static final Material COIN_BASE_MATERIAL = Material.GOLD_NUGGET;
    private static final Material ITEM_SPAWN_MARKER_ITEM_MATERIAL = Material.TORCH;
    private static final Material ENTRY_POINT_MARKER_ITEM_MATERIAL = Material.ARROW; // Item to display for entry points

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

        // --- Handle Right-Click (Placement or Rotation) ---
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if ("COIN_PLACER".equals(toolType)) {
                handleCoinPlacerTool(event, player, pdc);
            } else if ("ITEM_SPAWN_PLACER".equals(toolType)) {
                handleItemSpawnPlacerTool(event, player);
            } else if ("ENTRY_POINT_PLACER".equals(toolType)) {
                handleEntryPointPlacerTool(event, player); // Doesn't need tool PDC anymore
            }
            // Add other right-click tool handlers here
        }
        // --- Handle Left-Click (Removal) ---
        else if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Check if the tool held is *any* of our placement tools
            if ("COIN_PLACER".equals(toolType) || "ITEM_SPAWN_PLACER".equals(toolType) || "ENTRY_POINT_PLACER".equals(toolType)) {
                 handleMarkerRemoval(event, player);
            }
        }
    }

    // --- Tool Handlers ---

    private void handleCoinPlacerTool(PlayerInteractEvent event, Player player, PersistentDataContainer toolPdc) {
        event.setCancelled(true);
        if (!player.hasPermission("sot.admin.placedisplay")) { /* ... perm msg ... */ return; }
        if (!toolPdc.has(TOOL_VALUE_KEY, PersistentDataType.INTEGER)) { /* ... missing value msg ... */ return; }
        int baseValue = toolPdc.get(TOOL_VALUE_KEY, PersistentDataType.INTEGER);
        spawnCoinDisplayVisual(player, baseValue, event.getClickedBlock(), event.getBlockFace());
    }

    private void handleItemSpawnPlacerTool(PlayerInteractEvent event, Player player) {
        event.setCancelled(true);
        if (!player.hasPermission("sot.admin.placeitemspawn")) { /* ... perm msg ... */ return; }
        // Require clicking the top face of a block
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getBlockFace() != BlockFace.UP) {
             player.sendActionBar(Component.text("Right-click on the top face of a block to place marker.", NamedTextColor.YELLOW));
             return;
        }
        placeItemSpawnMarker(player, event.getClickedBlock(), event.getBlockFace());
    }

    private void handleEntryPointPlacerTool(PlayerInteractEvent event, Player player) {
        event.setCancelled(true); // Cancel event regardless of outcome below
        if (!player.hasPermission("sot.admin.placeentrypoint")) {
             player.sendMessage(Component.text("You don't have permission to use this tool.", NamedTextColor.RED));
             return;
        }

        // 1. Check if player is looking at an existing Entry Point marker entity
        double range = 5.0; // Range to check for existing markers
        Predicate<Entity> filter = entity -> entity instanceof ItemDisplay // Check type
                && entity.getPersistentDataContainer().has(BUILD_MARKER_TAG, PersistentDataType.BYTE) // Check general marker tag
                && "ENTRYPOINT".equals(entity.getPersistentDataContainer().get(MARKER_TYPE_KEY, PersistentDataType.STRING)); // Check specific type

        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), range, filter);

        if (result != null && result.getHitEntity() != null && result.getHitEntity() instanceof ItemDisplay) {
            // Player is looking at an existing entry point marker -> ROTATE IT
            rotateEntryPointMarker(player, (ItemDisplay) result.getHitEntity());
        }
        // 2. If not looking at a marker, check if they right-clicked a block -> PLACE NEW
        else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null && event.getBlockFace() != null) {
            // Place a new marker, default direction based on player facing away from block face
            Direction defaultDirection = Direction.fromBlockFace(event.getBlockFace().getOppositeFace()); // Get direction pointing OUT from block
             if(defaultDirection == Direction.UP || defaultDirection == Direction.DOWN) {
                 // If clicked top/bottom, default to player's horizontal facing
                 defaultDirection = Direction.fromYaw(player.getLocation().getYaw());
             }
            placeEntryPointMarker(player, event.getClickedBlock(), event.getBlockFace(), defaultDirection);
        }
        // 3. If right-clicked air and not looking at marker -> do nothing or give feedback
        else if (event.getAction() == Action.RIGHT_CLICK_AIR) {
             player.sendActionBar(Component.text("Right-click a block face to place, or an existing marker to rotate.", NamedTextColor.YELLOW));
        }
    }


    /** Handles logic for removing markers (Left-Click) */
    private void handleMarkerRemoval(PlayerInteractEvent event, Player player) {
        event.setCancelled(true);
        if (!player.hasPermission("sot.admin.removemarker")) {
             player.sendActionBar(Component.text("You don't have permission to remove markers.", NamedTextColor.RED));
             return;
        }
        double range = 6.0;
        Predicate<Entity> filter = entity -> (entity instanceof ItemDisplay) // Only check ItemDisplays now if ArmorStands aren't used
                   && entity.getPersistentDataContainer().has(BUILD_MARKER_TAG, PersistentDataType.BYTE);

        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), range, filter);

        if (result != null && result.getHitEntity() != null) {
            Entity hitEntity = result.getHitEntity();
            // No need to double-check tag if filter is reliable
            String markerType = hitEntity.getPersistentDataContainer().getOrDefault(MARKER_TYPE_KEY, PersistentDataType.STRING, "Unknown");
            hitEntity.remove();
            player.sendActionBar(Component.text("Removed " + markerType + " marker.", NamedTextColor.YELLOW));
        } else {
            player.sendActionBar(Component.text("No marker found in sight.", NamedTextColor.GRAY));
        }
    }


    // --- Spawning and Rotating Methods ---

    /** Spawns the visual ItemDisplay for a coin stack. */
    private void spawnCoinDisplayVisual(Player player, int baseValue, Block clickedBlock, BlockFace clickedFace) {
        int modelIdToUse;
        if (baseValue >= 50) modelIdToUse = COIN_STACK_LARGE_MODEL_ID;
        else if (baseValue >= 20) modelIdToUse = COIN_STACK_MEDIUM_MODEL_ID;
        else modelIdToUse = COIN_STACK_SMALL_MODEL_ID;

        ItemStack displayStack = new ItemStack(COIN_BASE_MATERIAL);
        ItemMeta meta = displayStack.getItemMeta();
        if (meta != null) { meta.setCustomModelData(modelIdToUse); displayStack.setItemMeta(meta); }
        else { player.sendMessage(Component.text("Error: Could not get ItemMeta for Coin Display.", NamedTextColor.RED)); return; }

        Location spawnLocation = calculatePlacementLocation(player, clickedBlock, clickedFace, 0.1);

        try {
            player.getWorld().spawn(spawnLocation, ItemDisplay.class, display -> {
                display.setItemStack(displayStack);
                display.setGravity(false); display.setInvulnerable(true); display.setBillboard(Display.Billboard.CENTER);
                float scale = 1.5f;
                Transformation transformation = new Transformation( new Vector3f(0f, 0f, 0f), new AxisAngle4f(0f, 0f, 0f, 1f), new Vector3f(scale, scale, scale), new AxisAngle4f(0f, 0f, 0f, 1f) );
                display.setTransformation(transformation);
                PersistentDataContainer pdc = display.getPersistentDataContainer();
                pdc.set(BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte)1);
                pdc.set(MARKER_TYPE_KEY, PersistentDataType.STRING, "DISPLAY_COIN"); // Add type for removal message
            });
            player.sendActionBar(Component.text("Placed Coin Display (Value: " + baseValue + ")", NamedTextColor.GREEN));
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Failed to spawn Coin ItemDisplay via tool", e);
             player.sendMessage(Component.text("An error occurred while spawning the coin display entity.", NamedTextColor.RED));
        }
    }

    /** Places an ItemDisplay showing a flat torch item, tagged as an item spawn marker. */
    private void placeItemSpawnMarker(Player player, Block clickedBlock, BlockFace clickedFace) {
         Block blockToPlaceOn = clickedBlock; // Clicked on top face, place marker above
         Block targetBlock = blockToPlaceOn.getRelative(BlockFace.UP); // Target air block above
         Location spawnLocation = targetBlock.getLocation().add(0.5, 0.0, 0.5); // Center of the block space, Y aligned with base

         if (!targetBlock.getType().isAir() && targetBlock.getType() != Material.CAVE_AIR && targetBlock.getType() != Material.VOID_AIR) {
              player.sendActionBar(Component.text("Cannot place marker here (space occupied).", NamedTextColor.RED));
              return;
         }

         try {
             ItemStack torchItem = new ItemStack(ITEM_SPAWN_MARKER_ITEM_MATERIAL);
             player.getWorld().spawn(spawnLocation, ItemDisplay.class, display -> {
                 display.setItemStack(torchItem);
                 display.setGravity(false); display.setInvulnerable(true); display.setPersistent(true); display.setBillboard(Display.Billboard.FIXED);
                 float scale = 0.7f;
                 AxisAngle4f rotation = new AxisAngle4f((float) Math.toRadians(90), 1f, 0f, 0f); // Lay flat on X axis
                 Vector3f translation = new Vector3f(0f, -0.4f, 0f); // Lower slightly
                 Transformation transformation = new Transformation( translation, rotation, new Vector3f(scale, scale, scale), new AxisAngle4f(0f, 0f, 0f, 1f) );
                 display.setTransformation(transformation);
                 PersistentDataContainer pdc = display.getPersistentDataContainer();
                 pdc.set(MARKER_TYPE_KEY, PersistentDataType.STRING, "SPAWN_ITEM");
                 pdc.set(BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte)1);
             });
              player.sendActionBar(Component.text("Placed Item Spawn Marker (Torch Item).", NamedTextColor.GREEN));
         } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Failed to spawn marker ItemDisplay for item spawn", e);
             player.sendMessage(Component.text("Error placing marker entity.", NamedTextColor.RED));
         }
    }

    /**
     * Places a new ItemDisplay showing an arrow pointing in the specified direction,
     * tagged as an entry point marker.
     * @param player Player using the tool.
     * @param clickedBlock The block clicked.
     * @param clickedFace The face of the block clicked.
     * @param directionToPlace The initial direction the entry point should face.
     */
    private void placeEntryPointMarker(Player player, Block clickedBlock, BlockFace clickedFace, Direction directionToPlace) {
        Block targetBlock = clickedBlock.getRelative(clickedFace);
        Location spawnLocation = targetBlock.getLocation().add(0.5, 0.5, 0.5); // Center of the target block

        if (!targetBlock.getType().isAir() && targetBlock.getType() != Material.CAVE_AIR && targetBlock.getType() != Material.VOID_AIR) {
             player.sendActionBar(Component.text("Cannot place entry marker here (space occupied).", NamedTextColor.RED));
             return;
        }

        try {
            ItemStack arrowItem = new ItemStack(ENTRY_POINT_MARKER_ITEM_MATERIAL);
            AxisAngle4f rotation = calculateRotationForDirection(directionToPlace); // Use helper
            float scale = 1.0f;
            Vector3f translation = new Vector3f(0f, 0f, 0f);
            Transformation transformation = new Transformation(translation, rotation, new Vector3f(scale, scale, scale), new AxisAngle4f());

            player.getWorld().spawn(spawnLocation, ItemDisplay.class, display -> {
                display.setItemStack(arrowItem);
                display.setGravity(false); display.setInvulnerable(true); display.setPersistent(true); display.setBillboard(Display.Billboard.FIXED);
                display.setTransformation(transformation); // Apply calculated rotation

                PersistentDataContainer pdc = display.getPersistentDataContainer();
                pdc.set(MARKER_TYPE_KEY, PersistentDataType.STRING, "ENTRYPOINT");
                pdc.set(DIRECTION_KEY, PersistentDataType.STRING, directionToPlace.name()); // Store initial direction
                pdc.set(BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte)1);
            });

            player.sendActionBar(Component.text("Placed Entry Point Marker (" + directionToPlace.name() + ").", NamedTextColor.GREEN));

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn marker ItemDisplay for entry point", e);
            player.sendMessage(Component.text("Error placing marker entity.", NamedTextColor.RED));
        }
    }

     /**
      * Rotates an existing entry point marker entity to the next cardinal direction.
      * @param player The player triggering the rotation.
      * @param display The ItemDisplay entity representing the entry point marker.
      */
     private void rotateEntryPointMarker(Player player, ItemDisplay display) {
         PersistentDataContainer pdc = display.getPersistentDataContainer();
         // Redundant check if filter works, but safe
         if (!"ENTRYPOINT".equals(pdc.get(MARKER_TYPE_KEY, PersistentDataType.STRING))) return;

         String currentDirStr = pdc.get(DIRECTION_KEY, PersistentDataType.STRING);
         Direction currentDirection;
         try {
             currentDirection = (currentDirStr != null) ? Direction.valueOf(currentDirStr) : Direction.NORTH;
         } catch (IllegalArgumentException e) { currentDirection = Direction.NORTH; }

         // Cycle through cardinal directions: N -> E -> S -> W -> N
         Direction nextDirection;
         switch (currentDirection) {
             case NORTH: nextDirection = Direction.EAST; break;
             case EAST:  nextDirection = Direction.SOUTH; break;
             case SOUTH: nextDirection = Direction.WEST; break;
             case WEST:
             default:    nextDirection = Direction.NORTH; break;
         }

         // Update PDC
         pdc.set(DIRECTION_KEY, PersistentDataType.STRING, nextDirection.name());

         // Update Transformation
         AxisAngle4f newRotation = calculateRotationForDirection(nextDirection);
         
         Transformation currentTransform = display.getTransformation();
         // Create new transformation keeping scale/translation, only changing rotation
         Quaternionf quaternionRotation = new Quaternionf(newRotation);

        // Create the new Transformation object
        Transformation newTransform = new Transformation(
            currentTransform.getTranslation(),
            quaternionRotation, // Use the converted Quaternionf
            currentTransform.getScale(),
            currentTransform.getRightRotation()
        );

        // Apply the new transformation
        display.setTransformation(newTransform);
         display.setTransformation(newTransform);

         player.sendActionBar(Component.text("Rotated Entry Point Marker to " + nextDirection.name(), NamedTextColor.YELLOW));
     }

     /** Calculates the Y-axis rotation for an ItemDisplay arrow to face a direction. */
     private AxisAngle4f calculateRotationForDirection(Direction direction) {
          float yaw = 0f; // Default: South
          switch (direction) {
              case NORTH: yaw = 180f; break;
              case EAST:  yaw = -90f; break;
              case WEST:  yaw = 90f;  break;
              case SOUTH: yaw = 0f;   break;
              // UP/DOWN would need X/Z rotation, not handled here
            default:
                break;
          }
          // Rotation around Y axis
          return new AxisAngle4f((float) Math.toRadians(yaw), 0f, 1f, 0f);
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
