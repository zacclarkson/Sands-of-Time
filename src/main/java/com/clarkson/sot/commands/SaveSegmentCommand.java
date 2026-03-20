package com.clarkson.sot.commands;

import com.clarkson.sot.dungeon.segment.Direction;
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.SegmentBound;
import com.clarkson.sot.dungeon.segment.SegmentType;
import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.events.ToolListener;
import com.clarkson.sot.main.SoT;
import com.clarkson.sot.utils.SegmentBuilderKeys;
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
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * /sotsavesegment <name> <type> [totalCoins]
 *
 * Reads all BUILD_MARKER_TAG entities within the current WorldEdit selection,
 * converts their absolute positions to relative offsets, and saves the segment.
 * Schematic filename is auto-generated as <name>.schem.
 */
public class SaveSegmentCommand implements CommandExecutor {

    private final SoT plugin;
    private final StructureSaver structureSaver;
    private final WorldEditPlugin worldEdit;
    private final SegmentBuilderKeys keys;

    public SaveSegmentCommand(SoT plugin, SegmentBuilderKeys keys) {
        this.plugin = plugin;
        this.keys = keys;
        this.structureSaver = new StructureSaver(plugin);

        Plugin wep = Bukkit.getServer().getPluginManager().getPlugin("WorldEdit");
        if (wep instanceof WorldEditPlugin we) {
            this.worldEdit = we;
        } else {
            this.worldEdit = null;
            plugin.getLogger().severe("WorldEdit not found — /sotsavesegment will not work.");
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (worldEdit == null) {
            player.sendMessage(Component.text("WorldEdit is not available.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /" + label + " <name> <type> [totalCoins]", NamedTextColor.RED));
            player.sendMessage(Component.text("Types: " + getTypeNames(), NamedTextColor.GRAY));
            return true;
        }

        String segmentName = args[0];
        SegmentType segmentType;
        try {
            segmentType = SegmentType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Invalid type: " + args[1] + ". Valid: " + getTypeNames(), NamedTextColor.RED));
            return true;
        }

        int totalCoins = 0;
        if (args.length >= 3) {
            try {
                totalCoins = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Invalid number for totalCoins: " + args[2], NamedTextColor.RED));
                return true;
            }
        }

        // --- Get WorldEdit selection ---
        Region selection;
        Location worldOrigin;
        BlockVector3 size;
        BlockVector3 selMin;

        try {
            com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);
            SessionManager sessionManager = WorldEdit.getInstance().getSessionManager();
            LocalSession localSession = sessionManager.get(wePlayer);
            selection = localSession.getSelection(wePlayer.getWorld());

            BlockVector3 min = selection.getMinimumPoint();
            BlockVector3 max = selection.getMaximumPoint();
            selMin = min;
            worldOrigin = BukkitAdapter.adapt(player.getWorld(), min);
            size = max.subtract(min).add(1, 1, 1);
        } catch (IncompleteRegionException e) {
            player.sendMessage(Component.text("WorldEdit selection is incomplete. Select two points first.", NamedTextColor.RED));
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting WorldEdit selection", e);
            player.sendMessage(Component.text("Error reading WorldEdit selection.", NamedTextColor.RED));
            return true;
        }

        // --- Scan all build markers within the selection ---
        BoundingBox bb = new BoundingBox(
            selMin.x(), selMin.y(), selMin.z(),
            selMin.x() + size.x(), selMin.y() + size.y(), selMin.z() + size.z()
        );
        Collection<Entity> nearby = player.getWorld().getNearbyEntities(bb);

        List<Segment.RelativeEntryPoint> entryPoints   = new ArrayList<>();
        List<BlockVector3>               coinSpawns     = new ArrayList<>();
        List<BlockVector3>               itemSpawns     = new ArrayList<>();
        List<BlockVector3>               sandSpawns     = new ArrayList<>();
        List<BlockVector3>               sandSacrifices = new ArrayList<>();
        List<BlockVector3>               mobSpawners    = new ArrayList<>();
        List<SegmentBound>               gates          = new ArrayList<>();
        SegmentBound                     vaultDoorBound = null;
        BlockVector3                     leverOffset    = null;

        for (Entity entity : nearby) {
            if (!(entity instanceof BlockDisplay)) continue;
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            if (!pdc.has(keys.BUILD_MARKER_TAG, PersistentDataType.BYTE)) continue;

            String type = pdc.get(keys.MARKER_TYPE, PersistentDataType.STRING);
            if (type == null) continue;

            // Skip purely visual entities — they carry no data
            if (ToolListener.MT_BOUND_VISUAL.equals(type) || ToolListener.MT_BOUND_TEMP.equals(type)) continue;

            BlockVector3 relPos = toRelative(entity.getLocation(), selMin);

            switch (type) {
                case ToolListener.MT_ENTRYPOINT -> {
                    String dirStr = pdc.get(keys.DIRECTION, PersistentDataType.STRING);
                    if (dirStr != null) {
                        try {
                            Direction dir = Direction.valueOf(dirStr);
                            entryPoints.add(new Segment.RelativeEntryPoint(relPos, dir));
                        } catch (IllegalArgumentException ex) {
                            plugin.getLogger().warning("[SaveSegment] Unknown direction '" + dirStr + "' on entry point marker, skipping.");
                        }
                    }
                }
                case ToolListener.MT_COIN_SPAWN     -> coinSpawns.add(relPos);
                case ToolListener.MT_ITEM_SPAWN     -> itemSpawns.add(relPos);
                case ToolListener.MT_SAND_SPAWN     -> sandSpawns.add(relPos);
                case ToolListener.MT_SAND_SACRIFICE -> sandSacrifices.add(relPos);
                case ToolListener.MT_MOB_SPAWNER    -> mobSpawners.add(relPos);
                case ToolListener.MT_LEVER          -> {
                    if (leverOffset != null) {
                        player.sendMessage(Component.text("Warning: multiple lever markers found — using the first.", NamedTextColor.YELLOW));
                    } else {
                        leverOffset = relPos;
                    }
                }
                case ToolListener.MT_VAULT_DOOR -> {
                    SegmentBound bound = readBound(pdc, selMin);
                    if (bound != null) {
                        if (vaultDoorBound != null) {
                            player.sendMessage(Component.text("Warning: multiple vault door bounds found — using the first.", NamedTextColor.YELLOW));
                        } else {
                            vaultDoorBound = bound;
                        }
                    }
                }
                case ToolListener.MT_GATE -> {
                    SegmentBound bound = readBound(pdc, selMin);
                    if (bound != null) gates.add(bound);
                }
            }
        }

        // --- Validate gate ↔ lever constraint ---
        if (!gates.isEmpty() && leverOffset == null) {
            player.sendMessage(Component.text(
                "Save failed: this segment has " + gates.size() + " gate(s) but no lever marker. Place a lever marker first.",
                NamedTextColor.RED));
            return true;
        }
        if (gates.isEmpty() && leverOffset != null) {
            player.sendMessage(Component.text(
                "Warning: lever marker found but no gates. Lever offset will be saved but may be unused.", NamedTextColor.YELLOW));
        }

        // --- Build summary feedback ---
        player.sendMessage(Component.text("─── Segment Marker Summary ───", NamedTextColor.GOLD));
        player.sendMessage(summary("Entry Points",    entryPoints.size()));
        player.sendMessage(summary("Coin Spawns",     coinSpawns.size()));
        player.sendMessage(summary("Item Spawns",     itemSpawns.size()));
        player.sendMessage(summary("Sand Spawns",     sandSpawns.size()));
        player.sendMessage(summary("Sand Sacrifices", sandSacrifices.size()));
        player.sendMessage(summary("Mob Spawners",    mobSpawners.size()));
        player.sendMessage(summary("Gates",           gates.size()));
        player.sendMessage(Component.text("  Vault Door: ", NamedTextColor.GRAY)
            .append(Component.text(vaultDoorBound != null ? "yes" : "none", vaultDoorBound != null ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
        player.sendMessage(Component.text("  Lever: ", NamedTextColor.GRAY)
            .append(Component.text(leverOffset != null ? "yes" : "none", leverOffset != null ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));

        // --- Construct Segment ---
        String schematicFileName = segmentName.replaceAll("[^a-zA-Z0-9_.-]", "_") + ".schem";

        Segment segmentTemplate;
        try {
            segmentTemplate = new Segment(
                segmentName,
                segmentType,
                schematicFileName,
                size,
                entryPoints,
                sandSpawns,
                itemSpawns,
                coinSpawns,
                totalCoins,
                null,  // containedVault  — set manually in JSON if needed
                null,  // containedVaultKey
                null,  // vaultLocationOffset
                null,  // keyLocationOffset
                vaultDoorBound,
                gates,
                leverOffset,
                sandSacrifices,
                mobSpawners
            );
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error constructing Segment object for " + segmentName, e);
            player.sendMessage(Component.text("Error building segment data. Check console.", NamedTextColor.RED));
            return true;
        }

        // --- Save ---
        PlacedSegment placedSegment = new PlacedSegment(segmentTemplate, worldOrigin, 0);
        player.sendMessage(Component.text("Saving '" + segmentName + "'...", NamedTextColor.YELLOW));
        boolean success = structureSaver.saveStructure(placedSegment);

        if (success) {
            player.sendMessage(Component.text("Segment '" + segmentName + "' saved successfully!", NamedTextColor.GREEN));
            player.sendMessage(Component.text("  Schematic: " + schematicFileName, NamedTextColor.GREEN));
            player.sendMessage(Component.text("  Metadata:  " + segmentName + ".json", NamedTextColor.GREEN));
            player.sendMessage(Component.text("Reload templates to use the new segment.", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("Failed to save '" + segmentName + "'. Check console for errors.", NamedTextColor.RED));
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Converts an entity's absolute block location to a relative offset from the selection minimum. */
    private static BlockVector3 toRelative(Location loc, BlockVector3 selMin) {
        return BlockVector3.at(
            loc.getBlockX() - selMin.x(),
            loc.getBlockY() - selMin.y(),
            loc.getBlockZ() - selMin.z()
        );
    }

    /** Reads the stored absolute BOUND_MIN/MAX from a data entity and converts to relative. */
    private SegmentBound readBound(PersistentDataContainer pdc, BlockVector3 selMin) {
        Integer minX = pdc.get(keys.BOUND_MIN_X, PersistentDataType.INTEGER);
        Integer minY = pdc.get(keys.BOUND_MIN_Y, PersistentDataType.INTEGER);
        Integer minZ = pdc.get(keys.BOUND_MIN_Z, PersistentDataType.INTEGER);
        Integer maxX = pdc.get(keys.BOUND_MAX_X, PersistentDataType.INTEGER);
        Integer maxY = pdc.get(keys.BOUND_MAX_Y, PersistentDataType.INTEGER);
        Integer maxZ = pdc.get(keys.BOUND_MAX_Z, PersistentDataType.INTEGER);

        if (minX == null || minY == null || minZ == null || maxX == null || maxY == null || maxZ == null) {
            plugin.getLogger().warning("[SaveSegment] Bound data entity is missing coordinate PDC values, skipping.");
            return null;
        }

        BlockVector3 relMin = BlockVector3.at(minX - selMin.x(), minY - selMin.y(), minZ - selMin.z());
        BlockVector3 relMax = BlockVector3.at(maxX - selMin.x(), maxY - selMin.y(), maxZ - selMin.z());
        return new SegmentBound(relMin, relMax);
    }

    private Component summary(String label, int count) {
        NamedTextColor valueColor = count > 0 ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY;
        return Component.text("  " + label + ": ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(count), valueColor));
    }

    private String getTypeNames() {
        return java.util.Arrays.stream(SegmentType.values())
            .map(Enum::name)
            .collect(Collectors.joining(", "));
    }
}
