package com.clarkson.sot.commands;

import com.clarkson.sot.dungeon.segment.SegmentType;
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
// Removed SessionOwner import as it's unused
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;


import org.bukkit.Bukkit;
// Removed: import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

// Adventure API Imports
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList; // For empty lists
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors; // For joining segment type names

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
            // Use Adventure Component for console message
            sender.sendMessage(Component.text("This command can only be run by a player.", NamedTextColor.RED));
            return true;
        }
        if (worldEdit == null) {
             // Use Adventure Component for player message
             sender.sendMessage(Component.text("WorldEdit is not available. Cannot save segment.", NamedTextColor.RED));
             return true;
        }

        Player player = (Player) sender;

        // --- Argument Parsing (Basic Example) ---
        if (args.length < 3) {
            // Send usage message using Adventure Components
            player.sendMessage(Component.text("Usage: /" + label + " <name> <type> <schematic_filename> [totalCoins]", NamedTextColor.RED));
            player.sendMessage(Component.text("Example: /" + label + " my_room CORRIDOR my_room.schem 50", NamedTextColor.GRAY));
            // TODO: List available SegmentTypes using Adventure
            player.sendMessage(Component.text("Available types: ", NamedTextColor.GRAY)
                .append(Component.text(String.join(", ", getSegmentTypeNames()), NamedTextColor.WHITE)));
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
                // Use Adventure Component for error message
                player.sendMessage(Component.text("Invalid number for totalCoins: " + args[3], NamedTextColor.RED));
                return true;
            }
        }

        // Validate SegmentType
        SegmentType segmentType;
        try {
            segmentType = SegmentType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            // Use Adventure Component for error message
            player.sendMessage(Component.text("Invalid segment type: " + args[1], NamedTextColor.RED));
            player.sendMessage(Component.text("Valid types are: ", NamedTextColor.GRAY)
                .append(Component.text(String.join(", ", getSegmentTypeNames()), NamedTextColor.WHITE)));
            return true;
        }

        // Validate schematic filename format (basic)
        if (!schematicFileName.toLowerCase().endsWith(".schem") && !schematicFileName.toLowerCase().endsWith(".schematic")) {
             // Allow both common extensions
             schematicFileName += ".schem"; // Append default if missing
             // Use Adventure Component for info message
             player.sendMessage(Component.text("Appending '.schem' to schematic filename.", NamedTextColor.YELLOW));
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
            // Use Adventure Component for error message
            player.sendMessage(Component.text("Your WorldEdit selection is incomplete. Please select two points.", NamedTextColor.RED));
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting WorldEdit selection for " + player.getName(), e);
            // Use Adventure Component for error message
            player.sendMessage(Component.text("An error occurred while getting your WorldEdit selection.", NamedTextColor.RED));
            return true;
        }

        // --- Create Segment Template (Basic - Missing complex data) ---
        Segment segmentTemplate;
        try {
            // TODO: Passing empty lists for entry/spawn points and null for vault/key info.
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
             // Use Adventure Component for error message
             player.sendMessage(Component.text("An error occurred creating the segment template data.", NamedTextColor.RED));
             return true;
        }


        // --- Create PlacedSegment ---
        // Depth is 0 as we are saving a base template outside generation context
        PlacedSegment placedSegment = new PlacedSegment(segmentTemplate, worldOrigin, 0);

        // --- Call StructureSaver ---
        // Use Adventure Component for status message
        player.sendMessage(Component.text("Attempting to save segment '" + segmentName + "'...", NamedTextColor.YELLOW));
        boolean success = structureSaver.saveStructure(placedSegment);

        if (success) {
            // Use Adventure Components for success message
            player.sendMessage(Component.text("Segment '" + segmentName + "' saved successfully!", NamedTextColor.GREEN));
            player.sendMessage(Component.text("Schematic: ", NamedTextColor.GREEN)
                .append(Component.text(schematicFileName, NamedTextColor.WHITE)));
            player.sendMessage(Component.text("Metadata: ", NamedTextColor.GREEN)
                .append(Component.text(segmentName + ".json", NamedTextColor.WHITE)));
            player.sendMessage(Component.text("Reload templates or restart server to use the new segment.", NamedTextColor.YELLOW));
            // Consider adding automatic reloading if feasible
        } else {
            // Use Adventure Component for failure message
            player.sendMessage(Component.text("Failed to save segment '" + segmentName + "'. Check console for errors.", NamedTextColor.RED));
        }

        return true;
    }

    // Helper to get enum names (replace with your actual enum path)
    private List<String> getSegmentTypeNames() {
        // Using streams for a slightly more modern approach
        return List.of(SegmentType.values()) // Get all enum values
                   .stream()                 // Create a stream
                   .map(Enum::name)          // Map each enum value to its name (String)
                   .collect(Collectors.toList()); // Collect the names into a List
    }
}
