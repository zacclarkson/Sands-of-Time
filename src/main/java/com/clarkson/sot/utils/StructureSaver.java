package com.clarkson.sot.utils;

// Local project imports
import com.clarkson.sot.dungeon.segment.*; // Import SegmentType if needed by Segment
import com.clarkson.sot.dungeon.VaultColor; // Import VaultColor if needed by Segment
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
import org.jetbrains.annotations.Nullable; // For nullable checks

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
import java.util.regex.Pattern; // Import Pattern


/**
 * Saves Segment data: schematic from the live world based on a PlacedSegment instance,
 * and metadata JSON based on the referenced Segment template.
 */
public class StructureSaver {

    private final Plugin plugin;
    private final Gson gson;
    private static final Pattern INVALID_FILE_CHARS = Pattern.compile("[^a-zA-Z0-9_.-]");
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
    public boolean saveStructure(PlacedSegment placedSegment) {
        // --- Initial Validation (on the PlacedSegment) ---
        if (!isPlacedSegmentValidForSaving(placedSegment)) {
            return false;
        }

        // --- JSON Metadata Saving (Uses the TEMPLATE referenced by PlacedSegment) ---
        // This saves the schematicFileName from the template into the JSON.
        boolean jsonSaved = saveMetadataJson(placedSegment.getSegmentTemplate());
        if (!jsonSaved) {
             plugin.getLogger().warning("[StructureSaver] Failed to save JSON metadata for segment '" + placedSegment.getName() + "', schematic saving will still be attempted.");
             // Decide if you want to return false here or allow schematic saving attempt
        }

        // --- Schematic Saving (Uses the absolute coordinates from PlacedSegment) ---
        // This uses the schematicFileName from the template to name the schematic file.
        boolean schematicSaved = saveSchematicInternal(placedSegment);

        // Return true only if both succeeded (adjust if schematic saving is optional on JSON fail)
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
        // Validate the placement information (needs a world for saving schematic)
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
    private boolean saveMetadataJson(Segment segmentTemplate) {
        String segmentName = segmentTemplate.getName();
        JsonElement segmentTemplateJson;

        // --- Serialize Template Data ---
        try {
            // This helper serializes the template, including the schematicFileName
            segmentTemplateJson = serializeSegmentTemplate(segmentTemplate);
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
        if (safeName.isEmpty()) {
            plugin.getLogger().severe("[StructureSaver] Cannot save JSON for template '" + segmentName + "': Name becomes empty after sanitization.");
            return false;
        }
        if (safeName.length() > MAX_FILENAME_LENGTH) {
            plugin.getLogger().warning("[StructureSaver] Sanitized filename for template '" + segmentName + "' ('" + safeName + "') may exceed filesystem limits.");
        }

        File jsonFile;
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                plugin.getLogger().severe("[StructureSaver] Failed to create plugin data folder: " + dataFolder.getAbsolutePath());
                return false;
            }
            jsonFile = new File(dataFolder, safeName + ".json");
        } catch (InvalidPathException | SecurityException e) {
            plugin.getLogger().log(Level.SEVERE, "[StructureSaver] Failed to construct file path for JSON: " + safeName + ".json", e);
            return false;
        }

        // --- Write JSON to File ---
        try (FileWriter writer = new FileWriter(jsonFile)) {
             gson.toJson(segmentTemplateJson, writer);
             plugin.getLogger().info("[StructureSaver] JSON metadata saved successfully (template format): " + jsonFile.getName());
             return true;
        } catch (JsonIOException | IOException | SecurityException e) {
             plugin.getLogger().log(Level.SEVERE, "[StructureSaver] Failed to write JSON metadata file: " + jsonFile.getName(), e);
             return false;
        }
    }

