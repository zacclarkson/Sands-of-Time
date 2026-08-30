package com.clarkson.sot.dungeon.segment;

import com.clarkson.sot.entities.Area;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/**
 * Relative-to-absolute conversion of segment template geometry through a {@link PlacedSegment}.
 *
 * <p>Rotation is applied at paste time (WorldEdit {@code AffineTransform().rotateY}), so a raw template
 * offset added straight onto a placement origin lands outside a rotated footprint.
 * {@link PlacedSegment#getAbsoluteLocation} deliberately does <em>not</em> rotate -- every caller has to,
 * which is what this class exists to stop the next feature forgetting.
 */
public final class SegmentGeometry {

    private SegmentGeometry() {}

    /**
     * Converts a template-relative bound into an absolute world {@link Area}.
     *
     * <p>The corners go through {@link SegmentRotation#rotateBound} rather than being rotated
     * individually, because rotating can swap which corner is the minimum: one 90 degree step maps
     * {@code (x,z) -> (z, sizeX-1-x)}, so the larger X becomes the smaller Z. rotateBound re-derives
     * min/max after rotating both corners.
     */
    @NotNull
    public static Area toAbsoluteArea(@NotNull PlacedSegment placement, @NotNull SegmentBound bound) {
        SegmentBound rotated = SegmentRotation.rotateBound(
                bound, placement.getRotationSteps(), placement.getSegmentTemplate().getSize());
        return new Area(placement.getAbsoluteLocation(rotated.getMin()),
                        placement.getAbsoluteLocation(rotated.getMax()));
    }

    /** Converts a template-relative offset into an absolute block {@link Location}. */
    @NotNull
    public static Location toAbsoluteLocation(@NotNull PlacedSegment placement, @NotNull BlockVector3 offset) {
        return placement.getAbsoluteLocation(placement.getRotatedOffset(offset));
    }
}
