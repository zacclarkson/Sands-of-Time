package com.clarkson.sot.utils;

import com.clarkson.sot.dungeon.Segment;
import com.clarkson.sot.entities.Area;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import java.util.List;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class StructureSaver {

    private final Plugin plugin;
    private final Gson gson;

    public StructureSaver(Plugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create(); // Pretty print for better readability
    }

    public void saveStructure(Segment segment) throws IOException { 

        File file = new File(plugin.getDataFolder(), "segments.json");

        // Ensure the file exists
        if (!file.exists()) {
            if (file.createNewFile()) {
                System.out.println("File created: " + file.getAbsolutePath());
            } else {
                System.out.println("Failed to create the file.");
            }
        }

        // Write to the file
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(gson.toJson(serializeSegment(segment)));
        }
    }

    private JsonElement serializeSegment(Segment segment) {
    JsonObject json = new JsonObject();
    json.addProperty("name", segment.getName());
    json.addProperty("type", segment.getType().toString());
    json.add("bounds", serializeBounds(segment.getBounds()));

    // Serialize entry points
    JsonArray entryPoints = new JsonArray();
    for (EntryPoint ep : segment.getEntryPoints()) {
        JsonObject epJson = new JsonObject();
        epJson.addProperty("x", ep.getLocation().getBlockX());
        epJson.addProperty("y", ep.getLocation().getBlockY());
        epJson.addProperty("z", ep.getLocation().getBlockZ());
        epJson.addProperty("direction", ep.getDirection().toString());
        entryPoints.add(epJson);
    }
    json.add("entryPoints", entryPoints);

    // Serialize spawn locations
    json.add("sandSpawnLocations", serializeLocations(segment.getSandSpawnLocations()));
    json.add("itemSpawnLocations", serializeLocations(segment.getItemSpawnLocations()));
    json.add("coinSpawnLocations", serializeLocations(segment.getCoinSpawnLocations()));

    json.addProperty("totalCoins", segment.getTotalCoins());

    return json;
}

    private JsonElement serializeBounds(Area bounds) {
        JsonObject boundsJson = new JsonObject();
        boundsJson.add("minPoint", serializeLocation(bounds.getMinPoint()));
        boundsJson.add("maxPoint", serializeLocation(bounds.getMaxPoint()));
        return boundsJson;
    }

    private JsonElement serializeLocation(Location minPoint) {
        JsonObject locJson = new JsonObject();
        locJson.addProperty("x", minPoint.getBlockX());
        locJson.addProperty("y", minPoint.getBlockY());
        locJson.addProperty("z", minPoint.getBlockZ());
        return locJson;
    }

    private JsonArray serializeLocations(List<Location> locations) {
        JsonArray jsonArray = new JsonArray();
        for (Location loc : locations) {
            JsonObject locJson = new JsonObject();
            locJson.addProperty("x", loc.getBlockX());
            locJson.addProperty("y", loc.getBlockY());
            locJson.addProperty("z", loc.getBlockZ());
            jsonArray.add(locJson);
        }
        return jsonArray;
    }
}