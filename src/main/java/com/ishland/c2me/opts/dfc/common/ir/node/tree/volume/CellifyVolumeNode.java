package com.ishland.c2me.opts.dfc.common.ir.node.tree.volume;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.MarkerTreeNode;

public record CellifyVolumeNode(
        int cellSizeXZ, int cellSizeY
) implements MarkerTreeNode {
}
