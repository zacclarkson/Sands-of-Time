package com.clarkson.sot.utils;

import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.Segment.RelativeEntryPoint;
// WorldEdit imports
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.World; // WorldEdit World

// Bukkit imports
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
// No longer need Bukkit Vector here

// Gson imports
import com.google.gson.*;

// Java IO and Util
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.List;
import java.util.logging.Level;


/**
 * Saves Segment data: schematic from the live world based on a PlacedSegment instance,
 * and metadata JSON based on the referenced Segment template.
 */
public class StructureSaver {

    private final Plugin plugin;
    private final Gson gson;
    private static final java.util.regex.Pattern INVALID_FILE_CHARS = java.util.regex.Pattern.compile("[^a-zA-Z0-9_.-]");
    private static final int MAX_FILENAME_LENGTH = 200;
    private static final long MAX_SCHEMATIC_VOLUME = 1_000_000; // Example limit

    public StructureSaver(Plugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    /**
     * Saves segment schematic based on its placement in the world and saves
     * metadata JSON based on its underlying template.
     *
     * @param placedSegment The PlacedSegment object representing the structure instance in the world.
     * @return true if BOTH JSON metadata and the schematic were saved successfully, false otherwise.
     */
    public boolean saveStructure(PlacedSegment placedSegment) { // Changed parameter type
        // --- Initial Validation (on the PlacedSegment) ---
        if (!isPlacedSegmentValidForSaving(placedSegment)) {
            return false;
        }

        // --- JSON Metadata Saving (Uses the TEMPLATE referenced by PlacedSegment) ---
        boolean jsonSaved = saveMetadataJson(placedSegment.getSegmentTemplate()); // Pass the template
        if (!jsonSaved) {
             plugin.getLogger().warning("[StructureSaver] Failed to save JSON metadata for segment '" + placedSegment.getName() + "', schematic saving will still be attempted.");
        }

        // --- Schematic Saving (Uses the absolute coordinates from PlacedSegment) ---
        boolean schematicSaved = saveSchematicInternal(placedSegment); // Pass the placed segment

        return jsonSaved && schematicSaved;
    }

    /** Validates the input PlacedSegment object */
    private boolean isPlacedSegmentValidForSaving(PlacedSegment placedSegment) {
        if (placedSegment == null) {
            plugin.getLogger().severe("[StructureSaver] Cannot save structure: PlacedSegment object is null.");
            return false;
        }
        // Validate the underlying template
        Segment template = placedSegment.getSegmentTemplate();
        if (template == null) {
             plugin.getLogger().severe("[StructureSaver] Cannot save structure: PlacedSegment contains a null template reference.");
             return false;
        }
        String name = template.getName(); // Use template name for consistency
        if (name == null || name.trim().isEmpty()) {
            plugin.getLogger().severe("[StructureSaver] Cannot save structure: Segment template name is null or empty.");
            return false;
        }
         if (template.getSchematicFileName() == null || template.getSchematicFileName().trim().isEmpty()) {
             plugin.getLogger().severe("[StructureSaver] Cannot save structure for '" + name + "': Template is missing schematic filename.");
             return false;
         }
         BlockVector3 size = template.getSize();
         if (size == null || size.x() <= 0 || size.y() <= 0 || size.z() <= 0) {
             plugin.getLogger().severe("[StructureSaver] Cannot save structure for '" + name + "': Template has invalid size.");
             return false;
         }
        // Validate the placement information
        if (placedSegment.getWorldOrigin() == null || placedSegment.getWorldOrigin().getWorld() == null) {
             plugin.getLogger().severe("[StructureSaver] Cannot save structure for '" + name + "': PlacedSegment world origin or its world is null.");
             return false;
        }
         if (placedSegment.getWorldBounds() == null || placedSegment.getWorldBounds().getMinPoint() == null || placedSegment.getWorldBounds().getMaxPoint() == null) {
             plugin.getLogger().severe("[StructureSaver] Cannot save structure for '" + name + "': PlacedSegment calculated world bounds are invalid.");
             return false;
         }
        return true;
    }


    /**
     * Saves the Segment TEMPLATE's metadata as a JSON file.
     *
     * @param segmentTemplate The world-independent segment template object.
     * @return true on success, false on failure.
     */
    private boolean saveMetadataJson(Segment segmentTemplate) { // Takes the template directly
        String segmentName = segmentTemplate.getName();
        JsonElement segmentTemplateJson;

        // --- Serialize Template Data ---
        try {
            segmentTemplateJson = serializeSegmentTemplate(segmentTemplate); // Use helper
            if (segmentTemplateJson == null || segmentTemplateJson.isJsonNull()) {
                 plugin.getLogger().severe("[StructureSaver] Failed to serialize segment template metadata for: " + segmentName);
                 return false;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[StructureSaver] Unexpected error during JSON serialization preparation for template: " + segmentName, e);
            return false;
        }

        // --- Prepare File Path ---
        String safeName = sanitizeFileName(segmentName);
        if (safeName.isEmpty()) { /* ... error log ... */ return false; }
        if (safeName.length() > MAX_FILENAME_LENGTH) { /* ... warning log ... */ }

        File jsonFile;
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) { /* ... error log ... */ return false; }
            jsonFile = new File(dataFolder, safeName + ".json");
        } catch (InvalidPathException | SecurityException e) { /* ... error log ... */ return false; }

        // --- Write JSON to File ---
        try (FileWriter writer = new FileWriter(jsonFile)) {
             gson.toJson(segmentTemplateJson, writer);
             plugin.getLogger().info("[StructureSaver] JSON metadata saved successfully (template format): " + jsonFile.getName());
             return true;
        } catch (JsonIOException | IOException | SecurityException e) { /* ... error log ... */ return false; }
    }

