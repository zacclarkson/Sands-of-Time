package com.clarkson.sot.events;

import com.clarkson.sot.dungeon.VaultColor;
import com.clarkson.sot.dungeon.segment.Direction;
import com.clarkson.sot.main.SoT;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Handles interactions with the master segment builder tool (BLAZE_ROD tagged as SEGMENT_BUILDER).
 * <p>
 * Right-click: places a marker in the current mode.
 * Left-click:  removes the marker being looked at.
 * <p>
 * All markers are BlockDisplay entities tagged with BUILD_MARKER_TAG so they can be
 * found by SaveSegmentCommand and removed by left-click.
 */
public class ToolListener implements Listener {

    private final SoT plugin;
    private final BuilderSessionManager sessionManager;

    // --- PDC keys ---
    private final NamespacedKey TOOL_TYPE_KEY;
    private final NamespacedKey BUILD_MARKER_TAG;
    private final NamespacedKey MARKER_TYPE_KEY;
    private final NamespacedKey DIRECTION_KEY;
    private final NamespacedKey VAULT_COLOR_KEY;
    private final NamespacedKey COIN_VALUE_KEY;
    private final NamespacedKey BOUND_GROUP_KEY;
    private final NamespacedKey BOUND_MIN_KEY;
    private final NamespacedKey BOUND_MAX_KEY;

    // Marker type string constants
    private static final String MT_ENTRY_POINT      = "ENTRY_POINT";
    private static final String MT_ENTRY_FRAME      = "ENTRY_FRAME";
    private static final String MT_VAULT_DOOR       = "VAULT_DOOR";
    private static final String MT_VAULT_MARKER     = "VAULT_MARKER";
    private static final String MT_KEY_SPAWN        = "KEY_SPAWN";
    private static final String MT_GATE             = "GATE";
    private static final String MT_BOUND_FRAME      = "BOUND_FRAME";
    private static final String MT_BOUND_CORNER1    = "BOUND_CORNER1";
    private static final String MT_LEVER            = "LEVER";
    private static final String MT_SAND_SPAWN       = "SAND_SPAWN";
    private static final String MT_SAND_SACRIFICE   = "SAND_SACRIFICE";
    private static final String MT_SAFE_EXIT        = "SAFE_EXIT";
    private static final String MT_COIN_SPAWN       = "COIN_SPAWN";
    private static final String MT_ITEM_SPAWN       = "ITEM_SPAWN";
    private static final String MT_MOB_SPAWNER      = "MOB_SPAWNER";

    // Entry point frame: 2 wide x 3 tall (all 6 positions are border)
    private static final int EP_WIDTH  = 2;
    private static final int EP_HEIGHT = 3;

    public ToolListener(@NotNull SoT plugin, @NotNull BuilderSessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        TOOL_TYPE_KEY   = new NamespacedKey(plugin, SegmentBuilderKeys.TOOL_TYPE);
        BUILD_MARKER_TAG= new NamespacedKey(plugin, SegmentBuilderKeys.BUILD_MARKER_TAG);
        MARKER_TYPE_KEY = new NamespacedKey(plugin, SegmentBuilderKeys.MARKER_TYPE);
        DIRECTION_KEY   = new NamespacedKey(plugin, SegmentBuilderKeys.DIRECTION);
        VAULT_COLOR_KEY = new NamespacedKey(plugin, SegmentBuilderKeys.VAULT_COLOR);
        COIN_VALUE_KEY  = new NamespacedKey(plugin, SegmentBuilderKeys.COIN_VALUE);
        BOUND_GROUP_KEY = new NamespacedKey(plugin, SegmentBuilderKeys.BOUND_GROUP);
        BOUND_MIN_KEY   = new NamespacedKey(plugin, SegmentBuilderKeys.BOUND_MIN);
        BOUND_MAX_KEY   = new NamespacedKey(plugin, SegmentBuilderKeys.BOUND_MAX);
    }

    // -------------------------------------------------------------------------
    // Main event handler
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String toolType = meta.getPersistentDataContainer()
                .get(TOOL_TYPE_KEY, PersistentDataType.STRING);
        if (!SegmentBuilderKeys.TOOL_TYPE_VALUE.equals(toolType)) return;

