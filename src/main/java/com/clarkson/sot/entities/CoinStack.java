package com.clarkson.sot.entities;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay; // Assuming ItemDisplay is used
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta; // Needed for CustomModelData
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Represents a stack of coins on the floor that grants score when picked up.
 * Implements the FloorItem interface. Uses static NamespacedKeys for PDC interaction.
 * Spawns an ItemDisplay entity with CustomModelData for visual representation.
 */
public class CoinStack implements FloorItem {

    // --- Constants for Custom Models ---
    // Replace these with your actual CustomModelData integer values from your resource pack
    private static final int COIN_STACK_SMALL_MODEL_ID = 1001; // Example ID
    private static final int COIN_STACK_MEDIUM_MODEL_ID = 1002; // Example ID
    private static final int COIN_STACK_LARGE_MODEL_ID = 1003; // Example ID

    // --- Dependencies & Core Properties ---
    private final Plugin plugin;
    private final UUID uniqueId;
    private final Location location;
    private ItemDisplay itemDisplay; // Visual representation

    // --- Context ---
    private final UUID teamId;
    private final UUID segmentInstanceId;
    private final int depth;

    // --- Coin Specific ---
    private final int baseValue; // Base point value before scaling
    private final int modelIdToUse; // Store which model ID this stack uses

    // --- State ---
    private boolean isPickedUp;

    // --- Static PDC Keys ---
    private static final String FLOOR_ITEM_UUID_KEY_STR = "sot_floor_item_uuid";
    private static final String FLOOR_ITEM_TEAM_KEY_STR = "sot_floor_item_team";
    private static NamespacedKey UUID_KEY;
    private static NamespacedKey TEAM_KEY;

    /**
     * Initializes the static NamespacedKey fields.
     * MUST be called once during plugin startup (e.g., in onEnable).
     * @param pluginInstance The instance of the main plugin.
     */
    public static void initializeKeys(Plugin pluginInstance) {
        if (UUID_KEY == null) {
            UUID_KEY = new NamespacedKey(pluginInstance, FLOOR_ITEM_UUID_KEY_STR);
        }
        if (TEAM_KEY == null) {
            TEAM_KEY = new NamespacedKey(pluginInstance, FLOOR_ITEM_TEAM_KEY_STR);
        }
    }


    /**
     * Constructor for CoinStack.
     * Requires that initializeKeys() has been called previously.
     *
     * @param plugin            The main plugin instance (used for logging).
     * @param location          Absolute world location.
     * @param baseValue         Base score value before depth scaling.
     * @param teamId            Owning team's UUID.
     * @param segmentInstanceId Originating segment instance UUID.
     * @param depth             Dungeon depth.
     */
    public CoinStack(Plugin plugin, Location location, int baseValue, UUID teamId, UUID segmentInstanceId, int depth) {
        // Ensure static keys have been initialized
        if (UUID_KEY == null || TEAM_KEY == null) {
            throw new IllegalStateException("NamespacedKeys for CoinStack have not been initialized! Call CoinStack.initializeKeys() in onEnable.");
        }

        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.location = Objects.requireNonNull(location, "Location cannot be null");
        this.baseValue = baseValue;
        this.teamId = Objects.requireNonNull(teamId, "Team ID cannot be null");
        this.segmentInstanceId = Objects.requireNonNull(segmentInstanceId, "Segment Instance ID cannot be null");
        this.depth = depth;

        this.uniqueId = UUID.randomUUID();
        this.isPickedUp = false;

        // --- Determine which CustomModelData ID to use ---
        // Example logic: Use different models based on the coin value
        if (this.baseValue >= 50) { // Example threshold
            this.modelIdToUse = COIN_STACK_LARGE_MODEL_ID;
        } else if (this.baseValue >= 20) { // Example threshold
            this.modelIdToUse = COIN_STACK_MEDIUM_MODEL_ID;
        } else {
            this.modelIdToUse = COIN_STACK_SMALL_MODEL_ID;
        }

        // Spawn the visual representation using the chosen model ID
        spawnRepresentation();
    }

