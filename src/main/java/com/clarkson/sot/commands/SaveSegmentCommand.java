package com.clarkson.sot.commands;

import com.clarkson.sot.dungeon.segment.SegmentType;
import com.clarkson.sot.dungeon.VaultColor;
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.main.SoT; // Your main plugin class
import com.clarkson.sot.utils.StructureSaver;

// WorldEdit imports
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;
import com.sk89q.worldedit.session.SessionOwner;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;


import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList; // For empty lists
import java.util.List;
import java.util.logging.Level;

/**
 * Command to save a selected structure as a new Segment template.
 * Usage: /sotsavesegment <name> <type> <schematic_filename> [totalCoins]
 * NOTE: This is a basic example. It does NOT handle parsing entry points,
 * spawn points, or vault/key locations from arguments, which is complex.
 * An in-game marking tool is recommended for those features.
 */
public class SaveSegmentCommand implements CommandExecutor {

    private final SoT plugin;
    private final StructureSaver structureSaver;
    private final WorldEditPlugin worldEdit;

    public SaveSegmentCommand(SoT plugin) {
        this.plugin = plugin;
        this.structureSaver = new StructureSaver(plugin); // Instantiate the saver

        // Get WorldEdit plugin instance
        Plugin wep = Bukkit.getServer().getPluginManager().getPlugin("WorldEdit");
        if (wep instanceof WorldEditPlugin) {
            this.worldEdit = (WorldEditPlugin) wep;
        } else {
            this.worldEdit = null;
            plugin.getLogger().severe("WorldEdit plugin not found or is not the correct type! /sotsavesegment will not work.");
            // Disable the command or handle appropriately
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }
        if (worldEdit == null) {
             sender.sendMessage(ChatColor.RED + "WorldEdit is not available. Cannot save segment.");
             return true;
        }

        Player player = (Player) sender;

        // --- Argument Parsing (Basic Example) ---
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /" + label + " <name> <type> <schematic_filename> [totalCoins]");
            player.sendMessage(ChatColor.GRAY + "Example: /" + label + " my_room CORRIDOR my_room.schem 50");
            // TODO: List available SegmentTypes
            return true;
        }

        String segmentName = args[0];
        String typeStr = args[1].toUpperCase();
        String schematicFileName = args[2];
        int totalCoins = 0;
        if (args.length >= 4) {
            try {
                totalCoins = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid number for totalCoins: " + args[3]);
                return true;
            }
        }

        // Validate SegmentType
        SegmentType segmentType;
        try {
            segmentType = SegmentType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid segment type: " + args[1]);
            player.sendMessage(ChatColor.GRAY + "Valid types are: " + String.join(", ", getSegmentTypeNames())); // Helper needed
            return true;
        }

        // Validate schematic filename format (basic)
        if (!schematicFileName.toLowerCase().endsWith(".schem") && !schematicFileName.toLowerCase().endsWith(".schematic")) {
             // Allow both common extensions
             schematicFileName += ".schem"; // Append default if missing
             player.sendMessage(ChatColor.YELLOW + "Appending '.schem' to schematic filename.");
        }

        // --- Get WorldEdit Selection ---
        Region selection;
        Location worldOrigin; // Absolute minimum point of the selection
        BlockVector3 size;

        try {
            // Adapt player for WorldEdit session
             com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);
             SessionManager sessionManager = WorldEdit.getInstance().getSessionManager();
             LocalSession localSession = sessionManager.get(wePlayer);

            // Get the selection from the player's session
            selection = localSession.getSelection(wePlayer.getWorld()); // Pass world context
            worldOrigin = BukkitAdapter.adapt(player.getWorld(), selection.getMinimumPoint()); // Convert WE min point to Bukkit Location

            // Calculate size
            BlockVector3 min = selection.getMinimumPoint();
            BlockVector3 max = selection.getMaximumPoint();
            size = max.subtract(min).add(1, 1, 1); // Size is max - min + 1

        } catch (IncompleteRegionException e) {
            player.sendMessage(ChatColor.RED + "Your WorldEdit selection is incomplete. Please select two points.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting WorldEdit selection for " + player.getName(), e);
            player.sendMessage(ChatColor.RED + "An error occurred while getting your WorldEdit selection.");
            return true;
        }

        // --- Create Segment Template (Basic - Missing complex data) ---
        Segment segmentTemplate;
        try {
            // WARNING: Passing empty lists for entry/spawn points and null for vault/key info.
            // A real implementation needs to get this data, likely via in-game marking or more args.
            segmentTemplate = new Segment(
                    segmentName,
                    segmentType,
                    schematicFileName,
                    size,
                    new ArrayList<>(), // Empty Entry Points - NEEDS IMPLEMENTATION
                    new ArrayList<>(), // Empty Sand Spawns - NEEDS IMPLEMENTATION
                    new ArrayList<>(), // Empty Item Spawns - NEEDS IMPLEMENTATION
                    new ArrayList<>(), // Empty Coin Spawns - NEEDS IMPLEMENTATION
                    totalCoins,
                    null, // No contained vault - NEEDS IMPLEMENTATION
                    null, // No contained key - NEEDS IMPLEMENTATION
                    null, // No vault offset - NEEDS IMPLEMENTATION
                    null  // No key offset - NEEDS IMPLEMENTATION
            );
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Error creating Segment template object for " + segmentName, e);
             player.sendMessage(ChatColor.RED + "An error occurred creating the segment template data.");
             return true;
        }


        // --- Create PlacedSegment ---
        // Depth is 0 as we are saving a base template outside generation context
        PlacedSegment placedSegment = new PlacedSegment(segmentTemplate, worldOrigin, 0);

        // --- Call StructureSaver ---
        player.sendMessage(ChatColor.YELLOW + "Attempting to save segment '" + segmentName + "'...");
        boolean success = structureSaver.saveStructure(placedSegment);

        if (success) {
            player.sendMessage(ChatColor.GREEN + "Segment '" + segmentName + "' saved successfully!");
            player.sendMessage(ChatColor.GREEN + "Schematic: " + schematicFileName);
            player.sendMessage(ChatColor.GREEN + "Metadata: " + segmentName + ".json");
            player.sendMessage(ChatColor.YELLOW + "Reload templates or restart server to use the new segment.");
            // Consider adding automatic reloading if feasible
        } else {
            player.sendMessage(ChatColor.RED + "Failed to save segment '" + segmentName + "'. Check console for errors.");
        }

        return true;
    }

    // Helper to get enum names (replace with your actual enum path)
    private List<String> getSegmentTypeNames() {
        List<String> names = new ArrayList<>();
        for (SegmentType type : SegmentType.values()) {
            names.add(type.name());
        }
        return names;
    }
}
