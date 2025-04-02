package com.clarkson.sot.utils;

// WorldEdit imports (only for BlockVector3)
import com.sk89q.worldedit.math.BlockVector3;

// Bukkit imports (only for Plugin)
import org.bukkit.plugin.Plugin;

import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.Segment.RelativeEntryPoint;
// Gson imports
import com.google.gson.*;

// Java IO and Util
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.List;
import java.util.logging.Level;


/**
 * Saves Segment TEMPLATE metadata to a JSON file using relative coordinates.
 * Takes a world-independent Segment template as input.
 * NOTE: This class DOES NOT save schematic files, only the JSON metadata.
 */
public class StructureMetadataSaver { // Renamed class

    private final Plugin plugin;
    private final Gson gson;
    // Regex to find characters NOT safe for typical filenames
    private static final java.util.regex.Pattern INVALID_FILE_CHARS = java.util.regex.Pattern.compile("[^a-zA-Z0-9_.-]");
    // Example limit - adjust based on target OS filesystem limitations
    private static final int MAX_FILENAME_LENGTH = 200;

    public StructureMetadataSaver(Plugin plugin) { // Renamed constructor
        this.plugin = plugin;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    /**
     * Saves segment template metadata JSON based on the provided world-independent Segment template.
     *
     * @param segmentTemplate The world-independent Segment template object.
     * @return true if JSON metadata was saved successfully, false otherwise.
     */
    public boolean saveMetadata(Segment segmentTemplate) { // Renamed method, takes template
        // --- Initial Validation (on the input template) ---
        if (!isSegmentTemplateValid(segmentTemplate)) {
             // Error logged internally
            return false;
        }

        // --- JSON Metadata Saving ---
        String segmentName = segmentTemplate.getName(); // Get name for logging
        JsonElement segmentJsonElement;
        try {
            // Directly serialize the template data from the input object
            segmentJsonElement = serializeSegmentTemplate(segmentTemplate);
            if (segmentJsonElement == null || segmentJsonElement.isJsonNull()) {
                 plugin.getLogger().severe("[StructureMetadataSaver] Failed to serialize segment template metadata for: " + segmentName);
                 return false;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[StructureMetadataSaver] Unexpected error during JSON serialization for template: " + segmentName, e);
            return false;
        }

        // --- Prepare File Path ---
        String safeName = sanitizeFileName(segmentName);
        if (safeName.isEmpty()) {
            plugin.getLogger().severe("[StructureMetadataSaver] Cannot save JSON for template '" + segmentName + "': Name becomes empty after sanitization.");
            return false;
        }
        if (safeName.length() > MAX_FILENAME_LENGTH) {
             plugin.getLogger().warning("[StructureMetadataSaver] Sanitized filename for template '" + segmentName + "' ('" + safeName + "') may exceed filesystem limits.");
        }

        File jsonFile;
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                 plugin.getLogger().severe("[StructureMetadataSaver] Failed to create plugin data folder: " + dataFolder.getAbsolutePath());
                 return false;
            }
            jsonFile = new File(dataFolder, safeName + ".json");
        } catch (InvalidPathException | SecurityException e) {
             plugin.getLogger().log(Level.SEVERE, "[StructureMetadataSaver] Failed to construct file path for JSON: " + safeName + ".json", e);
             return false;
        }

        // --- Write JSON to File ---
        try (FileWriter writer = new FileWriter(jsonFile)) {
             gson.toJson(segmentJsonElement, writer);
             plugin.getLogger().info("[StructureMetadataSaver] JSON metadata saved successfully: " + jsonFile.getName());
             return true;
        } catch (JsonIOException | IOException | SecurityException e) {
             plugin.getLogger().log(Level.SEVERE, "[StructureMetadataSaver] Failed to write JSON metadata file: " + jsonFile.getName(), e);
             return false;
        }
    }

    /** Validates the essential parts of a Segment template object */
    private boolean isSegmentTemplateValid(Segment segmentTemplate) {
        if (segmentTemplate == null) {
            plugin.getLogger().severe("[StructureMetadataSaver] Cannot save metadata: Segment template object is null.");
            return false;
        }
        String name = segmentTemplate.getName();
        if (name == null || name.trim().isEmpty()) {
            plugin.getLogger().severe("[StructureMetadataSaver] Cannot save metadata: Segment template name is null or empty.");
            return false;
        }
        // Check schematic filename existence in the template
        if (segmentTemplate.getSchematicFileName() == null || segmentTemplate.getSchematicFileName().trim().isEmpty()) {
             plugin.getLogger().severe("[StructureMetadataSaver] Cannot save metadata for '" + name + "': Template is missing schematic filename.");
             return false;
        }
        // Check size from the template
        BlockVector3 size = segmentTemplate.getSize();
         if (size == null || size.x() <= 0 || size.y() <= 0 || size.z() <= 0) {
             plugin.getLogger().severe("[StructureMetadataSaver] Cannot save metadata for '" + name + "': Template has invalid size: " + size);
             return false;
         }
        // Add other checks if lists like entryPoints cannot be null etc.
        return true;
    }


