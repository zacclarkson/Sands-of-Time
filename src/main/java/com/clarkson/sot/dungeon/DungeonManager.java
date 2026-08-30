package com.clarkson.sot.dungeon;

// Local project imports
import com.clarkson.sot.dungeon.segment.EntryPoint;
import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.SegmentBound;
import com.clarkson.sot.dungeon.segment.SegmentGeometry;
import com.clarkson.sot.entities.Area;
import com.clarkson.sot.events.FloorItemManager;
import com.clarkson.sot.main.GameManager;

// Bukkit/WorldEdit imports
import com.clarkson.sot.events.SegmentBuilderKeys;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity; // Import Entity
import org.bukkit.entity.Player; // Import Player
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import com.sk89q.worldedit.math.BlockVector3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// WorldEdit imports for pasting & cleanup
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.regions.CuboidRegion; // Import CuboidRegion
import com.sk89q.worldedit.regions.Region; // Import Region
import com.sk89q.worldedit.world.block.BlockTypes; // Import BlockTypes


// Java imports
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages a specific, live instance of a dungeon for a single team.
 * Takes a DungeonBlueprint, translates it to absolute coordinates,
 * builds the instance in the world using WorldEdit, initializes features,
 * and holds the final Dungeon data object.
 */
public class DungeonManager {

    // --- Dependencies ---
    private final Plugin plugin;
    // Managers retrieved from GameManager
    private final VaultManager vaultManager;
    private final FloorItemManager floorItemManager;
    private final DoorManager doorManager;
    private final MobManager mobManager;
    /**
     * Rusty-key and sand rolls, seeded from the round seed so a seed reproduces what is on the floor
     * and not merely the shape of the rooms. Every team derives the same value, so every team's
     * dungeon populates identically -- which is also the fairer behaviour for a race.
     */
    private final Random random;

    // --- Instance State ---
    private final UUID teamId;
    private final Location dungeonOrigin; // Absolute world origin for this instance
    private final World world;
    private final DungeonBlueprint blueprintData; // The relative blueprint used
    private final List<PlacedSegment> placedSegmentsInWorld; // Actual segments placed in the world
    // --- Constants ---
    private static final double SAND_SPAWN_CHANCE = 0.4; // Example: 40% chance for sand to spawn at a location

    /**
     * Chance that an ITEM_SPAWN marker yields a rusty key instead of rolling the loot table.
     *
     * <p>Rusty keys are the only way through a segment door, and they are placed by chance rather
     * than one-per-room, so a branch can in principle come up with no key in it and stay shut for
     * the round. Raise this if that happens often -- the doorway count is logged at generation.
     */
    private static final double RUSTY_KEY_SPAWN_CHANCE = 0.20;

    /**
     * Mixed into the round seed for this instance's RNG. Population uses a salted sub-seed rather
     * than continuing the generator's own stream so that it cannot be perturbed by how many draws
     * layout generation happened to consume (a validation retry changes that count).
     */
    private static final long POPULATION_SEED_SALT = 0xA5A5F100D5EEDL;

    // The consolidated data object with ABSOLUTE locations for this instance
    private Dungeon dungeonData;
    private Location timerBaseLocation; // Absolute base of the visual sand timer; null if no TIMER marker
    private Location bankLocation; // Absolute cell holding the coin bank; null if no BANK marker

    /**
     * Constructor for a team's specific DungeonManager instance.
     * Retrieves dependent managers from GameManager.
     *
     * @param plugin           The main plugin instance.
     * @param gameManager      The main GameManager (used to get other managers).
     * @param teamId           The UUID of the team this dungeon belongs to.
     * @param dungeonOrigin    The absolute world location for the origin (0,0,0 point) of this dungeon instance.
     * @param blueprintData    The relative layout blueprint generated by DungeonGenerator.
     */
    /** Constructor */
    public DungeonManager(@NotNull Plugin plugin, @NotNull GameManager gameManager,
                          @NotNull UUID teamId, @NotNull Location dungeonOrigin, @NotNull DungeonBlueprint blueprintData) {

        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.vaultManager = Objects.requireNonNull(gameManager.getVaultManager(), "VaultManager cannot be null via GameManager");
        this.floorItemManager = Objects.requireNonNull(gameManager.getFloorItemManager(), "FloorItemManager cannot be null via GameManager");
        this.doorManager = Objects.requireNonNull(gameManager.getDoorManager(), "DoorManager cannot be null via GameManager");
        this.mobManager = Objects.requireNonNull(gameManager.getMobManager(), "MobManager cannot be null via GameManager");
        this.teamId = Objects.requireNonNull(teamId, "Team ID cannot be null");
        this.dungeonOrigin = Objects.requireNonNull(dungeonOrigin, "Dungeon origin cannot be null");
        this.world = Objects.requireNonNull(dungeonOrigin.getWorld(), "Dungeon origin must have a valid world");
        this.blueprintData = Objects.requireNonNull(blueprintData, "Dungeon blueprint cannot be null");

        this.placedSegmentsInWorld = new ArrayList<>();
        this.dungeonData = null;
        this.random = populationRandom(gameManager.getRoundSeed());
    }

