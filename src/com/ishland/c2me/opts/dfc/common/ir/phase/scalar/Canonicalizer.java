package com.ishland.c2me.opts.dfc.common.ir.phase.scalar;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.value.GraphUtil;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.graph.value.NodeState;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.EntryNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ProgramNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ReturnNode;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;

import java.util.BitSet;
import java.util.Objects;

public class Canonicalizer implements Runnable {

    private static void addDownstream(IrGraph graph, IntArrayList list, int element) {
        final var value = graph.getValue().getUsages(element);
        final var memory = graph.getMemory().getUsages(element);
        for (int i = 0; i < value.size(); i++) {
            insert(list, value.getInt(i));
        }
        for (int i = 0; i < memory.size(); i++) {
            insert(list, memory.getInt(i));
        }
    }

    private static void addUpstream(IrGraph graph, IntArrayList list, int element) {
        final var value = graph.getValue().getOperands(element);
        final var memory = graph.getMemory().getOperands(element);
        for (int i = 0; i < value.size(); i++) {
            insert(list, value.getInt(i));
        }
        for (int i = 0; i < memory.size(); i++) {
            insert(list, memory.getInt(i));
        }
    }

    private static void insert(IntArrayList list, int element) {
        if (list.isEmpty()) {
            list.add(element);
            return;
        }
        final int index = IntArrays.binarySearch(list.elements(), 0, list.size(), element);
        if (index >= 0) return;
        list.add(-index - 1, element);
    }

    private static void remove(EdgeAccessor edge, int id, IntArrayList workQueue) {
        final var op = edge.getOperands(id);
        while (!op.isEmpty()) {
            final int operand = GraphUtil.getLast(op);
            edge.removeEdge(operand, id);
            insert(workQueue, operand);
        }
    }

    public static class Scheduled extends Canonicalizer {

        private final BitSet[] scheduling;

        public Scheduled(GraphContext ctx, BitSet[] scheduling) {
            super(ctx);
            this.scheduling = scheduling;
        }

        @Override
        protected void onRemoveControl(int control, int node) {
            super.onRemoveControl(control, node);
        }
    }

    private final GraphContext context;

    private IntArrayList workQueue = new IntArrayList();
    private IntArrayList workQueue2 = new IntArrayList();

    public Canonicalizer(GraphContext ctx) {
        this.context = ctx;
    }

    public void addToWorkQueue(int node) {
        insert(workQueue2, node);
    }

    private void deadCodeElim(IrGraph graph, int id, IntArrayList workQueue) {
        final var value = graph.getValue();
        final var memory = graph.getMemory();
        final var control = graph.getControl();

        final var op = control.getOperands(id);
        while (!op.isEmpty()) {
            final int operand = GraphUtil.getLast(op);
            onRemoveControl(operand, id);
            control.removeEdge(operand, id);
            insert(workQueue, operand);
        }
        remove(value, id, workQueue);
        remove(memory, id, workQueue);
        graph.removeNode(id);
    }

    protected void onRemoveControl(int control, int node) {}

    protected void onAddControl(int control, int node) {}

    /// The control spine of a graph (program start/end, entries and returns)
    /// is structural: a valid graph always contains it, so it is never
    /// subject to dead code elimination even when it has no usages.
    private static boolean isControlSpine(IrNode node) {
        return node instanceof ProgramNode || node instanceof EntryNode || node instanceof ReturnNode;
    }


    @Override
    public void run() {
        final var graph = context.getIrGraph();
        do {
            final var buffer = workQueue2;
            workQueue2 = workQueue;
            workQueue = buffer;
            for (int i = 0; i < workQueue.size(); ++i) {
                int id = workQueue.getInt(i);
                final var state = graph.get(id);
                if (state == null) {
                    // Node was already eliminated earlier in this run
                    continue;
                }
                final var node = state.node();
                // Fast path: node is already dead
                if (graph.isOrphan(id) && !isControlSpine(node)) {
                    // Dead code elimination
                    deadCodeElim(graph, id, workQueue2);
                    continue;
                }
                // Step.1: compute type
                final var type = node.compute(graph, id, context);
                if (type != null && !Objects.equals(type, state.type())) {
                    // Type changed. Propagate information to users.
                    graph.replaceNode(id, new NodeState(node, type));
                    addDownstream(graph, workQueue2, id);
                    continue;
                }
                // Try to do optimization
                if (!node.optimize(graph, id, this, context)) {
                    continue;
                }
                // Fast path: node is already dead
                if (graph.isOrphan(id) && !isControlSpine(node)) {
                    deadCodeElim(graph, id, workQueue2);
                    continue;
                }
                // Node changed. Propagate information to neighbors.
                addDownstream(graph, workQueue2, id);
                addUpstream(graph, workQueue2, id);
            }
            workQueue.clear();
        } while (!workQueue2.isEmpty());
    }
}
