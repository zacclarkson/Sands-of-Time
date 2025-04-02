package com.clarkson.sot.utils;

import com.clarkson.sot.dungeon.SegmentType; // Assuming SegmentType enum location
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.Segment.RelativeEntryPoint;
// WorldEdit Vector class for relative positions and size
import com.sk89q.worldedit.math.BlockVector3;

// Gson imports
import com.google.gson.*;
import org.bukkit.plugin.Plugin;

// Java IO and Util
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Loads Segment TEMPLATES from .json metadata files.
 * Reconstructs world-independent Segment objects containing relative coordinates,
 * dimensions, and schematic file references.
 */
public class StructureLoader {

    private final Gson gson = new Gson(); // Reusable Gson instance
    private final Plugin plugin;          // For logging purposes

    public StructureLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads Segment template metadata from .json files found directly within the specified directory.
     *
     * @param dataDir The base directory containing the .json segment metadata files (e.g., plugin.getDataFolder()).
     * @return A List of loaded Segment template objects. Returns an empty list if the directory is invalid, not found, or contains no valid .json segment files.
     */
    public List<Segment> loadSegmentTemplates(File dataDir) { // Removed World parameter
        List<Segment> loadedSegments = new ArrayList<>();

        // --- Validate Input Directory ---
        if (dataDir == null) {
             plugin.getLogger().severe("[StructureLoader] Cannot load segments: Provided data directory is null.");
             return loadedSegments;
        }
        if (!dataDir.isDirectory()) {
            plugin.getLogger().severe("[StructureLoader] Cannot load segments: Provided path is not a directory: " + dataDir.getAbsolutePath());
            return loadedSegments;
        }

        // --- List Potential JSON Files ---
        File[] jsonFiles = dataDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

        if (jsonFiles == null || jsonFiles.length == 0) {
            plugin.getLogger().info("[StructureLoader] No '.json' segment files found directly in directory: " + dataDir.getAbsolutePath());
            return loadedSegments;
        }

        plugin.getLogger().info("[StructureLoader] Found " + jsonFiles.length + " potential segment JSON files in " + dataDir.getAbsolutePath() + ". Attempting to load templates...");

        // --- Read, Parse, and Deserialize Each JSON File ---
        for (File jsonFile : jsonFiles) {
            try (FileReader reader = new FileReader(jsonFile)) {
                JsonObject segmentJson = gson.fromJson(reader, JsonObject.class);
                Segment segment = deserializeSegmentTemplateFromJson(segmentJson, jsonFile.getName()); // Pass JsonObject

                if (segment != null) {
                    loadedSegments.add(segment);
                    plugin.getLogger().info("[StructureLoader] Successfully loaded segment template: '" + segment.getName() + "' from " + jsonFile.getName());
                }
                // Errors logged within deserializeSegmentTemplateFromJson

            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[StructureLoader] IOException while reading segment file: " + jsonFile.getName(), e);
            } catch (JsonIOException | JsonSyntaxException e) {
                plugin.getLogger().log(Level.SEVERE, "[StructureLoader] Invalid JSON syntax or read error in segment file: " + jsonFile.getName(), e);
            } catch (Exception e) {
                 plugin.getLogger().log(Level.SEVERE, "[StructureLoader] Unexpected error processing segment file: " + jsonFile.getName(), e);
            }
        }

        plugin.getLogger().info("[StructureLoader] Finished loading segment templates. Total loaded: " + loadedSegments.size());
        return loadedSegments;
    }