    /**
     * The RNG that populates a dungeon instance, derived from the seed its layout was generated
     * from. Deliberately takes no account of the team: every team is populated from the same
     * sub-seed and walks the same blueprint-ordered spawn lists, so every team's dungeon ends up
     * identical. Falls back to an unseeded RNG only when no layout has been generated, which in
     * practice means a caller constructed a DungeonManager outside {@code GameManager.startGame}.
     */
    @NotNull
    static Random populationRandom(@Nullable Long roundSeed) {
        return roundSeed != null ? new Random(roundSeed ^ POPULATION_SEED_SALT) : new Random();
    }
    /**
     * Initializes the dungeon instance in the world.
     * 1. Calculates absolute locations for features.
     * 2. Pastes segments based on the absolute locations.
     * 3. Creates the Dungeon data object.
     * 4. Initializes Vaults, Keys, Doors, and FloorItems using their respective managers.
     *
     * @return true if initialization was generally successful, false otherwise.
     */
    public boolean initializeInstance() {
        plugin.getLogger().info("Initializing dungeon instance for team " + teamId + " at origin " + dungeonOrigin.toVector());

        // --- 1. Calculate Absolute Locations ---
        Map<VaultColor, Location> absVaultMarkers = calculateAbsoluteLocations(blueprintData.getVaultMarkerRelativeLocations());
        Map<VaultColor, Location> absKeySpawns = calculateAbsoluteLocations(blueprintData.getKeySpawnRelativeLocations());
        List<Location> absSandSpawns = calculateAbsoluteLocations(blueprintData.getSandSpawnRelativeLocations());
        List<Location> absCoinSpawns = calculateAbsoluteLocations(blueprintData.getCoinSpawnRelativeLocations());
        List<Location> absItemSpawns = calculateAbsoluteLocations(blueprintData.getItemSpawnRelativeLocations());
        List<Location> absPlayerSpawns = calculateAbsoluteLocations(blueprintData.getPlayerSpawnRelativeLocations());
        List<Location> absSandTimers = calculateAbsoluteLocations(blueprintData.getSandTimerRelativeLocations());
        List<Location> absMobSpawners = calculateAbsoluteLocations(blueprintData.getMobSpawnerRelativeLocations());
        Location absHubLocation = dungeonOrigin.clone().add(blueprintData.getHubRelativeLocation());
        Vector safeExitRelative = blueprintData.getSafeExitRelativeLocation();
        Location absSafeExitLocation = (safeExitRelative != null) ? dungeonOrigin.clone().add(safeExitRelative) : null;
        Vector timerBaseRelative = blueprintData.getTimerBaseRelativeLocation();
        this.timerBaseLocation = (timerBaseRelative != null) ? dungeonOrigin.clone().add(timerBaseRelative) : null;
        Vector bankRelative = blueprintData.getBankRelativeLocation();
        this.bankLocation = (bankRelative != null) ? dungeonOrigin.clone().add(bankRelative) : null;
        List<EntryPoint> absDoorways = calculateAbsoluteDoorways(blueprintData.getDoorways());
        List<EntryPoint> absUnusedOpenings = calculateAbsoluteDoorways(blueprintData.getUnusedOpenings());


        // --- 2. Paste Schematics (Populates placedSegmentsInWorld) ---
        if (!pasteSegmentSchematics()) {
             plugin.getLogger().severe("Failed to paste one or more schematics for team " + teamId);
             return false;
        }
        plugin.getLogger().info("Pasted all " + placedSegmentsInWorld.size() + " segment schematics for team " + teamId);

        // Strip any builder-marker Display entities baked into older schematics (saved before we
        // stopped copying entities). Without this they render as floating markers during play.
        removeBakedBuildMarkers();


        // --- 3. Create Dungeon Data Object ---
        // Death cages and their sacrifice points come straight from the blueprint, which guarantees
        // the two lists are the same length and index-aligned (DungeonGenerator derives a point for
        // any cage the templates left unpaired), so this is a plain zip.
        List<Location> absCages = calculateAbsoluteLocations(blueprintData.getDeathCageRelativeLocations());
        List<Location> absSacrificePoints = calculateAbsoluteLocations(blueprintData.getSandSacrificeRelativeLocations());
        List<DeathCage> deathCages = new ArrayList<>();
        for (int i = 0; i < absCages.size() && i < absSacrificePoints.size(); i++) {
            deathCages.add(new DeathCage(absCages.get(i), absSacrificePoints.get(i)));
        }
        plugin.getLogger().info("Prepared " + deathCages.size() + " death cage(s) for team " + teamId);

         try {
            this.dungeonData = new Dungeon(
                teamId, world, dungeonOrigin, blueprintData,
                absHubLocation, absVaultMarkers, absKeySpawns,
                absSandSpawns, absCoinSpawns, absItemSpawns,
                deathCages, absSafeExitLocation, this.bankLocation, absPlayerSpawns, absSandTimers,
                absMobSpawners,
                absDoorways, absUnusedOpenings
            );
             plugin.getLogger().info("Created Dungeon data object for team " + teamId);
         } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Exception creating Dungeon data object for team " + teamId, e);
             return false;
         }


