package com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.BooleanType;
import com.ishland.c2me.opts.dfc.common.ir.type.BranchType;
import com.ishland.c2me.opts.dfc.common.ir.type.ControlLike;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.flowsched.util.Assertions;

public enum IfBranchNode implements EndBlockNode {

    BRANCH;

    @Override
    public IrType initialType() {
        return BranchType.BOT;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getOperands(id);
        final int upstream = operands.getInt(0);
        final int cond = operands.getInt(1);
        final var upstreamType = ControlLike.getControl(graph.getType(upstream));
        final var condType = (BooleanType) graph.getType(cond);
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
        final int originalOp = graph.getUsages(id).getInt(branch - 1);
        final var upstream = graph.getOperands(id).getInt(0);
        IrNode.transferDownstream(graph, originalOp, upstream, canonicalizer);
        // Now we're OK to be eliminated
        return true;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }
}
