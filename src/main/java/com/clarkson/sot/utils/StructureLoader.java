package com.clarkson.sot.utils;

// Local project imports
import com.clarkson.sot.dungeon.SegmentType;
import com.clarkson.sot.dungeon.VaultColor; // Added import
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.Segment.RelativeEntryPoint;

// WorldEdit imports
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
 * dimensions, gameplay metadata, and schematic file references.
 */
public class StructureLoader {

    // Use a single Gson instance for efficiency
    private final Gson gson = new Gson();
    private final Plugin plugin; // Reference to the plugin for logging

    /**
     * Constructor for StructureLoader.
     * @param plugin The main plugin instance.
     */
    public StructureLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads Segment template metadata from .json files found directly within the specified directory.
     *
     * @param dataDir The base directory containing the .json segment metadata files (e.g., plugin.getDataFolder()).
     * @return A List of loaded Segment template objects. Returns an empty list if the directory is invalid, not found, or contains no valid .json segment files.
     */
    public List<Segment> loadSegmentTemplates(File dataDir) {
        List<Segment> loadedSegments = new ArrayList<>();

        // --- Validate Input Directory ---
        if (dataDir == null) {
            plugin.getLogger().severe("[StructureLoader] Cannot load segments: Provided data directory is null.");
            return loadedSegments; // Return empty list
        }
        if (!dataDir.isDirectory()) {
            plugin.getLogger().severe("[StructureLoader] Cannot load segments: Provided path is not a directory: " + dataDir.getAbsolutePath());
            return loadedSegments; // Return empty list
        }

        // --- List Potential JSON Files ---
        // Find files ending with .json (case-insensitive)
        File[] jsonFiles = dataDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

        // Check if any JSON files were found
        if (jsonFiles == null || jsonFiles.length == 0) {
            plugin.getLogger().info("[StructureLoader] No '.json' segment files found directly in directory: " + dataDir.getAbsolutePath());
            return loadedSegments; // Return empty list
        }

        plugin.getLogger().info("[StructureLoader] Found " + jsonFiles.length + " potential segment JSON files in " + dataDir.getAbsolutePath() + ". Attempting to load templates...");

        // --- Read, Parse, and Deserialize Each JSON File ---
        for (File jsonFile : jsonFiles) {
            // Use try-with-resources for FileReader to ensure it's closed
            try (FileReader reader = new FileReader(jsonFile)) {
                // Parse the JSON file into a JsonObject
                JsonObject segmentJson = gson.fromJson(reader, JsonObject.class);
                // Attempt to deserialize the JsonObject into a Segment object
                Segment segment = deserializeSegmentTemplateFromJson(segmentJson, jsonFile.getName());

                // If deserialization was successful, add the segment to the list
                if (segment != null) {
                    loadedSegments.add(segment);
                    plugin.getLogger().info("[StructureLoader] Successfully loaded segment template: '" + segment.getName() + "' from " + jsonFile.getName());
                }
                // Errors during deserialization are logged within deserializeSegmentTemplateFromJson

            } catch (IOException e) {
                // Handle errors reading the file
                plugin.getLogger().log(Level.SEVERE, "[StructureLoader] IOException while reading segment file: " + jsonFile.getName(), e);
            } catch (JsonIOException | JsonSyntaxException e) {
                // Handle errors parsing the JSON structure
                plugin.getLogger().log(Level.SEVERE, "[StructureLoader] Invalid JSON syntax or read error in segment file: " + jsonFile.getName(), e);
            } catch (Exception e) {
                // Catch any other unexpected errors during processing
                plugin.getLogger().log(Level.SEVERE, "[StructureLoader] Unexpected error processing segment file: " + jsonFile.getName(), e);
            }
        }

        plugin.getLogger().info("[StructureLoader] Finished loading segment templates. Total loaded: " + loadedSegments.size());
        return loadedSegments; // Return the list of successfully loaded segments
    }

