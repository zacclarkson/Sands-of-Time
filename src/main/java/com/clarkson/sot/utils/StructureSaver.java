import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
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
        CuboidRegion region = new CuboidRegion(
                BukkitAdapter.adapt(minPoint.getWorld()),
                BlockVector3.at(minPoint.getX(), minPoint.getY(), minPoint.getZ()),
                BlockVector3.at(maxPoint.getX(), maxPoint.getY(), maxPoint.getZ())
        );

        // Create a list to store block data
        List<BlockData> blocks = new ArrayList<>();
        for (BlockVector3 vector : region) {
            Block block = minPoint.getWorld().getBlockAt(vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
            if (block.getType() != Material.AIR) {
                blocks.add(new BlockData(
                        vector.getBlockX() - minPoint.getBlockX(),
                        vector.getBlockY() - minPoint.getBlockY(),
                        vector.getBlockZ() - minPoint.getBlockZ(),
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
        private final int x;
        private final int y;
        private final int z;
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
        private final String name;
        private final List<BlockData> blocks;

        public Structure(String name, List<BlockData> blocks) {
            this.name = name;
            this.blocks = blocks;
        }
    }
}