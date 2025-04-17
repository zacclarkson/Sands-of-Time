package com.clarkson.sot.utils; // Or a suitable package

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Central repository for NamespacedKeys and constant values used for
 * Persistent Data Containers on items and entities within the SoT plugin.
 */
public final class SoTKeys {

    // --- Prevent Instantiation ---
    private SoTKeys() { }

    // --- Key Strings (Private Constants) ---
    private static final String KEY_TYPE_STR = "sot_key_type";
    private static final String VAULT_COLOR_STR = "sot_vault_color";
    private static final String FLOOR_ITEM_UUID_STR = "sot_floor_item_uuid";
    private static final String FLOOR_ITEM_TEAM_STR = "sot_floor_item_team";
    private static final String TOOL_TYPE_STR = "sot_tool_type";
    private static final String TOOL_VALUE_STR = "sot_tool_value";
    private static final String MARKER_TYPE_STR = "sot_marker_type";
    private static final String DIRECTION_STR = "sot_direction";
    private static final String BUILD_MARKER_TAG_STR = "sot_build_marker";
    // Add other key strings here...

    // --- Public NamespacedKey Objects (Initialized once) ---
    public static NamespacedKey KEY_TYPE;
    public static NamespacedKey VAULT_COLOR;
    public static NamespacedKey FLOOR_ITEM_UUID;
    public static NamespacedKey FLOOR_ITEM_TEAM;
    public static NamespacedKey TOOL_TYPE;
    public static NamespacedKey TOOL_VALUE;
    public static NamespacedKey MARKER_TYPE;
    public static NamespacedKey DIRECTION;
    public static NamespacedKey BUILD_MARKER_TAG;
    // Add other public keys here...

    // --- Public Constant String Values ---
    public static final String RUSTY_KEY_VALUE = "RUSTY";
    public static final String VAULT_KEY_VALUE = "VAULT";
    // Marker Type Values
    public static final String MARKER_TYPE_SPAWN_ITEM = "SPAWN_ITEM";
    public static final String MARKER_TYPE_SPAWN_COIN = "SPAWN_COIN"; // If needed
    public static final String MARKER_TYPE_ENTRYPOINT = "ENTRYPOINT";
    public static final String MARKER_TYPE_VAULT = "VAULT_MARKER"; // Example
    public static final String MARKER_TYPE_KEY_SPAWN = "KEY_SPAWN"; // Example
    public static final String MARKER_TYPE_DISPLAY_COIN = "DISPLAY_COIN"; // For build coin displays
    // Tool Type Values
    public static final String TOOL_TYPE_COIN_PLACER = "COIN_PLACER";
    public static final String TOOL_TYPE_ITEM_SPAWN_PLACER = "ITEM_SPAWN_PLACER";
    public static final String TOOL_TYPE_ENTRY_POINT_PLACER = "ENTRY_POINT_PLACER";


    /**
     * Initializes all static NamespacedKey fields.
     * MUST be called once during plugin startup (e.g., in onEnable).
     * Throws an IllegalStateException if called more than once.
     * @param pluginInstance The instance of the main plugin.
     */
    public static void initializeKeys(@NotNull Plugin pluginInstance) {
        Objects.requireNonNull(pluginInstance, "Plugin instance cannot be null for key initialization");

        if (KEY_TYPE != null) {
            // Already initialized
            pluginInstance.getLogger().warning("SoTKeys already initialized!");
            return;
            // OR: throw new IllegalStateException("SoTKeys can only be initialized once.");
        }

        pluginInstance.getLogger().info("Initializing SoTKeys...");
        KEY_TYPE = new NamespacedKey(pluginInstance, KEY_TYPE_STR);
        VAULT_COLOR = new NamespacedKey(pluginInstance, VAULT_COLOR_STR);
        FLOOR_ITEM_UUID = new NamespacedKey(pluginInstance, FLOOR_ITEM_UUID_STR);
        FLOOR_ITEM_TEAM = new NamespacedKey(pluginInstance, FLOOR_ITEM_TEAM_STR);
        TOOL_TYPE = new NamespacedKey(pluginInstance, TOOL_TYPE_STR);
        TOOL_VALUE = new NamespacedKey(pluginInstance, TOOL_VALUE_STR);
        MARKER_TYPE = new NamespacedKey(pluginInstance, MARKER_TYPE_STR);
        DIRECTION = new NamespacedKey(pluginInstance, DIRECTION_STR);
        BUILD_MARKER_TAG = new NamespacedKey(pluginInstance, BUILD_MARKER_TAG_STR);
        // Initialize other keys here...
        pluginInstance.getLogger().info("SoTKeys initialized.");
    }
}
