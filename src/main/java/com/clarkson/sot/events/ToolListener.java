package com.clarkson.sot.events;

import com.clarkson.sot.dungeon.VaultColor;
import com.clarkson.sot.dungeon.segment.Direction;
import com.clarkson.sot.main.SoT;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
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
 * Each marker type has a themed display: a data anchor (BlockDisplay or ItemDisplay) that carries
 * the marker's PDC data, plus optional cosmetic ItemDisplay icons and a TextDisplay label. All are
 * tagged with BUILD_MARKER_TAG and share a BOUND_GROUP id so they are found by SaveSegmentCommand
 * and removed together by left-click / undo / clear. Cosmetic entities are tagged ICON/LABEL (and
 * frame pieces ENTRY_FRAME/BOUND_FRAME) so SaveSegmentCommand skips them.
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
    private final NamespacedKey SPINNABLE_KEY;

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
    private static final String MT_COIN_SPAWN       = "COIN_SPAWN";
    private static final String MT_ITEM_SPAWN       = "ITEM_SPAWN";
    private static final String MT_MOB_SPAWNER      = "MOB_SPAWNER";
    private static final String MT_SAFE_EXIT        = "SAFE_EXIT";
    private static final String MT_ICON             = "ICON";   // cosmetic floating icon
    private static final String MT_LABEL            = "LABEL";  // cosmetic floating text label

    // Entry point marker: one centered glass block with a flat stick arrow floating above it.
    private static final Material EP_GLASS   = Material.LIME_STAINED_GLASS;
    private static final float  EP_GLASS_SCALE = 0.9f;  // sub-block so it reads as a marker
    private static final double EP_ARROW_HEIGHT = 1.4;  // arrow plane, blocks above the glass base
    private static final float  EP_SHAFT_SCALE  = 0.7f;
    private static final float  EP_BARB_SCALE   = 0.45f;
    private static final double EP_TIP_DIST      = 0.45; // shaft-centre -> arrow tip
    private static final double EP_BARB_DIST     = 0.22; // tip -> barb centre
    private static final double EP_BARB_LATERAL  = 0.05; // shift both barbs left of the shaft (- = right)
    // A stick's item texture runs diagonally (bottom-left to top-right) within its sprite, so its
    // visible long axis sits ~45 deg off the sprite's vertical. Pre-roll about the sprite normal to
    // align that art with the axis the flatten+yaw math expects. Flip the sign if it points the
    // wrong way across the diagonal.
    private static final float  EP_STICK_ROLL   = (float) Math.toRadians(45);

    // --- Themed marker display tuning ---
    private static final float  MARKER_LABEL_SCALE  = 0.6f;
    private static final double MARKER_LABEL_HEIGHT = 1.3;  // point-marker label height above cell floor
    private static final double ENTRY_LABEL_HEIGHT  = 2.2;  // entry-point label, above the arrow
    private static final double BOUND_LABEL_EXTRA   = 1.2;  // bound-frame label, above the top edge
    private static final float  MARKER_ICON_SCALE   = 0.45f;
    private static final double MARKER_ICON_HEIGHT  = 1.0;  // floating icon height above cell floor
    private static final float  COIN_MARKER_SCALE   = 0.6f;
    private static final double COIN_MARKER_HEIGHT  = 0.55;
    private static final float  KEY_MARKER_SCALE    = 0.7f;
    private static final double KEY_MARKER_HEIGHT   = 0.5;
    // Sand sacrifice uses soul sand (was gold — gold now reads as coins).
    private static final Material SACRIFICE_MATERIAL = Material.SOUL_SAND;
    // Coin custom-model-data thresholds, mirroring CoinStack so the marker matches the real coin.
    private static final int COIN_MODEL_SMALL = 1001, COIN_MODEL_MEDIUM = 1002, COIN_MODEL_LARGE = 1003;

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
        SPINNABLE_KEY   = new NamespacedKey(plugin, SegmentBuilderKeys.SPINNABLE);
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
            case VAULT_MARKER: {
                VaultColor c = session.getVaultColor();
                Material mat = (c != null) ? c.getConcreteMaterial() : Material.PURPLE_WOOL;
                Component lbl = (c != null)
                        ? Component.text("Vault " + c.name(), c.getTextColor())
                        : Component.text("Vault", NamedTextColor.LIGHT_PURPLE);
                placeBlockMarker(event, player, MT_VAULT_MARKER, mat, 0.6f, c, -1,
                        new ItemStack(Material.NETHER_STAR), lbl);
                break;
            }
            case KEY_SPAWN: {
                VaultColor c = session.getVaultColor();
                Component lbl = (c != null)
                        ? Component.text("Key " + c.name(), c.getTextColor())
                        : Component.text("Key", NamedTextColor.YELLOW);
                placeItemMarker(event, player, MT_KEY_SPAWN, buildKeyItem(c),
                        KEY_MARKER_SCALE, KEY_MARKER_HEIGHT, false, c, -1, lbl);
                break;
            }
            case LEVER:
                placeBlockMarker(event, player, MT_LEVER, Material.LEVER, 0.5f, null, -1,
                        null, Component.text("Lever", NamedTextColor.WHITE));
                break;
            case SAND_SPAWN:
                placeBlockMarker(event, player, MT_SAND_SPAWN, Material.SAND, 0.5f, null, -1,
                        null, Component.text("Sand", NamedTextColor.YELLOW));
                break;
            case SAND_SACRIFICE:
                placeBlockMarker(event, player, MT_SAND_SACRIFICE, SACRIFICE_MATERIAL, 0.5f, null, -1,
                        null, Component.text("Sacrifice", NamedTextColor.GOLD));
                break;
            case COIN_SPAWN: {
                int val = session.getCoinValue();
                placeItemMarker(event, player, MT_COIN_SPAWN, buildCoinItem(val),
                        COIN_MARKER_SCALE, COIN_MARKER_HEIGHT, true, null, val,
                        Component.text("Coin x" + val, NamedTextColor.GOLD));
                break;
            }
            case ITEM_SPAWN:
                placeBlockMarker(event, player, MT_ITEM_SPAWN, Material.BARREL, 0.5f, null, -1,
                        new ItemStack(Material.DIAMOND), Component.text("Item", NamedTextColor.AQUA));
                break;
            case MOB_SPAWNER:
                placeBlockMarker(event, player, MT_MOB_SPAWNER, Material.SPAWNER, 0.5f, null, -1,
                        new ItemStack(Material.ZOMBIE_HEAD), Component.text("Mob Spawner", NamedTextColor.RED));
                break;
            case SAFE_EXIT:
                // Deliberately not END_PORTAL_FRAME: that block is the in-game escape trigger
                // EscapeListener looks for, and a marker shaped like it would confuse builders.
                placeBlockMarker(event, player, MT_SAFE_EXIT, Material.LIME_CONCRETE, 0.5f, null, -1,
                        new ItemStack(Material.ENDER_PEARL),
                        Component.text("Safe Exit", NamedTextColor.GREEN));
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

        spawnEntryPointMarker(player, airBlock.getLocation(), dir, null, true);
    }

    /**
     * Spawns the entry point marker: a single centered LIME_STAINED_GLASS block (the ENTRY_POINT
     * anchor) with a flat stick arrow floating above it, pointing in {@code dir}. The arrow sticks
     * are ItemDisplay entities tagged ENTRY_FRAME so SaveSegmentCommand skips them. All entities
     * share a group ID so left-click removes them together.
     *
     * @param airBlockLoc  the (integer) corner location of the air block the marker occupies.
     * @param reuseGroupId if non-null, reuse this group ID (used by rotate so undo still tracks it);
     *                     otherwise a fresh group ID is generated.
     * @param recordUndo   if true, push the group onto the player's undo stack.
     * @return the group ID of the placed marker.
     */
    private String spawnEntryPointMarker(Player player, Location airBlockLoc, Direction dir,
                                         @Nullable String reuseGroupId, boolean recordUndo) {
        String groupId = (reuseGroupId != null) ? reuseGroupId : UUID.randomUUID().toString();
        World world = player.getWorld();

        // 1. Centered glass block (the anchor). A BlockDisplay's position is its corner, so spawn
        //    at the block's integer corner and centre the sub-block via the transform translation.
        float off = (1.0f - EP_GLASS_SCALE) / 2.0f;
        Location glassLoc = new Location(world,
                airBlockLoc.getBlockX(), airBlockLoc.getBlockY(), airBlockLoc.getBlockZ());
        BlockData glass = EP_GLASS.createBlockData();
        try {
            world.spawn(glassLoc, BlockDisplay.class, display -> {
                display.setBlock(glass);
                display.setGravity(false);
                display.setInvulnerable(true);
                display.setPersistent(true);
                display.setTransformation(new Transformation(
                        new Vector3f(off, off, off),
                        new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(EP_GLASS_SCALE, EP_GLASS_SCALE, EP_GLASS_SCALE),
                        new AxisAngle4f(0f, 0f, 0f, 1f)
                ));
                PersistentDataContainer pdc = display.getPersistentDataContainer();
                pdc.set(BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte) 1);
                pdc.set(BOUND_GROUP_KEY, PersistentDataType.STRING, groupId);
                pdc.set(MARKER_TYPE_KEY, PersistentDataType.STRING, MT_ENTRY_POINT);
                pdc.set(DIRECTION_KEY, PersistentDataType.STRING, dir.name());
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn entry point glass anchor", e);
        }

        // 2. Flat stick arrow floating above the glass, pointing in `dir`.
        spawnArrow(world, airBlockLoc, dir, groupId);

        // 3. Floating label above the arrow.
        spawnLabel(world, airBlockLoc.getBlockX() + 0.5,
                airBlockLoc.getBlockY() + ENTRY_LABEL_HEIGHT, airBlockLoc.getBlockZ() + 0.5,
                groupId, Component.text("Entry " + dir.name(), NamedTextColor.GREEN));

        if (recordUndo) {
            sessionManager.getSession(player).pushUndo(groupId);
        }

        player.sendActionBar(Component.text(
                "Placed Entry Point (" + dir.name() + ")", NamedTextColor.GREEN));
        return groupId;
    }

    /**
     * Spawns a flat, horizontal arrow made of three sticks (shaft + two barbs) in the XZ plane
     * above the glass block, its point aimed in {@code dir} (the direction a player walks through).
     */
    private void spawnArrow(World world, Location airBlockLoc, Direction dir, String groupId) {
        double cx = airBlockLoc.getBlockX() + 0.5;
        double cz = airBlockLoc.getBlockZ() + 0.5;
        double y  = airBlockLoc.getBlockY() + EP_ARROW_HEIGHT;

        float yaw = yawFor(dir);
        double fx = Math.sin(yaw), fz = Math.cos(yaw); // forward unit vector in XZ

        // Shaft, centred over the glass, long axis along the facing direction.
        spawnStick(world, cx, y, cz, yaw, EP_SHAFT_SCALE, groupId);

        // Two barbs at the tip, swept back 135 deg either side to form the arrowhead. The tip is
        // also nudged sideways along the arrow's left vector (fz, -fx) so the head sits centred.
        double tx = cx + fx * EP_TIP_DIST + fz * EP_BARB_LATERAL;
        double tz = cz + fz * EP_TIP_DIST - fx * EP_BARB_LATERAL;
        float leftYaw  = yaw + (float) Math.toRadians(135);
        float rightYaw = yaw - (float) Math.toRadians(135);
        spawnStick(world, tx + Math.sin(leftYaw)  * EP_BARB_DIST, y,
                          tz + Math.cos(leftYaw)  * EP_BARB_DIST, leftYaw,  EP_BARB_SCALE, groupId);
        spawnStick(world, tx + Math.sin(rightYaw) * EP_BARB_DIST, y,
                          tz + Math.cos(rightYaw) * EP_BARB_DIST, rightYaw, EP_BARB_SCALE, groupId);
    }

    /**
     * Spawns one stick as an ItemDisplay lying flat in the XZ plane, its long axis pointing along
     * the horizontal direction given by {@code yaw} (0 = +Z / SOUTH, increasing toward +X / EAST).
     */
    private void spawnStick(World world, double x, double y, double z,
                            float yaw, float scale, String groupId) {
        Location loc = new Location(world, x, y, z);
        // Rotation is applied right-to-left: first roll about the sprite normal (Z) to bring the
        // stick's diagonal art onto the sprite's vertical, then pitch +90 about X to lay it flat
        // (vertical long axis maps to +Z), then yaw about Y so +Z rotates onto the facing direction.
        Quaternionf rot = new Quaternionf(new AxisAngle4f(yaw, 0f, 1f, 0f))
                .mul(new Quaternionf(new AxisAngle4f((float) Math.toRadians(90), 1f, 0f, 0f)))
                .mul(new Quaternionf(new AxisAngle4f(EP_STICK_ROLL, 0f, 0f, 1f)));
        try {
            world.spawn(loc, ItemDisplay.class, disp -> {
                disp.setItemStack(new ItemStack(Material.STICK));
                disp.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                disp.setGravity(false);
                disp.setInvulnerable(true);
                disp.setPersistent(true);
                disp.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        rot,
                        new Vector3f(scale, scale, scale),
                        new Quaternionf()
                ));
                PersistentDataContainer pdc = disp.getPersistentDataContainer();
                pdc.set(BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte) 1);
                pdc.set(BOUND_GROUP_KEY, PersistentDataType.STRING, groupId);
                pdc.set(MARKER_TYPE_KEY, PersistentDataType.STRING, MT_ENTRY_FRAME);
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to spawn entry point arrow stick", e);
        }
    }

    /** Yaw (radians) that rotates the canonical +Z axis onto the given cardinal direction. */
    private float yawFor(Direction dir) {
        switch (dir) {
            case SOUTH: return 0f;
            case EAST:  return (float) Math.toRadians(90);
            case NORTH: return (float) Math.toRadians(180);
            case WEST:  return (float) Math.toRadians(-90);
            default:    return (float) Math.toRadians(180);
        }
    }

    /** Rotates an existing entry point marker to the next cardinal direction. */
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

        // The glass anchor spawns at the block's integer corner, so its location is the air block.
        Location airBlockLoc = new Location(anchor.getWorld(),
                anchor.getLocation().getBlockX(),
                anchor.getLocation().getBlockY(),
                anchor.getLocation().getBlockZ());
        removeGroupEntities(groupId, anchor.getWorld());

        // Respawn at the same block with the new direction, reusing the group ID so the
        // existing undo entry still points at it (and without pushing a duplicate).
        spawnEntryPointMarker(player, airBlockLoc, next, groupId, false);
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
                        ? (session.getVaultColor() != null
                                ? session.getVaultColor().getGlassMaterial()
                                : Material.PURPLE_STAINED_GLASS)
                        : Material.GRAY_STAINED_GLASS;
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
            VaultColor color = (mode == BuilderMode.VAULT_DOOR) ? session.getVaultColor() : null;
            Material frameMat = (mode == BuilderMode.VAULT_DOOR)
                    ? (color != null ? color.getGlassMaterial() : Material.PURPLE_STAINED_GLASS)
                    : Material.GRAY_STAINED_GLASS;

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

        // Floating label centred above the top edge of the frame.
        Component boundLabel = MT_VAULT_DOOR.equals(markerType)
                ? Component.text("Vault Door" + (vaultColor != null ? " " + vaultColor.name() : ""),
                        vaultColor != null ? vaultColor.getTextColor() : NamedTextColor.LIGHT_PURPLE)
                : Component.text("Gate", NamedTextColor.GRAY);
        spawnLabel(player.getWorld(), (minX + maxX) / 2.0 + 0.5, maxY + BOUND_LABEL_EXTRA,
                (minZ + maxZ) / 2.0 + 0.5, groupId, boundLabel);

        sessionManager.getSession(player).pushUndo(groupId);

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
     * Validates a right-click for a point marker and returns the air cell (integer corner Location)
     * to place it in, or null (with an action-bar message) if the click was invalid.
     */
    @Nullable
    private Location validatedAirCell(PlayerInteractEvent event, Player player) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || event.getBlockFace() == null) {
            player.sendActionBar(Component.text("Right-click a block face to place.", NamedTextColor.YELLOW));
            return null;
        }
        Block airBlock = event.getClickedBlock().getRelative(event.getBlockFace());
        if (!airBlock.getType().isAir()) {
            player.sendActionBar(Component.text("Space is occupied.", NamedTextColor.RED));
            return null;
        }
        return airBlock.getLocation();
    }

    /**
     * Places a themed point marker whose data anchor is a centered BlockDisplay, plus an optional
     * floating icon and a floating label. The block is spawned at the cell's integer corner and
     * centred via the transform (a BlockDisplay's position is its corner).
     *
     * @param markerType MARKER_TYPE_KEY value (carries the data).
     * @param material   Block material for the anchor.
     * @param scale      Uniform scale of the anchor block.
     * @param color      Optional VaultColor (stored on the anchor if non-null).
     * @param coinValue  Coin value to store (-1 to skip).
     * @param icon       Optional floating icon ItemStack above the marker (null = none).
     * @param label      Floating text label above the marker.
     */
    private void placeBlockMarker(PlayerInteractEvent event, Player player,
                                  String markerType, Material material, float scale,
                                  @Nullable VaultColor color, int coinValue,
                                  @Nullable ItemStack icon, Component label) {
        Location cell = validatedAirCell(event, player);
        if (cell == null) return;

        World world = player.getWorld();
        double cx = cell.getBlockX() + 0.5, cz = cell.getBlockZ() + 0.5;
        double baseY = cell.getBlockY();
        float offset = (1.0f - scale) / 2.0f;
        Location spawnLoc = new Location(world, cell.getBlockX(), baseY, cell.getBlockZ());
        BlockData blockData = material.createBlockData();
        final String colorName = (color != null) ? color.name() : null;
        final int finalCoinValue = coinValue;
        final String groupId = UUID.randomUUID().toString();

        try {
            world.spawn(spawnLoc, BlockDisplay.class, display -> {
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
                pdc.set(BOUND_GROUP_KEY, PersistentDataType.STRING, groupId);
                if (colorName != null) {
                    pdc.set(VAULT_COLOR_KEY, PersistentDataType.STRING, colorName);
                }
                if (finalCoinValue > 0) {
                    pdc.set(COIN_VALUE_KEY, PersistentDataType.INTEGER, finalCoinValue);
                }
            });

            if (icon != null) {
                spawnIcon(world, cx, baseY + MARKER_ICON_HEIGHT, cz, groupId, icon, MARKER_ICON_SCALE);
            }
            spawnLabel(world, cx, baseY + MARKER_LABEL_HEIGHT, cz, groupId, label);
            sessionManager.getSession(player).pushUndo(groupId);
            player.sendActionBar(Component.text("Placed ", NamedTextColor.GREEN).append(label));

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn point marker: " + markerType, e);
            player.sendMessage(Component.text("Error placing marker entity.", NamedTextColor.RED));
        }
    }

    /**
     * Places a themed point marker whose data anchor is a floating ItemDisplay (used for coin/key),
     * plus a floating label. When {@code spin} is true the anchor is tagged so the marker animation
     * task rotates it (and billboarding is disabled so the rotation is visible).
     */
    private void placeItemMarker(PlayerInteractEvent event, Player player, String markerType,
                                 ItemStack anchorItem, float scale, double height, boolean spin,
                                 @Nullable VaultColor color, int coinValue, Component label) {
        Location cell = validatedAirCell(event, player);
        if (cell == null) return;

        World world = player.getWorld();
        double cx = cell.getBlockX() + 0.5, cz = cell.getBlockZ() + 0.5;
        double baseY = cell.getBlockY();
        final String colorName = (color != null) ? color.name() : null;
        final int finalCoinValue = coinValue;
        final boolean finalSpin = spin;
        final String groupId = UUID.randomUUID().toString();

        try {
            world.spawn(new Location(world, cx, baseY + height, cz), ItemDisplay.class, disp -> {
                disp.setItemStack(anchorItem);
                disp.setBillboard(finalSpin ? Display.Billboard.FIXED : Display.Billboard.CENTER);
                disp.setGravity(false);
                disp.setInvulnerable(true);
                disp.setPersistent(true);
                disp.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(scale, scale, scale),
                        new AxisAngle4f(0f, 0f, 0f, 1f)
                ));
                PersistentDataContainer pdc = disp.getPersistentDataContainer();
                pdc.set(BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte) 1);
                pdc.set(MARKER_TYPE_KEY, PersistentDataType.STRING, markerType);
                pdc.set(BOUND_GROUP_KEY, PersistentDataType.STRING, groupId);
                if (colorName != null) {
                    pdc.set(VAULT_COLOR_KEY, PersistentDataType.STRING, colorName);
                }
                if (finalCoinValue > 0) {
                    pdc.set(COIN_VALUE_KEY, PersistentDataType.INTEGER, finalCoinValue);
                }
                if (finalSpin) {
                    pdc.set(SPINNABLE_KEY, PersistentDataType.BYTE, (byte) 1);
                }
            });

            spawnLabel(world, cx, baseY + MARKER_LABEL_HEIGHT, cz, groupId, label);
            sessionManager.getSession(player).pushUndo(groupId);
            player.sendActionBar(Component.text("Placed ", NamedTextColor.GREEN).append(label));

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn item marker: " + markerType, e);
            player.sendMessage(Component.text("Error placing marker entity.", NamedTextColor.RED));
        }
    }

    /** Spawns a cosmetic floating icon (ItemDisplay, tagged ICON) that always faces the viewer. */
    private void spawnIcon(World world, double cx, double cy, double cz, String groupId,
                           ItemStack icon, float scale) {
        try {
            world.spawn(new Location(world, cx, cy, cz), ItemDisplay.class, disp -> {
                disp.setItemStack(icon);
                disp.setBillboard(Display.Billboard.CENTER);
                disp.setGravity(false);
                disp.setInvulnerable(true);
                disp.setPersistent(true);
                disp.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(scale, scale, scale),
                        new AxisAngle4f(0f, 0f, 0f, 1f)
                ));
                PersistentDataContainer pdc = disp.getPersistentDataContainer();
                pdc.set(BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte) 1);
                pdc.set(BOUND_GROUP_KEY, PersistentDataType.STRING, groupId);
                pdc.set(MARKER_TYPE_KEY, PersistentDataType.STRING, MT_ICON);
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to spawn marker icon", e);
        }
    }

    /** Spawns a cosmetic floating text label (TextDisplay, tagged LABEL) that always faces the viewer. */
    private void spawnLabel(World world, double cx, double cy, double cz, String groupId, Component text) {
        try {
            world.spawn(new Location(world, cx, cy, cz), TextDisplay.class, td -> {
                td.text(text);
                td.setBillboard(Display.Billboard.CENTER);
                td.setSeeThrough(true);
                td.setDefaultBackground(false);
                td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                td.setGravity(false);
                td.setInvulnerable(true);
                td.setPersistent(true);
                td.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(MARKER_LABEL_SCALE, MARKER_LABEL_SCALE, MARKER_LABEL_SCALE),
                        new AxisAngle4f(0f, 0f, 0f, 1f)
                ));
                PersistentDataContainer pdc = td.getPersistentDataContainer();
                pdc.set(BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte) 1);
                pdc.set(BOUND_GROUP_KEY, PersistentDataType.STRING, groupId);
                pdc.set(MARKER_TYPE_KEY, PersistentDataType.STRING, MT_LABEL);
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to spawn marker label", e);
        }
    }

    /** Builds the gold-nugget coin item with CustomModelData matching CoinStack's value thresholds. */
    private ItemStack buildCoinItem(int value) {
        ItemStack coin = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = coin.getItemMeta();
        if (meta != null) {
            int modelId = (value >= 50) ? COIN_MODEL_LARGE : (value >= 20) ? COIN_MODEL_MEDIUM : COIN_MODEL_SMALL;
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(List.of((float) modelId));
            meta.setCustomModelDataComponent(cmd);
            meta.displayName(Component.text("Coin x" + value, NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            coin.setItemMeta(meta);
        }
        return coin;
    }

    /** Builds the tripwire-hook key item, colored to the vault (mirrors the real Key item look). */
    private ItemStack buildKeyItem(@Nullable VaultColor color) {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            if (color != null) {
                meta.displayName(Component.text(color.name() + " Vault Key", color.getTextColor())
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                meta.displayName(Component.text("Key", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            key.setItemMeta(meta);
        }
        return key;
    }

    // -------------------------------------------------------------------------
    // Left-click: remove any marker
    // -------------------------------------------------------------------------

    private void handleLeftClick(Player player) {
        Predicate<Entity> filter = e ->
                e instanceof Display
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
            if (e instanceof Display
                    && e.getPersistentDataContainer().has(BUILD_MARKER_TAG, PersistentDataType.BYTE)
                    && groupId.equals(e.getPersistentDataContainer().get(BOUND_GROUP_KEY, PersistentDataType.STRING))) {
                toRemove.add(e);
            }
        }
        toRemove.forEach(Entity::remove);
        return toRemove.size();
    }
}
