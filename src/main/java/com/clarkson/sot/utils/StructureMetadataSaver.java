package com.clarkson.sot.utils;

// WorldEdit imports (only for BlockVector3)
import com.sk89q.worldedit.math.BlockVector3;

// Bukkit imports (only for Plugin)
import org.bukkit.plugin.Plugin;

// Local project imports
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.Segment.RelativeEntryPoint;
import com.clarkson.sot.dungeon.VaultColor; // Import VaultColor if needed for serialization

// Gson imports
import com.google.gson.*;

// Java IO and Util
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern; // Import Pattern for regex


/**
 * Saves Segment TEMPLATE metadata to a JSON file using relative coordinates.
 * Takes a world-independent Segment template as input.
 * NOTE: This class DOES NOT save schematic files, only the JSON metadata.
 */
public class StructureMetadataSaver {

    private final Plugin plugin;
    private final Gson gson;
    // Regex to find characters NOT safe for typical filenames
    // Made static final as it's a constant pattern
    private static final Pattern INVALID_FILE_CHARS = Pattern.compile("[^a-zA-Z0-9_.-]");
    // Example limit - adjust based on target OS filesystem limitations
    private static final int MAX_FILENAME_LENGTH = 200;

    /**
     * Constructor for StructureMetadataSaver.
     * Initializes Gson for pretty printing JSON.
     * @param plugin The main plugin instance, used for logging and accessing data folder.
     */
    public StructureMetadataSaver(Plugin plugin) {
        this.plugin = plugin;
        // Initialize Gson with pretty printing enabled
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    /**
     * Saves segment template metadata JSON based on the provided world-independent Segment template.
     * Performs validation, serialization, and file writing.
     *
     * @param segmentTemplate The world-independent Segment template object.
     * @return true if JSON metadata was saved successfully, false otherwise.
     */
    public boolean saveMetadata(Segment segmentTemplate) {
        // --- Initial Validation (on the input template) ---
        if (!isSegmentTemplateValid(segmentTemplate)) {
            // Error logged internally in isSegmentTemplateValid
            return false;
        }

        // --- JSON Metadata Saving ---
        String segmentName = segmentTemplate.getName(); // Get name for logging
        JsonElement segmentJsonElement;
        try {
            // Directly serialize the template data from the input object
            segmentJsonElement = serializeSegmentTemplate(segmentTemplate);
            // Check if serialization resulted in null or JsonNull
            if (segmentJsonElement == null || segmentJsonElement.isJsonNull()) {
                plugin.getLogger().severe("[StructureMetadataSaver] Failed to serialize segment template metadata for: " + segmentName);
                return false;
            }
        } catch (Exception e) {
            // Catch unexpected errors during serialization process
            plugin.getLogger().log(Level.SEVERE, "[StructureMetadataSaver] Unexpected error during JSON serialization preparation for template: " + segmentName, e);
            return false;
        }

        // --- Prepare File Path ---
        String safeName = sanitizeFileName(segmentName);
        if (safeName.isEmpty()) {
            plugin.getLogger().severe("[StructureMetadataSaver] Cannot save JSON for template '" + segmentName + "': Name becomes empty after sanitization.");
            return false;
        }
        // Warn if sanitized name might be too long for some filesystems
        if (safeName.length() > MAX_FILENAME_LENGTH) {
            plugin.getLogger().warning("[StructureMetadataSaver] Sanitized filename for template '" + segmentName + "' ('" + safeName + "') may exceed filesystem limits.");
        }

        File jsonFile;
        try {
            // Get plugin's data folder
            File dataFolder = plugin.getDataFolder();
            // Create data folder if it doesn't exist
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                plugin.getLogger().severe("[StructureMetadataSaver] Failed to create plugin data folder: " + dataFolder.getAbsolutePath());
                return false;
            }
            // Define the target JSON file path
            jsonFile = new File(dataFolder, safeName + ".json");
        } catch (InvalidPathException | SecurityException e) {
            // Handle errors constructing the file path
            plugin.getLogger().log(Level.SEVERE, "[StructureMetadataSaver] Failed to construct file path for JSON: " + safeName + ".json", e);
            return false;
        }

        // --- Write JSON to File ---
        // Use try-with-resources for FileWriter to ensure it's closed properly
        try (FileWriter writer = new FileWriter(jsonFile)) {
            // Write the JSON element to the file using Gson
            gson.toJson(segmentJsonElement, writer);
            plugin.getLogger().info("[StructureMetadataSaver] JSON metadata saved successfully: " + jsonFile.getName());
            return true; // Indicate success
        } catch (JsonIOException | IOException | SecurityException e) {
            // Handle errors during file writing
            plugin.getLogger().log(Level.SEVERE, "[StructureMetadataSaver] Failed to write JSON metadata file: " + jsonFile.getName(), e);
            return false; // Indicate failure
        }
    }