        // --- 4. Initialize Features using Managers ---
        // These managers use the absolute locations stored in dungeonData
        try {
            vaultManager.initializeForInstance(this.dungeonData);
            placeBankBlock(); // Must run after the paste: a schematic would overwrite the chest
            placeBranchSignifiers(); // Likewise: coloured wall markings written over the pasted wall
            doorManager.initializeDoorsForInstance(this.dungeonData); // Initialize doors
            // After the doors, so a gate overlapping an opening sealUnusedOpenings just walled off
            // wins -- the interactive thing should.
            doorManager.initializeGatesForInstance(teamId,
                    resolveGateGroups(placedSegmentsInWorld, plugin.getLogger()),
                    resolveVaultDoors(placedSegmentsInWorld, plugin.getLogger()));
            placeSacrificePoints(); // Build the chests teammates click to revive
            populateFloorItems(); // Spawn floor items
            armMobSpawners(); // Arm mob spawners (mobs appear when a player gets close)
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Error during feature manager initialization for team " + teamId, e);
             return false;
        }

        plugin.getLogger().info("Successfully initialized dungeon instance for team " + teamId);
        return true;
    }

    // createDungeonDataObject() removed as logic moved into initializeInstance()

    /** Helper to convert relative map to absolute map */
    private Map<VaultColor, Location> calculateAbsoluteLocations(Map<VaultColor, Vector> relativeMap) {
        Map<VaultColor, Location> absoluteMap = new HashMap<>();
        for (Map.Entry<VaultColor, Vector> entry : relativeMap.entrySet()) {
            absoluteMap.put(entry.getKey(), dungeonOrigin.clone().add(entry.getValue()));
        }
        return absoluteMap;
    }

    /** Helper to convert blueprint-relative doorways into absolute ones for this instance. */
    private List<EntryPoint> calculateAbsoluteDoorways(List<Doorway> relativeDoorways) {
        return relativeDoorways.stream()
                               .map(d -> new EntryPoint(dungeonOrigin.clone().add(d.getRelativePosition()),
                                                        d.getDirection()))
                               .collect(Collectors.toList());
    }

    /** Helper to convert relative list to absolute list */
    private List<Location> calculateAbsoluteLocations(List<Vector> relativeList) {
        return relativeList.stream()
                           .map(vec -> dungeonOrigin.clone().add(vec))
                           .collect(Collectors.toList());
    }


    /** Pastes all segment schematics into the world using a single EditSession. */
    private boolean pasteSegmentSchematics() {
        this.placedSegmentsInWorld.clear();
        boolean overallSuccess = true; // Track if any paste fails

        // Adapt world once
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

        // Create a single EditSession for all paste operations in this instance
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).build()) {
            for (PlacedSegment blueprintSegment : blueprintData.getRelativeSegments()) {
                Segment template = blueprintSegment.getSegmentTemplate();
                Vector relativeOriginVec = blueprintSegment.getWorldOrigin().toVector();
                Location absoluteOriginLoc = dungeonOrigin.clone().add(relativeOriginVec);

                int rotationSteps = blueprintSegment.getRotationSteps();

                // Call pasting logic, passing the single EditSession
                boolean success = pasteSchematic(template, absoluteOriginLoc, rotationSteps, editSession); // Pass session
                if (!success) {
                    plugin.getLogger().severe("Failed to paste schematic '" + template.getSchematicFileName() + "' for team " + teamId + " at " + absoluteOriginLoc.toVector());
                    overallSuccess = false; // Mark failure but continue pasting others if desired
                    // return false; // Option: Stop immediately on first failure
                } else {
                     // Only add to placedSegmentsInWorld if successfully pasted (carry the rotation so
                     // world-space feature/entry lookups stay correct).
                     PlacedSegment worldSegment = new PlacedSegment(template, absoluteOriginLoc, blueprintSegment.getDepth(), rotationSteps);
                     this.placedSegmentsInWorld.add(worldSegment);
                     plugin.getLogger().finer("Pasted segment " + template.getName() + " for team " + teamId + " at " + absoluteOriginLoc.toVector());
                }
            }
            // Optional: Flush the session once after all operations are queued
            // Operations are usually completed implicitly when the try-with-resources block closes the session.
            // editSession.flushSession();
            plugin.getLogger().fine("Completed pasting operations for team " + teamId);

        } catch (Exception e) { // Catch unexpected errors
             plugin.getLogger().log(Level.SEVERE, "Unexpected error during paste session for team " + teamId, e);
             return false;
        }

        return overallSuccess; // Return true only if all pastes succeeded
    }

    /**
     * Pastes a single schematic using WorldEdit within a provided EditSession.
     * @param template The segment template containing schematic info.
     * @param pasteOrigin The absolute world location to paste the schematic at.
     * @param editSession The EditSession to use for the paste operation.
     * @return true if pasting was successful, false otherwise.
     */
    private boolean pasteSchematic(Segment template, Location pasteOrigin, int rotationSteps, EditSession editSession) {
        File schematicDir = new File(plugin.getDataFolder(), "schematics");
        File schematicFile = new File(schematicDir, template.getSchematicFileName());

        if (!schematicFile.exists()) {
            plugin.getLogger().severe("Schematic file not found: " + schematicFile.getPath());
            return false;
        }

        try {
            // findByFile(File) is deprecated in WorldEdit 7.4; it just delegates to this.
            ClipboardFormat format = ClipboardFormats.findByPath(schematicFile.toPath());
            if (format == null) {
                 plugin.getLogger().severe("Unknown schematic format: " + schematicFile.getName());
                 return false;
            }
            try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                Clipboard clipboard = reader.read();

                // Rotate about Y by the placement's step count. WorldEdit rotates the clipboard about
                // its origin, which can push blocks negative; shift the paste target by the rotated
                // footprint's min corner so the rotated segment's min corner lands exactly on
                // pasteOrigin — matching SegmentRotation.rotatePoint used for the marker offsets.
                AffineTransform transform = new AffineTransform().rotateY(90.0 * (((rotationSteps % 4) + 4) % 4));
                int[] rmin = rotatedFootprintMin(template.getSize(), transform);

                BlockVector3 to = BlockVector3.at(
                        pasteOrigin.getBlockX() - rmin[0],
                        pasteOrigin.getBlockY(),
                        pasteOrigin.getBlockZ() - rmin[1]);

                ClipboardHolder holder = new ClipboardHolder(clipboard);
                holder.setTransform(transform); // setTransform returns void, so cannot be chained
                Operation operation = holder
                        .createPaste(editSession)
                        .to(to)
                        .ignoreAirBlocks(true) // Paste non-air blocks
                        .build();
                Operations.complete(operation); // Queue and complete the operation within the session
            }
            return true;
        } catch (IOException | WorldEditException e) {
             plugin.getLogger().log(Level.SEVERE, "Failed to paste schematic " + template.getSchematicFileName() + " at " + pasteOrigin.toVector(), e);
             return false;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Unexpected error pasting schematic " + template.getSchematicFileName(), e);
            return false;
        }
    }

    /**
     * Min X/Z (as {x,z}, rounded) of a segment's footprint {@code [0,size-1]} after {@code transform},
     * used to shift the paste so the rotated segment's min corner lands on its placement origin.
     */
    private static int[] rotatedFootprintMin(BlockVector3 size, AffineTransform transform) {
        int sx = size.x() - 1, sz = size.z() - 1;
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        for (int x : new int[]{0, sx}) {
            for (int z : new int[]{0, sz}) {
                Vector3 v = transform.apply(Vector3.at(x, 0, z));
                minX = Math.min(minX, v.getX());
                minZ = Math.min(minZ, v.getZ());
            }
        }
        return new int[]{(int) Math.round(minX), (int) Math.round(minZ)};
    }


    /**
     * Spawns floor items (Coins, Generic Items) using the FloorItemManager
     * and places Sand blocks probabilistically based on the absolute locations
     * stored in the `dungeonData` object. Called by `initializeInstance`.
     */
    /**
     * Builds the chest at each death cage's sacrifice point.
     *
     * <p>Placing this block is what makes a sacrifice point exist at all: the marker records an
     * <em>air</em> cell, so without writing something here there is nothing for a teammate to
     * right-click and the revive can never fire.
     *
     * <p>Chests are forced to {@link org.bukkit.block.data.type.Chest.Type#SINGLE}. Two sacrifice
     * points placed next to each other would otherwise pair into a double chest, which moves the
     * block a click actually lands on and would break the point-to-cage lookup.
     */
    private void placeSacrificePoints() {
        if (dungeonData == null) return;

        int placed = 0;
        for (DeathCage cage : dungeonData.getDeathCages()) {
            Location loc = cage.getSacrificePointLocation();
            try {
                Block block = loc.getBlock();
                if (!(block.isPassable() || block.getType().isAir() || block.isLiquid())) {
                    plugin.getLogger().warning("Could not place sacrifice chest at " + loc.toVector()
                            + " for team " + teamId + ": block is " + block.getType()
                            + ". That cage cannot be revived from this round.");
                    continue;
                }
                block.setType(Material.CHEST, false);
                BlockData data = block.getBlockData();
                if (data instanceof org.bukkit.block.data.type.Chest chestData) {
                    chestData.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
                    block.setBlockData(chestData, false);
                }
                placed++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error placing sacrifice chest at " + loc + " for team " + teamId, e);
            }
        }
        plugin.getLogger().info("Placed " + placed + " sacrifice chest(s) for team " + teamId);
    }

    private void populateFloorItems() {
        // Ensure data is ready
        if (dungeonData == null) {
            plugin.getLogger().severe("Cannot populate floor items: Dungeon data object is null for team " + teamId);
            return;
        }
        if (floorItemManager == null) {
             plugin.getLogger().severe("Cannot populate floor items: FloorItemManager is null!");
             // Possibly throw an exception or return early depending on how critical floor items are
             return;
        }

        plugin.getLogger().fine("Populating floor items for team " + teamId + " instance " + dungeonData.getInstanceId());
        UUID instanceUUID = dungeonData.getInstanceId();

        // --- Populate Coins ---
        List<Location> coinLocs = dungeonData.getCoinSpawnLocations();
        if (coinLocs != null && !coinLocs.isEmpty()) {
            plugin.getLogger().finer("Processing " + coinLocs.size() + " potential coin spawn locations.");
            for (Location absLoc : coinLocs) {
                if (absLoc == null) continue;
                try {
                    int depth = dungeonData.getDepthAtLocation(absLoc, this.placedSegmentsInWorld);
                    int baseValue = coinBaseValueForDepth(depth);
                    floorItemManager.spawnCoinStack(absLoc, baseValue, teamId, instanceUUID, depth);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error processing coin spawn at " + absLoc + " for team " + teamId, e);
                }
            }
        } else {
             plugin.getLogger().finer("No coin spawn locations found for team " + teamId);
        }

        // --- Populate Generic Items ---
        List<Location> itemLocs = dungeonData.getItemSpawnLocations();
        int rustyKeyCount = 0;
        if (itemLocs != null && !itemLocs.isEmpty()) {
            plugin.getLogger().finer("Processing " + itemLocs.size() + " potential item spawn locations.");
            for (Location absLoc : itemLocs) {
                 if (absLoc == null) continue;
                 try {
                     int depth = dungeonData.getDepthAtLocation(absLoc, this.placedSegmentsInWorld);
                     if (spawnsRustyKey(random.nextDouble())) {
                         floorItemManager.spawnRustyKey(absLoc, teamId, instanceUUID, depth);
                         rustyKeyCount++;
                     } else {
                         floorItemManager.spawnGenericItem(absLoc, teamId, instanceUUID, depth, random);
                     }
                 } catch (Exception e) {
                     plugin.getLogger().log(Level.WARNING, "Error processing item spawn at " + absLoc + " for team " + teamId, e);
                 }
            }
            plugin.getLogger().fine("Spawned " + rustyKeyCount + " rusty keys out of " + itemLocs.size()
                    + " item spawn locations for team " + teamId + ".");
        } else {
             plugin.getLogger().finer("No generic item spawn locations found for team " + teamId);
        }

        // --- Populate Sand (Place block probabilistically) ---
        List<Location> sandLocs = dungeonData.getSandSpawnLocations();
        int sandPlacedCount = 0;
        if (sandLocs != null && !sandLocs.isEmpty()) {
             plugin.getLogger().finer("Processing " + sandLocs.size() + " potential sand spawn locations (Chance: " + (SAND_SPAWN_CHANCE * 100) + "%).");
             for (Location absLoc : sandLocs) {
                  if (absLoc == null) continue;
                  try {
                      // Check probability
                      if (random.nextDouble() < SAND_SPAWN_CHANCE) {
                          Block block = absLoc.getBlock();
                          // Place sand if the block is replaceable (air, water, maybe tall grass etc.)
                          if (block.isPassable() || block.getType().isAir() || block.isLiquid()) {
                              block.setType(Material.SAND, false); // false = don't cause physics updates yet
                              sandPlacedCount++;
                          } else {
                               plugin.getLogger().finer("Skipped sand placement at " + absLoc.toVector() + ": Block not replaceable (" + block.getType() + ")");
                          }
                      }
                  } catch (Exception e) {
                     plugin.getLogger().log(Level.WARNING, "Error processing sand spawn at " + absLoc + " for team " + teamId, e);
                  }
             }
             plugin.getLogger().fine("Placed " + sandPlacedCount + " sand blocks out of " + sandLocs.size() + " potential locations.");
        } else {
             plugin.getLogger().finer("No sand spawn locations found for team " + teamId);
        }

        plugin.getLogger().fine("Finished populating floor items for team " + teamId);
    }


    /**
     * Whether an item spawn location becomes a rusty key rather than rolling the loot table.
     *
     * <p>Split out from {@link #populateFloorItems()} so the spawn rate is testable: everything
     * else in that method needs a pasted dungeon, a live world and WorldEdit.
     *
     * @param roll A uniform random draw in [0, 1).
     */
    static boolean spawnsRustyKey(double roll) {
        return roll < RUSTY_KEY_SPAWN_CHANCE;
    }

    /**
     * Base coin value for a pickup at the given depth, before {@code ScoreManager}'s depth multiplier.
     *
     * <p>Shared with {@link MobManager}, which pays the same for a destroyed mob spawner, so the two
     * cannot drift apart.
     */
    static int coinBaseValueForDepth(int depth) {
        return 5 + (depth / 2);
    }

    /**
     * Hands every {@code MOB_SPAWNER} marker in this instance to the {@link MobManager}.
     *
     * <p>This places the spawner blocks; no mob appears until a member of the team comes near one.
     * Depth is resolved the same way {@link #populateFloorItems()} resolves it, because this class
     * is the only place holding {@link #placedSegmentsInWorld}.
     */
    private void armMobSpawners() {
        if (dungeonData == null) {
            plugin.getLogger().severe("Cannot arm mob spawners: Dungeon data object is null for team " + teamId);
            return;
        }

        List<Location> spawnerLocs = dungeonData.getMobSpawnerLocations();
        if (spawnerLocs.isEmpty()) {
            plugin.getLogger().fine("No mob spawner locations found for team " + teamId);
            return;
        }

        int armed = 0;
        for (Location absLoc : spawnerLocs) {
            if (absLoc == null) continue;
            try {
                int depth = dungeonData.getDepthAtLocation(absLoc, this.placedSegmentsInWorld);
                mobManager.armSpawner(absLoc, teamId, dungeonData.getInstanceId(), depth);
                armed++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error arming mob spawner at " + absLoc
                        + " for team " + teamId, e);
            }
        }
        plugin.getLogger().fine("Placed " + armed + " of " + spawnerLocs.size()
                + " mob spawners for team " + teamId + ".");
    }

    // --- Getters ---
    @NotNull public Location getDungeonOrigin() { return dungeonOrigin.clone(); } // Clone for safety
    @NotNull public World getWorld() { return world; }
    @NotNull public UUID getTeamId() { return teamId; }
    @Nullable public Dungeon getDungeonData() { return dungeonData; } // Can be null before init finishes

    /** Absolute base of this instance's visual sand-timer column, or null if the hub has no TIMER marker. */
    @Nullable public Location getTimerBaseLocation() { return (timerBaseLocation != null) ? timerBaseLocation.clone() : null; }
    /** Absolute cell the coin bank stands in; null when no segment template defined a BANK marker. */
    @Nullable public Location getBankLocation() { return (bankLocation != null) ? bankLocation.clone() : null; }
    @NotNull public List<PlacedSegment> getPlacedSegmentsInWorld() { return Collections.unmodifiableList(this.placedSegmentsInWorld); }

    /**
     * Pairs each placed segment's gates with the lever that opens them, in absolute coordinates.
     *
     * <p>Read straight off the placed segments rather than carried on the blueprint. Doorways ride
     * the blueprint because the DFS <em>discards</em> which connections it made and that cannot be
     * recovered afterwards; gates discard nothing -- every GATE and LEVER marker on a placed template
     * is used verbatim. Flattening them into a dungeon-wide list, the shape every other blueprint
     * feature uses, would also lose the per-segment pairing that makes a lever mean anything.
     *
     * <p>Static and package-private so it can be tested against hand-built placements: everything
     * else on this path needs WorldEdit and a pasted dungeon.
     */
    @NotNull
    static List<GateGroup> resolveGateGroups(@NotNull List<PlacedSegment> placedSegments, @NotNull Logger log) {
        List<GateGroup> groups = new ArrayList<>();
        for (PlacedSegment placed : placedSegments) {
            Segment template = placed.getSegmentTemplate();
            if (template == null) continue;

            List<SegmentBound> gates = template.getGates();
            BlockVector3 leverOffset = template.getLeverOffset();

            if (gates.isEmpty()) {
                if (leverOffset != null) {
                    log.fine("Segment " + template.getName() + " has a LEVER marker but no gates; ignoring it.");
                }
                continue;
            }
            if (leverOffset == null) {
                // SaveSegmentCommand refuses this combination, so only hand-edited JSON reaches here.
                // Leaving the gates unbuilt keeps the area open rather than sealing it behind a wall
                // nothing can ever raise.
                log.warning("Segment " + template.getName() + " declares " + gates.size()
                        + " gate(s) but no lever; leaving them open. Re-save the template with a LEVER marker.");
                continue;
            }

            List<Area> bounds = new ArrayList<>();
            for (SegmentBound gate : gates) {
                bounds.add(SegmentGeometry.toAbsoluteArea(placed, gate));
            }
            groups.add(new GateGroup(SegmentGeometry.toAbsoluteLocation(placed, leverOffset),
                    bounds, template.getName()));
        }
        return groups;
    }

    /**
     * Resolves each placed segment's vault door into an absolute wall plus the colour that opens it.
     *
     * <p>Two segments carrying the same colour is not an error: {@code openVaultDoors} opens every
     * door of a colour, so a duplicate simply opens both.
     */
    @NotNull
    static List<VaultDoorPlacement> resolveVaultDoors(@NotNull List<PlacedSegment> placedSegments, @NotNull Logger log) {
        List<VaultDoorPlacement> doors = new ArrayList<>();
        for (PlacedSegment placed : placedSegments) {
            Segment template = placed.getSegmentTemplate();
            if (template == null) continue;

            SegmentBound bound = template.getVaultDoorBound();
            if (bound == null) continue;

            VaultColor color = template.getContainedVault();
            if (color == null) {
                // /sotmode VAULT_DOOR with no colour stores none on the marker, so the saved template
                // has a wall nothing can ever open.
                log.warning("Segment " + template.getName() + " has a vault door with no vault colour;"
                        + " skipping it. Re-place the marker with /sotmode VAULT_DOOR <color>.");
                continue;
            }
            doors.add(new VaultDoorPlacement(color, SegmentGeometry.toAbsoluteArea(placed, bound),
                    template.getName()));
        }
        return doors;
    }

    /** Finds the PlacedSegment (with absolute world coords) at a given absolute world location within this instance. */
    @Nullable
    public PlacedSegment getSegmentAtLocation(@NotNull Location location) {
        if (world == null || !world.equals(location.getWorld())) {
            return null;
        }
        // Iterate through segments placed in the world for this instance
        for (PlacedSegment segment : placedSegmentsInWorld) {
            // Use the Area's contains method
            if (segment.getWorldBounds().contains(location)) {
                return segment;
            }
        }
        return null;
    }

    /**
     * Writes the coin bank into the world: an ender chest in the cell the BANK marker named.
     *
     * <p>The block is what makes the bank exist — the same lesson as {@link com.clarkson.sot.entities.Door},
     * where registering the object without writing blocks left nothing to click. Must be called after
     * {@link #pasteSegmentSchematics()}, which would otherwise paint over the chest.
     *
     * <p>Nothing is needed at teardown: {@link #cleanupInstance()} clears the whole blueprint region.
     */
    private void placeBankBlock() {
        if (bankLocation == null) {
            plugin.getLogger().warning("No BANK marker for team " + teamId
                    + "; this dungeon has no coin bank. Add a BANK marker to the HUB segment and re-save it.");
            return;
        }

        Block bankBlock = world.getBlockAt(bankLocation);
        bankBlock.setType(Material.ENDER_CHEST, false);

        // Face the chest at the hub so it does not render staring into a wall.
        BlockData data = bankBlock.getBlockData();
        if (data instanceof Directional directional) {
            BlockFace facing = facingTowardsHub();
            if (directional.getFaces().contains(facing)) {
                directional.setFacing(facing);
                bankBlock.setBlockData(directional, false);
            }
        }

        plugin.getLogger().info("Placed the coin bank for team " + teamId + " at " + bankLocation.toVector());
    }

    /**
     * Writes the coloured wall markings that tell players which vault colour lies down a branch.
     *
     * <p>The colour is already decided: {@link DungeonGenerator#resolveBranchSignifiers} paired each
     * template placeholder with the branch beside it when the blueprint was generated, and dropped
     * any placeholder whose branch reaches no vault. All that is left here is writing the block.
     *
     * <p>Must run after {@link #pasteSegmentSchematics()}, which would otherwise paint over them,
     * and needs nothing at teardown: {@link #cleanupInstance()} clears the whole blueprint region.
     */
    private void placeBranchSignifiers() {
        List<BranchSignifier> signifiers = blueprintData.getBranchSignifiers();
        if (signifiers.isEmpty()) return;

        int placed = 0, skipped = 0;
        for (BranchSignifier signifier : signifiers) {
            Location loc = dungeonOrigin.clone().add(signifier.getRelativePosition());
            try {
                Block block = world.getBlockAt(loc);
                // The marker records an air cell against a wall, so anything solid here means the
                // template moved on since it was placed. Leave the segment's own geometry alone.
                if (!(block.isPassable() || block.getType().isAir() || block.isLiquid())) {
                    plugin.getLogger().fine("Skipped branch signifier at " + loc.toVector()
                            + " for team " + teamId + ": cell holds " + block.getType());
                    skipped++;
                    continue;
                }
                block.setType(signifier.getColor().getConcreteMaterial(), false);
                placed++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error placing branch signifier at " + loc + " for team " + teamId, e);
                skipped++;
            }
        }
        plugin.getLogger().info("Placed " + placed + " branch colour marking(s) for team " + teamId
                + (skipped > 0 ? " (" + skipped + " skipped -- cell not empty)" : ""));
    }

    /** The cardinal direction from the bank cell towards the hub, defaulting to NORTH. */
    private BlockFace facingTowardsHub() {
        Location hub = (dungeonData != null) ? dungeonData.getHubLocation() : null;
        if (hub == null || bankLocation == null) return BlockFace.NORTH;

        double dx = hub.getX() - bankLocation.getX();
        double dz = hub.getZ() - bankLocation.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    /**
     * Removes the blocks and entities associated with this dungeon instance.
     * Clears the area using WorldEdit and removes non-player entities.
     * Also clears state from associated managers.
     */
    /**
     * Removes builder-marker Display entities that were baked into a segment schematic (segments
     * saved while StructureSaver still copied entities). Marker entities are tagged with
     * {@link SegmentBuilderKeys#BUILD_MARKER_TAG}; players and gameplay entities are left untouched.
     */
    private void removeBakedBuildMarkers() {
        if (blueprintData == null) return;
        Area relativeBounds = blueprintData.getRelativeBounds();
        Location absMinLoc = dungeonOrigin.clone().add(relativeBounds.getMinPoint().toVector());
        Location absMaxLoc = dungeonOrigin.clone().add(relativeBounds.getMaxPoint().toVector());
        NamespacedKey markerKey = new NamespacedKey(plugin, SegmentBuilderKeys.BUILD_MARKER_TAG);
        try {
            Collection<Entity> entitiesInBounds = world.getNearbyEntities(new org.bukkit.util.BoundingBox(
                    absMinLoc.getX(), absMinLoc.getY(), absMinLoc.getZ(),
                    absMaxLoc.getX() + 1, absMaxLoc.getY() + 1, absMaxLoc.getZ() + 1));
            int removed = 0;
            for (Entity entity : entitiesInBounds) {
                if (entity instanceof Player) continue;
                if (entity.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE)) {
                    entity.remove();
                    removed++;
                }
            }
            if (removed > 0) {
                plugin.getLogger().info("Removed " + removed + " baked-in builder markers for team " + teamId);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error removing baked-in builder markers for team " + teamId, e);
        }
    }

    public void cleanupInstance() {
         plugin.getLogger().info("Attempting cleanup for dungeon instance of team " + teamId + " at origin " + dungeonOrigin.toVector());

         // The blueprint is what gives us the bounds, so it is the only hard requirement.
         // dungeonData being null means initializeInstance() failed part-way — which is exactly when
         // there are half-pasted blocks to remove, so that case must NOT skip the clear below.
         if (blueprintData == null) {
             plugin.getLogger().warning("Cannot cleanup instance for team " + teamId + ": Blueprint data is missing.");
             // Still attempt to clear manager states
             clearManagerStates();
             return;
         }
         if (dungeonData == null) {
             plugin.getLogger().warning("Cleaning up a partially initialized dungeon for team " + teamId
                     + "; clearing the blueprint bounds anyway.");
         }

         // --- 1. Calculate Absolute Bounds ---
         Area relativeBounds = blueprintData.getRelativeBounds();
         Location absMinLoc = dungeonOrigin.clone().add(relativeBounds.getMinPoint().toVector());
         Location absMaxLoc = dungeonOrigin.clone().add(relativeBounds.getMaxPoint().toVector());
         plugin.getLogger().fine("Calculated absolute cleanup bounds: " + absMinLoc.toVector() + " to " + absMaxLoc.toVector());

         // Adapt world and create WorldEdit region
         com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
         BlockVector3 minBV3 = BukkitAdapter.asBlockVector(absMinLoc);
         BlockVector3 maxBV3 = BukkitAdapter.asBlockVector(absMaxLoc);
         Region cleanupRegion = new CuboidRegion(weWorld, minBV3, maxBV3);

         // --- 2. Clear Blocks with WorldEdit ---
         plugin.getLogger().info("Clearing blocks within dungeon bounds for team " + teamId);
         try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                 .world(weWorld)
                 // Removed fastMode(true) as it is not a valid method
                 .build()) {
             // Set the entire region to air
             editSession.setBlocks(cleanupRegion, BlockTypes.AIR.getDefaultState());
             // Operations.complete(editSession); // Often not needed with try-with-resources flushing
             plugin.getLogger().fine("WorldEdit block clearing operation completed for team " + teamId);
         } catch (WorldEditException e) {
             plugin.getLogger().log(Level.SEVERE, "WorldEditException during dungeon cleanup for team " + teamId, e);
             // Continue cleanup even if block clearing fails? Or stop? For now, continue.
         } catch (Exception e) {
              plugin.getLogger().log(Level.SEVERE, "Unexpected error during WorldEdit cleanup for team " + teamId, e);
         }

         // --- 3. Remove Entities (Optional but recommended) ---
         plugin.getLogger().info("Removing non-player entities within dungeon bounds for team " + teamId);
         try {
             // Get entities within the absolute bounding box
             // Note: getNearbyEntities is often preferred over getEntitiesByBoundingBox for performance if available/suitable
             Collection<Entity> entitiesInBounds = world.getNearbyEntities(
                 new org.bukkit.util.BoundingBox(
                     absMinLoc.getX(), absMinLoc.getY(), absMinLoc.getZ(),
                     absMaxLoc.getX() + 1, absMaxLoc.getY() + 1, absMaxLoc.getZ() + 1 // Add 1 for inclusive check
                 )
             );

             int removedCount = 0;
             for (Entity entity : entitiesInBounds) {
                 // IMPORTANT: Do NOT remove players!
                 if (!(entity instanceof Player)) {
                     // TODO: Add more filters? E.g., only remove Items, Monsters, specific marker types?
                     entity.remove();
                     removedCount++;
                 }
             }
             plugin.getLogger().fine("Removed " + removedCount + " non-player entities for team " + teamId);
         } catch (Exception e) {
              plugin.getLogger().log(Level.SEVERE, "Error removing entities during cleanup for team " + teamId, e);
         }


         // --- 4. Clear Internal State and Manager States ---
         clearManagerStates(); // Call helper to clear states

         plugin.getLogger().info("Cleanup logic finished for team " + teamId);
     }

     /** Helper method to clear internal state and notify managers */
     private void clearManagerStates() {
          placedSegmentsInWorld.clear();
          // Unconditional: these are keyed by teamId and no-op on a team they know nothing about,
          // whereas gating them on dungeonData would skip cleanup for a team whose instance failed
          // part-way through initialization — and team UUIDs are reused every round, so a leaked
          // entry makes the next round's vaults report as already open.
          if (vaultManager != null) vaultManager.clearTeamState(teamId);
          if (doorManager != null) doorManager.clearTeamState(teamId);
          if (floorItemManager != null) floorItemManager.clearTeamState(teamId);
          if (mobManager != null) mobManager.clearTeamState(teamId);
          dungeonData = null; // Clear local reference
          plugin.getLogger().fine("Cleared internal manager states for team " + teamId);
     }

}