    /**
     * Attempts to deserialize a JsonObject into a world-independent Segment template object.
     * Extracts all fields defined in the Segment class from the JSON.
     *
     * @param json           The JsonObject containing the segment template data.
     * @param sourceFileName The name of the file being parsed (for logging context).
     * @return A fully reconstructed Segment object, or null if critical data is missing/invalid.
     */
    private Segment deserializeSegmentTemplateFromJson(JsonObject json, String sourceFileName) {
        try {
            // --- Extract Core Properties ---
            String name = getJsonString(json, "name", sourceFileName);
            String schematicFileName = getJsonString(json, "schematicFileName", sourceFileName);

            // --- Validate Critical Fields ---
            if (name == null) {
                plugin.getLogger().warning("[StructureLoader] Skipping template from " + sourceFileName + ": Missing or invalid 'name'.");
                return null;
            }
            if (schematicFileName == null) {
                plugin.getLogger().warning("[StructureLoader] Skipping template '" + name + "' from " + sourceFileName + ": Missing or invalid 'schematicFileName'. This is required.");
                return null;
            }

            // --- Deserialize Structure & Spawns ---
            String typeStr = getJsonString(json, "type", sourceFileName);
            SegmentType type = parseSegmentType(typeStr, name, sourceFileName); // Handles null typeStr

            BlockVector3 size = deserializeBlockVector3(json.getAsJsonObject("size"), "size", name, sourceFileName);
            if (size == null || size.x() <= 0 || size.y() <= 0 || size.z() <= 0) {
                 plugin.getLogger().severe("[StructureLoader] Skipping template '" + name + "' from " + sourceFileName + ": Invalid or missing 'size'.");
                 return null; // Size is essential
            }

            List<RelativeEntryPoint> entryPoints = deserializeRelativeEntryPoints(json.getAsJsonArray("entryPoints"), name, sourceFileName);
            List<BlockVector3> sandSpawns = deserializeBlockVectorList(json.getAsJsonArray("sandSpawnLocations"), "sandSpawnLocations", name, sourceFileName);
            List<BlockVector3> itemSpawns = deserializeBlockVectorList(json.getAsJsonArray("itemSpawnLocations"), "itemSpawnLocations", name, sourceFileName);
            List<BlockVector3> coinSpawns = deserializeBlockVectorList(json.getAsJsonArray("coinSpawnLocations"), "coinSpawnLocations", name, sourceFileName);

            // --- Deserialize Gameplay Metadata (with defaults) ---
            Integer totalCoins = getJsonInt(json, "totalCoins", sourceFileName);
            Double coinMultiplier = getJsonDouble(json, "coinMultiplier", sourceFileName);
            Boolean isHub = getJsonBoolean(json, "isHub", sourceFileName);
            Boolean isPuzzleRoom = getJsonBoolean(json, "isPuzzleRoom", sourceFileName);
            Boolean isLavaParkour = getJsonBoolean(json, "isLavaParkour", sourceFileName);

            // Deserialize Vault/Key Enums (can be null)
            String vaultStr = getJsonString(json, "containedVault", sourceFileName);
            VaultColor containedVault = parseVaultColor(vaultStr, name, "containedVault", sourceFileName);
            String keyStr = getJsonString(json, "containedVaultKey", sourceFileName);
            VaultColor containedVaultKey = parseVaultColor(keyStr, name, "containedVaultKey", sourceFileName);

            // Deserialize Vault/Key Offsets (can be null)
            BlockVector3 vaultLocationOffset = null;
            if (json.has("vaultLocationOffset")) { // Only attempt if key exists
                 vaultLocationOffset = deserializeBlockVector3(json.getAsJsonObject("vaultLocationOffset"), "vaultLocationOffset", name, sourceFileName);
            }
            BlockVector3 keyLocationOffset = null;
            if (json.has("keyLocationOffset")) { // Only attempt if key exists
                 keyLocationOffset = deserializeBlockVector3(json.getAsJsonObject("keyLocationOffset"), "keyLocationOffset", name, sourceFileName);
            }

            // --- Construct the Segment Template Object ---
            // Ensure this call matches the Segment constructor signature exactly
            return new Segment(
                    name,
                    type, // Can be null if not specified or invalid
                    schematicFileName,
                    size,
                    // Provide empty lists if deserialization returned null (though helpers return empty lists now)
                    entryPoints != null ? entryPoints : new ArrayList<>(),
                    sandSpawns != null ? sandSpawns : new ArrayList<>(),
                    itemSpawns != null ? itemSpawns : new ArrayList<>(),
                    coinSpawns != null ? coinSpawns : new ArrayList<>(),
                    totalCoins != null ? totalCoins : 0, // Default totalCoins to 0 if missing/invalid
                    // Provide defaults for new metadata fields if missing/invalid
                    coinMultiplier != null ? coinMultiplier : 1.0, // Default multiplier to 1.0
                    isHub != null ? isHub : false,                 // Default isHub to false
                    isPuzzleRoom != null ? isPuzzleRoom : false,   // Default isPuzzleRoom to false
                    isLavaParkour != null ? isLavaParkour : false, // Default isLavaParkour to false
                    containedVault,      // Can be null
                    containedVaultKey,   // Can be null
                    vaultLocationOffset, // Can be null
                    keyLocationOffset    // Can be null
            );

        } catch (JsonParseException | IllegalStateException | ClassCastException | NullPointerException e) {
            // Handle errors related to JSON structure or unexpected data types
            plugin.getLogger().log(Level.SEVERE, "[StructureLoader] Failed to parse template data from " + sourceFileName + ". Invalid JSON structure or data types.", e);
            return null;
        } catch (Exception e) {
            // Catch any other unexpected errors during deserialization
            plugin.getLogger().log(Level.SEVERE, "[StructureLoader] Unexpected error deserializing template from " + sourceFileName, e);
            return null;
        }
    }