    /**
     * Validates the essential fields of a Segment template object before saving.
     * Checks for null or invalid values in critical fields like name, schematic filename, and size.
     * @param segmentTemplate The Segment template to validate.
     * @return true if the template is valid for saving, false otherwise.
     */
    private boolean isSegmentTemplateValid(Segment segmentTemplate) {
        // Check if the template object itself is null
        if (segmentTemplate == null) {
            plugin.getLogger().severe("[StructureMetadataSaver] Cannot save metadata: Segment template object is null.");
            return false;
        }
        // Check the segment name
        String name = segmentTemplate.getName();
        if (name == null || name.trim().isEmpty()) {
            plugin.getLogger().severe("[StructureMetadataSaver] Cannot save metadata: Segment template name is null or empty.");
            return false;
        }
        // Check the schematic filename
        if (segmentTemplate.getSchematicFileName() == null || segmentTemplate.getSchematicFileName().trim().isEmpty()) {
            plugin.getLogger().severe("[StructureMetadataSaver] Cannot save metadata for '" + name + "': Template is missing schematic filename.");
            return false;
        }
        // Check the segment size
        BlockVector3 size = segmentTemplate.getSize();
        if (size == null || size.x() <= 0 || size.y() <= 0 || size.z() <= 0) {
            plugin.getLogger().severe("[StructureMetadataSaver] Cannot save metadata for '" + name + "': Template has invalid size: " + size);
            return false;
        }
        // Add other essential checks here if needed (e.g., entryPoints list cannot be null)
        // if (segmentTemplate.getEntryPoints() == null) { ... return false; }

        return true; // All checks passed
    }


    /**
     * Serializes the world-independent Segment template data into a JsonElement.
     * Reads directly from the template object's fields/getters using the provided Segment object.
     * Includes all fields defined in the Segment class.
     *
     * @param segmentTemplate The world-independent Segment template object.
     * @return JsonElement representing the template data, or null on failure.
     */
    private JsonElement serializeSegmentTemplate(Segment segmentTemplate) {
        try {
            // Create the main JSON object
            JsonObject json = new JsonObject();
            String segmentName = segmentTemplate.getName(); // Assumed valid due to isSegmentTemplateValid check

            // --- Serialize Core Identification & Structure ---
            json.addProperty("name", segmentName);
            // Add type as string, handle null SegmentType
            json.addProperty("type", segmentTemplate.getType() != null ? segmentTemplate.getType().toString() : null);
            json.addProperty("schematicFileName", segmentTemplate.getSchematicFileName());
            json.add("size", serializeBlockVector3(segmentTemplate.getSize())); // Use helper for BlockVector3
            json.add("entryPoints", serializeRelativeEntryPointList(segmentTemplate.getEntryPoints(), segmentName)); // Use helper for list

            // --- Serialize Feature Spawn Locations (Relative) ---
            json.add("sandSpawnLocations", serializeBlockVectorList(segmentTemplate.getSandSpawnLocations(), "sandSpawnLocations", segmentName));
            json.add("itemSpawnLocations", serializeBlockVectorList(segmentTemplate.getItemSpawnLocations(), "itemSpawnLocations", segmentName));
            json.add("coinSpawnLocations", serializeBlockVectorList(segmentTemplate.getCoinSpawnLocations(), "coinSpawnLocations", segmentName));

            // --- Serialize Gameplay Metadata ---
            json.addProperty("totalCoins", segmentTemplate.getTotalCoins());
            json.addProperty("coinMultiplier", segmentTemplate.getCoinMultiplier()); // Added
            json.addProperty("isHub", segmentTemplate.isHub()); // Added
            json.addProperty("isPuzzleRoom", segmentTemplate.isPuzzleRoom()); // Added
            json.addProperty("isLavaParkour", segmentTemplate.isLavaParkour()); // Added

            // Serialize vault/key info only if they are present (not null)
            VaultColor containedVault = segmentTemplate.getContainedVault();
            if (containedVault != null) {
                json.addProperty("containedVault", containedVault.toString()); // Added
            }
            VaultColor containedVaultKey = segmentTemplate.getContainedVaultKey();
            if (containedVaultKey != null) {
                json.addProperty("containedVaultKey", containedVaultKey.toString()); // Added
            }

            // Serialize vault/key location offsets only if they are present (not null)
            // Use the getters which implicitly check if the corresponding vault/key is defined
            BlockVector3 vaultOffset = segmentTemplate.getVaultOffset();
            if (vaultOffset != null) {
                json.add("vaultLocationOffset", serializeBlockVector3(vaultOffset)); // Added
            }
            BlockVector3 keyOffset = segmentTemplate.getKeyOffset();
            if (keyOffset != null) {
                json.add("keyLocationOffset", serializeBlockVector3(keyOffset)); // Added
            }

            // Return the completed JSON object
            return json;

        } catch (Exception e) {
            // Log any unexpected errors during serialization
            plugin.getLogger().log(Level.SEVERE, "[StructureMetadataSaver] Unexpected error during template JSON serialization for: " + segmentTemplate.getName(), e);
            return null; // Return null on failure
        }
    }

