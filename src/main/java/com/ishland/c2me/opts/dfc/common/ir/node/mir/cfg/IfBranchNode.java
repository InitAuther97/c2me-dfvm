package com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.BooleanType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.BranchType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.ControlLike;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.TypeUtil;
import com.ishland.flowsched.util.Assertions;

/// Ends a block with a conditional branch on its single data operand. Has
/// exactly two control successors, a {@link BranchBeginNode#TRUE} and a
/// {@link BranchBeginNode#FALSE} in that order, and exactly one control
/// operand: the {@link BeginBlockNode} starting its block.
public enum IfBranchNode implements EndBlockNode {

    BRANCH;

    @Override
    public IrType initialType() {
        return BranchType.BOT;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final int upstream = IrNode.control(graph, id);
        final int cond = IrNode.data(graph, id, 0);
        final var upstreamType = ControlLike.getControl(graph.getType(upstream));
        final var condType = (BooleanType) TypeUtil.getValueType(graph, cond);
        Assertions.assertTrue(condType.getData() != 0, "Boolean type is not yet determined");
        // If we're dead, we're just dead
        return upstreamType.isAlive() ? BranchType.forIf(condType) : BranchType.TOP;
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        final var type = (BranchType) graph.getType(id);
        final int branch = type.getBranch();
        if (branch == 0 || branch == 3) {
            // We are still alive, or we're dead
            // If we are alive, nothing to do
            // If we are dead, wait until downstream eliminates themselves
            return false;
        }
        // Replace downstream edges to above
        final var control = graph.getControl();
        final int originalOp = control.getUsages(id).getInt(branch - 1);
        final var upstream = control.getOperands(id).getInt(0);
        IrNode.transferDownstream(control, originalOp, upstream, canonicalizer);
        // Now we're OK to be eliminated
        return true;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }
}