    /**
     * Serializes the world-independent Segment template data into a JsonElement.
     * Reads directly from the template object's fields/getters.
     *
     * @param segmentTemplate The world-independent Segment template object.
     * @return JsonElement representing the template data, or null on failure.
     */
    private JsonElement serializeSegmentTemplate(Segment segmentTemplate) {
        try {
            JsonObject json = new JsonObject();
            String segmentName = segmentTemplate.getName(); // Assumed valid

            // --- Add Properties Directly from Template ---
            json.addProperty("name", segmentName);
            json.addProperty("type", segmentTemplate.getType() != null ? segmentTemplate.getType().toString() : "UNKNOWN");
            json.addProperty("schematicFileName", segmentTemplate.getSchematicFileName()); // Get from template
            json.add("size", serializeBlockVector3(segmentTemplate.getSize())); // Get from template
            json.addProperty("totalCoins", segmentTemplate.getTotalCoins());

            // --- Add Relative Entry Points (Directly from template's list) ---
            json.add("entryPoints", serializeRelativeEntryPointList(segmentTemplate.getEntryPoints(), segmentName));

            // --- Add Relative Spawn Locations (Directly from template's lists) ---
            json.add("sandSpawnLocations", serializeBlockVectorList(segmentTemplate.getSandSpawnLocations(), "sandSpawnLocations", segmentName));
            json.add("itemSpawnLocations", serializeBlockVectorList(segmentTemplate.getItemSpawnLocations(), "itemSpawnLocations", segmentName));
            json.add("coinSpawnLocations", serializeBlockVectorList(segmentTemplate.getCoinSpawnLocations(), "coinSpawnLocations", segmentName));

            return json;

        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "[StructureMetadataSaver] Unexpected error during template JSON serialization for: " + segmentTemplate.getName(), e);
             return null;
        }
    }

    /** Serializes a BlockVector3 to a JSON object {"x": _, "y": _, "z": _} */
    private JsonObject serializeBlockVector3(BlockVector3 vec) {
        JsonObject vecJson = new JsonObject();
        if (vec != null) {
            vecJson.addProperty("x", vec.x());
            vecJson.addProperty("y", vec.y());
            vecJson.addProperty("z", vec.z());
        }
        return vecJson; // Return empty object if vec is null
    }

    /** Serializes a List of RelativeEntryPoint objects */
    private JsonArray serializeRelativeEntryPointList(List<RelativeEntryPoint> relativeEntryPoints, String segmentName) {
        JsonArray jsonArray = new JsonArray();
        if (relativeEntryPoints != null) {
            for (RelativeEntryPoint ep : relativeEntryPoints) {
                if (ep != null && ep.getRelativePosition() != null && ep.getDirection() != null) { // Add null checks
                    JsonObject epJson = new JsonObject();
                    epJson.add("relativePosition", serializeBlockVector3(ep.getRelativePosition()));
                    epJson.addProperty("direction", ep.getDirection().toString()); // Assumes Direction has toString()
                    jsonArray.add(epJson);
                } else {
                     plugin.getLogger().warning("[StructureMetadataSaver] Skipping null or incomplete RelativeEntryPoint during serialization in template: " + segmentName);
                }
            }
        }
        return jsonArray;
    }

    /** Serializes a List of BlockVector3 objects */
    private JsonArray serializeBlockVectorList(List<BlockVector3> vectorList, String listName, String segmentName) {
        JsonArray jsonArray = new JsonArray();
        if (vectorList != null) {
            for (BlockVector3 vec : vectorList) {
                if (vec != null) { // Add null check
                    jsonArray.add(serializeBlockVector3(vec));
                } else {
                    plugin.getLogger().warning("[StructureMetadataSaver] Skipping null BlockVector3 in list '" + listName + "' during serialization in template: " + segmentName);
                }
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

    // --- REMOVED ---
    // - saveSchematicInternal method (cannot save schematic from template)
    // - isSegmentValidForSaving (replaced by isSegmentTemplateValid)
    // - Calculation helpers (serializeRelativeEntryPoints, serializeRelativeLocations from previous version)

}
