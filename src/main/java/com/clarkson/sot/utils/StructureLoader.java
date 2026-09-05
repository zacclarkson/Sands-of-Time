package com.clarkson.sot.utils;

// Local project imports
import com.clarkson.sot.dungeon.segment.*;
import com.clarkson.sot.dungeon.VaultColor;
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.Segment.RelativeEntryPoint;
import com.clarkson.sot.dungeon.segment.SegmentBound;

// WorldEdit imports
import com.sk89q.worldedit.math.BlockVector3;

// Gson imports
import com.google.gson.*;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// Java IO and Util
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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

        // Sort by filename before loading. File.listFiles() has no defined order, and the dungeon
        // seed indexes into this list (DungeonGenerator picks templates by random index, and takes
        // the first HUB it finds), so an unsorted list would make the same seed produce a different
        // dungeon on a different machine -- or after the data folder is simply re-copied.
        Arrays.sort(jsonFiles, Comparator.comparing(File::getName));

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
                    warnIfDeclaredSizeIsTooSmall(segment, new File(dataDir, "schematics"));
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
     * Warns when a template declares a {@code size} smaller than the schematic it points at.
     *
     * <p>Nothing else compares the two, and the declared size is what the blueprint bounds — and so the
     * region {@code DungeonManager.cleanupInstance()} air-fills between rounds — are built from. Under-declare
     * it and the part of the build outside those bounds is left standing at teardown, which the next
     * round's paste cannot clear either ({@code ignoreAirBlocks}). That failure surfaces a round later and
     * nowhere near its cause, so it is worth saying at load.
     *
     * <p>Only an under-declared size is a defect: declaring more is how the bundled hub reserves the air
     * above it for the visual sand timer column. Everything here is best effort — a schematic that is
     * absent or unreadable is left to the paste, which already fails loudly and names the file.
     */
    private void warnIfDeclaredSizeIsTooSmall(Segment segment, File schematicsDir) {
        File schematicFile = new File(schematicsDir, segment.getSchematicFileName());
        if (!schematicFile.isFile()) {
            plugin.getLogger().fine("[StructureLoader] No schematic at " + schematicFile.getPath()
                    + " to size-check template '" + segment.getName() + "' against.");
            return;
        }
        BlockVector3 actual;
        try {
            actual = SchematicDimensions.read(schematicFile);
        } catch (Exception | LinkageError e) {
            plugin.getLogger().fine("[StructureLoader] Could not read the dimensions of "
                    + schematicFile.getName() + " to size-check '" + segment.getName() + "': " + e);
            return;
        }
        if (actual == null) {
            plugin.getLogger().fine("[StructureLoader] " + schematicFile.getName()
                    + " carries no dimension header; not size-checking '" + segment.getName() + "'.");
            return;
        }
        BlockVector3 declared = segment.getSize();
        if (!SchematicDimensions.covers(declared, actual)) {
            plugin.getLogger().warning("[StructureLoader] Template '" + segment.getName() + "' declares size "
                    + describe(declared) + " but " + schematicFile.getName() + " is " + describe(actual)
                    + ". Everything outside the declared size is left behind when the dungeon is cleaned up"
                    + " between rounds. Re-save the segment with a WorldEdit selection that covers the whole"
                    + " build, or edit \"size\" in the template's .json.");
        }
    }

    /** Formats a size as {@code 42x17x37} for the size-mismatch warning. */
    private static String describe(BlockVector3 size) {
        return size.x() + "x" + size.y() + "x" + size.z();
    }

    /**
     * Attempts to deserialize a JsonObject into a world-independent Segment template object.
     * Extracts all fields defined in the Segment class from the JSON (as per latest Segment structure).
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

            // --- Deserialize Gameplay Metadata ---
            Integer totalCoins = getJsonInt(json, "totalCoins", sourceFileName);
            // Removed loading for: coinMultiplier, isHub, isPuzzleRoom, isLavaParkour

            // Deserialize Vault/Key Enums (can be null)
            String vaultStr = getJsonString(json, "containedVault", sourceFileName);
            VaultColor containedVault = parseVaultColor(vaultStr, name, "containedVault", sourceFileName);
            String keyStr = getJsonString(json, "containedVaultKey", sourceFileName);
            VaultColor containedVaultKey = parseVaultColor(keyStr, name, "containedVaultKey", sourceFileName);

            // Deserialize Vault/Key Offsets (can be null)
            BlockVector3 vaultLocationOffset = null;
            if (json.has("vaultLocationOffset") && json.get("vaultLocationOffset").isJsonObject()) { // Check if it exists and is an object
                 vaultLocationOffset = deserializeBlockVector3(json.getAsJsonObject("vaultLocationOffset"), "vaultLocationOffset", name, sourceFileName);
            }
            BlockVector3 keyLocationOffset = null;
            if (json.has("keyLocationOffset") && json.get("keyLocationOffset").isJsonObject()) { // Check if it exists and is an object
                 keyLocationOffset = deserializeBlockVector3(json.getAsJsonObject("keyLocationOffset"), "keyLocationOffset", name, sourceFileName);
            }

            // --- Deserialize new fields ---
            SegmentBound vaultDoorBound = null;
            if (json.has("vaultDoorBound") && json.get("vaultDoorBound").isJsonObject()) {
                vaultDoorBound = deserializeSegmentBound(
                        json.getAsJsonObject("vaultDoorBound"), "vaultDoorBound", name, sourceFileName);
            }

            List<SegmentBound> gates = deserializeSegmentBoundList(
                    json.getAsJsonArray("gates"), "gates", name, sourceFileName);

            BlockVector3 leverOffset = null;
            if (json.has("leverOffset") && json.get("leverOffset").isJsonObject()) {
                leverOffset = deserializeBlockVector3(
                        json.getAsJsonObject("leverOffset"), "leverOffset", name, sourceFileName);
            }

            BlockVector3 safeExitOffset = null;
            if (json.has("safeExitOffset") && json.get("safeExitOffset").isJsonObject()) {
                safeExitOffset = deserializeBlockVector3(
                        json.getAsJsonObject("safeExitOffset"), "safeExitOffset", name, sourceFileName);
            }

            SegmentBound safeExitBound = null;
            if (json.has("safeExitBound") && json.get("safeExitBound").isJsonObject()) {
                safeExitBound = deserializeSegmentBound(
                        json.getAsJsonObject("safeExitBound"), "safeExitBound", name, sourceFileName);
            }

            List<BlockVector3> sandSacrifices = deserializeBlockVectorList(
                    json.getAsJsonArray("sandSacrificeLocations"), "sandSacrificeLocations", name, sourceFileName);
            List<BlockVector3> mobSpawners = deserializeBlockVectorList(
                    json.getAsJsonArray("mobSpawnerLocations"), "mobSpawnerLocations", name, sourceFileName);
            // Optional: templates saved before gate sacrifices had a price carry no array here, and
            // the Segment constructor pads a short or missing list with the default cost. (A stale
            // "sandTradeLocations" key from the removed trade chest is simply never read.)
            List<Integer> sandSacrificeCosts = json.has("sandSacrificeCosts")
                    ? deserializeSacrificeCosts(json.get("sandSacrificeCosts"), name, sourceFileName)
                    : new ArrayList<>();

            // --- Deserialize hub features ---
            BlockVector3 bankOffset = null;
            if (json.has("bankLocationOffset") && json.get("bankLocationOffset").isJsonObject()) {
                bankOffset = deserializeBlockVector3(
                        json.getAsJsonObject("bankLocationOffset"), "bankLocationOffset", name, sourceFileName);
            }
            List<BlockVector3> deathCages = deserializeBlockVectorList(
                    json.getAsJsonArray("deathCageLocations"), "deathCageLocations", name, sourceFileName);
            List<BlockVector3> sandTimers = deserializeBlockVectorList(
                    json.getAsJsonArray("sandTimerLocations"), "sandTimerLocations", name, sourceFileName);
            // Optional: templates saved before PLAYER_SPAWN existed simply have no array here.
            List<BlockVector3> playerSpawns = json.has("playerSpawnLocations")
                    ? deserializeBlockVectorList(json.getAsJsonArray("playerSpawnLocations"),
                            "playerSpawnLocations", name, sourceFileName)
                    : new ArrayList<>();
            BlockVector3 timerOffset = null;
            if (json.has("timerLocationOffset") && json.get("timerLocationOffset").isJsonObject()) {
                timerOffset = deserializeBlockVector3(
                        json.getAsJsonObject("timerLocationOffset"), "timerLocationOffset", name, sourceFileName);
            }
            // Optional: templates saved before BRANCH_SIGNIFIER existed simply have no array here,
            // and generate a dungeon with no colour markings rather than failing to load.
            List<BlockVector3> branchSignifiers = json.has("branchSignifierLocations")
                    ? deserializeBlockVectorList(json.getAsJsonArray("branchSignifierLocations"),
                            "branchSignifierLocations", name, sourceFileName)
                    : new ArrayList<>();

            // --- Construct the Segment Template Object ---
            return new Segment(
                    name,
                    type,
                    schematicFileName,
                    size,
                    entryPoints != null ? entryPoints : new ArrayList<>(),
                    sandSpawns  != null ? sandSpawns  : new ArrayList<>(),
                    itemSpawns  != null ? itemSpawns  : new ArrayList<>(),
                    coinSpawns  != null ? coinSpawns  : new ArrayList<>(),
                    totalCoins  != null ? totalCoins  : 0,
                    containedVault,
                    containedVaultKey,
                    vaultLocationOffset,
                    keyLocationOffset,
                    // New fields
                    vaultDoorBound,
                    gates  != null ? gates  : new ArrayList<>(),
                    leverOffset,
                    sandSacrifices != null ? sandSacrifices : new ArrayList<>(),
                    mobSpawners    != null ? mobSpawners    : new ArrayList<>(),
                    safeExitOffset,
                    bankOffset,
                    deathCages != null ? deathCages : new ArrayList<>(),
                    safeExitBound,
                    sandTimers != null ? sandTimers : new ArrayList<>(),
                    timerOffset,
                    playerSpawns != null ? playerSpawns : new ArrayList<>(),
                    branchSignifiers != null ? branchSignifiers : new ArrayList<>(),
                    sandSacrificeCosts
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
            // Check if it's a primitive string and not null or empty
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String value = element.getAsString();
                return (value != null && !value.trim().isEmpty()) ? value.trim() : null;
            } else if (element.isJsonNull()) {
                 return null; // Explicitly handle JsonNull
            }
        }
        return null;
    }

    /** Safely gets an Integer value from a JsonObject, returning null if missing, not a number, or format error. */
    private Integer getJsonInt(JsonObject json, String key, String sourceFileName) {
        if (json != null && json.has(key)) {
             JsonElement element = json.get(key);
             if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                 try {
                     return element.getAsInt();
                 } catch (NumberFormatException e) {
                     plugin.getLogger().warning("[StructureLoader] Invalid integer format for field '" + key + "' in " + sourceFileName);
                     return null;
                 }
             } else if (element.isJsonNull()) {
                  return null;
             }
        }
        return null;
    }


    /** Parses a String into a SegmentType enum, handling null input and invalid values. */
    private SegmentType parseSegmentType(@Nullable String typeStr, String segmentName, String sourceFileName) {
        if (typeStr == null) {
            // Log warning if type is missing, as it's now more important
            plugin.getLogger().warning("[StructureLoader] Missing segment type for template '" + segmentName + "' in " + sourceFileName + ". Type set to null.");
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
    private VaultColor parseVaultColor(@Nullable String colorStr, String segmentName, String fieldName, String sourceFileName) {
         if (colorStr == null) {
             return null; // Field wasn't present or was null
         }
         try {
             // Attempt to convert uppercase string to enum value
             return VaultColor.valueOf(colorStr.toUpperCase());
         } catch (IllegalArgumentException e) {
             plugin.getLogger().warning("[StructureLoader] Invalid VaultColor '" + colorStr + "' for field '" + fieldName + "' in template '" + segmentName + "' from " + sourceFileName + ". Value set to null.");
             return null;
         }
     }

    /**
     * Deserializes a JSON object representing coordinates into a BlockVector3.
     * Expected format: {"x": 1, "y": 0, "z": 5}
     * Logs warnings if the format is incorrect or coordinates are missing. Returns null on failure.
     */
    @Nullable
    private BlockVector3 deserializeBlockVector3(@Nullable JsonElement vecElement, String context, String segmentName, String sourceFileName) {
        if (vecElement == null || !vecElement.isJsonObject()) {
            // Only log warning if context suggests it should usually be present (like 'size')
            if ("size".equals(context)) {
                 plugin.getLogger().warning("[StructureLoader] Invalid or missing JSON object for " + context + " in template '" + segmentName + "' from " + sourceFileName);
            }
            return null;
        }
        JsonObject vecJson = vecElement.getAsJsonObject();
        try {
            Integer x = getJsonInt(vecJson, "x", sourceFileName);
            Integer y = getJsonInt(vecJson, "y", sourceFileName);
            Integer z = getJsonInt(vecJson, "z", sourceFileName);

            if (x == null || y == null || z == null) {
                plugin.getLogger().warning("[StructureLoader] Missing or invalid coordinate (x, y, or z) for " + context + " in template '" + segmentName + "' from " + sourceFileName);
                return null;
            }
            return BlockVector3.at(x, y, z);
        } catch (Exception e) {
            plugin.getLogger().warning("[StructureLoader] Failed to create BlockVector3 for " + context + " in template '" + segmentName + "' from " + sourceFileName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Deserializes the {@code sandSacrificeCosts} array. A non-numeric entry is replaced by
     * {@link Segment#DEFAULT_SACRIFICE_COST} and an out-of-range one is clamped, each with a warning,
     * so one bad number never costs the whole template.
     */
    private List<Integer> deserializeSacrificeCosts(@Nullable JsonElement arrayElement, String segmentName, String sourceFileName) {
        List<Integer> costs = new ArrayList<>();
        if (arrayElement == null || !arrayElement.isJsonArray()) {
            plugin.getLogger().warning("[StructureLoader] 'sandSacrificeCosts' in " + sourceFileName
                    + " (segment " + segmentName + ") is not an array; using the default cost for every chest.");
            return costs;
        }
        JsonArray array = arrayElement.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                plugin.getLogger().warning("[StructureLoader] sandSacrificeCosts[" + i + "] in " + sourceFileName
                        + " (segment " + segmentName + ") is not a number; using " + Segment.DEFAULT_SACRIFICE_COST + ".");
                costs.add(Segment.DEFAULT_SACRIFICE_COST);
                continue;
            }
            int raw = element.getAsInt();
            int clamped = Segment.clampSacrificeCost(raw);
            if (clamped != raw) {
                plugin.getLogger().warning("[StructureLoader] sandSacrificeCosts[" + i + "] in " + sourceFileName
                        + " (segment " + segmentName + ") is " + raw + "; clamped to " + clamped
                        + " (valid range 1-" + Segment.MAX_SACRIFICE_COST + ").");
            }
            costs.add(clamped);
        }
        return costs;
    }

    /**
     * Deserializes a JSON array of coordinate objects into a List of BlockVector3.
     * Handles null or invalid arrays, and skips invalid elements within the array. Returns an empty list if input is null/invalid.
     */
    private List<BlockVector3> deserializeBlockVectorList(@Nullable JsonElement arrayElement, String listName, String segmentName, String sourceFileName) {
        List<BlockVector3> vectors = new ArrayList<>();
        if (arrayElement == null || !arrayElement.isJsonArray()) {
            // Missing lists are often okay, don't log unless necessary
            return vectors;
        }
        JsonArray vecArray = arrayElement.getAsJsonArray();
        for (int i = 0; i < vecArray.size(); i++) {
            JsonElement vecElement = vecArray.get(i);
            BlockVector3 vec = deserializeBlockVector3(vecElement, listName + "[" + i + "]", segmentName, sourceFileName);
            if (vec != null) {
                vectors.add(vec);
            } // Error logged in deserializeBlockVector3 if null
        }
        return vectors;
    }

    /**
     * Deserializes a JSON object into a SegmentBound.
     * Expected format: {"min": {"x": X, "y": Y, "z": Z}, "max": {"x": X, "y": Y, "z": Z}}
     */
    @Nullable
    private SegmentBound deserializeSegmentBound(@Nullable JsonObject boundJson,
                                                  String context, String segmentName, String sourceFileName) {
        if (boundJson == null) return null;
        BlockVector3 min = deserializeBlockVector3(boundJson.get("min"), context + ".min", segmentName, sourceFileName);
        BlockVector3 max = deserializeBlockVector3(boundJson.get("max"), context + ".max", segmentName, sourceFileName);
        if (min == null || max == null) {
            plugin.getLogger().warning("[StructureLoader] Incomplete SegmentBound for " + context
                    + " in '" + segmentName + "' from " + sourceFileName);
            return null;
        }
        return new SegmentBound(min, max);
    }

    /**
     * Deserializes a JSON array of SegmentBound objects.
     */
    @NotNull
    private List<SegmentBound> deserializeSegmentBoundList(@Nullable JsonElement arrayElement,
                                                            String listName, String segmentName, String sourceFileName) {
        List<SegmentBound> bounds = new ArrayList<>();
        if (arrayElement == null || !arrayElement.isJsonArray()) return bounds;
        JsonArray arr = arrayElement.getAsJsonArray();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement el = arr.get(i);
            if (el != null && el.isJsonObject()) {
                SegmentBound bound = deserializeSegmentBound(el.getAsJsonObject(),
                        listName + "[" + i + "]", segmentName, sourceFileName);
                if (bound != null) bounds.add(bound);
            }
        }
        return bounds;
    }

    /**
     * Deserializes a JSON array of entry point objects into a List of RelativeEntryPoint.
     * Expected entry point format: {"relativePosition": {"x": X, "y": Y, "z": Z}, "direction": "NORTH"}
     * Handles null or invalid arrays, and skips invalid elements. Returns an empty list if input is null/invalid.
     */
    private List<RelativeEntryPoint> deserializeRelativeEntryPoints(@Nullable JsonElement arrayElement, String segmentName, String sourceFileName) {
        List<RelativeEntryPoint> entryPoints = new ArrayList<>();
         if (arrayElement == null || !arrayElement.isJsonArray()) {
             return entryPoints;
         }
        JsonArray epArray = arrayElement.getAsJsonArray();
        for (int i = 0; i < epArray.size(); i++) {
            JsonElement epElement = epArray.get(i);
            if (epElement != null && epElement.isJsonObject()) {
                JsonObject epJson = epElement.getAsJsonObject();
                BlockVector3 relPos = deserializeBlockVector3(epJson.get("relativePosition"), "entryPoints[" + i + "].relativePosition", segmentName, sourceFileName);
                String dirStr = getJsonString(epJson, "direction", sourceFileName);
                Direction direction = null;
                if (dirStr != null) {
                    try {
                        direction = Direction.valueOf(dirStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("[StructureLoader] Invalid direction string '" + dirStr + "' in entryPoints[" + i + "] for template '" + segmentName + "' from " + sourceFileName);
                    }
                } else {
                     plugin.getLogger().warning("[StructureLoader] Missing direction string in entryPoints[" + i + "] for template '" + segmentName + "' from " + sourceFileName);
                }

                if (relPos != null && direction != null) {
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
