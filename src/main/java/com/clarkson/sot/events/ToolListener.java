package com.clarkson.sot.events;

import com.clarkson.sot.commands.BuilderMode;
import com.clarkson.sot.dungeon.segment.Direction;
import com.clarkson.sot.main.SoT;
import com.clarkson.sot.utils.BuilderSessionManager;
import com.clarkson.sot.utils.PlayerBuilderSession;
import com.clarkson.sot.utils.SegmentBuilderKeys;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
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
import org.joml.Vector3f;

import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Handles all interactions with the master Segment Builder tool.
 *
 * Right-click: place marker (mode-dependent).
 * Left-click: remove marker in sight, or cancel in-progress bound selection.
 *
 * Marker visuals use BlockDisplay entities so they are clearly visible at distance.
 * All markers are tagged with BUILD_MARKER_TAG and are never included in the saved schematic.
 */
public class ToolListener implements Listener {

    // --- Marker type string constants (package-private so SaveSegmentCommand can use them) ---
    static final String MT_ENTRYPOINT     = "ENTRYPOINT";
    static final String MT_COIN_SPAWN     = "COIN_SPAWN";
    static final String MT_ITEM_SPAWN     = "ITEM_SPAWN";
    static final String MT_SAND_SPAWN     = "SAND_SPAWN";
    static final String MT_SAND_SACRIFICE = "SAND_SACRIFICE";
    static final String MT_MOB_SPAWNER    = "MOB_SPAWNER";
    static final String MT_LEVER          = "LEVER";
    static final String MT_VAULT_DOOR     = "VAULT_DOOR";  // data entity
    static final String MT_GATE           = "GATE";         // data entity
    static final String MT_BOUND_VISUAL   = "BOUND_VISUAL"; // corner visual, no data
    static final String MT_BOUND_TEMP     = "BOUND_TEMP";   // first-corner pending visual

    private static final String TOOL_ID = "SEGMENT_BUILDER";

    private final SoT plugin;
    private final BuilderSessionManager sessionManager;
    final SegmentBuilderKeys keys; // package-private for SaveSegmentCommand