    /**
     * Helper method to serialize a WorldEdit BlockVector3 into a JSON object format: {"x": _, "y": _, "z": _}.
     * @param vec The BlockVector3 to serialize.
     * @return A JsonObject representing the vector, or an empty JsonObject if the input is null.
     */
    private JsonObject serializeBlockVector3(BlockVector3 vec) {
        JsonObject vecJson = new JsonObject();
        if (vec != null) {
            vecJson.addProperty("x", vec.x());
            vecJson.addProperty("y", vec.y());
            vecJson.addProperty("z", vec.z());
        }
        return vecJson; // Return empty object if vec is null
    }

    /**
     * Helper method to serialize a List of RelativeEntryPoint objects into a JSON array.
     * Each entry point object in the array will have "relativePosition" and "direction".
     * @param relativeEntryPoints The list of RelativeEntryPoint objects to serialize.
     * @param segmentName The name of the segment (for logging purposes).
     * @return A JsonArray containing the serialized entry points.
     */
    private JsonArray serializeRelativeEntryPointList(List<RelativeEntryPoint> relativeEntryPoints, String segmentName) {
        JsonArray jsonArray = new JsonArray();
        // Check if the list is null before iterating
        if (relativeEntryPoints != null) {
            for (RelativeEntryPoint ep : relativeEntryPoints) {
                // Check if the entry point and its components are valid
                if (ep != null && ep.getRelativePosition() != null && ep.getDirection() != null) {
                    JsonObject epJson = new JsonObject();
                    // Serialize the relative position using the helper
                    epJson.add("relativePosition", serializeBlockVector3(ep.getRelativePosition()));
                    // Add the direction as a string
                    epJson.addProperty("direction", ep.getDirection().toString()); // Assumes Direction enum has a meaningful toString()
                    jsonArray.add(epJson);
                } else {
                    // Log a warning if an invalid entry point is encountered
                    plugin.getLogger().warning("[StructureMetadataSaver] Skipping null or incomplete RelativeEntryPoint during serialization in template: " + segmentName);
                }
            }
        }
        return jsonArray;
    }

    /**
     * Helper method to serialize a List of WorldEdit BlockVector3 objects into a JSON array.
     * @param vectorList The list of BlockVector3 objects to serialize.
     * @param listName The name of the list being serialized (for logging purposes).
     * @param segmentName The name of the segment (for logging purposes).
     * @return A JsonArray containing the serialized vectors.
     */
    private JsonArray serializeBlockVectorList(List<BlockVector3> vectorList, String listName, String segmentName) {
        JsonArray jsonArray = new JsonArray();
        // Check if the list is null before iterating
        if (vectorList != null) {
            for (BlockVector3 vec : vectorList) {
                // Check if the vector itself is not null
                if (vec != null) {
                    // Serialize using the helper and add to the array
                    jsonArray.add(serializeBlockVector3(vec));
                } else {
                    // Log a warning if a null vector is found in the list
                    plugin.getLogger().warning("[StructureMetadataSaver] Skipping null BlockVector3 in list '" + listName + "' during serialization in template: " + segmentName);
                }
            }
        }
        return jsonArray;
    }

    /**
     * Sanitizes a string to be used as a filename by removing or replacing invalid characters.
     * Replaces characters not matching [a-zA-Z0-9_.-] with underscores.
     * @param name The original string name.
     * @return A sanitized string suitable for use as a filename.
     */
    private String sanitizeFileName(String name) {
        if (name == null) return "";
        String trimmedName = name.trim(); // Remove leading/trailing whitespace
        if (trimmedName.isEmpty()) return "";
        // Replace invalid characters with underscores using the pre-compiled pattern
        return INVALID_FILE_CHARS.matcher(trimmedName).replaceAll("_");
    }
}
