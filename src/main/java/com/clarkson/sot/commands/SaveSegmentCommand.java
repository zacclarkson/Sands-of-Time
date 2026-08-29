package com.clarkson.sot.commands;

import com.clarkson.sot.dungeon.VaultColor;
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.Segment.RelativeEntryPoint;
import com.clarkson.sot.dungeon.segment.SegmentBound;
import com.clarkson.sot.dungeon.segment.SegmentType;
import com.clarkson.sot.dungeon.segment.Direction;
import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.events.SegmentBuilderKeys;
import com.clarkson.sot.main.SoT;
import com.clarkson.sot.utils.StructureSaver;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * /sotsavesegment <name> <type>
 * <p>
 * Scans all build-phase marker entities inside the player's WorldEdit selection,
 * converts their positions to relative offsets from the selection's minimum corner,
 * and saves the segment template + schematic.
 * <p>
 * The schematic filename is auto-derived as {@code <name>.schem}.
 */
public class SaveSegmentCommand implements CommandExecutor {

    /** Blocks beyond the selection to also scan, so just-outside markers can be reported. */
    private static final int OUTSIDE_SCAN_PAD = 2;

    private final SoT plugin;
    private final StructureSaver structureSaver;
    private final WorldEditPlugin worldEdit;

    // PDC keys
    private final NamespacedKey BUILD_MARKER_TAG;
    private final NamespacedKey MARKER_TYPE_KEY;
    private final NamespacedKey DIRECTION_KEY;
    private final NamespacedKey VAULT_COLOR_KEY;
    private final NamespacedKey COIN_VALUE_KEY;
    private final NamespacedKey BOUND_MIN_KEY;
    private final NamespacedKey BOUND_MAX_KEY;

