package com.clarkson.sot.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StructureSaver {

    private final Plugin plugin;
    private final Gson gson;

    public StructureSaver(Plugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create(); // Pretty print for better readability
    }

    public void saveStructure(Location minPoint, Location maxPoint, String structureName) throws IOException {
        // Create BlockVector3 objects for the minimum and maximum points
        BlockVector3 minVector = BlockVector3.at(minPoint.getBlockX(), minPoint.getBlockY(), minPoint.getBlockZ());
        BlockVector3 maxVector = BlockVector3.at(maxPoint.getBlockX(), maxPoint.getBlockY(), maxPoint.getBlockZ());

        // Create a CuboidRegion using the updated API
        CuboidRegion region = new CuboidRegion(minVector, maxVector);

        // Create a list to store block data
        List<BlockData> blocks = new ArrayList<>();
        for (BlockVector3 vector : region) {
            Block block = minPoint.getWorld().getBlockAt(vector.x(), vector.y(), vector.z()); // Use x(), y(), z()
            if (block.getType() != Material.AIR) {
                blocks.add(new BlockData(
                        vector.x() - minPoint.getBlockX(),
                        vector.y() - minPoint.getBlockY(),
                        vector.z() - minPoint.getBlockZ(),
                        block.getType().name()
                ));
            }
        }

        // Create the structure object
        Structure structure = new Structure(structureName, blocks);

        // Write the structure to a JSON file
        File file = new File(plugin.getDataFolder(), structureName + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(structure, writer);
        }
    }
    // Inner class to represent block data
    private static class BlockData {
        @SuppressWarnings("unused") // Used by Gson for serialization
        private final int x;
        @SuppressWarnings("unused") // Used by Gson for serialization
        private final int y;
        @SuppressWarnings("unused") // Used by Gson for serialization
        private final int z;
        @SuppressWarnings("unused") // Used by Gson for serialization
        private final String type;

        public BlockData(int x, int y, int z, String type) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
        }
    }

    // Inner class to represent the structure
    private static class Structure {
        @SuppressWarnings("unused") // Used by Gson for serialization
        private final String name;
        @SuppressWarnings("unused") // Used by Gson for serialization
        private final List<BlockData> blocks;

        public Structure(String name, List<BlockData> blocks) {
            this.name = name;
            this.blocks = blocks;
        }
    }
}