        if (!player.hasPermission("sot.admin.builder")) {
            player.sendActionBar(Component.text("No permission.", NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            handleRightClick(event, player);
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            handleLeftClick(player);
        }
    }

    // -------------------------------------------------------------------------
    // Right-click dispatch
    // -------------------------------------------------------------------------

    private void handleRightClick(PlayerInteractEvent event, Player player) {
        PlayerBuilderSession session = sessionManager.getSession(player);
        BuilderMode mode = session.getMode();

        switch (mode) {
            case ENTRY_POINT:
                handleEntryPoint(event, player);
                break;
            case VAULT_DOOR:
            case GATE:
                handleBoundFirstOrSecondClick(event, player, session, mode);
                break;
            case VAULT_MARKER:
                placePointMarker(event, player, MT_VAULT_MARKER,
                        Material.PURPLE_WOOL, 0.5f, session.getVaultColor(), -1);
                break;
            case KEY_SPAWN:
                placePointMarker(event, player, MT_KEY_SPAWN,
                        Material.YELLOW_WOOL, 0.5f, session.getVaultColor(), -1);
                break;
            case LEVER:
                placePointMarker(event, player, MT_LEVER,
                        Material.LEVER, 0.5f, null, -1);
                break;
            case SAND_SPAWN:
                placePointMarker(event, player, MT_SAND_SPAWN,
                        Material.SAND, 0.5f, null, -1);
                break;
            case SAND_SACRIFICE:
                placePointMarker(event, player, MT_SAND_SACRIFICE,
                        Material.GOLD_BLOCK, 0.5f, null, -1);
                break;
            case SAFE_EXIT:
                placePointMarker(event, player, MT_SAFE_EXIT,
                        Material.END_PORTAL_FRAME, 0.5f, null, -1);
                break;
            case COIN_SPAWN:
                placePointMarker(event, player, MT_COIN_SPAWN,
                        Material.YELLOW_CONCRETE, 0.4f, null, session.getCoinValue());
                break;
            case ITEM_SPAWN:
                placePointMarker(event, player, MT_ITEM_SPAWN,
                        Material.BARREL, 0.5f, null, -1);
                break;
            case MOB_SPAWNER:
                placePointMarker(event, player, MT_MOB_SPAWNER,
                        Material.SPAWNER, 0.5f, null, -1);
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Entry point handler (right-click places or rotates)
    // -------------------------------------------------------------------------

    private void handleEntryPoint(PlayerInteractEvent event, Player player) {
        // First check: is the player looking at an existing entry-point anchor? -> rotate
        Predicate<Entity> anchorFilter = e ->
                e instanceof BlockDisplay
                && e.getPersistentDataContainer().has(BUILD_MARKER_TAG, PersistentDataType.BYTE)
                && MT_ENTRY_POINT.equals(e.getPersistentDataContainer()
                        .get(MARKER_TYPE_KEY, PersistentDataType.STRING));

        RayTraceResult ray = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), 6.0, anchorFilter);

        if (ray != null && ray.getHitEntity() instanceof BlockDisplay) {
            rotateEntryPointFrame((BlockDisplay) ray.getHitEntity(), player);
            return;
        }

        // Otherwise: right-clicked a block face -> place new entry point frame
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || event.getBlockFace() == null) {
            player.sendActionBar(Component.text(
                    "Right-click a wall face to place, or an existing entry point to rotate.",
                    NamedTextColor.YELLOW));
            return;
        }

        Block clicked = event.getClickedBlock();
        BlockFace face = event.getBlockFace();

        // Air block in front of the clicked face = bottom-left corner of the opening
        Block airBlock = clicked.getRelative(face);
        if (!airBlock.getType().isAir()) {
            player.sendActionBar(Component.text(
                    "No air space in front of that face.", NamedTextColor.RED));
            return;
        }

        Direction dir = Direction.fromBlockFace(face);
        if (dir == Direction.UP || dir == Direction.DOWN) {
            dir = Direction.fromYaw(player.getLocation().getYaw());
        }

        spawnEntryPointFrame(player, airBlock.getLocation(), dir);
    }

