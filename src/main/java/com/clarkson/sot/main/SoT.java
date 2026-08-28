package com.clarkson.sot.main;

import java.io.File;
import java.util.logging.Level;

// Import Bukkit classes needed for placeholder locations
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import org.bukkit.plugin.java.JavaPlugin;

// Import Commands
import com.clarkson.sot.commands.*;
// Import Listeners / Session management
import com.clarkson.sot.events.BuilderSessionManager;
import com.clarkson.sot.events.DeathListener;
import com.clarkson.sot.events.EscapeListener;
import com.clarkson.sot.events.ToolListener;
// Import Entities if needed for static init
import com.clarkson.sot.entities.CoinStack;
import com.clarkson.sot.entities.FloorLoot;
import com.clarkson.sot.entities.Key;
// Import the central item/key registry
import com.clarkson.sot.utils.ItemManager;


public class SoT extends JavaPlugin {

    // --- Instance Variables ---
    // GameManager owns the single set of gameplay managers; retrieve them via its getters
    // rather than building a parallel set here (that previously left listeners bound to
    // instances that did not hold the live game state).
    private GameManager gameManager;
    private BuilderSessionManager builderSessionManager;

    @Override
    public void onEnable() {
        getLogger().info("Sands of Time Enabling...");

        // --- Configuration & Resource Handling ---
        saveDefaultConfig(); // Save config.yml if not present
        ensureSchematicsDir(); // Ensure plugins/SoT/schematics exists

        // TODO: Uncomment saveResource calls ONLY when default files exist in src/main/resources
        // saveResource("default_segments/hub_segment.json", false);
        // saveResource("default_segments/schematics/hub_segment.schem", false);


        // --- Load Required Config/Locations FIRST ---
        // TODO: Replace these placeholders with actual loading from config.yml or command storage
        World mainWorld = Bukkit.getWorlds().get(0); // Get default world as placeholder
        if (mainWorld == null) {
             getLogger().severe("Could not get a default world for placeholder locations! Disabling plugin.");
             getServer().getPluginManager().disablePlugin(this);
             return;
        }
        Location placeholderLobby = new Location(mainWorld, 0, 100, 0); // Example placeholder
        Location placeholderTrapped = new Location(mainWorld, 10, 100, 10); // Example placeholder
        // Add proper null checks after loading from config


        // --- Initialize Core Managers (Correct Order) ---
        // 1. Initialize GameManager (as others depend on it)
        try {
             // Use placeholder locations for now
             gameManager = new GameManager(this, placeholderLobby, placeholderTrapped);
        } catch (NullPointerException e) {
             getLogger().log(Level.SEVERE, "Failed to initialize GameManager - lobby or trapped location might be null!", e);
             getServer().getPluginManager().disablePlugin(this);
             return;
        } catch (Exception e) {
             getLogger().log(Level.SEVERE, "An unexpected error occurred initializing GameManager!", e);
             getServer().getPluginManager().disablePlugin(this);
             return;
        }

        // 2. Managers are owned and constructed by GameManager (which also loads the dungeon
        //    segment templates in its constructor). We register GameManager's instances as
        //    listeners below so events act on the objects that hold the live game state.

        // 3. Initialize static keys if needed.
        //    ItemManager owns the key/tool item tags and must be initialized before any item is
        //    created or checked, or every door and builder-tool check silently fails.
        ItemManager.initializeKeys(this);
        CoinStack.initializeKeys(this);
        Key.initializeKeys(this);
        FloorLoot.initializeKeys(this);

        // 4. Builder session manager (shared between ToolListener and SetBuilderModeCommand)
        builderSessionManager = new BuilderSessionManager();


        // --- Register Commands ---
        SetBuilderModeCommand modeCmd = new SetBuilderModeCommand(builderSessionManager);
        this.getCommand("sotbuilder").setExecutor(new GiveBuilderToolCommand(this));
        this.getCommand("sotmode").setExecutor(modeCmd);
        this.getCommand("sotmode").setTabCompleter(modeCmd);
        this.getCommand("sotsavesegment").setExecutor(new SaveSegmentCommand(this));

        GameCommand gameCmd = new GameCommand(gameManager);
        this.getCommand("sot").setExecutor(gameCmd);
        this.getCommand("sot").setTabCompleter(gameCmd);


        // --- Register Listeners ---
        // All gameplay listeners are the GameManager-owned instances so they operate on the
        // live game state. FloorItemManager and DoorManager were previously never registered.
        getServer().getPluginManager().registerEvents(new ToolListener(this, builderSessionManager), this);
        getServer().getPluginManager().registerEvents(gameManager.getVaultManager(), this);
        getServer().getPluginManager().registerEvents(gameManager.getBankingManager(), this);
        getServer().getPluginManager().registerEvents(gameManager.getSandManager(), this);
        getServer().getPluginManager().registerEvents(gameManager.getFloorItemManager(), this);
        getServer().getPluginManager().registerEvents(gameManager.getDoorManager(), this);
        getServer().getPluginManager().registerEvents(new DeathListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new EscapeListener(gameManager), this);


        getLogger().info("Sands of Time Enabled Successfully.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Sands of Time Disabling...");
        // Plugin shutdown logic
        getLogger().info("Sands of Time Disabled.");
    }

    // Method to ensure schematics sub-directory exists
    private void ensureSchematicsDir() {
         File schematicsDir = new File(getDataFolder(), "schematics");
         if (!schematicsDir.exists()) {
             getLogger().info("Creating schematics directory...");
             schematicsDir.mkdirs();
         }
     }

     // TODO: Implement helper method to load locations from config.yml safely
     // private Location getConfigLocation(String path) { ... }

     // --- Getters ---
     public GameManager getGameManager() { return gameManager; }

}
