package com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.stamp.ForeignKernelStamp;
import com.ishland.c2me.opts.dfc.common.ir.type.FormalType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.TypeUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// The descriptor of a kernel: declares the kernel's name, the assumed
/// return value type (FormalType.TOP by default) used as this node's initial
/// type, and the metadata of a foreign implementation, if any.
///
/// A body kernel's actual return type is solved from the value consumed by
/// its {@link KernelRootNode} (driven by
/// {@link com.ishland.c2me.opts.dfc.common.ir.phase.hir.HIRCanonicalizationPhase}).
/// That dependency is a lookup, not an edge, so the descriptor stays a root
/// node of the body and no loop forms through the kernel body. Roots and arguments take
/// memory edges from this descriptor purely as ownership markers. Invocations
/// take a memory edge from it to mark their callee.
///
/// Memory operands are [constantPool, volume]. The symbolic volume can be a
/// base domain or a chain of transformations rooted in a GroupNode. Including
/// that dependency in GVN prevents merging descriptors across distinct groups
/// or sampling contexts; it is not an executable memory-state dependency.
///
/// A foreign kernel (non-null {@code foreign}) has no implementation in the
/// graph: it owns no {@link KernelRootNode}, and its declared
/// return type is final. The metadata describes its runtime parameters, its
/// compile-time constants and how the two are arranged into the foreign
/// target's parameter order.
public record KernelNode(
        String name,
        IrType returnType,
        @Nullable ForeignKernelStamp foreign
) implements HIRNode {

    public KernelNode(String name, IrType returnType) {
        this(name, returnType, null);
    }

    public KernelNode(String name) {
        this(name, FormalType.TOP, null);
    }

    public boolean isForeign() {
        return foreign != null;
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
        if (foreign != null) {
            // No body to solve from: the declared return type is final
            return returnType;
        }
        final int root = findRoot(graph, id);
        if (root == -1) {
            return null; // body not attached yet
        }
        final var returned = graph.getValue().getOperands(root);
        if (returned.isEmpty()) {
            return null; // body is still being constructed
        }
        return TypeUtil.getValueType(graph, returned.getInt(0));
    }

    private static int findRoot(IrGraph graph, int id) {
        final var usages = graph.getMemory().getUsages(id);
        for (int i = 0; i < usages.size(); i++) {
            final int usage = usages.getInt(i);
            if (graph.getNode(usage) instanceof KernelRootNode) {
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
        return that instanceof KernelNode(String n, IrType t, ForeignKernelStamp f)
                && Objects.equals(name, n)
                && Objects.equals(returnType, t)
                && Objects.equals(foreign, f);
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