    /**
     * Spawns the visual representation (ItemDisplay) using an ItemStack
     * with the appropriate CustomModelData applied.
     * Also tags the entity with relevant PDC data.
     */
    private void spawnRepresentation() {
        // 1. Create the base ItemStack (must match the item your resource pack model overrides)
        ItemStack displayStack = new ItemStack(Material.GOLD_NUGGET); // Or GOLD_INGOT, etc.

        // 2. Get and modify ItemMeta
        ItemMeta meta = displayStack.getItemMeta();
        if (meta != null) {
            // 3. Set the Custom Model Data
            meta.setCustomModelData(this.modelIdToUse);

            // Optionally set other meta like name (usually not needed for display entities)
            // meta.displayName(Component.text("Coin Stack"));

            // 4. Apply the modified meta back to the ItemStack
            displayStack.setItemMeta(meta);
        } else {
            plugin.getLogger().warning("Could not get ItemMeta for CoinStack visual item: " + displayStack.getType());
            // Proceeding without custom model data if meta is null
        }

        // 5. Define spawn location (slightly above floor, centered)
        Location spawnLocation = this.location.clone().add(0.5, 0.1, 0.5);

        // 6. Spawn the ItemDisplay entity
        try {
             this.itemDisplay = this.location.getWorld().spawn(spawnLocation, ItemDisplay.class, display -> {
                 // 7. Set the ItemStack with CustomModelData onto the ItemDisplay
                 display.setItemStack(displayStack);

                 // 8. Set other ItemDisplay properties
                 display.setGravity(false);
                 display.setPersistent(false); // Don't save these entities across server restarts
                 display.setInvulnerable(true);
                 // TODO: Adjust ItemDisplay Transformation (size, rotation, alignment) if desired
                 // Example: display.setTransformation(new Transformation(...));
                 // Example: display.setBillboard(Display.Billboard.CENTER); // Make it face the player

                 // 9. Add PDC Tags to the ENTITY for identification during gameplay/pickup
                 PersistentDataContainer pdc = display.getPersistentDataContainer();
                 pdc.set(UUID_KEY, PersistentDataType.STRING, this.uniqueId.toString());
                 pdc.set(TEAM_KEY, PersistentDataType.STRING, this.teamId.toString());
                 // Add a tag to easily identify floor items in general, if needed by FloorItemManager
                 // pdc.set(new NamespacedKey(plugin, "sot_floor_item_type"), PersistentDataType.STRING, "COIN");
             });
              plugin.getLogger().finer("Spawned CoinStack visual " + uniqueId + " with Model ID " + this.modelIdToUse + " at " + location.toVector());
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Failed to spawn ItemDisplay for CoinStack " + uniqueId, e);
             // Consider how to handle this - maybe the CoinStack shouldn't be functional?
        }
    }

    // --- FloorItem Interface Implementation ---

    @Override
    public UUID getUniqueId() { return this.uniqueId; }

    @Override
    public Location getLocation() { return this.location.clone(); }

    @Override
    public ItemStack getItemStack() {
        // Return null or AIR as coins usually just grant score, not a physical item.
        return null;
    }

    @Override
    public Entity getVisualEntity() {
        // Return the ItemDisplay if it exists and is valid
        return (this.itemDisplay != null && this.itemDisplay.isValid()) ? this.itemDisplay : null;
    }

    @Override
    public int getDepth() { return this.depth; }

    @Override
    public UUID getTeamId() { return this.teamId; }

    @Override
    public UUID getSegmentInstanceId() { return this.segmentInstanceId; }

    @Override
    public boolean isPickedUp() { return this.isPickedUp; }

    @Override
    public void handlePickup(Player player) {
        if (this.isPickedUp) { return; }
        this.isPickedUp = true;
        removeRepresentation(); // Remove the visual entity
        plugin.getLogger().fine("CoinStack " + uniqueId + " state updated to picked up by " + player.getName());
        // ScoreManager handles giving score based on baseValue and depth
    }

    @Override
    public void removeRepresentation() {
        if (this.itemDisplay != null) {
            if (this.itemDisplay.isValid()) {
                 this.itemDisplay.remove();
                 plugin.getLogger().finer("Removed CoinStack visual " + uniqueId);
            } else {
                 plugin.getLogger().warning("Attempted to remove invalid/dead ItemDisplay for CoinStack " + uniqueId);
            }
            this.itemDisplay = null; // Clear reference
        }
    }

    // --- CoinStack Specific Methods ---

    /**
     * Gets the base point value of this coin stack before any scaling.
     * @return The base point value.
     */
    public int getBaseValue() {
        return this.baseValue;
    }

    /**
     * Gets the CustomModelData ID used for this specific coin stack's visual.
     * @return The model ID.
     */
    public int getModelIdUsed() {
        return this.modelIdToUse;
    }
}
