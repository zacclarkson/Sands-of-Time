package com.clarkson.sot.entities;

// Removed incorrect GameManager/ScoreManager imports from previous version
// import com.clarkson.sot.main.GameManager;
// import com.clarkson.sot.scoring.ScoreManager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay; // Assuming ItemDisplay is used
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
// Removed BukkitRunnable import as it wasn't used

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Represents a stack of coins on the floor that grants score when picked up.
 * Implements the FloorItem interface. Uses static NamespacedKeys for PDC interaction.
 */
public class CoinStack implements FloorItem {

    // --- Dependencies & Core Properties ---
    private final Plugin plugin; // Still needed for logging, potentially tasks
    private final UUID uniqueId;
    private final Location location;
    private ItemDisplay itemDisplay; // Visual representation

    // --- Context ---
    private final UUID teamId;
    private final UUID segmentInstanceId;
    private final int depth;

    // --- Coin Specific ---
    private final int baseValue; // Base point value before scaling

    // --- State ---
    private boolean isPickedUp;

    // --- Static PDC Keys ---
    // Define the key strings here or in a central Constants class
    private static final String FLOOR_ITEM_UUID_KEY_STR = "sot_floor_item_uuid";
    private static final String FLOOR_ITEM_TEAM_KEY_STR = "sot_floor_item_team";
    // Add other key strings as needed

    // Static NamespacedKey objects, initialized once
    private static NamespacedKey UUID_KEY;
    private static NamespacedKey TEAM_KEY;
    // Add other static NamespacedKey fields as needed

    /**
     * Initializes the static NamespacedKey fields.
     * MUST be called once during plugin startup (e.g., in onEnable).
     * @param pluginInstance The instance of the main plugin.
     */
    public static void initializeKeys(Plugin pluginInstance) {
        if (UUID_KEY == null) { // Initialize only if null
            UUID_KEY = new NamespacedKey(pluginInstance, FLOOR_ITEM_UUID_KEY_STR);
        }
        if (TEAM_KEY == null) { // Initialize only if null
            TEAM_KEY = new NamespacedKey(pluginInstance, FLOOR_ITEM_TEAM_KEY_STR);
        }
        // Initialize other static keys here
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
        // Ensure static keys have been initialized (critical check)
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

        // No need to initialize instance fields for uuidKey/teamKey anymore

        // Spawn the visual representation
        spawnRepresentation();
    }

    /**
     * Spawns the visual representation (e.g., ItemDisplay showing gold).
     * Uses the static NamespacedKeys to tag the entity.
     */
    private void spawnRepresentation() {
        ItemStack displayStack = new ItemStack(Material.GOLD_NUGGET); // Or GOLD_INGOT
        Location spawnLocation = this.location.clone().add(0.5, 0.1, 0.5); // Center visually

        try {
             this.itemDisplay = this.location.getWorld().spawn(spawnLocation, ItemDisplay.class, display -> {
                 display.setItemStack(displayStack);
                 display.setGravity(false);
                 display.setPersistent(false);
                 display.setInvulnerable(true);
                 // TODO: Adjust ItemDisplay Transformation (size, rotation) if desired

                 // Add PDC Tags using the static keys
                 PersistentDataContainer pdc = display.getPersistentDataContainer();
                 pdc.set(UUID_KEY, PersistentDataType.STRING, this.uniqueId.toString());
                 pdc.set(TEAM_KEY, PersistentDataType.STRING, this.teamId.toString());
                 // Add other tags if needed (e.g., "item_type" -> "COIN")
             });
              plugin.getLogger().finer("Spawned CoinStack visual " + uniqueId + " at " + location.toVector());
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Failed to spawn ItemDisplay for CoinStack " + uniqueId, e);
             // Handle error - maybe prevent item from being functional?
        }
    }

    // --- FloorItem Interface Implementation ---

    @Override
    public UUID getUniqueId() {
        return this.uniqueId;
    }

    @Override
    public Location getLocation() {
        return this.location.clone(); // Return a clone for safety
    }

    @Override
    public ItemStack getItemStack() {
        // CoinStacks don't typically grant a physical item, just score.
        return null; // Or return new ItemStack(Material.AIR);
    }

    @Override
    public Entity getVisualEntity() {
        // Return the ItemDisplay if it exists and is valid
        return (this.itemDisplay != null && this.itemDisplay.isValid()) ? this.itemDisplay : null;
    }

    @Override
    public int getDepth() {
        return this.depth;
    }

    @Override
    public UUID getTeamId() {
        return this.teamId;
    }

    @Override
    public UUID getSegmentInstanceId() {
        return this.segmentInstanceId;
    }

    @Override
    public boolean isPickedUp() {
        return this.isPickedUp;
    }

    /**
     * Handles the internal state change when this CoinStack is picked up.
     * Sets the picked-up flag and removes the visual representation.
     * The actual score processing is handled by the listener that detected the pickup.
     *
     * @param player The player who picked up the item (passed by the listener).
     */
    @Override
    public void handlePickup(Player player) {
        // Prevent double processing
        if (this.isPickedUp) {
            return;
        }

        // 1. Mark as picked up internally
        this.isPickedUp = true;

        // 2. Remove visual representation
        removeRepresentation();

        // 3. Log the pickup event for this specific item
        plugin.getLogger().fine("CoinStack " + uniqueId + " state updated to picked up by " + player.getName());

        // Reminder: Score processing and player feedback messages are handled
        // by the listener (e.g., FloorItemManager) calling ScoreManager.
    }

    /**
     * Removes the visual representation (ItemDisplay) from the world.
     */
    @Override
    public void removeRepresentation() {
        // Add null check for itemDisplay before calling methods on it
        if (this.itemDisplay != null) {
            if (this.itemDisplay.isValid()) { // Check if entity is still valid before removing
                 this.itemDisplay.remove();
                 plugin.getLogger().finer("Removed CoinStack visual " + uniqueId);
            } else {
                 plugin.getLogger().warning("Attempted to remove invalid/dead ItemDisplay for CoinStack " + uniqueId);
            }
            this.itemDisplay = null; // Clear reference in either case
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

    // This method seems out of place for the interface/implementation logic
    // It was likely a remnant from an earlier structure. Removing it unless
    // there's a specific reason for FloorItems to spawn representations at arbitrary locations.
    // @Override
    // public void spawnRepresentation(Location location) {
    //     // This logic is handled by the internal spawnRepresentation() called by the constructor
    //     throw new UnsupportedOperationException("Use the constructor to spawn representation at the initial location.");
    // }

}