    /**
     * Internal method to save the block/entity data as a schematic file,
     * using the absolute coordinates from the PlacedSegment.
     *
     * @param placedSegment The PlacedSegment object representing the instance in the world.
     * @return true on success, false on failure.
     */
    private boolean saveSchematicInternal(PlacedSegment placedSegment) { // Takes PlacedSegment
        // Get absolute bounds directly from the PlacedSegment
        Location minPointLoc = placedSegment.getWorldBounds().getMinPoint();
        Location maxPointLoc = placedSegment.getWorldBounds().getMaxPoint();
        String segmentName = placedSegment.getName(); // Get name via PlacedSegment

        // World check (already done in validation, but good practice)
        if (minPointLoc.getWorld() == null) {
             plugin.getLogger().severe("[StructureSaver] Cannot save schematic for '" + segmentName + "': World is null (checked again).");
             return false;
        }

        // Convert absolute Bukkit Locations to absolute WorldEdit BlockVector3
        BlockVector3 minAbs = BukkitAdapter.asBlockVector(minPointLoc);
        BlockVector3 maxAbs = BukkitAdapter.asBlockVector(maxPointLoc);

        World weWorld;
        try {
             weWorld = BukkitAdapter.adapt(minPointLoc.getWorld());
        } catch (IllegalArgumentException e) { /* ... error log ... */ return false; }

        CuboidRegion region;
        try {
            region = new CuboidRegion(weWorld, minAbs, maxAbs); // Use absolute vectors
        } catch(IllegalArgumentException e) { /* ... error log ... */ return false; }

        // --- Volume/Size Checks ---
        long volume = region.getVolume();
        if (volume <= 0) { /* ... warning log ... */ return false; }
        // Use volume limit based on template size maybe? Or keep world region volume check.
        // BlockVector3 templateSize = placedSegment.getSegmentTemplate().getSize();
        // long templateVolume = (long)templateSize.getX() * templateSize.getY() * templateSize.getZ();
        // if (templateVolume > MAX_SCHEMATIC_VOLUME) { ... }
        if (volume > MAX_SCHEMATIC_VOLUME) { /* ... error log ... */ return false; }


        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(minAbs); // Set origin to the absolute min corner of the region being copied

        // --- Perform WorldEdit Copy Operation ---
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).build()) {
            ForwardExtentCopy copy = new ForwardExtentCopy(
                editSession, region, clipboard, region.getMinimumPoint()
            );
            copy.setCopyingEntities(true);
            Operations.complete(copy);

            // --- Prepare File Path ---
            // Use the schematic filename stored in the template for consistency
            String schematicFileName = placedSegment.getSegmentTemplate().getSchematicFileName();
            if (schematicFileName == null || schematicFileName.trim().isEmpty()) {
                 plugin.getLogger().severe("[StructureSaver] Cannot save schematic for '" + segmentName + "': Template has no schematic filename defined.");
                 return false; // Use filename from template
            }
            // Optional: could sanitize schematicFileName again, but it should be safe if saved correctly
            // String safeSchematicFileName = sanitizeFileName(schematicFileName);


            File schematicDir = new File(plugin.getDataFolder(), "schematics");
            if (!schematicDir.exists() && !schematicDir.mkdirs()) { /* ... error log ... */ return false; }

            // Use the filename from the template object
            File schematicFile = new File(schematicDir, schematicFileName);

            // --- Write Schematic File ---
            try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(new FileOutputStream(schematicFile))) {
                writer.write(clipboard);
                plugin.getLogger().info("[StructureSaver] Schematic saved successfully: " + schematicFile.getAbsolutePath());
                return true;
            } catch (IOException e) { /* ... error log ... */ return false; }

        } catch (WorldEditException e) { /* ... error log ... */ return false;
        } catch (OutOfMemoryError e) { /* ... error log ... */ return false;
        } catch (Exception e) { /* ... error log ... */ return false; }
    }

    // --- JSON Serialization Logic (Serializes the TEMPLATE data) ---

    /**
     * Serializes the world-independent Segment template data into a JsonElement.
     * Reads directly from the template object's fields/getters.
     *
     * @param segmentTemplate The world-independent Segment template object.
     * @return JsonElement representing the template data, or null on failure.
     */
    private JsonElement serializeSegmentTemplate(Segment segmentTemplate) {
        // This method remains the same as in StructureMetadataSaver
        // It takes the TEMPLATE and serializes its contents.
        try {
            JsonObject json = new JsonObject();
            String segmentName = segmentTemplate.getName(); // Assumed valid

            json.addProperty("name", segmentName);
            json.addProperty("type", segmentTemplate.getType() != null ? segmentTemplate.getType().toString() : "UNKNOWN");
            json.addProperty("schematicFileName", segmentTemplate.getSchematicFileName()); // From template
            json.add("size", serializeBlockVector3(segmentTemplate.getSize())); // From template
            json.addProperty("totalCoins", segmentTemplate.getTotalCoins());
            json.add("entryPoints", serializeRelativeEntryPointList(segmentTemplate.getEntryPoints(), segmentName)); // From template
            json.add("sandSpawnLocations", serializeBlockVectorList(segmentTemplate.getSandSpawnLocations(), "sandSpawnLocations", segmentName)); // From template
            json.add("itemSpawnLocations", serializeBlockVectorList(segmentTemplate.getItemSpawnLocations(), "itemSpawnLocations", segmentName)); // From template
            json.add("coinSpawnLocations", serializeBlockVectorList(segmentTemplate.getCoinSpawnLocations(), "coinSpawnLocations", segmentName)); // From template

            return json;

        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "[StructureSaver] Unexpected error during template JSON serialization for: " + segmentTemplate.getName(), e);
             return null;
        }
    }

    // --- Serialization Helpers ---
    // These helpers serialize the relative data structures (BlockVector3, RelativeEntryPoint)
    // They remain the same as in StructureMetadataSaver

    private JsonObject serializeBlockVector3(BlockVector3 vec) {
        JsonObject vecJson = new JsonObject();
        if (vec != null) {
            vecJson.addProperty("x", vec.x());
            vecJson.addProperty("y", vec.y());
            vecJson.addProperty("z", vec.z());
        }
        return vecJson;
    }

    private JsonArray serializeRelativeEntryPointList(List<RelativeEntryPoint> relativeEntryPoints, String segmentName) {
        JsonArray jsonArray = new JsonArray();
        if (relativeEntryPoints != null) {
            for (RelativeEntryPoint ep : relativeEntryPoints) {
                if (ep != null && ep.getRelativePosition() != null && ep.getDirection() != null) {
                    JsonObject epJson = new JsonObject();
                    epJson.add("relativePosition", serializeBlockVector3(ep.getRelativePosition()));
                    epJson.addProperty("direction", ep.getDirection().toString());
                    jsonArray.add(epJson);
                } else { /* ... warning log ... */ }
            }
        }
        return jsonArray;
    }

    private JsonArray serializeBlockVectorList(List<BlockVector3> vectorList, String listName, String segmentName) {
        JsonArray jsonArray = new JsonArray();
        if (vectorList != null) {
            for (BlockVector3 vec : vectorList) {
                if (vec != null) {
                    jsonArray.add(serializeBlockVector3(vec));
                } else { /* ... warning log ... */ }
            }
        }
        return jsonArray;
    }

    // sanitizeFileName remains the same
    private String sanitizeFileName(String name) {
        if (name == null) return "";
        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) return "";
        return INVALID_FILE_CHARS.matcher(trimmedName).replaceAll("_");
    }
}
