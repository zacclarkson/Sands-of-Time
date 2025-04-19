package com.clarkson.sot.entities;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer; // Import the serializer
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Display; // Import Display for Billboard
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay; // Use ItemDisplay
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta; // Needed for display name check
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation; // For scaling/rotating display
import org.joml.AxisAngle4f; // For rotation
import org.joml.Vector3f; // For translation/scale
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import java.util.HashMap; // For inventory overflow check
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Represents a generic loot item found on the floor (e.g., torch, sword, armor).
 * Implements the FloorItem interface using an ItemDisplay for visuals.
 * Handles giving the item to the player upon pickup. Score awarding is handled
 * externally by the ScoreManager via FloorItemManager.
 */
public class FloorLoot implements FloorItem {

    // --- Dependencies & Core Properties ---
    private final Plugin plugin;
    private final UUID uniqueId;
    private final Location location;
    private final ItemStack itemStack; // The actual item this loot represents
    private ItemDisplay itemDisplay; // Visual representation

    // --- Context ---
    private final UUID teamId;
    private final UUID segmentInstanceId;
    private final int depth;

    // --- State ---
    private boolean isPickedUp;

    // --- Static PDC Keys (Shared with CoinStack where applicable) ---
    // Reuse keys if they serve the same purpose across different FloorItem types
    // These should be initialized once in your plugin's onEnable method.
    private static final String FLOOR_ITEM_UUID_KEY_STR = "sot_floor_item_uuid";
    private static final String FLOOR_ITEM_TEAM_KEY_STR = "sot_floor_item_team";
    private static final String FLOOR_ITEM_TYPE_KEY_STR = "sot_floor_item_type"; // Specific type
    private static NamespacedKey UUID_KEY;
    private static NamespacedKey TEAM_KEY;
    private static NamespacedKey TYPE_KEY;

    /**
     * Initializes the static NamespacedKey fields.
     * MUST be called once during plugin startup (e.g., in onEnable).
     * @param pluginInstance The instance of the main plugin.
     */
    public static void initializeKeys(@NotNull Plugin pluginInstance) {
        Objects.requireNonNull(pluginInstance, "Plugin instance cannot be null for key initialization");
        // Avoid re-initializing if already done
        if (UUID_KEY == null) {
            UUID_KEY = new NamespacedKey(pluginInstance, FLOOR_ITEM_UUID_KEY_STR);
            pluginInstance.getLogger().config("Initialized FloorLoot UUID_KEY");
        }
        if (TEAM_KEY == null) {
            TEAM_KEY = new NamespacedKey(pluginInstance, FLOOR_ITEM_TEAM_KEY_STR);
             pluginInstance.getLogger().config("Initialized FloorLoot TEAM_KEY");
        }
        if (TYPE_KEY == null) {
            TYPE_KEY = new NamespacedKey(pluginInstance, FLOOR_ITEM_TYPE_KEY_STR);
             pluginInstance.getLogger().config("Initialized FloorLoot TYPE_KEY");
        }
        // Initialize CoinStack keys as well if needed and not done elsewhere
        // CoinStack.initializeKeys(pluginInstance);
    }


    /**
     * Constructor for FloorLoot.
     * Requires that initializeKeys() has been called previously.
     *
     * @param plugin            The main plugin instance (used for logging).
     * @param location          Absolute world location for the loot.
     * @param itemStack         The ItemStack this loot represents (e.g., TORCH, IRON_SWORD). Must not be null or AIR.
     * @param teamId            Owning team's UUID.
     * @param segmentInstanceId Originating segment instance UUID.
     * @param depth             Dungeon depth.
     */
    public FloorLoot(@NotNull Plugin plugin, @NotNull Location location, @NotNull ItemStack itemStack,
                     @NotNull UUID teamId, @NotNull UUID segmentInstanceId, int depth) {

        // --- Input Validation ---
        Objects.requireNonNull(plugin, "Plugin cannot be null");
        Objects.requireNonNull(location, "Location cannot be null");
        Objects.requireNonNull(itemStack, "ItemStack cannot be null");
        if (itemStack.getType() == Material.AIR || itemStack.getAmount() < 1) {
            throw new IllegalArgumentException("FloorLoot ItemStack cannot be AIR or have amount < 1");
        }
        Objects.requireNonNull(teamId, "Team ID cannot be null");
        Objects.requireNonNull(segmentInstanceId, "Segment Instance ID cannot be null");

        // Ensure static keys have been initialized (runtime check)
        if (UUID_KEY == null || TEAM_KEY == null || TYPE_KEY == null) {
            throw new IllegalStateException("NamespacedKeys for FloorLoot have not been initialized! Call FloorLoot.initializeKeys() in onEnable.");
        }

        this.plugin = plugin;
        this.location = location.clone(); // Clone location for safety
        this.itemStack = itemStack.clone(); // Clone item stack for safety
        this.teamId = teamId;
        this.segmentInstanceId = segmentInstanceId;
        this.depth = depth;

        this.uniqueId = UUID.randomUUID();
        this.isPickedUp = false;

        // Spawn the visual representation using an ItemDisplay
        spawnRepresentation();
    }