    public SaveSegmentCommand(@NotNull SoT plugin) {
        this.plugin = plugin;
        this.structureSaver = new StructureSaver(plugin);

        Plugin wep = Bukkit.getServer().getPluginManager().getPlugin("WorldEdit");
        this.worldEdit = (wep instanceof WorldEditPlugin) ? (WorldEditPlugin) wep : null;
        if (this.worldEdit == null) {
            plugin.getLogger().severe("WorldEdit not found — /sotsavesegment will not work.");
        }

        BUILD_MARKER_TAG = new NamespacedKey(plugin, SegmentBuilderKeys.BUILD_MARKER_TAG);
        MARKER_TYPE_KEY  = new NamespacedKey(plugin, SegmentBuilderKeys.MARKER_TYPE);
        DIRECTION_KEY    = new NamespacedKey(plugin, SegmentBuilderKeys.DIRECTION);
        VAULT_COLOR_KEY  = new NamespacedKey(plugin, SegmentBuilderKeys.VAULT_COLOR);
        COIN_VALUE_KEY   = new NamespacedKey(plugin, SegmentBuilderKeys.COIN_VALUE);
        BOUND_MIN_KEY    = new NamespacedKey(plugin, SegmentBuilderKeys.BOUND_MIN);
        BOUND_MAX_KEY    = new NamespacedKey(plugin, SegmentBuilderKeys.BOUND_MAX);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (worldEdit == null) {
            sender.sendMessage(Component.text("WorldEdit unavailable.", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /" + label + " <name> <type>", NamedTextColor.RED));
            player.sendMessage(Component.text("Types: " + getTypeNames(), NamedTextColor.GRAY));
            return true;
        }

        String segmentName = args[0];
        SegmentType segmentType;
        try {
            segmentType = SegmentType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Invalid type: " + args[1], NamedTextColor.RED));
            player.sendMessage(Component.text("Valid types: " + getTypeNames(), NamedTextColor.GRAY));
            return true;
        }

        // --- Get WorldEdit selection ---
        Region selection;
        Location worldOrigin;
        BlockVector3 size;
        BlockVector3 selMin;

        try {
            com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);
            SessionManager sm = WorldEdit.getInstance().getSessionManager();
            LocalSession session = sm.get(wePlayer);
            selection = session.getSelection(wePlayer.getWorld());
            selMin = selection.getMinimumPoint();
            BlockVector3 selMax = selection.getMaximumPoint();
            size = selMax.subtract(selMin).add(1, 1, 1);
            worldOrigin = BukkitAdapter.adapt(player.getWorld(), selMin);
        } catch (IncompleteRegionException e) {
            player.sendMessage(Component.text(
                    "Your WorldEdit selection is incomplete. Select two points first.",
                    NamedTextColor.RED));
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting WE selection for " + player.getName(), e);
            player.sendMessage(Component.text("Error reading selection.", NamedTextColor.RED));
            return true;
        }

        // --- Collect build marker entities in and just around the selection ---
        // Inclusive block bounds of the WorldEdit selection.
        final int minX = selMin.x(), minY = selMin.y(), minZ = selMin.z();
        final int maxX = minX + size.x() - 1, maxY = minY + size.y() - 1, maxZ = minZ + size.z() - 1;

        // Scan a box padded by OUTSIDE_SCAN_PAD blocks so markers sitting just outside the selection
        // are seen and can be reported (an entry-point anchor sits one cell beyond its wall). Whether
        // a marker is actually inside is decided per-entity by an explicit inclusive block-range test
        // below — not by BoundingBox overlap, which is edge-exclusive and unreliable for the zero-size
        // bounding boxes of Display entities.
        BoundingBox scanBox = new BoundingBox(
                minX - OUTSIDE_SCAN_PAD,       minY - OUTSIDE_SCAN_PAD,       minZ - OUTSIDE_SCAN_PAD,
                maxX + 1 + OUTSIDE_SCAN_PAD,   maxY + 1 + OUTSIDE_SCAN_PAD,   maxZ + 1 + OUTSIDE_SCAN_PAD
        );
        Collection<Entity> entities = player.getWorld().getNearbyEntities(scanBox,
                e -> e instanceof Display
                  && e.getPersistentDataContainer().has(BUILD_MARKER_TAG, PersistentDataType.BYTE));

        // --- Parse markers into Segment fields ---
        List<RelativeEntryPoint> entryPoints   = new ArrayList<>();
        List<BlockVector3> sandSpawns          = new ArrayList<>();
        List<BlockVector3> itemSpawns          = new ArrayList<>();
        List<BlockVector3> coinSpawns          = new ArrayList<>();
        List<BlockVector3> sandSacrifices      = new ArrayList<>();
        List<BlockVector3> mobSpawners         = new ArrayList<>();
        List<SegmentBound> gates               = new ArrayList<>();

        SegmentBound vaultDoorBound            = null;
        BlockVector3 vaultOffset               = null;
        BlockVector3 keyOffset                 = null;
        BlockVector3 leverOffset               = null;
        BlockVector3 safeExitOffset            = null;
        SegmentBound safeExitBound             = null;
        BlockVector3 bankOffset                = null;
        List<BlockVector3> deathCageOffsets    = new ArrayList<>();
        List<BlockVector3> sandTimerOffsets    = new ArrayList<>();
        VaultColor containedVault              = null;
        VaultColor containedVaultKey           = null;

        int totalCoins = 0;

        // Placement warnings, collected then reported once so a busy segment doesn't spam chat.
        List<BlockVector3> entriesOffEdge = new ArrayList<>();   // entry not on the face it faces
        List<String> markersOffGround     = new ArrayList<>();   // floor marker with no solid block below
        List<String> markersOutside       = new ArrayList<>();   // data marker outside the selection

        for (Entity entity : entities) {
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            String type = pdc.getOrDefault(MARKER_TYPE_KEY, PersistentDataType.STRING, "");

            // Explicit inclusive block-range test — WYSIWYG, and immune to zero-size-box edge quirks.
            int bx = entity.getLocation().getBlockX();
            int by = entity.getLocation().getBlockY();
            int bz = entity.getLocation().getBlockZ();
            boolean inside = bx >= minX && bx <= maxX && by >= minY && by <= maxY && bz >= minZ && bz <= maxZ;
            if (!inside) {
                // A data marker (not a cosmetic/frame piece) just outside won't be saved — flag it.
                if (!type.isEmpty() && !isCosmeticType(type)) {
                    markersOutside.add(type + " at " + bx + "," + by + "," + bz);
                }
                continue;
            }

            // Skip cosmetic entities — data lives on the anchor only. ENTRY_FRAME/BOUND_FRAME/
            // BOUND_CORNER1 are frame pieces; ICON/LABEL are the themed icons and floating labels.
            if (isCosmeticType(type)) {
                continue;
            }

            BlockVector3 relPos = toRelative(entity.getLocation(), selMin);

            // Floor markers (loot, coins, spawners, sand) should rest on solid ground so items and
            // mobs don't spawn floating. Warn (don't block) if the block directly below is not solid.
            if (isFloorMarker(type) && !hasSolidGroundBelow(entity.getLocation())) {
                markersOffGround.add(type + " at " + relPos.x() + "," + relPos.y() + "," + relPos.z());
            }

            switch (type) {
                case "ENTRY_POINT": {
                    String dirStr = pdc.get(DIRECTION_KEY, PersistentDataType.STRING);
                    if (dirStr != null) {
                        try {
                            Direction dir = Direction.valueOf(dirStr);
                            entryPoints.add(new RelativeEntryPoint(relPos, dir));
                            // The arrow points OUT of the segment, so the entry must sit on the
                            // selection face it faces (e.g. an EAST entry on the max-X face). If it
                            // doesn't, neighbours won't line up when the generator connects via
                            // direction.getOpposite().
                            if (!isEntryOnMatchingFace(relPos, dir, size)) {
                                entriesOffEdge.add(relPos);
                            }
                        } catch (IllegalArgumentException ex) {
                            warn(player, "Entry point with unknown direction '" + dirStr + "' — skipped.");
                        }
                    }
                    break;
                }
                case "VAULT_DOOR": {
                    SegmentBound bound = readBound(pdc, selMin);
                    if (bound != null) {
                        if (vaultDoorBound != null) {
                            warn(player, "Multiple VAULT_DOOR markers found — using first.");
                        } else {
                            vaultDoorBound = bound;
                            String colorStr = pdc.get(VAULT_COLOR_KEY, PersistentDataType.STRING);
                            if (colorStr != null) {
                                try { containedVault = VaultColor.valueOf(colorStr); }
                                catch (IllegalArgumentException ex) {
                                    warn(player, "VAULT_DOOR has unknown color '" + colorStr + "'.");
                                }
                            }
                        }
                    }
                    break;
                }
                case "VAULT_MARKER": {
                    if (vaultOffset != null) {
                        warn(player, "Multiple VAULT_MARKER entities — using first.");
                    } else {
                        vaultOffset = relPos;
                        if (containedVault == null) {
                            String colorStr = pdc.get(VAULT_COLOR_KEY, PersistentDataType.STRING);
                            if (colorStr != null) {
                                try { containedVault = VaultColor.valueOf(colorStr); }
                                catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }
                    break;
                }
                case "KEY_SPAWN": {
                    if (keyOffset != null) {
                        warn(player, "Multiple KEY_SPAWN entities — using first.");
                    } else {
                        keyOffset = relPos;
                        String colorStr = pdc.get(VAULT_COLOR_KEY, PersistentDataType.STRING);
                        if (colorStr != null) {
                            try { containedVaultKey = VaultColor.valueOf(colorStr); }
                            catch (IllegalArgumentException ignored) {}
                        }
                    }
                    break;
                }
                case "GATE": {
                    SegmentBound bound = readBound(pdc, selMin);
                    if (bound != null) gates.add(bound);
                    break;
                }
                case "LEVER": {
                    if (leverOffset != null) {
                        warn(player, "Multiple LEVER markers — only one allowed. Using first.");
                    } else {
                        leverOffset = relPos;
                    }
                    break;
                }
                case "SAND_SPAWN":
                    sandSpawns.add(relPos);
                    break;
                case "SAND_SACRIFICE":
                    sandSacrifices.add(relPos);
                    break;
                case "SAFE_EXIT": {
                    SegmentBound bound = readBound(pdc, selMin);
                    if (bound != null) {
                        if (safeExitBound != null) {
                            warn(player, "Multiple SAFE_EXIT markers — only one allowed. Using first.");
                        } else {
                            safeExitBound = bound;
                            // Keep a representative point (bound min) for the legacy escape path.
                            safeExitOffset = bound.getMin();
                        }
                    }
                    break;
                }
                case "BANK": {
                    if (bankOffset != null) {
                        warn(player, "Multiple BANK markers — only one allowed. Using first.");
                    } else {
                        bankOffset = relPos;
                    }
                    break;
                }
                case "DEATH_CAGE":
                    deathCageOffsets.add(relPos);
                    break;
                case "TIMER_DEPOSIT":
                    sandTimerOffsets.add(relPos);
                    break;
                case "COIN_SPAWN": {
                    coinSpawns.add(relPos);
                    Integer val = pdc.get(COIN_VALUE_KEY, PersistentDataType.INTEGER);
                    totalCoins += (val != null) ? val : 10;
                    break;
                }
                case "ITEM_SPAWN":
                    itemSpawns.add(relPos);
                    break;
                case "MOB_SPAWNER":
                    mobSpawners.add(relPos);
                    break;
                default:
                    if (!type.isEmpty()) {
                        plugin.getLogger().fine("[SaveSegment] Unknown marker type '" + type + "' — skipped.");
                    }
                    break;
            }
        }

        // --- Validate ---
        if (entryPoints.isEmpty()) {
            warn(player, "No ENTRY_POINT markers — this segment can't connect to anything.");
        }
        if (!entriesOffEdge.isEmpty()) {
            warn(player, entriesOffEdge.size() + " entry point(s) not on the segment face they point "
                    + "toward — neighbours won't line up. Offending offsets: " + formatVecs(entriesOffEdge));
        }
        if (!markersOffGround.isEmpty()) {
            warn(player, markersOffGround.size() + " floor marker(s) with no solid block below "
                    + "(will spawn floating): " + String.join("; ", limit(markersOffGround, 8)));
        }
        if (!markersOutside.isEmpty()) {
            warn(player, markersOutside.size() + " marker(s) are OUTSIDE your selection and were NOT "
                    + "saved — grow the selection to include them (entry-point anchors sit one block "
                    + "beyond their wall). Missing: " + String.join("; ", limit(markersOutside, 8)));
        }

        if (!gates.isEmpty() && leverOffset == null) {
            player.sendMessage(Component.text(
                    "Save failed: segment has " + gates.size() + " gate(s) but no LEVER marker.",
                    NamedTextColor.RED));
            return true;
        }
        if (gates.isEmpty() && leverOffset != null) {
            player.sendMessage(Component.text(
                    "Warning: LEVER marker present but no gates found.", NamedTextColor.YELLOW));
        }
        if (deathCageOffsets.size() > 4) {
            warn(player, deathCageOffsets.size() + " DEATH_CAGE markers — the runtime uses at most 4 "
                    + "(one per player); extras will be ignored.");
        }

        // --- Print summary ---
        player.sendMessage(Component.text("--- Segment Marker Summary ---", NamedTextColor.GOLD));
        player.sendMessage(info("Entry Points",    entryPoints.size()));
        player.sendMessage(info("Sand Spawns",     sandSpawns.size()));
        player.sendMessage(info("Item Spawns",     itemSpawns.size()));
        player.sendMessage(info("Coin Spawns",     coinSpawns.size())
                .append(Component.text(" (total value: " + totalCoins + ")", NamedTextColor.GRAY)));
        player.sendMessage(info("Sand Sacrifices", sandSacrifices.size()));
        player.sendMessage(info("Mob Spawners",    mobSpawners.size()));
        player.sendMessage(info("Gates",           gates.size()));
        player.sendMessage(Component.text("  Lever: ", NamedTextColor.WHITE)
                .append(Component.text(leverOffset != null ? "yes" : "none",
                        leverOffset != null ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  Vault Door: ", NamedTextColor.WHITE)
                .append(Component.text(vaultDoorBound != null
                        ? (containedVault != null ? containedVault.name() : "color unknown")
                        : "none",
                        vaultDoorBound != null ? NamedTextColor.AQUA : NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  Vault Marker: ", NamedTextColor.WHITE)
                .append(Component.text(vaultOffset != null ? "yes" : "none",
                        vaultOffset != null ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  Key Spawn: ", NamedTextColor.WHITE)
                .append(Component.text(keyOffset != null
                        ? (containedVaultKey != null ? containedVaultKey.name() : "color unknown")
                        : "none",
                        keyOffset != null ? NamedTextColor.AQUA : NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  Safe Exit: ", NamedTextColor.WHITE)
                .append(Component.text(safeExitBound != null ? "yes (portal area)" : "none",
                        safeExitBound != null ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  Bank: ", NamedTextColor.WHITE)
                .append(Component.text(bankOffset != null ? "yes" : "none",
                        bankOffset != null ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
        player.sendMessage(info("Death Cages",    deathCageOffsets.size()));
        player.sendMessage(info("Timer Deposits", sandTimerOffsets.size()));

        // A hub without an exit still saves; escaping just falls back to the hub location.
        if (segmentType == SegmentType.HUB && safeExitBound == null) {
            warn(player, "HUB segment has no SAFE_EXIT marker — escaping will fall back to the hub.");
        } else if (segmentType != SegmentType.HUB && safeExitBound != null) {
            warn(player, "SAFE_EXIT on a non-HUB segment — a HUB segment's marker takes priority.");
        }

        // --- Build Segment ---
        String schematicFileName = segmentName + ".schem";
        Segment segmentTemplate;
        try {
            segmentTemplate = new Segment(
                    segmentName, segmentType, schematicFileName, size,
                    entryPoints, sandSpawns, itemSpawns, coinSpawns,
                    totalCoins, containedVault, containedVaultKey,
                    vaultOffset, keyOffset,
                    vaultDoorBound, gates, leverOffset,
                    sandSacrifices, mobSpawners,
                    safeExitOffset,
                    bankOffset, deathCageOffsets, safeExitBound, sandTimerOffsets
            );
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error constructing Segment for " + segmentName, e);
            player.sendMessage(Component.text("Error creating segment data.", NamedTextColor.RED));
            return true;
        }

        PlacedSegment placed = new PlacedSegment(segmentTemplate, worldOrigin, 0);

        player.sendMessage(Component.text("Saving segment '" + segmentName + "'...", NamedTextColor.YELLOW));
        boolean success = structureSaver.saveStructure(placed);

        if (success) {
            player.sendMessage(Component.text("Segment saved: " + segmentName + ".json + "
                    + schematicFileName, NamedTextColor.GREEN));
            player.sendMessage(Component.text(
                    "Run /sotreloadsegments to load it now (or restart the server).",
                    NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text(
                    "Failed to save segment. Check console for errors.", NamedTextColor.RED));
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Cosmetic / frame marker types that carry no saved data (skipped by the scan). */
    private boolean isCosmeticType(String type) {
        switch (type) {
            case "ENTRY_FRAME":
            case "BOUND_FRAME":
            case "BOUND_CORNER1":
            case "ICON":
            case "LABEL":
            case "DEATH_REVIVE":
                return true;
            default:
                return false;
        }
    }

    /** Floor-resting marker types that should sit on solid ground. */
    private boolean isFloorMarker(String type) {
        switch (type) {
            case "COIN_SPAWN":
            case "ITEM_SPAWN":
            case "MOB_SPAWNER":
            case "SAND_SPAWN":
            case "SAND_SACRIFICE":
                return true;
            default:
                return false;
        }
    }

    /** True if the block directly below this location is solid (a floor to rest on). */
    private boolean hasSolidGroundBelow(Location loc) {
        Block below = loc.getBlock().getRelative(BlockFace.DOWN);
        return below.getType().isSolid();
    }

    /**
     * True if an entry point sits on the selection face its arrow points toward. The arrow points
     * OUT of the segment, so an EAST entry must be on the max-X face, WEST on min-X, etc. Only the
     * single non-zero axis of the direction is checked against the selection's [0, size-1] bounds.
     */
    private boolean isEntryOnMatchingFace(BlockVector3 relPos, Direction dir, BlockVector3 size) {
        Vector v = dir.getBlockVector();
        if (v.getBlockX() > 0) return relPos.x() == size.x() - 1;
        if (v.getBlockX() < 0) return relPos.x() == 0;
        if (v.getBlockY() > 0) return relPos.y() == size.y() - 1;
        if (v.getBlockY() < 0) return relPos.y() == 0;
        if (v.getBlockZ() > 0) return relPos.z() == size.z() - 1;
        if (v.getBlockZ() < 0) return relPos.z() == 0;
        return false;
    }

    /** Formats up to a few BlockVector3 offsets as "x,y,z" for a compact warning line. */
    private String formatVecs(List<BlockVector3> vecs) {
        List<String> parts = new ArrayList<>();
        for (BlockVector3 v : vecs) parts.add(v.x() + "," + v.y() + "," + v.z());
        return String.join("; ", limit(parts, 8));
    }

    /** Returns the first {@code max} items, appending an ellipsis note if the list was longer. */
    private List<String> limit(List<String> items, int max) {
        if (items.size() <= max) return items;
        List<String> out = new ArrayList<>(items.subList(0, max));
        out.add("… (+" + (items.size() - max) + " more)");
        return out;
    }

    /** Converts an absolute world Location to a relative BlockVector3 offset from selMin. */
    private BlockVector3 toRelative(Location loc, BlockVector3 selMin) {
        return BlockVector3.at(
                loc.getBlockX() - selMin.x(),
                loc.getBlockY() - selMin.y(),
                loc.getBlockZ() - selMin.z()
        );
    }

    /** Reads a SegmentBound from a bound anchor entity's PDC, converting to relative coords. */
    @Nullable
    private SegmentBound readBound(PersistentDataContainer pdc, BlockVector3 selMin) {
        String minStr = pdc.get(BOUND_MIN_KEY, PersistentDataType.STRING);
        String maxStr = pdc.get(BOUND_MAX_KEY, PersistentDataType.STRING);
        if (minStr == null || maxStr == null) return null;
        try {
            BlockVector3 absMin = parseVec(minStr);
            BlockVector3 absMax = parseVec(maxStr);
            BlockVector3 relMin = absMin.subtract(selMin);
            BlockVector3 relMax = absMax.subtract(selMin);
            return new SegmentBound(relMin, relMax);
        } catch (Exception e) {
            plugin.getLogger().warning("[SaveSegment] Failed to parse bound coords: " + e.getMessage());
            return null;
        }
    }

    private BlockVector3 parseVec(String csv) {
        String[] parts = csv.split(",");
        return BlockVector3.at(
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim())
        );
    }

    private void warn(Player player, String msg) {
        player.sendMessage(Component.text("Warning: " + msg, NamedTextColor.YELLOW));
        plugin.getLogger().warning("[SaveSegment] " + msg);
    }

    private Component info(String label, int count) {
        return Component.text("  " + label + ": ", NamedTextColor.WHITE)
                .append(Component.text(String.valueOf(count),
                        count > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY));
    }

    private String getTypeNames() {
        return java.util.Arrays.stream(SegmentType.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }
}
