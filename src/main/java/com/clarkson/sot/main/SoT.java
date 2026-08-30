package com.clarkson.sot.main;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

// Import Bukkit classes needed for the configured game locations
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
import com.clarkson.sot.events.BlockProtectionListener;
import com.clarkson.sot.events.BuilderSessionManager;
import com.clarkson.sot.events.CountdownFreezeListener;
import com.clarkson.sot.events.DeathListener;
import com.clarkson.sot.events.EscapeListener;
import com.clarkson.sot.events.NetherPortalListener;
import com.clarkson.sot.events.SegmentBuilderKeys;
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

        // Install any segment templates bundled in the jar (e.g. the hub) into the data folder so a
        // fresh server has a working HUB. Must run BEFORE GameManager is constructed, since its
        // constructor loads templates from the data folder. Skips files that already exist so
        // in-game edits are never clobbered.
        installBundledSegments();


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

        // 1c. Apply the dungeon seed from config.yml. Unlike the locations, having none is the
        //     normal case -- it just means every round rolls its own -- so this never blocks a game.
        Long configuredSeed = SoTConfig.readSeed(getConfig(), SoTConfig.SEED_PATH, getLogger());
        gameManager.setDungeonSeed(configuredSeed);
        if (configuredSeed != null) {
            getLogger().info("Dungeon seed fixed at " + configuredSeed
                    + " ('" + SoTConfig.SEED_PATH + "' in config.yml); every round will lay out identically.");
        } else {
            getLogger().info("No dungeon seed set; each round rolls its own. The seed used is logged"
                    + " at generation, and /sot seed <value> pins it.");
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
        SaveSegmentCommand saveCmd = new SaveSegmentCommand(this);
        this.getCommand("sotsavesegment").setExecutor(saveCmd);
        this.getCommand("sotsavesegment").setTabCompleter(saveCmd);
        this.getCommand("sotclearmarkers").setExecutor(new ClearMarkersCommand(this, builderSessionManager));
        this.getCommand("sotundo").setExecutor(new UndoMarkerCommand(this, builderSessionManager));
        this.getCommand("sotreloadsegments").setExecutor(new ReloadSegmentsCommand(this, gameManager));

        GameCommand gameCmd = new GameCommand(this, gameManager);
        this.getCommand("sot").setExecutor(gameCmd);
        this.getCommand("sot").setTabCompleter(gameCmd);


        // --- Register Listeners ---
        // All gameplay listeners are the GameManager-owned instances so they operate on the
        // live game state, and this is the only place any of them is registered -- the managers
        // deliberately do not register themselves (that ran every handler twice).
        getServer().getPluginManager().registerEvents(new ToolListener(this, builderSessionManager), this);
        getServer().getPluginManager().registerEvents(gameManager.getVaultManager(), this);
        getServer().getPluginManager().registerEvents(gameManager.getBankingManager(), this);
        getServer().getPluginManager().registerEvents(gameManager.getSandManager(), this);
        getServer().getPluginManager().registerEvents(gameManager.getFloorItemManager(), this);
        getServer().getPluginManager().registerEvents(gameManager.getDoorManager(), this);
        getServer().getPluginManager().registerEvents(new DeathListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new EscapeListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new CountdownFreezeListener(gameManager), this);
        // Stops players mining the dungeon apart mid-round -- notably their own sand timer column.
        // Registered at LOW priority inside the listener so a denied break never reaches SandManager.
        getServer().getPluginManager().registerEvents(new BlockProtectionListener(gameManager), this);
        // Nether portals are used as the safe-exit visual; suppress the vanilla teleport so nobody is
        // sent to the Nether when they walk into one.
        getServer().getPluginManager().registerEvents(new NetherPortalListener(), this);

        // Animate spinnable builder markers (e.g. coin markers). Cancelled in onDisable so a
        // plugin hot-reload does not orphan the task.
        markerSpinTask = startMarkerSpinTask();

        getLogger().info("Sands of Time Enabled Successfully.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Sands of Time Disabling...");
        // Hand players the server's own scoreboard back: a sidebar set by the plugin outlives
        // the plugin being disabled, which matters on the hot-reload deploy path.
        if (gameManager != null) {
            gameManager.getScoreboardManager().stop();
        }
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

    /**
     * Installs segment templates bundled in the jar (under {@code bundled_segments/}) into the plugin
     * data folder, so a fresh server ships with a working HUB and {@code /sot start} works without a
     * hand-built segment. The names come from {@code bundled_segments/manifest.txt}; for each name the
     * {@code <name>.json} goes to the data folder root and {@code schematics/<name>.schem} to the
     * schematics sub-dir — the layout {@code StructureLoader} reads. Existing files are left untouched
     * so in-game edits are never overwritten.
     */
    private void installBundledSegments() {
        java.io.InputStream manifest = getResource("bundled_segments/manifest.txt");
        if (manifest == null) {
            return; // No segments bundled in this build.
        }
        File dataFolder = getDataFolder();
        File schematicsDir = new File(dataFolder, "schematics");
        try (java.io.BufferedReader reader =
                     new java.io.BufferedReader(new java.io.InputStreamReader(manifest, java.nio.charset.StandardCharsets.UTF_8))) {
            String name;
            while ((name = reader.readLine()) != null) {
                name = name.trim();
                if (name.isEmpty() || name.startsWith("#")) continue;
                copyResourceIfAbsent("bundled_segments/" + name + ".json",
                        new File(dataFolder, name + ".json"));
                copyResourceIfAbsent("bundled_segments/schematics/" + name + ".schem",
                        new File(schematicsDir, name + ".schem"));
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to install bundled segments", e);
        }
    }

    /** Copies a jar resource to {@code target} only if the target does not already exist. */
    private void copyResourceIfAbsent(String resourcePath, File target) {
        if (target.exists()) return;
        try (java.io.InputStream in = getResource(resourcePath)) {
            if (in == null) {
                getLogger().warning("Bundled resource missing: " + resourcePath);
                return;
            }
            java.nio.file.Files.copy(in, target.toPath());
            getLogger().info("Installed bundled segment file: " + target.getName());
        } catch (java.io.IOException e) {
            getLogger().log(Level.WARNING, "Failed to install bundled resource " + resourcePath, e);
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
