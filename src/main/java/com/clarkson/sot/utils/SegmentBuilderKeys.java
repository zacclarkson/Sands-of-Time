package com.clarkson.sot.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Centralised PDC key definitions for the segment builder tool.
 * Shared between ToolListener and SaveSegmentCommand so key names stay in sync.
 */
public class SegmentBuilderKeys {

    /** Identifies the master builder tool. Value: "SEGMENT_BUILDER". */
    public final NamespacedKey TOOL_TYPE;

    /** Identifies the type of a placed marker entity. */
    public final NamespacedKey MARKER_TYPE;

    /** Direction stored on ENTRYPOINT markers. */
    public final NamespacedKey DIRECTION;

    /** Vault colour stored on vault-related entities (currently unused on bounds, reserved). */
    public final NamespacedKey VAULT_COLOR;

    /** Tag present (as byte 1) on every build-phase marker entity. */
    public final NamespacedKey BUILD_MARKER_TAG;

    /** UUID string shared by all entities belonging to the same bound group. */
    public final NamespacedKey BOUND_ID;

    /** Absolute world coordinates of the bound minimum corner (stored on data entity). */
    public final NamespacedKey BOUND_MIN_X;
    public final NamespacedKey BOUND_MIN_Y;
    public final NamespacedKey BOUND_MIN_Z;

    /** Absolute world coordinates of the bound maximum corner (stored on data entity). */
    public final NamespacedKey BOUND_MAX_X;
    public final NamespacedKey BOUND_MAX_Y;
    public final NamespacedKey BOUND_MAX_Z;

    public SegmentBuilderKeys(Plugin plugin) {
        TOOL_TYPE       = new NamespacedKey(plugin, "sot_tool_type");
        MARKER_TYPE     = new NamespacedKey(plugin, "sot_marker_type");
        DIRECTION       = new NamespacedKey(plugin, "sot_direction");
        VAULT_COLOR     = new NamespacedKey(plugin, "sot_vault_color");
        BUILD_MARKER_TAG = new NamespacedKey(plugin, "sot_build_marker");
        BOUND_ID        = new NamespacedKey(plugin, "sot_bound_id");
        BOUND_MIN_X     = new NamespacedKey(plugin, "sot_bound_min_x");
        BOUND_MIN_Y     = new NamespacedKey(plugin, "sot_bound_min_y");
        BOUND_MIN_Z     = new NamespacedKey(plugin, "sot_bound_min_z");
        BOUND_MAX_X     = new NamespacedKey(plugin, "sot_bound_max_x");
        BOUND_MAX_Y     = new NamespacedKey(plugin, "sot_bound_max_y");
        BOUND_MAX_Z     = new NamespacedKey(plugin, "sot_bound_max_z");
    }
}
