package com.clarkson.sot.dungeon.segment;

/**
 * Categories for segment templates.
 * Note: The gold key room is identified by {@code containedVaultKey == VaultColor.GOLD},
 * not by a separate LAVA_PARKOUR type — the builder labels it as whatever room type fits
 * (typically a challenge room variant).
 */
public enum SegmentType {
    HUB,
    CORRIDOR,
    SMALL_ROOM,
    LARGE_ROOM,
    VAULT,
    STAIRS,
    PUZZLE,
    START,
    END
}