    public ToolListener(SoT plugin, BuilderSessionManager sessionManager, SegmentBuilderKeys keys) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.keys = keys;
    }

    // -------------------------------------------------------------------------
    // Main event dispatcher
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (!TOOL_ID.equals(meta.getPersistentDataContainer().get(keys.TOOL_TYPE, PersistentDataType.STRING))) return;

        event.setCancelled(true);

        PlayerBuilderSession session = sessionManager.getSession(player.getUniqueId());
        Action action = event.getAction();

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            handleRemoval(player, session);
            return;
        }

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        BuilderMode mode = session.getMode();
        switch (mode) {
            case ENTRY_POINT -> handleEntryPoint(event, player, session);
            case VAULT_DOOR  -> handleBoundCorner(event, player, session, false);
            case GATE        -> handleBoundCorner(event, player, session, true);
            default          -> handleSimpleMarker(event, player, mode);
        }
    }

    // -------------------------------------------------------------------------
    // Entry point handler
    // -------------------------------------------------------------------------

    private void handleEntryPoint(PlayerInteractEvent event, Player player, PlayerBuilderSession session) {
        if (!player.hasPermission("sot.builder.place")) {
            player.sendActionBar(Component.text("No permission.", NamedTextColor.RED));
            return;
        }

        // Check if player is looking at an existing entry point marker → rotate it
        Predicate<Entity> filter = e -> e instanceof BlockDisplay
                && e.getPersistentDataContainer().has(keys.BUILD_MARKER_TAG, PersistentDataType.BYTE)
                && MT_ENTRYPOINT.equals(e.getPersistentDataContainer().get(keys.MARKER_TYPE, PersistentDataType.STRING));

        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), 5.0, filter);

        if (result != null && result.getHitEntity() instanceof BlockDisplay existing) {
            rotateEntryPoint(player, existing);
            return;
        }

        // Otherwise place a new marker
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            player.sendActionBar(Component.text("Right-click a wall face to place, or an existing marker to rotate.", NamedTextColor.YELLOW));
            return;
        }

        Block clicked = event.getClickedBlock();
        BlockFace face = event.getBlockFace();
        Block target = clicked.getRelative(face);

        if (!target.getType().isAir()) {
            player.sendActionBar(Component.text("Cannot place here — air block required.", NamedTextColor.RED));
            return;
        }

        Direction dir = Direction.fromBlockFace(face);
        if (dir == null || dir == Direction.UP || dir == Direction.DOWN) {
            dir = Direction.fromYaw(player.getLocation().getYaw());
        }

        final Direction finalDir = dir;
        Location spawnLoc = target.getLocation();

        try {
            player.getWorld().spawn(spawnLoc, BlockDisplay.class, display -> {
                display.setBlock(Bukkit.createBlockData(Material.LIME_STAINED_GLASS));
                display.setGravity(false);
                display.setInvulnerable(true);
                display.setPersistent(true);
                display.setGlowing(true);
                display.setTransformation(fullBlockTransform());

                PersistentDataContainer pdc = display.getPersistentDataContainer();
                pdc.set(keys.BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte) 1);
                pdc.set(keys.MARKER_TYPE, PersistentDataType.STRING, MT_ENTRYPOINT);
                pdc.set(keys.DIRECTION, PersistentDataType.STRING, finalDir.name());
            });
            player.sendActionBar(Component.text("Entry Point [" + finalDir.name() + "] placed — right-click to rotate.", NamedTextColor.GREEN));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn entry point marker", e);
            player.sendMessage(Component.text("Error placing marker.", NamedTextColor.RED));
        }
    }

    private void rotateEntryPoint(Player player, BlockDisplay display) {
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        String currentStr = pdc.getOrDefault(keys.DIRECTION, PersistentDataType.STRING, Direction.NORTH.name());
        Direction current;
        try { current = Direction.valueOf(currentStr); } catch (IllegalArgumentException e) { current = Direction.NORTH; }

        Direction next = switch (current) {
            case NORTH -> Direction.EAST;
            case EAST  -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            default    -> Direction.NORTH;
        };

        pdc.set(keys.DIRECTION, PersistentDataType.STRING, next.name());
        player.sendActionBar(Component.text("Entry Point rotated → " + next.name(), NamedTextColor.YELLOW));
    }

    // -------------------------------------------------------------------------
    // Bound selection handler (VAULT_DOOR / GATE)
    // -------------------------------------------------------------------------

    private void handleBoundCorner(PlayerInteractEvent event, Player player, PlayerBuilderSession session, boolean isGate) {
        if (!player.hasPermission("sot.builder.place")) {
            player.sendActionBar(Component.text("No permission.", NamedTextColor.RED));
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            String hint = session.hasPendingCorner()
                    ? "First corner set — right-click a block face to set the second corner."
                    : "Right-click a block face to set the first corner of the opening.";
            player.sendActionBar(Component.text(hint, NamedTextColor.YELLOW));
            return;
        }

        Block clicked = event.getClickedBlock();
        Block target = clicked.getRelative(event.getBlockFace());

        if (!target.getType().isAir()) {
            player.sendActionBar(Component.text(
                "Corner must be an open (air) block — click the face bordering the opening.", NamedTextColor.RED));
            return;
        }

        Location cornerLoc = target.getLocation();
        Material mat = isGate ? Material.LIGHT_GRAY_STAINED_GLASS : Material.PURPLE_STAINED_GLASS;
        String label = isGate ? "Gate" : "Vault Door";

        if (!session.hasPendingCorner()) {
            // First corner: place temp entity and store in session
            UUID tempId = spawnTempCornerMarker(player, cornerLoc, mat);
            if (tempId != null) {
                session.setPendingCorner(cornerLoc, tempId);
                player.sendActionBar(Component.text(
                    label + " — first corner set. Right-click the opposite corner.", NamedTextColor.YELLOW));
            }
        } else {
            // Second corner: complete the bound
            Location corner1 = session.getPendingCorner1();
            UUID tempId = session.getPendingCornerEntityId();
            if (tempId != null) {
                Entity temp = Bukkit.getEntity(tempId);
                if (temp != null) temp.remove();
            }
            session.clearPendingBound();
            completeBound(player, corner1, cornerLoc, isGate ? MT_GATE : MT_VAULT_DOOR, mat, label);
        }
    }

    private UUID spawnTempCornerMarker(Player player, Location loc, Material mat) {
        UUID[] holder = {null};
        try {
            player.getWorld().spawn(loc, BlockDisplay.class, display -> {
                display.setBlock(Bukkit.createBlockData(mat));
                display.setGravity(false);
                display.setInvulnerable(true);
                display.setPersistent(true);
                display.setGlowing(true);
                // Slightly inset so it's visually distinct from permanent corners
                display.setTransformation(new Transformation(
                    new Vector3f(0.1f, 0.1f, 0.1f), new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(0.8f, 0.8f, 0.8f), new AxisAngle4f(0, 0, 0, 1)
                ));
                PersistentDataContainer pdc = display.getPersistentDataContainer();
                pdc.set(keys.BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte) 1);
                pdc.set(keys.MARKER_TYPE, PersistentDataType.STRING, MT_BOUND_TEMP);
                holder[0] = display.getUniqueId();
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn temp corner marker", e);
        }
        return holder[0];
    }

    private void completeBound(Player player, Location corner1, Location corner2,
                               String markerType, Material mat, String label) {
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        String boundId = UUID.randomUUID().toString();

        // Two corner visuals
        spawnBoundCornerVisual(player, new Location(player.getWorld(), minX, minY, minZ), mat, boundId);
        spawnBoundCornerVisual(player, new Location(player.getWorld(), maxX, maxY, maxZ), mat, boundId);

        // Central data entity — stores the absolute min/max for SaveSegmentCommand to read
        double cx = (minX + maxX + 1) / 2.0;
        double cy = (minY + maxY + 1) / 2.0;
        double cz = (minZ + maxZ + 1) / 2.0;
        Location centerLoc = new Location(player.getWorld(), cx, cy, cz);

        try {
            player.getWorld().spawn(centerLoc, BlockDisplay.class, display -> {
                display.setBlock(Bukkit.createBlockData(mat));
                display.setGravity(false);
                display.setInvulnerable(true);
                display.setPersistent(true);
                display.setGlowing(true);
                // Small cube to mark the data entity
                display.setTransformation(new Transformation(
                    new Vector3f(-0.15f, -0.15f, -0.15f), new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(0.3f, 0.3f, 0.3f), new AxisAngle4f(0, 0, 0, 1)
                ));
                PersistentDataContainer pdc = display.getPersistentDataContainer();
                pdc.set(keys.BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte) 1);
                pdc.set(keys.MARKER_TYPE, PersistentDataType.STRING, markerType);
                pdc.set(keys.BOUND_ID, PersistentDataType.STRING, boundId);
                pdc.set(keys.BOUND_MIN_X, PersistentDataType.INTEGER, minX);
                pdc.set(keys.BOUND_MIN_Y, PersistentDataType.INTEGER, minY);
                pdc.set(keys.BOUND_MIN_Z, PersistentDataType.INTEGER, minZ);
                pdc.set(keys.BOUND_MAX_X, PersistentDataType.INTEGER, maxX);
                pdc.set(keys.BOUND_MAX_Y, PersistentDataType.INTEGER, maxY);
                pdc.set(keys.BOUND_MAX_Z, PersistentDataType.INTEGER, maxZ);
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn bound data entity", e);
            player.sendMessage(Component.text("Error placing bound.", NamedTextColor.RED));
            return;
        }

        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        player.sendActionBar(Component.text(
            label + " bound placed. " + w + " wide × " + h + " tall.", NamedTextColor.GREEN));
    }

    private void spawnBoundCornerVisual(Player player, Location loc, Material mat, String boundId) {
        try {
            player.getWorld().spawn(loc, BlockDisplay.class, display -> {
                display.setBlock(Bukkit.createBlockData(mat));
                display.setGravity(false);
                display.setInvulnerable(true);
                display.setPersistent(true);
                display.setGlowing(true);
                display.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f), new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(0.5f, 0.5f, 0.5f), new AxisAngle4f(0, 0, 0, 1)
                ));
                PersistentDataContainer pdc = display.getPersistentDataContainer();
                pdc.set(keys.BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte) 1);
                pdc.set(keys.MARKER_TYPE, PersistentDataType.STRING, MT_BOUND_VISUAL);
                pdc.set(keys.BOUND_ID, PersistentDataType.STRING, boundId);
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn bound corner visual", e);
        }
    }

    // -------------------------------------------------------------------------
    // Simple single-point marker handler (all other modes)
    // -------------------------------------------------------------------------

    private void handleSimpleMarker(PlayerInteractEvent event, Player player, BuilderMode mode) {
        if (!player.hasPermission("sot.builder.place")) {
            player.sendActionBar(Component.text("No permission.", NamedTextColor.RED));
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            player.sendActionBar(Component.text(mode.getHint(), NamedTextColor.YELLOW));
            return;
        }

        Block clicked = event.getClickedBlock();
        Block target = clicked.getRelative(event.getBlockFace());

        if (!target.getType().isAir()) {
            player.sendActionBar(Component.text("Cannot place here — air block required.", NamedTextColor.RED));
            return;
        }

        Material mat;
        String markerType;
        String label;

        switch (mode) {
            case COIN_SPAWN     -> { mat = Material.GOLD_BLOCK;          markerType = MT_COIN_SPAWN;     label = "Coin Spawn"; }
            case ITEM_SPAWN     -> { mat = Material.CYAN_CONCRETE;       markerType = MT_ITEM_SPAWN;     label = "Item Spawn"; }
            case SAND_SPAWN     -> { mat = Material.SAND;                markerType = MT_SAND_SPAWN;     label = "Sand Spawn"; }
            case SAND_SACRIFICE -> { mat = Material.ORANGE_CONCRETE;     markerType = MT_SAND_SACRIFICE; label = "Sand Sacrifice"; }
            case MOB_SPAWNER    -> { mat = Material.BONE_BLOCK;          markerType = MT_MOB_SPAWNER;    label = "Mob Spawner"; }
            case LEVER          -> { mat = Material.REDSTONE_BLOCK;      markerType = MT_LEVER;          label = "Lever"; }
            default             -> { player.sendActionBar(Component.text("Unknown mode.", NamedTextColor.RED)); return; }
        }

        final String finalMarkerType = markerType;
        final String finalLabel = label;
        Location spawnLoc = target.getLocation();

        try {
            player.getWorld().spawn(spawnLoc, BlockDisplay.class, display -> {
                display.setBlock(Bukkit.createBlockData(mat));
                display.setGravity(false);
                display.setInvulnerable(true);
                display.setPersistent(true);
                display.setGlowing(true);
                // Small cube sitting on the floor of the target block
                display.setTransformation(new Transformation(
                    new Vector3f(0.25f, 0f, 0.25f), new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(0.5f, 0.5f, 0.5f), new AxisAngle4f(0, 0, 0, 1)
                ));
                PersistentDataContainer pdc = display.getPersistentDataContainer();
                pdc.set(keys.BUILD_MARKER_TAG, PersistentDataType.BYTE, (byte) 1);
                pdc.set(keys.MARKER_TYPE, PersistentDataType.STRING, finalMarkerType);
            });
            player.sendActionBar(Component.text(finalLabel + " marker placed.", NamedTextColor.GREEN));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn " + markerType + " marker", e);
            player.sendMessage(Component.text("Error placing marker.", NamedTextColor.RED));
        }
    }

    // -------------------------------------------------------------------------
    // Removal handler
    // -------------------------------------------------------------------------

    private void handleRemoval(Player player, PlayerBuilderSession session) {
        if (!player.hasPermission("sot.builder.remove")) {
            player.sendActionBar(Component.text("No permission to remove markers.", NamedTextColor.RED));
            return;
        }

        // If a bound is in progress, left-click cancels it
        if (session.hasPendingCorner()) {
            UUID tempId = session.getPendingCornerEntityId();
            if (tempId != null) {
                Entity temp = Bukkit.getEntity(tempId);
                if (temp != null) temp.remove();
            }
            session.clearPendingBound();
            player.sendActionBar(Component.text("Pending bound selection cancelled.", NamedTextColor.YELLOW));
            return;
        }

        Predicate<Entity> filter = e -> e instanceof BlockDisplay
                && e.getPersistentDataContainer().has(keys.BUILD_MARKER_TAG, PersistentDataType.BYTE);

        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), 6.0, filter);

        if (result == null || result.getHitEntity() == null) {
            player.sendActionBar(Component.text("No marker in sight.", NamedTextColor.GRAY));
            return;
        }

        Entity hit = result.getHitEntity();
        PersistentDataContainer pdc = hit.getPersistentDataContainer();
        String markerType = pdc.getOrDefault(keys.MARKER_TYPE, PersistentDataType.STRING, "Unknown");
        String boundId = pdc.get(keys.BOUND_ID, PersistentDataType.STRING);

        // If the removed entity is the temp first-corner, also clear the session
        if (MT_BOUND_TEMP.equals(markerType)) {
            session.clearPendingBound();
        }

        if (boundId != null) {
            // Remove all entities belonging to this bound group
            int count = 0;
            for (Entity e : player.getWorld().getEntities()) {
                if (e instanceof BlockDisplay
                        && boundId.equals(e.getPersistentDataContainer().get(keys.BOUND_ID, PersistentDataType.STRING))) {
                    e.remove();
                    count++;
                }
            }
            player.sendActionBar(Component.text("Removed bound (" + count + " entities).", NamedTextColor.YELLOW));
        } else {
            hit.remove();
            player.sendActionBar(Component.text("Removed " + markerType + " marker.", NamedTextColor.YELLOW));
        }
    }

    // -------------------------------------------------------------------------
    // Transformation helpers
    // -------------------------------------------------------------------------

    /** A transformation that fills the full 1×1×1 block space starting at the entity origin. */
    private static Transformation fullBlockTransform() {
        return new Transformation(
            new Vector3f(0f, 0f, 0f), new AxisAngle4f(0, 0, 0, 1),
            new Vector3f(1f, 1f, 1f), new AxisAngle4f(0, 0, 0, 1)
        );
    }
}
