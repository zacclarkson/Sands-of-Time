package com.clarkson.sot.main;
import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

import com.clarkson.sot.commands.GiveCoinToolCommand;
import com.clarkson.sot.commands.GiveItemToolCommand;
import com.clarkson.sot.commands.PlaceCoinDisplayCommand;
import com.clarkson.sot.events.ToolListener;

public class SoT extends JavaPlugin {

     @Override
    public void onEnable() {
        getLogger().info("Sands of Time Enabling...");
        ensureSchematicsDir();

        // --- Configuration & Resource Handling ---
        saveDefaultConfig(); // Save config.yml if not present
        // Example: Copy default segments (assuming structure from previous discussion)
        saveResource("default_segments/hub_segment.json", false);
        saveResource("default_segments/schematics/hub_segment.schem", false);
        // ... add saveResource calls for all your default segments ...


        // --- Initialize Managers ---
        // Example: Initialize managers needed by commands/listeners first
        // structureLoader = new StructureLoader(this);
        // dungeonGenerator = new DungeonGenerator(this);
        // dungeonGenerator.loadSegmentTemplates(getDataFolder());


        // --- Register Commands ---
        this.getCommand("sotplacecoindisplay").setExecutor(new PlaceCoinDisplayCommand(this));
        this.getCommand("sotgetcointool").setExecutor(new GiveCoinToolCommand());
        this.getCommand("sotgetitemtool").setExecutor(new GiveItemToolCommand(this));
        // ... register other commands (like generatedungeon, sotsavesegment)


        // --- Register Listeners ---
        getServer().getPluginManager().registerEvents(new ToolListener(this), this); // <-- Register ToolListener
        // ... register other listeners (like VaultManager, FloorItemManager, etc.) ...


        getLogger().info("Sands of Time Enabled Successfully.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Sands of Time Disabling...");
        // Plugin shutdown logic
        getLogger().info("Sands of Time Disabled.");
    }

    // Method to ensure schematics sub-directory exists (call before saveResource for schematics)
    private void ensureSchematicsDir() {
         File schematicsDir = new File(getDataFolder(), "schematics");
         if (!schematicsDir.exists()) {
             getLogger().info("Creating schematics directory...");
             schematicsDir.mkdirs();
         }
     }

}