    /**
     * Internal method to save the block/entity data as a schematic file,
     * using the absolute coordinates from the PlacedSegment.
     *
     * @param placedSegment The PlacedSegment object representing the instance in the world.
     * @return true on success, false on failure.
     */
    private boolean saveSchematicInternal(PlacedSegment placedSegment) {
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
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE, "[StructureSaver] Failed to adapt Bukkit world for schematic saving: " + segmentName, e);
            return false;
        }

        CuboidRegion region;
        try {
            region = new CuboidRegion(weWorld, minAbs, maxAbs); // Use absolute vectors
        } catch(IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE, "[StructureSaver] Failed to create CuboidRegion for schematic saving: " + segmentName, e);
            return false;
        }

        // --- Volume/Size Checks ---
        long volume = region.getVolume();
        if (volume <= 0) {
            plugin.getLogger().warning("[StructureSaver] Calculated region volume is zero or negative for schematic: " + segmentName);
            return false;
        }
        if (volume > MAX_SCHEMATIC_VOLUME) {
            plugin.getLogger().severe("[StructureSaver] Schematic volume exceeds limit (" + MAX_SCHEMATIC_VOLUME + ") for: " + segmentName + ". Volume: " + volume);
            return false;
        }


        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        // Set the clipboard origin relative to the region's minimum point.
        // When pasting later, the paste location will correspond to this origin.
        clipboard.setOrigin(minAbs);

        // --- Perform WorldEdit Copy Operation ---
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).build()) {
            ForwardExtentCopy copy = new ForwardExtentCopy(
                editSession, region, clipboard, region.getMinimumPoint() // Source region, clipboard destination, source origin for copy offset
            );
            copy.setCopyingEntities(true); // Copy entities within the region
            Operations.complete(copy);

            // --- Prepare File Path ---
            // Use the schematic filename stored in the template for consistency
            String schematicFileName = placedSegment.getSegmentTemplate().getSchematicFileName();
            // Validation for filename already done in isPlacedSegmentValidForSaving

            File schematicDir = new File(plugin.getDataFolder(), "schematics");
            if (!schematicDir.exists() && !schematicDir.mkdirs()) {
                 plugin.getLogger().severe("[StructureSaver] Failed to create schematics directory: " + schematicDir.getAbsolutePath());
                 return false;
            }

            // Use the filename from the template object
            File schematicFile = new File(schematicDir, schematicFileName);

            // --- Write Schematic File ---
            // Use try-with-resources for the FileOutputStream and ClipboardWriter
            try (FileOutputStream fos = new FileOutputStream(schematicFile);
                 ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(fos)) { // Use Sponge V3 format

                writer.write(clipboard);
                plugin.getLogger().info("[StructureSaver] Schematic saved successfully: " + schematicFile.getAbsolutePath());
                return true; // Schematic saved successfully

            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[StructureSaver] Failed to write schematic file: " + schematicFile.getName(), e);
                return false;
            }

        } catch (WorldEditException e) {
            plugin.getLogger().log(Level.SEVERE, "[StructureSaver] WorldEdit error during schematic copy for: " + segmentName, e);
            return false;
        } catch (OutOfMemoryError e) {
            // Catch OOM specifically as large schematics can cause this
            plugin.getLogger().log(Level.SEVERE, "[StructureSaver] OutOfMemoryError while saving schematic (likely too large): " + segmentName, e);
            return false;
        } catch (Exception e) {
            // Catch any other unexpected errors during the edit session or operations
            plugin.getLogger().log(Level.SEVERE, "[StructureSaver] Unexpected error during schematic saving process for: " + segmentName, e);
            return false;
        }
    }

    // --- JSON Serialization Logic (Serializes the TEMPLATE data) ---

    /**
     * Serializes the world-independent Segment template data into a JsonElement.
     * Reads directly from the template object's fields/getters.
     * (Reflects changes where boolean flags/multiplier were removed from Segment)
     *
     * @param segmentTemplate The world-independent Segment template object.
     * @return JsonElement representing the template data, or null on failure.
     */
    private JsonElement serializeSegmentTemplate(Segment segmentTemplate) {
        // This method uses the latest refactored Segment structure
        try {
            JsonObject json = new JsonObject();
            String segmentName = segmentTemplate.getName(); // Assumed valid

            // --- Serialize Core Identification & Structure ---
            json.addProperty("name", segmentName);
            SegmentType type = segmentTemplate.getType();
            json.addProperty("type", type != null ? type.name() : null); // Save enum name
            json.addProperty("schematicFileName", segmentTemplate.getSchematicFileName()); // From template
            json.add("size", serializeBlockVector3(segmentTemplate.getSize())); // From template
            json.add("entryPoints", serializeRelativeEntryPointList(segmentTemplate.getEntryPoints(), segmentName)); // From template

             // --- Serialize Feature Spawn Locations (Relative) ---
            json.add("sandSpawnLocations", serializeBlockVectorList(segmentTemplate.getSandSpawnLocations(), "sandSpawnLocations", segmentName));
            json.add("itemSpawnLocations", serializeBlockVectorList(segmentTemplate.getItemSpawnLocations(), "itemSpawnLocations", segmentName));
            json.add("coinSpawnLocations", serializeBlockVectorList(segmentTemplate.getCoinSpawnLocations(), "coinSpawnLocations", segmentName));

            // --- Serialize Gameplay Metadata ---
            json.addProperty("totalCoins", segmentTemplate.getTotalCoins());
            // Removed: coinMultiplier, isHub, isPuzzleRoom, isLavaParkour

            // Serialize vault/key info only if they are present (not null)
            VaultColor containedVault = segmentTemplate.getContainedVault();
            if (containedVault != null) {
                json.addProperty("containedVault", containedVault.name());
            }
            VaultColor containedVaultKey = segmentTemplate.getContainedVaultKey();
            if (containedVaultKey != null) {
                json.addProperty("containedVaultKey", containedVaultKey.name());
            }

            // Serialize vault/key location offsets only if they are present (not null)
            BlockVector3 vaultOffset = segmentTemplate.getVaultOffset();
            if (vaultOffset != null) {
                json.add("vaultLocationOffset", serializeBlockVector3(vaultOffset));
            }
            BlockVector3 keyOffset = segmentTemplate.getKeyOffset();
            if (keyOffset != null) {
                json.add("keyLocationOffset", serializeBlockVector3(keyOffset));
            }

            return json;

        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "[StructureSaver] Unexpected error during template JSON serialization for: " + segmentTemplate.getName(), e);
             return null;
        }
    }

    // --- Serialization Helpers ---
    // These helpers serialize the relative data structures (BlockVector3, RelativeEntryPoint)

    private JsonElement serializeBlockVector3(@Nullable BlockVector3 vec) {
        if (vec == null) return JsonNull.INSTANCE;
        JsonObject vecJson = new JsonObject();
        vecJson.addProperty("x", vec.x());
        vecJson.addProperty("y", vec.y());
        vecJson.addProperty("z", vec.z());
        return vecJson;
    }

    private JsonArray serializeRelativeEntryPointList(@Nullable List<RelativeEntryPoint> relativeEntryPoints, String segmentName) {
        JsonArray jsonArray = new JsonArray();
        if (relativeEntryPoints != null) {
            for (RelativeEntryPoint ep : relativeEntryPoints) {
                if (ep != null && ep.getRelativePosition() != null && ep.getDirection() != null) {
                    JsonObject epJson = new JsonObject();
                    epJson.add("relativePosition", serializeBlockVector3(ep.getRelativePosition()));
                    epJson.addProperty("direction", ep.getDirection().name());
                    jsonArray.add(epJson);
                } else {
                    plugin.getLogger().warning("[StructureSaver] Skipping null or incomplete RelativeEntryPoint during serialization in template: " + segmentName);
                }
            }
        }
        return jsonArray;
    }

    private JsonArray serializeBlockVectorList(@Nullable List<BlockVector3> vectorList, String listName, String segmentName) {
        JsonArray jsonArray = new JsonArray();
        if (vectorList != null) {
            for (BlockVector3 vec : vectorList) {
                if (vec != null) {
                    jsonArray.add(serializeBlockVector3(vec));
                } else {
                    plugin.getLogger().warning("[StructureSaver] Skipping null BlockVector3 in list '" + listName + "' during serialization in template: " + segmentName);
                }
            }
        }
        return jsonArray;
    }

    // sanitizeFileName remains the same
    private String sanitizeFileName(@Nullable String name) {
        if (name == null) return "";
        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) return "";
        return INVALID_FILE_CHARS.matcher(trimmedName).replaceAll("_");
    }
}
