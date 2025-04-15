package com.clarkson.sot.main;

import java.io.File;
import java.util.logging.Level; // For logging errors

import org.bukkit.plugin.java.JavaPlugin;

// Import Managers
import com.clarkson.sot.dungeon.DungeonGenerator;
import com.clarkson.sot.dungeon.VaultManager;
import com.clarkson.sot.scoring.BankingManager;
import com.clarkson.sot.scoring.ScoreManager;
import com.clarkson.sot.utils.PlayerStateManager;
import com.clarkson.sot.utils.SandManager;
import com.clarkson.sot.utils.StructureLoader; // Assuming you have this utility
import com.clarkson.sot.utils.TeamManager;
// Import Commands
import com.clarkson.sot.commands.*; // Import all commands (or list individually)
// Import Listeners
import com.clarkson.sot.events.ToolListener;
// Import other necessary classes like Location if needed for setup


public class SoT extends JavaPlugin {

    // --- Instance Variables for Managers ---
    private GameManager gameManager;
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

        // TODO: Uncomment these lines ONLY when you have added the actual default files
        //       to src/main/resources/default_segments/ and src/main/resources/default_segments/schematics/
        // saveResource("default_segments/hub_segment.json", false);
        // saveResource("default_segments/schematics/hub_segment.schem", false);
        // saveResource("default_segments/corridor_basic.json", false); // Add all defaults you create
        // saveResource("default_segments/schematics/corridor_basic.schem", false);


        // --- Initialize Core Managers ---
        // Order can matter based on dependencies
        playerStateManager = new PlayerStateManager();
        // TODO: Get required locations (lobby, trapped) from config.yml or commands
        // Location lobbyLocation = getConfigLocation("lobby"); // Example helper needed
        // Location trappedLocation = getConfigLocation("trapped"); // Example helper needed
        // if (lobbyLocation == null || trappedLocation == null) {
        //     getLogger().severe("Lobby or Trapped location not configured! Disabling plugin.");
        //     getServer().getPluginManager().disablePlugin(this);
        //     return;
        // }
        // gameManager = new GameManager(this, lobbyLocation, trappedLocation); // Create GameManager first
        teamManager = new TeamManager(gameManager); // Pass GameManager if needed
        scoreManager = new ScoreManager(teamManager, gameManager, this); // Pass dependencies
        bankingManager = new BankingManager(scoreManager); // Pass ScoreManager
        sandManager = new SandManager(gameManager); // Pass GameManager
        vaultManager = new VaultManager(this, gameManager); // Pass Plugin and GameManager
        structureLoader = new StructureLoader(this);
        dungeonGenerator = new DungeonGenerator(this);

        // Load segment templates FROM the data folder (after defaults potentially copied)
        if (!dungeonGenerator.loadSegmentTemplates(getDataFolder())) {
             getLogger().warning("Could not load any segment templates from " + getDataFolder().getPath() + ". Dungeon generation may fail.");
             // Consider if this should be a fatal error depending on plugin state
        }

        // Initialize static keys if needed (e.g., for CoinStack)
        // CoinStack.initializeKeys(this);


        // --- Register Commands ---
        // Builder/Admin Tools
        this.getCommand("sotplacecoindisplay").setExecutor(new PlaceCoinDisplayCommand(this));
        this.getCommand("sotgetcointool").setExecutor(new GiveCoinToolCommand()); // Corrected constructor
        this.getCommand("sotgetitemtool").setExecutor(new GiveItemToolCommand(this));
        // this.getCommand("sotgetentrytool").setExecutor(new GiveEntryPointToolCommand(this)); // Register entry tool command
        // this.getCommand("sotsavesegment").setExecutor(new SaveSegmentCommand(this)); // Register save command

        // Game Control Commands (need CommandManager or individual executors)
        // Example using a CommandManager class (needs creation)
        // CommandManager commandManager = new CommandManager(gameManager, teamManager, ...);
        // this.getCommand("sot").setExecutor(commandManager); // Example main command
        // this.getCommand("team").setExecutor(commandManager); // Example team command

        // Or register individually:
        // this.getCommand("sot setup").setExecutor(new SetupCommand(gameManager)); // Example
        // this.getCommand("sot start").setExecutor(new StartCommand(gameManager)); // Example
        // ... etc ...


        // --- Register Listeners ---
        getServer().getPluginManager().registerEvents(new ToolListener(this), this);
        getServer().getPluginManager().registerEvents(vaultManager, this); // Register VaultManager as listener
        // getServer().getPluginManager().registerEvents(new PlayerConnectionListener(gameManager, playerStateManager), this); // Example
        // getServer().getPluginManager().registerEvents(new PlayerDeathListener(gameManager), this); // Example
        // getServer().getPluginManager().registerEvents(new ItemPickupListener(scoreManager, floorItemManager), this); // Example


        getLogger().info("Sands of Time Enabled Successfully.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Sands of Time Disabling...");
        // Plugin shutdown logic
        // Example: Force end game if running?
        // if (gameManager != null && gameManager.getCurrentState() == GameState.RUNNING) {
        //     gameManager.endGame();
        // }
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

     // --- Optional: Add getters for your managers if other classes need access via plugin instance ---
     // public GameManager getGameManager() { return gameManager; }
     // public TeamManager getTeamManager() { return teamManager; }
     // ... etc ...

}
