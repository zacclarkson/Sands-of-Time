package com.clarkson.sot.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.io.File;
import java.io.FileReader;

public class StructureLoader {

    private final Gson gson = new Gson();

    public void loadStructure(File file, Location location) throws Exception {
        // Read the JSON file
        JsonObject json = gson.fromJson(new FileReader(file), JsonObject.class);

        JsonArray blocks = json.getAsJsonArray("blocks");
        World world = location.getWorld();

        for (int i = 0; i < blocks.size(); i++) {
            JsonObject blockData = blocks.get(i).getAsJsonObject();
            int x = blockData.get("x").getAsInt() + location.getBlockX();
            int y = blockData.get("y").getAsInt() + location.getBlockY();
            int z = blockData.get("z").getAsInt() + location.getBlockZ();
            Material type = Material.valueOf(blockData.get("type").getAsString());

            world.getBlockAt(x, y, z).setType(type);
        }
    }
}