    // --- Helper methods for safe JSON access and deserialization ---

    /** Safely gets a String value from a JsonObject, returning null if missing, not a string, or empty. */
    private String getJsonString(JsonObject json, String key, String sourceFileName) {
        if (json != null && json.has(key)) {
             JsonElement element = json.get(key);
            // Check if it's a primitive string and not null
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String value = element.getAsString();
                // Return trimmed string if not empty, otherwise null
                return (value != null && !value.trim().isEmpty()) ? value.trim() : null;
            } else if (element.isJsonNull()) {
                 return null; // Explicitly handle JsonNull
            }
        }
        // Key not found or not a string primitive
        return null;
    }

    /** Safely gets an Integer value from a JsonObject, returning null if missing, not a number, or format error. */
    private Integer getJsonInt(JsonObject json, String key, String sourceFileName) {
        if (json != null && json.has(key)) {
             JsonElement element = json.get(key);
             // Check if it's a primitive number and not null
             if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                 try {
                     return element.getAsInt(); // Attempt to get as int
                 } catch (NumberFormatException e) {
                     // Log warning if number format is wrong (e.g., decimal for int)
                     plugin.getLogger().warning("[StructureLoader] Invalid integer format for field '" + key + "' in " + sourceFileName);
                     return null;
                 }
             } else if (element.isJsonNull()) {
                  return null; // Explicitly handle JsonNull
             }
        }
        // Key not found or not a number primitive
        return null;
    }

    /** Safely gets a Double value from a JsonObject, returning null if missing, not a number, or format error. */
    private Double getJsonDouble(JsonObject json, String key, String sourceFileName) {
         if (json != null && json.has(key)) {
              JsonElement element = json.get(key);
              // Check if it's a primitive number and not null
              if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                  try {
                      return element.getAsDouble(); // Attempt to get as double
                  } catch (NumberFormatException e) {
                      // This catch might be less common for getAsDouble but good practice
                      plugin.getLogger().warning("[StructureLoader] Invalid double format for field '" + key + "' in " + sourceFileName);
                      return null;
                  }
              } else if (element.isJsonNull()) {
                   return null; // Explicitly handle JsonNull
              }
         }
         // Key not found or not a number primitive
         return null;
     }

    /** Safely gets a Boolean value from a JsonObject, returning null if missing or not a boolean. */
    private Boolean getJsonBoolean(JsonObject json, String key, String sourceFileName) {
         if (json != null && json.has(key)) {
             JsonElement element = json.get(key);
             // Check if it's a primitive boolean and not null
             if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
                 return element.getAsBoolean();
             } else if (element.isJsonNull()) {
                  return null; // Explicitly handle JsonNull
             }
         }
         // Key not found or not a boolean primitive
         return null;
     }


    /** Parses a String into a SegmentType enum, handling null input and invalid values. */
    private SegmentType parseSegmentType(String typeStr, String segmentName, String sourceFileName) {
        if (typeStr == null) {
            // Log if type is missing (optional, could just return null silently)
            // plugin.getLogger().fine("[StructureLoader] Missing segment type for template '" + segmentName + "' in " + sourceFileName + ". Type set to null.");
            return null; // Return null if input string is null
        }
        try {
            // Attempt to convert uppercase string to enum value
            return SegmentType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Log warning if the string doesn't match any enum constant
            plugin.getLogger().warning("[StructureLoader] Invalid segment type '" + typeStr + "' for template '" + segmentName + "' in " + sourceFileName + ". Type set to null.");
            return null; // Return null for invalid type strings
        }
    }

    /** Parses a String into a VaultColor enum, handling null input and invalid values. */
    private VaultColor parseVaultColor(String colorStr, String segmentName, String fieldName, String sourceFileName) {
         if (colorStr == null) {
             return null; // Return null if input string is null (field wasn't present)
         }
         try {
             // Attempt to convert uppercase string to enum value
             return VaultColor.valueOf(colorStr.toUpperCase());
         } catch (IllegalArgumentException e) {
             // Log warning if the string doesn't match any enum constant
             plugin.getLogger().warning("[StructureLoader] Invalid VaultColor '" + colorStr + "' for field '" + fieldName + "' in template '" + segmentName + "' from " + sourceFileName + ". Value set to null.");
             return null; // Return null for invalid color strings
         }
     }

    /**
     * Deserializes a JSON object representing coordinates into a BlockVector3.
     * Expected format: {"x": 1, "y": 0, "z": 5}
     * Logs warnings if the format is incorrect or coordinates are missing.
     */
    private BlockVector3 deserializeBlockVector3(JsonObject vecJson, String context, String segmentName, String sourceFileName) {
        // Check if the input JsonObject is valid
        if (vecJson == null || !vecJson.isJsonObject()) {
            plugin.getLogger().warning("[StructureLoader] Invalid or missing JSON object for " + context + " in template '" + segmentName + "' from " + sourceFileName);
            return null;
        }
        try {
            // Safely get integer coordinates using helper
            Integer x = getJsonInt(vecJson, "x", sourceFileName);
            Integer y = getJsonInt(vecJson, "y", sourceFileName);
            Integer z = getJsonInt(vecJson, "z", sourceFileName);

            // Check if all coordinates were successfully parsed
            if (x == null || y == null || z == null) {
                plugin.getLogger().warning("[StructureLoader] Missing or invalid coordinate (x, y, or z) for " + context + " in template '" + segmentName + "' from " + sourceFileName);
                return null; // Return null if any coordinate is missing
            }
            // Create and return the BlockVector3 using WorldEdit's factory method
            return BlockVector3.at(x, y, z);
        } catch (Exception e) {
            // Catch any other unexpected errors during vector creation
            plugin.getLogger().warning("[StructureLoader] Failed to create BlockVector3 for " + context + " in template '" + segmentName + "' from " + sourceFileName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Deserializes a JSON array of coordinate objects into a List of BlockVector3.
     * Handles null or invalid arrays, and skips invalid elements within the array.
     */
    private List<BlockVector3> deserializeBlockVectorList(JsonArray vecArray, String listName, String segmentName, String sourceFileName) {
        List<BlockVector3> vectors = new ArrayList<>();
        // Check if the input JsonArray is valid
        if (vecArray == null || !vecArray.isJsonArray()) {
            // It's often normal for these lists to be missing, so don't log a warning unless expected.
            // plugin.getLogger().fine("[StructureLoader] Missing or invalid vector array '" + listName + "' for template '" + segmentName + "' in " + sourceFileName);
            return vectors; // Return empty list if array is invalid or missing
        }

        // Iterate through the elements of the array
        for (int i = 0; i < vecArray.size(); i++) {
            JsonElement vecElement = vecArray.get(i);
            // Check if the element is a valid JsonObject
            if (vecElement != null && vecElement.isJsonObject()) {
                // Deserialize the object into a BlockVector3 using the helper
                BlockVector3 vec = deserializeBlockVector3(vecElement.getAsJsonObject(), listName + "[" + i + "]", segmentName, sourceFileName);
                // Add the vector to the list if deserialization was successful
                if (vec != null) {
                    vectors.add(vec);
                } // Error is logged in deserializeBlockVector3 if null
            } else {
                // Log a warning for invalid elements in the array
                plugin.getLogger().warning("[StructureLoader] Invalid element type in vector array '" + listName + "' at index " + i + " (expected JSON object) for template '" + segmentName + "' in " + sourceFileName);
            }
        }
        return vectors; // Return the list of successfully deserialized vectors
    }

    /**
     * Deserializes a JSON array of entry point objects into a List of RelativeEntryPoint.
     * Expected entry point format: {"relativePosition": {"x": X, "y": Y, "z": Z}, "direction": "NORTH"}
     * Handles null or invalid arrays, and skips invalid elements.
     */
    private List<RelativeEntryPoint> deserializeRelativeEntryPoints(JsonArray epArray, String segmentName, String sourceFileName) {
        List<RelativeEntryPoint> entryPoints = new ArrayList<>();
        // Check if the input JsonArray is valid
        if (epArray == null || !epArray.isJsonArray()) {
            // plugin.getLogger().fine("[StructureLoader] Missing or invalid entryPoints array for template '" + segmentName + "' in " + sourceFileName);
            return entryPoints; // Return empty list if array is invalid or missing
        }

        // Iterate through the elements of the array
        for (int i = 0; i < epArray.size(); i++) {
            JsonElement epElement = epArray.get(i);
            // Check if the element is a valid JsonObject
            if (epElement != null && epElement.isJsonObject()) {
                JsonObject epJson = epElement.getAsJsonObject();
                BlockVector3 relPos = null;
                Direction direction = null;

                // Deserialize relative position (expecting a nested object)
                if (epJson.has("relativePosition") && epJson.get("relativePosition").isJsonObject()) {
                    relPos = deserializeBlockVector3(epJson.getAsJsonObject("relativePosition"), "entryPoints[" + i + "].relativePosition", segmentName, sourceFileName);
                } else {
                    plugin.getLogger().warning("[StructureLoader] Missing/invalid nested 'relativePosition' object in entryPoints[" + i + "] for template '" + segmentName + "' in " + sourceFileName);
                }

                // Deserialize direction string
                String dirStr = getJsonString(epJson, "direction", sourceFileName);
                if (dirStr != null) {
                    try {
                        // Convert uppercase string to Direction enum value
                        direction = Direction.valueOf(dirStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        // Log warning if direction string is invalid
                        plugin.getLogger().warning("[StructureLoader] Invalid direction string '" + dirStr + "' in entryPoints[" + i + "] for template '" + segmentName + "' from " + sourceFileName);
                    }
                } else {
                    // Log warning if direction string is missing
                    plugin.getLogger().warning("[StructureLoader] Missing direction string in entryPoints[" + i + "] for template '" + segmentName + "' from " + sourceFileName);
                }

                // Only create and add the RelativeEntryPoint if both position and direction were valid
                if (relPos != null && direction != null) {
                    entryPoints.add(new RelativeEntryPoint(relPos, direction));
                } else {
                    // Log warning if skipping due to missing parts
                    plugin.getLogger().warning("[StructureLoader] Skipping entryPoints[" + i + "] due to missing/invalid relativePosition or direction in template '" + segmentName + "' from " + sourceFileName);
                }
            } else {
                // Log warning for invalid elements in the array
                plugin.getLogger().warning("[StructureLoader] Invalid element type in entryPoints array at index " + i + " (expected JSON object) for template '" + segmentName + "' in " + sourceFileName);
            }
        }
        return entryPoints; // Return the list of successfully deserialized entry points
    }
}
