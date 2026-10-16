package com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.FormalType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

import java.util.Objects;

/// The descriptor of a kernel: declares the kernel's name and the assumed
/// return value type (FormalType.TOP by default), used as this node's
/// initial type. The actual return
/// type is solved from the value fed into the ReturnNode of the program whose
/// entry is linked from this descriptor (driven by
/// {@link com.ishland.c2me.opts.dfc.common.ir.phase.hir.HIRCanonicalizationPhase}).
/// That dependency is a lookup, not an edge, so the descriptor stays a root
/// node and no loop forms through the kernel body. A {@link KernelBeginNode}
/// takes a memory edge from its descriptor to mark the function it is the
/// control flow source of, and a {@link KernelInvokeNode} takes a memory edge
/// from it to mark its callee.
public record KernelDescriptorNode(
        String name,
        IrType returnType
) implements HIRNode {

    public KernelDescriptorNode(String name) {
        this(name, FormalType.TOP);
    }

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
    }

    @Override
    public IrType initialType() {
        return returnType;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final int begin = findBegin(graph, id);
        if (begin == -1) {
            return null; // not linked to a function entry yet
        }
        // The program must be defined once its entry is linked
        final int ret = graph.getProgramEnd(begin);
        final var returned = graph.getValue().getOperands(ret);
        if (returned.isEmpty()) {
            return null; // the function returns no value
        }
        return graph.getType(returned.getInt(0));
    }

    private static int findBegin(IrGraph graph, int id) {
        final var usages = graph.getMemory().getUsages(id);
        for (int i = 0; i < usages.size(); i++) {
            final int usage = usages.getInt(i);
            if (graph.getNode(usage) instanceof KernelBeginNode) {
                return usage;
            }
        }
        return -1;
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        return false;
    }

    @Override
    public boolean isEqual(IrNode that) {
        return that instanceof KernelDescriptorNode(String n, IrType t) && Objects.equals(name, n) && Objects.equals(returnType, t);
    }

    @Override
    public int hash() {
        return hashCode();
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {
    }

    @Override
    public int getCost() {
        return 0;
    }
}