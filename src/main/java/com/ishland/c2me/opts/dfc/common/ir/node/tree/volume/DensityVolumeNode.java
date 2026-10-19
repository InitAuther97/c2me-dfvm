package com.ishland.c2me.opts.dfc.common.ir.node.tree.volume;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.MarkerTreeNode;

/// Unknown attribute = -1
public record DensityVolumeNode(
    int alignmentX, int alignmentY, int alignmentZ,
    int sizeX,      int sizeY,      int sizeZ,
    int stepX,      int stepY,      int stepZ
) implements MarkerTreeNode {
    public static final DensityVolumeNode UNKNOWN = new DensityVolumeNode(
            -1, -1, -1,
            -1, -1, -1,
            -1, -1, -1
    );
}
