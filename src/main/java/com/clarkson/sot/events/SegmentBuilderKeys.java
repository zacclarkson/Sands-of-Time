package com.clarkson.sot.events;

/**
 * Centralised PDC key name constants shared between ToolListener and SaveSegmentCommand.
 * NamespacedKey instances are created per-class using these strings with the plugin instance.
 */
public final class SegmentBuilderKeys {

    private SegmentBuilderKeys() {}

    // --- Tool identification ---
    public static final String TOOL_TYPE        = "sot_tool_type";
    public static final String TOOL_TYPE_VALUE  = "SEGMENT_BUILDER";

    // --- Marker entity tags ---
    /** Set to (byte) 1 on every build-phase marker entity. */
    public static final String BUILD_MARKER_TAG = "sot_build_marker";
    /** String: the BuilderMode name of this marker (e.g. "ENTRY_POINT", "GATE"). */
    public static final String MARKER_TYPE      = "sot_marker_type";

    // --- Marker-specific data ---
    /** String: Direction name stored on entry-point anchor entities. */
    public static final String DIRECTION        = "sot_direction";
    /** Byte flag (1): marks an ItemDisplay that the marker animation task should spin. */
    public static final String SPINNABLE        = "sot_marker_spin";
    /** String: VaultColor name stored on vault door, vault marker, key spawn entities. */
    public static final String VAULT_COLOR      = "sot_vault_color";
    /** Integer: base coin value stored on coin-spawn marker entities. */
    public static final String COIN_VALUE       = "sot_coin_value";

    // --- Bound group (vault door / gate frames) ---
    /** String UUID: groups anchor + all frame entities for the same bound. */
    public static final String BOUND_GROUP      = "sot_bound_group";
    /** String "x,y,z": absolute world min corner stored on the bound anchor entity. */
    public static final String BOUND_MIN        = "sot_bound_min";
    /** String "x,y,z": absolute world max corner stored on the bound anchor entity. */
    public static final String BOUND_MAX        = "sot_bound_max";
}
