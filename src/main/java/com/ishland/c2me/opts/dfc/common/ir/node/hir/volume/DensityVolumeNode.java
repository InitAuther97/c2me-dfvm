package com.ishland.c2me.opts.dfc.common.ir.node.hir.volume;

/// Base sampling domain, consuming exactly one GroupNode as memory operand 0.
/// Unknown attribute = -1.
public record DensityVolumeNode(
    int alignmentX, int alignmentY, int alignmentZ,
    int sizeX,      int sizeY,      int sizeZ,
    int stepX,      int stepY,      int stepZ
) implements VolumeNode {
    public static final DensityVolumeNode UNKNOWN = new DensityVolumeNode(
            -1, -1, -1,
            -1, -1, -1,
            -1, -1, -1
    );
}