    /**
     * Spawns the ItemDisplay entity representing the loot item.
     * Tags the entity with necessary Persistent Data Container (PDC) keys.
     */
    private void spawnRepresentation() {
        // Define spawn location (slightly above floor, centered in the block)
        Location spawnLocation = this.location.clone().add(0.5, 0.1, 0.5);

        try {
             // Spawn the ItemDisplay entity using the provided location and configure it
             this.itemDisplay = this.location.getWorld().spawn(spawnLocation, ItemDisplay.class, display -> {
                 // Set the actual ItemStack onto the ItemDisplay
                 display.setItemStack(this.itemStack);

                 // --- Configure ItemDisplay Properties ---
                 display.setGravity(false);      // Prevent falling
                 display.setPersistent(false);   // Don't save entity across server restarts
                 display.setInvulnerable(true);  // Prevent destruction by players/environment
                 display.setBillboard(Display.Billboard.CENTER); // Make it always face the player

                 // --- Optional: Add Transformation (Example: make item lie flat) ---
                 float scale = 0.7f; // Adjust scale as needed
                 // Rotate 90 degrees around X-axis to make it appear flat on the ground
                 AxisAngle4f rotation = new AxisAngle4f((float) Math.toRadians(90), 1f, 0f, 0f);
                 // Adjust vertical translation if needed (e.g., slightly lower)
                 Vector3f translation = new Vector3f(0f, -0.2f, 0f);
                 Transformation transformation = new Transformation(
                         translation,
                         rotation,
                         new Vector3f(scale, scale, scale), // Apply scale
                         new AxisAngle4f(0f, 0f, 0f, 1f) // Default right rotation (usually identity)
                 );
                 display.setTransformation(transformation);
                 // --- End Transformation Example ---

                 // --- Add PDC Tags to the ENTITY for identification ---
                 PersistentDataContainer pdc = display.getPersistentDataContainer();
                 pdc.set(UUID_KEY, PersistentDataType.STRING, this.uniqueId.toString()); // Store this item's unique ID
                 pdc.set(TEAM_KEY, PersistentDataType.STRING, this.teamId.toString()); // Store the owning team's ID
                 pdc.set(TYPE_KEY, PersistentDataType.STRING, "GENERIC_LOOT"); // Identify the type of floor item
             });
              plugin.getLogger().finer("Spawned FloorLoot visual " + uniqueId + " (" + itemStack.getType() + ") at " + location.toVector());
        } catch (Exception e) {
             // Log error if spawning fails
             plugin.getLogger().log(Level.SEVERE, "Failed to spawn ItemDisplay for FloorLoot " + uniqueId, e);
             // Consider how to handle this - maybe mark as picked up immediately?
             this.isPickedUp = true;
        }
    }

    // --- FloorItem Interface Implementation ---

    @Override
    @NotNull
    public UUID getUniqueId() {
        return this.uniqueId;
    }

    @Override
    @NotNull
    public Location getLocation() {
        // Return a clone to prevent external modification of the internal location
        return this.location.clone();
    }

    @Override
    @NotNull
    public ItemStack getItemStack() {
        // Return a clone of the item stack to prevent external modification
        return this.itemStack.clone();
    }

