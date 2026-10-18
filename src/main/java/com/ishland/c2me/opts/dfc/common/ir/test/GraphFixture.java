package com.ishland.c2me.opts.dfc.common.ir.test;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.FunctionBeginNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.FunctionNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ReturnNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.cond.SelectNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant.DoubleConstantNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant.IntConstantNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.AddFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.AddINode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.DivFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.DivINode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.MaxFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.MinFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.MulFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.MulINode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.SqrtFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.SubFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.SubINode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import it.unimi.dsi.fastutil.ints.IntList;

/// Helper to quickly build an IR graph for tests, run phases on it and
/// inspect the results. Every fixture holds a valid control spine:
/// START -&gt; FunctionBeginNode -&gt; ReturnNode -&gt; END. Value nodes not reachable
/// from a kept-alive root are subject to dead code elimination during
/// canonicalization, so roots of interest should be anchored with
/// {@link #keepAlive(int)}.
public final class GraphFixture {

    private final GraphContext context = new GraphContext();
    private final IrGraph graph = context.getIrGraph();
    private final int entry;
    private final int ret;

    public GraphFixture() {
        final int descriptor = graph.addNode(new FunctionNode("test"));
        entry = graph.addNode(FunctionBeginNode.BEGIN);
        graph.getMemory().appendEdge(descriptor, entry);
        ret = graph.addNode(ReturnNode.RET);
        graph.getControl().appendEdge(context.getProgramStart(), entry);
        graph.getControl().appendEdge(entry, ret);
        graph.getControl().appendEdge(ret, context.getProgramEnd());
        graph.defineProgram(entry, ret);
    }

    public GraphContext context() {
        return context;
    }

    public IrGraph graph() {
        return graph;
    }

    public int entry() {
        return entry;
    }

    public int returnNode() {
        return ret;
    }

    // --- Building ---

    /// Add a node with the given value operands.
    public int node(IrNode node, int... operands) {
        final int id = graph.addNode(node);
        for (int operand : operands) {
            graph.getValue().appendEdge(operand, id);
        }
        return id;
    }

    public int intConst(int value) {
        return node(new IntConstantNode(value));
    }

    public int doubleConst(double value) {
        return node(new DoubleConstantNode(value));
    }

    /// Add an opaque input value of the given type.
    public int input(IrType type) {
        return node(new OpaqueInputNode(type));
    }

    // int operators
    public int iadd(int lhs, int rhs) { return node(AddINode.ADD_I, lhs, rhs); }
    public int isub(int lhs, int rhs) { return node(SubINode.SUB_I, lhs, rhs); }
    public int imul(int lhs, int rhs) { return node(MulINode.MUL_I, lhs, rhs); }
    public int idiv(int lhs, int rhs) { return node(DivINode.DIV_TRUNC, lhs, rhs); }
    public int select(int condition, int ifTrue, int ifFalse) { return node(SelectNode.SELECT, condition, ifTrue, ifFalse); }

    // double operators
    public int dadd(int lhs, int rhs) { return node(AddFPNode.ADD_D, lhs, rhs); }
    public int dsub(int lhs, int rhs) { return node(SubFPNode.SUB_D, lhs, rhs); }
    public int dmul(int lhs, int rhs) { return node(MulFPNode.MUL_D, lhs, rhs); }
    public int ddiv(int lhs, int rhs) { return node(DivFPNode.DIV_D, lhs, rhs); }
    public int dmin(int lhs, int rhs) { return node(MinFPNode.MIN_D, lhs, rhs); }
    public int dmax(int lhs, int rhs) { return node(MaxFPNode.MAX_D, lhs, rhs); }
    public int dsqrt(int input) { return node(SqrtFPNode.SQRT_D, input); }

    // float operators
    public int fadd(int lhs, int rhs) { return node(AddFPNode.ADD_F, lhs, rhs); }
    public int fsub(int lhs, int rhs) { return node(SubFPNode.SUB_F, lhs, rhs); }
    public int fmul(int lhs, int rhs) { return node(MulFPNode.MUL_F, lhs, rhs); }
    public int fdiv(int lhs, int rhs) { return node(DivFPNode.DIV_F, lhs, rhs); }
    public int fmin(int lhs, int rhs) { return node(MinFPNode.MIN_F, lhs, rhs); }
    public int fmax(int lhs, int rhs) { return node(MaxFPNode.MAX_F, lhs, rhs); }
    public int fsqrt(int input) { return node(SqrtFPNode.SQRT_F, input); }

    /// Anchor a node as a value operand of the return node, protecting it
    /// (and its upstream) from dead code elimination.
    public int keepAlive(int id) {
        graph.getValue().appendEdge(id, ret);
        return id;
    }

    // --- Phases ---

    /// Run the canonicalizer over the whole graph repeatedly until the
    /// graph stops changing. A node whose type just changed is only
    /// re-visited (and thus optimized) on a later pass, so a single pass
    /// is not enough to reach a fixpoint.
    public void canonicalize() {
        int lastModCount;
        do {
            lastModCount = graph.modCount();
            canonicalizeOnce(allLiveNodes());
        } while (graph.modCount() != lastModCount);
    }

    /// Run the canonicalizer once, seeded with the given nodes, or with
    /// every live node if no seeds are given.
    public void canonicalizeOnce(int... seeds) {
        final Canonicalizer canonicalizer = new Canonicalizer(context);
        if (seeds.length == 0) {
            seeds = allLiveNodes();
        }
        for (int seed : seeds) {
            canonicalizer.addToWorkQueue(seed);
        }
        canonicalizer.run();
    }

    private int[] allLiveNodes() {
        final it.unimi.dsi.fastutil.ints.IntArrayList ids = new it.unimi.dsi.fastutil.ints.IntArrayList();
        for (int id = 0; id < graph.nodes().size(); id++) {
            if (graph.get(id) != null) {
                ids.add(id);
            }
        }
        return ids.toIntArray();
    }

    // --- Inspection ---

    public IrType typeOf(int id) {
        return graph.getType(id);
    }

    public IrNode nodeAt(int id) {
        return graph.getNode(id);
    }

    public boolean isAlive(int id) {
        return graph.get(id) != null;
    }

    public IntList valueOperands(int id) {
        return graph.getValue().getOperands(id);
    }

    /// The id of the single value operand of the return node; useful to
    /// follow a kept-alive root after it was replaced.
    public int returnValue() {
        final IntList operands = graph.getValue().getOperands(ret);
        if (operands.size() != 1) {
            throw new AssertionError("Expected exactly one return value operand, got " + operands);
        }
        return operands.getInt(0);
    }

    /// Directly invoke {@link IrNode#compute} on a node, bypassing the
    /// canonicalizer (and thus constant folding).
    public IrType computeType(int id) {
        return nodeAt(id).compute(graph, id, context);
    }
}