    /**
     * Spawns a 2x3 frame of LIME_STAINED_GLASS BlockDisplays at the given bottom-left anchor location.
     * One entity is tagged as the ENTRY_POINT anchor; the rest as ENTRY_FRAME. All share a group ID.
     */
    private void spawnEntryPointFrame(Player player, Location anchorBlockLoc, Direction dir) {
        String groupId = UUID.randomUUID().toString();
        BlockData glass = Material.LIME_STAINED_GLASS.createBlockData();

        // Calculate frame offsets based on facing direction.
        // "anchor" is the bottom-left block of the opening from the outside perspective.
        List<int[]> offsets = getEntryPointOffsets(dir); // list of [dx, dy, dz]

        boolean firstEntity = true;
        UUID anchorEntityId = null;

        for (int[] off : offsets) {
            Location spawnLoc = anchorBlockLoc.clone().add(off[0], off[1], off[2]);
            // Spawn at block center
            Location entityLoc = spawnLoc.clone().add(0.5, 0.0, 0.5);

            int[] capturedOff = off;
            boolean isAnchor = firstEntity;
            try {
                BlockDisplay bd = player.getWorld().spawn(entityLoc, BlockDisplay.class, display -> {
                    display.setBlock(glass);
                    display.setGravity(false);
                    display.setInvulnerable(true);
                    display.setPersistent(true);
                    // Scale slightly sub-block to show individual blocks clearly
                    display.setTransformation(new Transformation(
                            new Vector3f(0f, 0f, 0f),
                            new AxisAngle4f(0f, 0f, 0f, 1f),
                            new Vector3f(0.9f, 0.9f, 0.9f),
                            new AxisAngle4f(0f, 0f, 0f, 1f)
                    ));
                    PersistentDataContainer pdc = display.getPersistentDataContainer();
                    pdc.set(BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte) 1);
                    pdc.set(BOUND_GROUP_KEY, PersistentDataType.STRING, groupId);
                    if (isAnchor) {
                        pdc.set(MARKER_TYPE_KEY, PersistentDataType.STRING, MT_ENTRY_POINT);
                        pdc.set(DIRECTION_KEY, PersistentDataType.STRING, dir.name());
                    } else {
                        pdc.set(MARKER_TYPE_KEY, PersistentDataType.STRING, MT_ENTRY_FRAME);
                    }
                });
                if (isAnchor) {
                    anchorEntityId = bd.getUniqueId();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to spawn entry point frame entity", e);
            }
            firstEntity = false;
        }

        player.sendActionBar(Component.text(
                "Placed Entry Point (" + dir.name() + ")", NamedTextColor.GREEN));
    }

    /** Rotates an existing entry point frame to the next cardinal direction. */
    private void rotateEntryPointFrame(BlockDisplay anchor, Player player) {
        PersistentDataContainer pdc = anchor.getPersistentDataContainer();
        String dirStr = pdc.get(DIRECTION_KEY, PersistentDataType.STRING);
        Direction current;
        try {
            current = (dirStr != null) ? Direction.valueOf(dirStr) : Direction.NORTH;
        } catch (IllegalArgumentException e) {
            current = Direction.NORTH;
        }

        Direction next;
        switch (current) {
            case NORTH: next = Direction.EAST;  break;
            case EAST:  next = Direction.SOUTH; break;
            case SOUTH: next = Direction.WEST;  break;
            default:    next = Direction.NORTH; break;
        }

        String groupId = pdc.getOrDefault(BOUND_GROUP_KEY, PersistentDataType.STRING, "");

        // Remove all existing frame entities (anchor + frames)
        Location anchorBlockLoc = anchor.getLocation().clone().subtract(0.5, 0, 0.5);
        removeGroupEntities(groupId, anchor.getWorld());

        // Respawn frame at the same anchor block location with new direction
        spawnEntryPointFrame(player, anchorBlockLoc, next);
    }

    /**
     * Returns the list of [dx, dy, dz] offsets for a 2-wide x 3-tall entry point frame,
     * oriented for the given facing direction. The anchor (index 0) is always included.
     */
    private List<int[]> getEntryPointOffsets(Direction dir) {
        List<int[]> offsets = new ArrayList<>();
        // For NORTH/SOUTH: frame spans X and Y; Z = 0
        // For EAST/WEST:   frame spans Z and Y; X = 0
        for (int row = 0; row < EP_HEIGHT; row++) {
            for (int col = 0; col < EP_WIDTH; col++) {
                if (dir == Direction.NORTH || dir == Direction.SOUTH) {
                    offsets.add(new int[]{col, row, 0});
                } else { // EAST / WEST
                    offsets.add(new int[]{0, row, col});
                }
            }
        }
        return offsets;
    }