    /**
     * Attempts to deserialize a JsonObject into a world-independent Segment template object.
     *
     * @param json           The JsonObject containing the segment template data.
     * @param sourceFileName The name of the file being parsed (for logging context).
     * @return A fully reconstructed Segment object, or null if critical data is missing/invalid.
     */
    private Segment deserializeSegmentTemplateFromJson(JsonObject json, String sourceFileName) {
        try {
            // --- Extract Simple Properties ---
            String name = getJsonString(json, "name", sourceFileName);
            String typeStr = getJsonString(json, "type", sourceFileName);
            String schematicFileName = getJsonString(json, "schematicFileName", sourceFileName);
            Integer totalCoins = getJsonInt(json, "totalCoins", sourceFileName);

            // --- Validate Critical Fields ---
            if (name == null) {
                 plugin.getLogger().warning("[StructureLoader] Skipping template from " + sourceFileName + ": Missing or invalid 'name'.");
                 return null;
            }
             if (schematicFileName == null) {
                 plugin.getLogger().warning("[StructureLoader] Skipping template '" + name + "' from " + sourceFileName + ": Missing or invalid 'schematicFileName'. This is required.");
                 return null;
             }

            // --- Deserialize Complex Properties ---
            SegmentType type = parseSegmentType(typeStr, name, sourceFileName);
            BlockVector3 size = deserializeBlockVector3(json.getAsJsonObject("size"), "size", name, sourceFileName); // Expects "size": {"x": W, "y": H, "z": L}
            List<RelativeEntryPoint> entryPoints = deserializeRelativeEntryPoints(json.getAsJsonArray("entryPoints"), name, sourceFileName); // Expects "entryPoints" array
            List<BlockVector3> sandSpawns = deserializeBlockVectorList(json.getAsJsonArray("sandSpawnLocations"), "sandSpawnLocations", name, sourceFileName);
            List<BlockVector3> itemSpawns = deserializeBlockVectorList(json.getAsJsonArray("itemSpawnLocations"), "itemSpawnLocations", name, sourceFileName);
            List<BlockVector3> coinSpawns = deserializeBlockVectorList(json.getAsJsonArray("coinSpawnLocations"), "coinSpawnLocations", name, sourceFileName);

            // --- Validate Essential Deserialized Objects ---
             if (size == null) {
                 plugin.getLogger().severe("[StructureLoader] Skipping template '" + name + "' from " + sourceFileName + ": Failed to deserialize 'size'.");
                 return null; // Size is essential
             }
             // Check if size components are valid (optional, constructor might do this)
             if (size.x() <= 0 || size.y() <= 0 || size.z() <= 0) {
                  plugin.getLogger().severe("[StructureLoader] Skipping template '" + name + "' from " + sourceFileName + ": Invalid dimensions in 'size' " + size + ".");
                  return null;
             }

            // --- Construct the Segment Template Object ---
            // Ensure this matches the refactored Segment constructor
            return new Segment(
                    name,
                    type,
                    schematicFileName,
                    size,
                    entryPoints != null ? entryPoints : new ArrayList<>(), // Provide empty lists if deserialization failed but wasn't critical
                    sandSpawns != null ? sandSpawns : new ArrayList<>(),
                    itemSpawns != null ? itemSpawns : new ArrayList<>(),
                    coinSpawns != null ? coinSpawns : new ArrayList<>(),
                    totalCoins != null ? totalCoins : 0 // Default coins if missing
            );

        } catch (JsonParseException | IllegalStateException | ClassCastException | NullPointerException e) {
             plugin.getLogger().log(Level.SEVERE, "[StructureLoader] Failed to parse template data from " + sourceFileName + ". Invalid JSON structure or data types.", e);
             return null;
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "[StructureLoader] Unexpected error deserializing template from " + sourceFileName, e);
             return null;
        }
    }

    // --- Helper methods for safe JSON access and deserialization ---

    // getJsonString, getJsonInt remain the same as before...

    private String getJsonString(JsonObject json, String key, String sourceFileName) {
        if (json != null && json.has(key) && json.get(key).isJsonPrimitive() && json.get(key).getAsJsonPrimitive().isString()) {
            String value = json.get(key).getAsString();
            return (value != null && !value.trim().isEmpty()) ? value.trim() : null;
        }
        return null;
    }

     private Integer getJsonInt(JsonObject json, String key, String sourceFileName) {
         if (json != null && json.has(key) && json.get(key).isJsonPrimitive() && json.get(key).getAsJsonPrimitive().isNumber()) {
             try {
                 return json.get(key).getAsInt();
             } catch (NumberFormatException e) {
                  plugin.getLogger().warning("[StructureLoader] Invalid number format for field '" + key + "' in " + sourceFileName);
                  return null;
             }
         }
         return null;
     }

    private SegmentType parseSegmentType(String typeStr, String segmentName, String sourceFileName) {
        if (typeStr == null) {
             plugin.getLogger().warning("[StructureLoader] Missing segment type for template '" + segmentName + "' in " + sourceFileName + ". Type set to null.");
             return null; // Or return a default SegmentType.DEFAULT;
        }
        try {
             return SegmentType.valueOf(typeStr.toUpperCase()); // Assuming enum
        } catch (IllegalArgumentException e) {
             plugin.getLogger().warning("[StructureLoader] Invalid segment type '" + typeStr + "' for template '" + segmentName + "' in " + sourceFileName + ". Type set to null.");
             return null; // Or return a default SegmentType.DEFAULT;
        }
    }

    /**
     * Deserializes a JSON object representing coordinates into a BlockVector3.
     * Expected format: {"x": 1, "y": 0, "z": 5}
     */
    private BlockVector3 deserializeBlockVector3(JsonObject vecJson, String context, String segmentName, String sourceFileName) {
         if (vecJson == null || !vecJson.isJsonObject()) {
             plugin.getLogger().warning("[StructureLoader] Invalid JSON object for " + context + " in template '" + segmentName + "' from " + sourceFileName);
             return null;
         }
        try {
            Integer x = getJsonInt(vecJson, "x", sourceFileName);
            Integer y = getJsonInt(vecJson, "y", sourceFileName);
            Integer z = getJsonInt(vecJson, "z", sourceFileName);

            if (x == null || y == null || z == null) {
                 plugin.getLogger().warning("[StructureLoader] Missing or invalid coordinate (x, y, or z) for " + context + " in template '" + segmentName + "' from " + sourceFileName);
                 return null;
            }
            return BlockVector3.at(x, y, z); // Use WorldEdit's factory method
        } catch (Exception e) {
            plugin.getLogger().warning("[StructureLoader] Failed to create BlockVector3 for " + context + " in template '" + segmentName + "' from " + sourceFileName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Deserializes a JSON array of coordinate objects into a List of BlockVector3.
     */
    private List<BlockVector3> deserializeBlockVectorList(JsonArray vecArray, String listName, String segmentName, String sourceFileName) {
        List<BlockVector3> vectors = new ArrayList<>();
         if (vecArray == null || !vecArray.isJsonArray()) {
             // Log only if the array itself is expected but invalid/missing
             // plugin.getLogger().warning("[StructureLoader] Invalid or missing vector array '" + listName + "' for template '" + segmentName + "' in " + sourceFileName);
             return vectors; // Return empty list
         }

        for (int i = 0; i < vecArray.size(); i++) {
             JsonElement vecElement = vecArray.get(i);
            if (vecElement != null && vecElement.isJsonObject()) {
                BlockVector3 vec = deserializeBlockVector3(vecElement.getAsJsonObject(), listName + "[" + i + "]", segmentName, sourceFileName);
                if (vec != null) {
                    vectors.add(vec);
                } // Error logged in deserializeBlockVector3 if null
            } else {
                 plugin.getLogger().warning("[StructureLoader] Invalid element type in vector array '" + listName + "' at index " + i + " (expected JSON object) for template '" + segmentName + "' in " + sourceFileName);
            }
        }
        return vectors;
    }

     /**
      * Deserializes a JSON array of entry point objects into a List of RelativeEntryPoint.
      * Expected entry point format: {"relativePosition": {"x": X, "y": Y, "z": Z}, "direction": "NORTH"}
      */
     private List<RelativeEntryPoint> deserializeRelativeEntryPoints(JsonArray epArray, String segmentName, String sourceFileName) {
         List<RelativeEntryPoint> entryPoints = new ArrayList<>();
         if (epArray == null || !epArray.isJsonArray()) {
              // plugin.getLogger().warning("[StructureLoader] Invalid or missing entryPoints array for template '" + segmentName + "' in " + sourceFileName);
              return entryPoints; // Return empty list
         }

         for (int i = 0; i < epArray.size(); i++) {
              JsonElement epElement = epArray.get(i);
             if (epElement != null && epElement.isJsonObject()) {
                 JsonObject epJson = epElement.getAsJsonObject();
                 BlockVector3 relPos = null;
                 Direction direction = null; // Use your actual Direction type/enum

                 // Deserialize relative position (assuming nested object)
                 if (epJson.has("relativePosition") && epJson.get("relativePosition").isJsonObject()) {
                      relPos = deserializeBlockVector3(epJson.getAsJsonObject("relativePosition"), "entryPoints[" + i + "].relativePosition", segmentName, sourceFileName);
                 } else {
                      plugin.getLogger().warning("[StructureLoader] Missing/invalid nested 'relativePosition' object in entryPoints[" + i + "] for template '" + segmentName + "' in " + sourceFileName);
                 }

                 // Deserialize direction string
                 String dirStr = getJsonString(epJson, "direction", sourceFileName);
                 if (dirStr != null) {
                     try {
                         // --- IMPORTANT: Replace with your actual Direction enum/class parsing ---
                         // Example 1: Using Bukkit BlockFace
                          direction = Direction.valueOf(dirStr.toUpperCase()); // Assuming Direction enum exists
                         // Example 2: Using your custom Direction enum
                         // direction = com.clarkson.sot.utils.Direction.valueOf(dirStr.toUpperCase());
                     } catch (IllegalArgumentException e) {
                          plugin.getLogger().warning("[StructureLoader] Invalid direction string '" + dirStr + "' in entryPoints[" + i + "] for template '" + segmentName + "' from " + sourceFileName);
                     }
                 } else {
                      plugin.getLogger().warning("[StructureLoader] Missing direction string in entryPoints[" + i + "] for template '" + segmentName + "' from " + sourceFileName);
                 }

                 // Only add if both relative position and direction were successfully parsed
                 if (relPos != null && direction != null) {
                      // Ensure your RelativeEntryPoint constructor takes BlockVector3 and your Direction type
                      entryPoints.add(new RelativeEntryPoint(relPos, direction));
                 } else {
                      plugin.getLogger().warning("[StructureLoader] Skipping entryPoints[" + i + "] due to missing/invalid relativePosition or direction in template '" + segmentName + "' from " + sourceFileName);
                 }
             } else {
                  plugin.getLogger().warning("[StructureLoader] Invalid element type in entryPoints array at index " + i + " (expected JSON object) for template '" + segmentName + "' in " + sourceFileName);
             }
         }
         return entryPoints;
     }

}
