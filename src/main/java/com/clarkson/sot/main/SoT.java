package com.clarkson.sot.main;

import java.io.File;
import java.util.logging.Level;

// Import Bukkit classes needed for placeholder locations
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import org.bukkit.plugin.java.JavaPlugin;

// Import Managers
import com.clarkson.sot.dungeon.DungeonGenerator;
import com.clarkson.sot.dungeon.VaultManager;
import com.clarkson.sot.scoring.BankingManager;
import com.clarkson.sot.scoring.ScoreManager;
import com.clarkson.sot.utils.PlayerStateManager;
import com.clarkson.sot.utils.SandManager;
import com.clarkson.sot.utils.StructureLoader;
import com.clarkson.sot.utils.TeamManager;
// Import Commands
import com.clarkson.sot.commands.*;
// Import Listeners
import com.clarkson.sot.events.ToolListener;
// Import Entities if needed for static init
import com.clarkson.sot.entities.CoinStack;


public class SoT extends JavaPlugin {

    // --- Instance Variables for Managers ---
    private GameManager gameManager; // Must be initialized first
    private TeamManager teamManager;
    private PlayerStateManager playerStateManager;
    private StructureLoader structureLoader;
    private DungeonGenerator dungeonGenerator;
    private VaultManager vaultManager;
    private ScoreManager scoreManager;
    private SandManager sandManager;
    private BankingManager bankingManager;
    // Add others as needed

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

        // 2. Initialize other managers that need GameManager
        playerStateManager = new PlayerStateManager();
        teamManager = new TeamManager(gameManager); // Now gameManager is not null
        scoreManager = new ScoreManager(teamManager, gameManager, this);
        bankingManager = new BankingManager(scoreManager);
        sandManager = new SandManager(gameManager);
        vaultManager = new VaultManager(this, gameManager);
        structureLoader = new StructureLoader(this);
        dungeonGenerator = new DungeonGenerator(this);

        // 3. Load segment templates (needs StructureLoader)
        if (!dungeonGenerator.loadSegmentTemplates(getDataFolder())) {
             getLogger().warning("Could not load any segment templates from " + getDataFolder().getPath() + ". Dungeon generation may fail.");
        }

        // 4. Initialize static keys if needed
        CoinStack.initializeKeys(this);


        // --- Register Commands ---
        // Pass necessary managers to commands that need them
        this.getCommand("sotplacecoindisplay").setExecutor(new PlaceCoinDisplayCommand(this));
        this.getCommand("sotgetcointool").setExecutor(new GiveCoinToolCommand());
        this.getCommand("sotgetitemtool").setExecutor(new GiveItemToolCommand(this));
        this.getCommand("sotgetentrytool").setExecutor(new GiveEntryPointToolCommand(this));
        // this.getCommand("sotsavesegment").setExecutor(new SaveSegmentCommand(this)); // Needs StructureSaver instance


        // --- Register Listeners ---
        getServer().getPluginManager().registerEvents(new ToolListener(this), this);
        getServer().getPluginManager().registerEvents(vaultManager, this); // VaultManager handles vault interactions


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

     // --- Getters for Managers (Optional) ---
     // public GameManager getGameManager() { return gameManager; }

}
