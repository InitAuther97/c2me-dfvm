package com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg;

import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;

/// A node on the control spine. Control edges between two control nodes
/// define the block structure: a {@link BeginBlockNode} has exactly one
/// control edge to the {@link EndBlockNode} terminating its block.
/// Control edges from a control node to a non-control node are scheduling
/// edges pinning that node to a block and carry no structural requirements.
public sealed interface ControlNode extends IrNode permits BeginBlockNode, EndBlockNode, ProgramNode {

    /// CFG nodes are never value-numbered: merging control nodes would
    /// rewrite the control spine.
    @Override
    default boolean isEqual(IrNode that) {
        return false;
    }

    default int getCost() {
        return 0;
    }
}
