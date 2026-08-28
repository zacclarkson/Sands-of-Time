package com.clarkson.sot.main;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

// Import Bukkit classes needed for the configured game locations
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
        // Every unset location falls back to the main world's spawn so the plugin still enables and
        // /sot set stays reachable; GameManager refuses to set up or start a round until both have
        // actually been configured.
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
             getLogger().severe("No worlds are loaded, so game locations cannot be resolved! Disabling plugin.");
             getServer().getPluginManager().disablePlugin(this);
             return;
        }
        Location fallback = worlds.get(0).getSpawnLocation();


        // --- Initialize Core Managers (Correct Order) ---
        // 1. Initialize GameManager (as others depend on it)
        try {
             gameManager = new GameManager(this, fallback, fallback);
        } catch (NullPointerException e) {
             getLogger().log(Level.SEVERE, "Failed to initialize GameManager - lobby or trapped location might be null!", e);
             getServer().getPluginManager().disablePlugin(this);
             return;
        } catch (Exception e) {
             getLogger().log(Level.SEVERE, "An unexpected error occurred initializing GameManager!", e);
             getServer().getPluginManager().disablePlugin(this);
             return;
        }

        // 1b. Apply the locations from config.yml, if they are set.
        applyConfiguredLocation(SoTConfig.LOBBY_PATH, "lobby", gameManager::setLobbyLocation, fallback);
        applyConfiguredLocation(SoTConfig.TRAPPED_PATH, "trapped", gameManager::setTrappedLocation, fallback);
        if (!gameManager.areLocationsConfigured()) {
            getLogger().warning("Sands of Time is not fully configured: "
                    + gameManager.getUnconfiguredLocationNames()
                    + " unset. /sot setup and /sot start will refuse to run until they are.");
        }

        // 2. Managers are owned and constructed by GameManager (which also loads the dungeon
        //    segment templates in its constructor). We register GameManager's instances as
        //    listeners below so events act on the objects that hold the live game state.

        // 3. Initialize static keys if needed
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

        GameCommand gameCmd = new GameCommand(this, gameManager);
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

     /**
      * Reads one location from config.yml and hands it to {@code apply}. When it is unset or
      * unusable the game manager keeps the fallback it was constructed with and stays flagged
      * unconfigured, so the plugin still enables and the admin can fix it with {@code /sot set}.
      */
     private void applyConfiguredLocation(String path, String name, Consumer<Location> apply, Location fallback) {
         Location configured = SoTConfig.readLocation(getConfig(), path, Bukkit::getWorld, getLogger());
         if (configured != null) {
             apply.accept(configured);
             getLogger().info("Loaded " + name + " location: " + SoTConfig.describe(configured));
         } else {
             getLogger().warning("No " + name + " location set ('" + path + "' in config.yml). Using "
                     + SoTConfig.describe(fallback) + " until an admin runs /sot set " + name + ".");
         }
     }

     // --- Getters ---
     public GameManager getGameManager() { return gameManager; }

}
