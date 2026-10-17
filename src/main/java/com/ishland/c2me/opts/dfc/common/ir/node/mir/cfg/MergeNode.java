package com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.graph.value.NodeState;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.PathSelectionNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.BranchType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.ControlLike;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

/// Joins two predecessor blocks and starts the merged block: a MergeNode
/// has exactly two control operands, both {@link BeginBlockNode}s, and like
/// every block begin, exactly one control edge to its own block end.
public enum MergeNode implements EndBlockNode, BeginBlockNode {

    MERGE;

    @Override
    public IrType initialType() {
        return BranchType.BOT;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getControl().getOperands(id);
        final int first = operands.getInt(0), last = operands.getInt(1);
        final NodeState node1 = graph.get(first), node2 = graph.get(last);
        final var type1 = ControlLike.getControl(node1.type());
        final var type2 = ControlLike.getControl(node2.type());
        return BranchType.forMerge(type1, type2);
    }

    /**
     * This method performs self elimination when the condition
     * for the respective branch node becomes a constant.
     *
     * @param graph         The graph the node resides in
     * @param self          Node's id
     * @param canonicalizer
     * @param ctx
     * @return
     */
    @Override
    public boolean optimize(IrGraph graph, int self, Canonicalizer canonicalizer, GraphContext ctx) {
        final var control = graph.getControl();
        final var usages = control.getUsages(self);
        // Check if we have any phi node or if we're already dead
        // This is the signal that we can be eliminated
        for (int i = 0; i < usages.size(); i++) {
            final var node = graph.getNode(usages.getInt(i));
            if (PathSelectionNode.isBranchSelection(node))
                return false;
        }

        // We don't have any Phi node, so it is safe to delete ourselves.
        // Move downstream to Merge's living parent.
        // If there's none living parent, we don't need to do anything,
        // and we'll be eliminated when downstream dies.
        // If both parents are alive, we have nothing to do,
        // because the elimination will be way too complex.
        // This is done in a separate phase.
        final var type = (BranchType) graph.getType(self);
        final int branch = type.getBranch();
        if (branch == 0 || branch == 3) return false;

        // Get the branch we'll transfer downstream to
        final int parent = control.getOperands(self).getInt(branch - 1);
        final int downstream = usages.getInt(0);
        control.removeEdge(self, downstream);
        control.addEdgePair(parent, 0, downstream, 0);
        // Now we can be eliminated
        return true;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }
}