    @Override
    @Nullable
    public Entity getVisualEntity() {
        // Return the ItemDisplay if it exists and is valid (not removed/killed)
        return (this.itemDisplay != null && this.itemDisplay.isValid()) ? this.itemDisplay : null;
    }

    @Override
    public int getDepth() {
        return this.depth;
    }

    @Override
    @NotNull
    public UUID getTeamId() {
        return this.teamId;
    }

    @Override
    @NotNull
    public UUID getSegmentInstanceId() {
        return this.segmentInstanceId;
    }

    @Override
    public boolean isPickedUp() {
        return this.isPickedUp;
    }

    /**
     * Handles the pickup logic. Called by FloorItemManager when proximity is detected.
     * Sets the state to picked up, removes the visual, gives the item to the player,
     * and provides feedback. Score awarding is handled externally.
     *
     * @param player The player who picked up the item.
     */
    @Override
    public void handlePickup(@NotNull Player player) {
        Objects.requireNonNull(player, "Player cannot be null for handlePickup");

        // Prevent double pickup
        if (this.isPickedUp) {
            return;
        }
        this.isPickedUp = true; // Mark as picked up immediately

        // Remove the visual ItemDisplay entity from the world
        removeRepresentation();

        // --- Give the actual item to the player ---
        // Use addItem to handle stacking and return leftovers if inventory is full
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(this.itemStack.clone()); // Give a clone

        // If the inventory was full, drop the remaining items at the player's location
        if (!leftover.isEmpty()) {
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            player.sendMessage(Component.text("Your inventory is full! Item dropped nearby.", NamedTextColor.YELLOW));
        }

        // --- Provide Feedback ---
        // Send feedback message to player (action bar is less intrusive)
        player.sendActionBar(Component.text("Picked up " + getItemName(this.itemStack) + "!", NamedTextColor.GREEN));

        // Play sound effect at the player's location
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.2f);

        plugin.getLogger().fine("FloorLoot " + uniqueId + " ("+this.itemStack.getType()+") picked up by " + player.getName());

        // Note: Awarding points/score is handled by the FloorItemManager calling ScoreManager,
        // based on this FloorLoot object. We don't need direct ScoreManager interaction here.
    }

    /**
     * Removes the ItemDisplay entity from the world if it exists and is valid.
     */
    @Override
    public void removeRepresentation() {
        if (this.itemDisplay != null) {
            // Check if the entity is still valid before attempting removal
            if (this.itemDisplay.isValid()) {
                 this.itemDisplay.remove();
                 plugin.getLogger().finer("Removed FloorLoot visual " + uniqueId);
            } else {
                 // Log if the entity was already invalid (e.g., removed by other means, chunk unloaded)
                 plugin.getLogger().warning("Attempted to remove invalid/dead ItemDisplay for FloorLoot " + uniqueId);
            }
            // Clear the reference regardless of validity
            this.itemDisplay = null;
        }
    }

    /**
     * Helper method to get a user-friendly name for an ItemStack.
     * Uses custom display name if available, otherwise formats the material name.
     * Uses the modern Adventure API Component methods.
     * @param stack The ItemStack.
     * @return A user-friendly string representation of the item name.
     */
    private String getItemName(@Nullable ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return "an item";

        ItemMeta meta = stack.getItemMeta();
        // Use the modern Adventure Component method first
        if (meta != null && meta.hasDisplayName()) {
             Component displayNameComponent = meta.displayName(); // Get the Component
             if (displayNameComponent != null) {
                 // Serialize the Component to plain text
                 return PlainTextComponentSerializer.plainText().serialize(displayNameComponent);
             }
        }

        // Fallback: Convert material name to title case (e.g., IRON_SWORD -> Iron Sword)
        String materialName = stack.getType().name().toLowerCase().replace('_', ' ');
        String[] parts = materialName.split(" ");
        StringBuilder titleCase = new StringBuilder();
        for (String part : parts) {
            if (part.length() > 0) {
                titleCase.append(Character.toUpperCase(part.charAt(0)))
                         .append(part.substring(1))
                         .append(" ");
            }
        }
        return titleCase.toString().trim();
    }
}