    // -------------------------------------------------------------------------
    // Two-click bound handler (VAULT_DOOR and GATE)
    // -------------------------------------------------------------------------

    private void handleBoundFirstOrSecondClick(PlayerInteractEvent event, Player player,
                                               PlayerBuilderSession session, BuilderMode mode) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || event.getBlockFace() == null) {
            player.sendActionBar(Component.text(
                    "Right-click a block face to select a corner.", NamedTextColor.YELLOW));
            return;
        }

        // Selection is the air block in front of the clicked face (the actual opening boundary)
        Block airBlock = event.getClickedBlock().getRelative(event.getBlockFace());
        if (!airBlock.getType().isAir()) {
            player.sendActionBar(Component.text(
                    "Corner must be an air block — click the face bordering the opening.",
                    NamedTextColor.RED));
            return;
        }

        Location corner = airBlock.getLocation();

        if (session.getPendingBoundCorner() == null) {
            // First corner
            session.setPendingBoundCorner(corner);

            // Spawn a temp first-corner indicator
            try {
                Material mat = (mode == BuilderMode.VAULT_DOOR)
                        ? Material.PURPLE_STAINED_GLASS : Material.GRAY_STAINED_GLASS;
                BlockDisplay bd = player.getWorld().spawn(
                        corner.clone().add(0.5, 0.0, 0.5), BlockDisplay.class, display -> {
                    display.setBlock(mat.createBlockData());
                    display.setGravity(false);
                    display.setInvulnerable(true);
                    display.setPersistent(true);
                    display.setTransformation(new Transformation(
                            new Vector3f(0f, 0f, 0f),
                            new AxisAngle4f(0f, 0f, 0f, 1f),
                            new Vector3f(0.9f, 0.9f, 0.9f),
                            new AxisAngle4f(0f, 0f, 0f, 1f)
                    ));
                    PersistentDataContainer pdc = display.getPersistentDataContainer();
                    pdc.set(BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte) 1);
                    pdc.set(MARKER_TYPE_KEY, PersistentDataType.STRING, MT_BOUND_CORNER1);
                });
                session.setPendingCornerEntityId(bd.getUniqueId());
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to spawn temp corner entity", e);
            }

            player.sendActionBar(Component.text(
                    "First corner set — right-click the opposite corner.", NamedTextColor.YELLOW));

        } else {
            // Second corner — complete the bound
            Location corner1 = session.getPendingBoundCorner();
            Location corner2 = corner;

            // Remove temp first-corner entity
            if (session.getPendingCornerEntityId() != null) {
                player.getWorld().getEntities().stream()
                        .filter(e -> e.getUniqueId().equals(session.getPendingCornerEntityId()))
                        .forEach(Entity::remove);
            }
            session.clearPendingBound();

            // Spawn the perimeter frame
            String markerType = (mode == BuilderMode.VAULT_DOOR) ? MT_VAULT_DOOR : MT_GATE;
            Material frameMat = (mode == BuilderMode.VAULT_DOOR)
                    ? Material.PURPLE_STAINED_GLASS : Material.GRAY_STAINED_GLASS;
            VaultColor color = (mode == BuilderMode.VAULT_DOOR) ? session.getVaultColor() : null;

            spawnBoundFrame(player, corner1, corner2, markerType, frameMat, color);
        }
    }

    /**
     * Spawns a hollow perimeter of BlockDisplay entities around the 2D rectangle defined
     * by two corner locations. One entity is the anchor (stores bound data); the rest are frames.
     */
    private void spawnBoundFrame(Player player, Location corner1, Location corner2,
                                 String markerType, Material material,
                                 @Nullable VaultColor vaultColor) {
        String groupId = UUID.randomUUID().toString();

        int x1 = corner1.getBlockX(), y1 = corner1.getBlockY(), z1 = corner1.getBlockZ();
        int x2 = corner2.getBlockX(), y2 = corner2.getBlockY(), z2 = corner2.getBlockZ();

        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);

        // Determine the flat axis (smallest delta → the plane's depth axis)
        int dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;

        List<int[]> perimeterPositions = new ArrayList<>();
        if (dz <= dx && dz <= dy) {
            // XY plane (flat in Z)
            int z = minZ;
            for (int x = minX; x <= maxX; x++) {
                perimeterPositions.add(new int[]{x, minY, z});
                if (minY != maxY) perimeterPositions.add(new int[]{x, maxY, z});
            }
            for (int y = minY + 1; y < maxY; y++) {
                perimeterPositions.add(new int[]{minX, y, z});
                if (minX != maxX) perimeterPositions.add(new int[]{maxX, y, z});
            }
        } else if (dx <= dy && dx <= dz) {
            // ZY plane (flat in X)
            int x = minX;
            for (int z = minZ; z <= maxZ; z++) {
                perimeterPositions.add(new int[]{x, minY, z});
                if (minY != maxY) perimeterPositions.add(new int[]{x, maxY, z});
            }
            for (int y = minY + 1; y < maxY; y++) {
                perimeterPositions.add(new int[]{x, y, minZ});
                if (minZ != maxZ) perimeterPositions.add(new int[]{x, y, maxZ});
            }
        } else {
            // XZ plane (flat in Y)
            int y = minY;
            for (int x = minX; x <= maxX; x++) {
                perimeterPositions.add(new int[]{x, y, minZ});
                if (minZ != maxZ) perimeterPositions.add(new int[]{x, y, maxZ});
            }
            for (int z = minZ + 1; z < maxZ; z++) {
                perimeterPositions.add(new int[]{minX, y, z});
                if (minX != maxX) perimeterPositions.add(new int[]{maxX, y, z});
            }
        }

        // Store absolute corner strings for SaveSegmentCommand
        String minStr = minX + "," + minY + "," + minZ;
        String maxStr = maxX + "," + maxY + "," + maxZ;

        BlockData blockData = material.createBlockData();
        boolean isFirstEntity = true;
        final String vaultColorName = (vaultColor != null) ? vaultColor.name() : null;

        for (int[] pos : perimeterPositions) {
            Location spawnLoc = new Location(player.getWorld(), pos[0] + 0.5, pos[1], pos[2] + 0.5);
            boolean isAnchor = isFirstEntity;
            try {
                player.getWorld().spawn(spawnLoc, BlockDisplay.class, display -> {
                    display.setBlock(blockData);
                    display.setGravity(false);
                    display.setInvulnerable(true);
                    display.setPersistent(true);
                    display.setTransformation(new Transformation(
                            new Vector3f(0f, 0f, 0f),
                            new AxisAngle4f(0f, 0f, 0f, 1f),
                            new Vector3f(0.9f, 0.9f, 0.9f),
                            new AxisAngle4f(0f, 0f, 0f, 1f)
                    ));
                    PersistentDataContainer pdc = display.getPersistentDataContainer();
                    pdc.set(BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte) 1);
                    pdc.set(BOUND_GROUP_KEY, PersistentDataType.STRING, groupId);
                    if (isAnchor) {
                        pdc.set(MARKER_TYPE_KEY, PersistentDataType.STRING, markerType);
                        pdc.set(BOUND_MIN_KEY, PersistentDataType.STRING, minStr);
                        pdc.set(BOUND_MAX_KEY, PersistentDataType.STRING, maxStr);
                        if (vaultColorName != null) {
                            pdc.set(VAULT_COLOR_KEY, PersistentDataType.STRING, vaultColorName);
                        }
                    } else {
                        pdc.set(MARKER_TYPE_KEY, PersistentDataType.STRING, MT_BOUND_FRAME);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to spawn bound frame entity", e);
            }
            isFirstEntity = false;
        }

        String colorLabel = (vaultColor != null) ? " (" + vaultColor.name() + ")" : "";
        String displayName = MT_VAULT_DOOR.equals(markerType) ? "Vault Door" : "Gate";
        int width  = (dz <= dx && dz <= dy) ? (maxX - minX + 1) : (maxZ - minZ + 1);
        int height = maxY - minY + 1;
        player.sendActionBar(Component.text(
                "Placed " + displayName + colorLabel
                + " (" + width + "×" + height + ")", NamedTextColor.GREEN));
    }

    // -------------------------------------------------------------------------
    // Generic point marker placement
    // -------------------------------------------------------------------------

    /**
     * Places a single small BlockDisplay as a point marker.
     *
     * @param event      The interact event.
     * @param player     The builder.
     * @param markerType MARKER_TYPE_KEY value.
     * @param material   The block material to display.
     * @param scale      Uniform scale factor.
     * @param color      Optional VaultColor (stored if non-null).
     * @param coinValue  Coin value to store (-1 to skip).
     */
    private void placePointMarker(PlayerInteractEvent event, Player player,
                                  String markerType, Material material, float scale,
                                  @Nullable VaultColor color, int coinValue) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || event.getBlockFace() == null) {
            player.sendActionBar(Component.text("Right-click a block face to place.", NamedTextColor.YELLOW));
            return;
        }

        Block airBlock = event.getClickedBlock().getRelative(event.getBlockFace());
        if (!airBlock.getType().isAir()) {
            player.sendActionBar(Component.text("Space is occupied.", NamedTextColor.RED));
            return;
        }

        // Offset by (0.5 - scale/2) to centre the small block within the block space
        float offset = (1.0f - scale) / 2.0f;
        Location spawnLoc = airBlock.getLocation().clone().add(0.5, 0, 0.5);
        BlockData blockData = material.createBlockData();
        final String colorName = (color != null) ? color.name() : null;
        final int finalCoinValue = coinValue;

        try {
            player.getWorld().spawn(spawnLoc, BlockDisplay.class, display -> {
                display.setBlock(blockData);
                display.setGravity(false);
                display.setInvulnerable(true);
                display.setPersistent(true);
                display.setTransformation(new Transformation(
                        new Vector3f(offset, offset, offset),
                        new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(scale, scale, scale),
                        new AxisAngle4f(0f, 0f, 0f, 1f)
                ));
                PersistentDataContainer pdc = display.getPersistentDataContainer();
                pdc.set(BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte) 1);
                pdc.set(MARKER_TYPE_KEY, PersistentDataType.STRING, markerType);
                if (colorName != null) {
                    pdc.set(VAULT_COLOR_KEY, PersistentDataType.STRING, colorName);
                }
                if (finalCoinValue > 0) {
                    pdc.set(COIN_VALUE_KEY, PersistentDataType.INTEGER, finalCoinValue);
                }
            });

            String colorLabel = (color != null) ? " (" + color.name() + ")" : "";
            String valueSuffix = (coinValue > 0) ? " value=" + coinValue : "";
            player.sendActionBar(Component.text(
                    "Placed " + markerType + colorLabel + valueSuffix, NamedTextColor.GREEN));

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn point marker: " + markerType, e);
            player.sendMessage(Component.text("Error placing marker entity.", NamedTextColor.RED));
        }
    }

    // -------------------------------------------------------------------------
    // Left-click: remove any marker
    // -------------------------------------------------------------------------

    private void handleLeftClick(Player player) {
        Predicate<Entity> filter = e ->
                e instanceof BlockDisplay
                && e.getPersistentDataContainer().has(BUILD_MARKER_TAG, PersistentDataType.BYTE);

        RayTraceResult ray = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), 6.0, filter);

        if (ray == null || ray.getHitEntity() == null) {
            player.sendActionBar(Component.text("No marker in sight.", NamedTextColor.GRAY));
            return;
        }

        Entity hit = ray.getHitEntity();
        PersistentDataContainer pdc = hit.getPersistentDataContainer();
        String markerType = pdc.getOrDefault(MARKER_TYPE_KEY, PersistentDataType.STRING, "Unknown");

        // If this entity belongs to a group (entry point frame or bound frame), remove all of them
        String groupId = pdc.get(BOUND_GROUP_KEY, PersistentDataType.STRING);
        if (groupId != null && !groupId.isEmpty()) {
            int removed = removeGroupEntities(groupId, hit.getWorld());
            player.sendActionBar(Component.text(
                    "Removed " + markerType + " marker group (" + removed + " entities).",
                    NamedTextColor.YELLOW));
        } else {
            hit.remove();
            player.sendActionBar(Component.text("Removed " + markerType + " marker.", NamedTextColor.YELLOW));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Removes all BUILD_MARKER_TAG entities in the world that share the given group ID.
     * @return number of entities removed.
     */
    private int removeGroupEntities(String groupId, org.bukkit.World world) {
        List<Entity> toRemove = new ArrayList<>();
        for (Entity e : world.getEntities()) {
            if (e instanceof BlockDisplay
                    && e.getPersistentDataContainer().has(BUILD_MARKER_TAG, PersistentDataType.BYTE)
                    && groupId.equals(e.getPersistentDataContainer().get(BOUND_GROUP_KEY, PersistentDataType.STRING))) {
                toRemove.add(e);
            }
        }
        toRemove.forEach(Entity::remove);
        return toRemove.size();
    }
}
