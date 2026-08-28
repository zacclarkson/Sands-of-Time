package com.clarkson.sot.main;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

// Import Bukkit classes needed for placeholder locations
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;

import org.joml.AxisAngle4f;
import org.joml.Quaternionf;

import org.bukkit.plugin.java.JavaPlugin;

// Import Commands
import com.clarkson.sot.commands.*;
// Import Listeners / Session management
import com.clarkson.sot.events.BuilderSessionManager;
import com.clarkson.sot.events.DeathListener;
import com.clarkson.sot.events.EscapeListener;
import com.clarkson.sot.events.SegmentBuilderKeys;
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

    // --- Builder marker animation (spinning coin markers) ---
    private static final float SPIN_STEP_DEG = 18f; // degrees advanced per interval
    private static final long  SPIN_INTERVAL = 2L;  // ticks between updates
    private final AtomicInteger spinTick = new AtomicInteger();
    private BukkitTask markerSpinTask;

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
        this.getCommand("sotclearmarkers").setExecutor(new ClearMarkersCommand(this, builderSessionManager));
        this.getCommand("sotundo").setExecutor(new UndoMarkerCommand(this, builderSessionManager));

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

        // Animate spinnable builder markers (e.g. coin markers). Cancelled in onDisable so a
        // plugin hot-reload does not orphan the task.
        markerSpinTask = startMarkerSpinTask();

        getLogger().info("Sands of Time Enabled Successfully.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Sands of Time Disabling...");
        if (markerSpinTask != null) {
            markerSpinTask.cancel();
            markerSpinTask = null;
        }
        getLogger().info("Sands of Time Disabled.");
    }

    /**
     * Starts a repeating task that spins every builder marker ItemDisplay tagged as spinnable.
     * A single global angle keeps all markers in sync; no per-entity state is stored. Iterates
     * loaded worlds' ItemDisplays every {@link #SPIN_INTERVAL} ticks — cheap on a builder/dev
     * server; would want tightening if used on a heavily-populated production world.
     */
    private BukkitTask startMarkerSpinTask() {
        final NamespacedKey spinKey = new NamespacedKey(this, SegmentBuilderKeys.SPINNABLE);
        return getServer().getScheduler().runTaskTimer(this, () -> {
            int t = spinTick.incrementAndGet();
            float angle = (float) Math.toRadians((t * SPIN_STEP_DEG) % 360.0);
            for (World world : Bukkit.getWorlds()) {
                for (ItemDisplay disp : world.getEntitiesByClass(ItemDisplay.class)) {
                    if (!disp.getPersistentDataContainer().has(spinKey, PersistentDataType.BYTE)) {
                        continue;
                    }
                    Transformation cur = disp.getTransformation();
                    disp.setInterpolationDelay(0);
                    disp.setInterpolationDuration((int) SPIN_INTERVAL);
                    disp.setTransformation(new Transformation(
                            cur.getTranslation(),
                            new Quaternionf(new AxisAngle4f(angle, 0f, 1f, 0f)),
                            cur.getScale(),
                            new Quaternionf()
                    ));
                }
            }
        }, SPIN_INTERVAL, SPIN_INTERVAL);
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
