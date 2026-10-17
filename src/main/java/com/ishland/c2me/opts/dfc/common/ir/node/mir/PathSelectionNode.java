package com.ishland.c2me.opts.dfc.common.ir.node.mir;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.memory.MemPhiNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.branch.PhiNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.BranchType;
import com.ishland.c2me.opts.dfc.common.ir.type.FormalType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

public abstract class PathSelectionNode implements MIRNode {

    protected abstract EdgeAccessor getEdgeForSelection(IrGraph graph);

    protected BranchType getBranchType(IrGraph graph, int id) {
        return (BranchType) graph.getType(graph.getControl().getOperands(id).getInt(0));
    }

    public static boolean isBranchSelection(IrNode node) {
        return node == PhiNode.PHI || node == MemPhiNode.PHI;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }

    @Override
    public int getCost() {
        return 0;
    }

    @Override
    public IrType initialType() {
        return FormalType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var control = graph.getControl().getOperands(id).getInt(0);
        final var value = getEdgeForSelection(graph).getOperands(id);
        final var branch = (BranchType) graph.getType(control);
        final var left = graph.getType(value.getInt(0));
        final var right = graph.getType(value.getInt(1));
        return switch (branch.getBranch()) {
            case 0 -> left.meet(right);
            case 1 -> left;
            case 2 -> right;
            case 3 -> left.meet(right).top();
            default -> throw new IllegalStateException("Hit impossible branch");
        };
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        final var branch = getBranchType(graph, id);
        final int type = branch.getBranch();
        if (type == 0 || type == 3) {
            // Both sides are dead; can't do anything but wait until we're dead.
            // Both sides are alive; nothing we can do.
            return collapseSameInput(graph, id, canonicalizer);
        }
        // Collapse to the corresponding input.
        final int input = getEdgeForSelection(graph).getOperands(id).getInt(type - 1);
        IrNode.transferDownstream(graph.getControl(), id, input, canonicalizer);
        // Now we can be eliminated.
        return true;
    }

    protected boolean collapseSameInput(IrGraph graph, int id, Canonicalizer canonicalizer) {
        final var edge = getEdgeForSelection(graph);
        final var input = edge.getOperands(id);
        final int first = input.getInt(0);
        if (first != input.getInt(1)) return false;
        IrNode.transferDownstream(edge, id, first, canonicalizer);
        return true;
    }
}
