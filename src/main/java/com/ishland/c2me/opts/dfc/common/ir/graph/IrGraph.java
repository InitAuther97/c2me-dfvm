package com.ishland.c2me.opts.dfc.common.ir.graph;

import com.ishland.c2me.opts.dfc.common.ir.LongUtil;
import com.ishland.c2me.opts.dfc.common.ir.graph.cfg.CFGAccess;
import com.ishland.c2me.opts.dfc.common.ir.graph.value.NodeState;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.FunctionBeginNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ReturnNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant.ConstantNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.function.FunctionNode;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.ConstantTree;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.flowsched.util.Assertions;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceLists;

import java.util.List;
import java.util.NoSuchElementException;

public class IrGraph {

    private final ReferenceArrayList<NodeState> nodes = new ReferenceArrayList<>();
    private final IntArrayList availableIds = new IntArrayList();

    private final EdgeList control = new EdgeList();
    private final EdgeList value   = new EdgeList();
    private final EdgeList memory  = new EdgeList();

    private final LongArrayList programs; // high: end (32bit) | begin (32bit)
    private final ReferenceArrayList<CFGAccess> cfgs;

    private int size, modCount;
    private int constantPool = -1;

    public IrGraph() {
        programs = new LongArrayList();
        cfgs = new ReferenceArrayList<>();
    }

    protected IrGraph(IrGraph graph) {
        this.nodes.addAll(graph.nodes);
        this.availableIds.addAll(graph.availableIds);
        this.control.copyFrom(graph.control);
        this.value.copyFrom(graph.value);
        this.memory.copyFrom(graph.memory);
        this.programs = graph.programs.clone();
        this.cfgs = graph.cfgs.clone();
        this.size = graph.size;
        this.modCount = 0;
        this.constantPool = graph.constantPool;
    }

    /// Sets the pool that every constant node consumes through a memory
    /// operand.
    public void setConstantPool(int constantPool) {
        this.constantPool = constantPool;
    }

    /// Registers an executable MIR function. HIR value DAGs use kernel roots
    /// and do not participate in this table.
    public void defineProgram(int begin, int end) {
        Assertions.assertTrue(
                getNode(begin) instanceof FunctionBeginNode && getNode(end) == ReturnNode.RET);
        final int idx = LongUtil.findByLow(programs.elements(), begin, 0, programs.size());
        Assertions.assertTrue(idx < 0, "Program starts with %d already exists", begin);
        final int realIdx = ~idx;
        programs.add(realIdx, LongUtil.make(begin, end));
        cfgs.add(realIdx, null);
    }

    public int getProgramEnd(int begin) {
        final int idx = LongUtil.findByLow(programs.elements(), begin, 0, programs.size());
        Assertions.assertTrue(idx >= 0, "Program starts with %d not found", begin);
        return LongUtil.getHigh(programs.getLong(idx));
    }

    public int countPrograms() {
        return programs.size();
    }

    public long getProgramAt(int i) {
        return programs.getLong(i);
    }

    public <T extends CFGAccess> T getOrComputeCFG(int begin, CFGAccess.Status<T> status) {
        final int idx = LongUtil.findByLow(programs.elements(), begin, 0, programs.size());
        Assertions.assertTrue(idx >= 0, "Program starts with %d not found", begin);
        return getOrComputeCFGAt(idx, status);
    }

    public void invalidateCFG(int begin) {
        final int idx = LongUtil.findByLow(programs.elements(), begin, 0, programs.size());
        Assertions.assertTrue(idx >= 0, "Program starts with %d not found", begin);
        invalidateCFGAt(idx);
    }

    public void invalidateCFGAt(int idx) {
        Assertions.assertTrue(idx < countPrograms(), "Program index overflow");
        cfgs.set(idx, null);
    }

    public <T extends CFGAccess> T getOrComputeCFGAt(int idx, CFGAccess.Status<T> status) {
        final var info = cfgs.get(idx);
        if (info != null && info.isAtLeast(status))
            return status.cast(info);
        final long program = programs.getLong(idx);
        final var cfg = CFGAccess.build(this, idx, info, status);
        cfgs.set(idx, cfg);
        return cfg;
    }

    public NodeState get(int id) {
        return nodes.get(id);
    }

    public IrNode getNode(int id) {
        return get(id).node();
    }

    /// Complete node type, including both results of a memory/value producer.
    public IrType getType(int id) {
        return get(id).type();
    }

    public int addNode(IrNode node) {
        final int id = allocate(new NodeState(node, node.initialType()));
        if (constantPool != -1 && isPooledConstant(node)) {
            memory.appendEdge(constantPool, id);
        }
        return id;
    }

    /// Whether {@code node} must consume the constant pool through a memory
    /// operand, keeping it reachable from the program start.
    private static boolean isPooledConstant(IrNode node) {
        return node instanceof ConstantNode
                || node instanceof KernelNode
                || node instanceof FunctionNode
                || node instanceof ConstantTree;
    }

    protected int allocate(NodeState item) {
        // We don't want to mess with node index
        // This preserves some ordering when inserting nodes
        final int id;
        id = nodes.size();
        nodes.add(item);
        control.allocate();
        value.allocate();
        memory.allocate();
        if (!availableIds.isEmpty()) {
            final int prev = availableIds.popInt();
            control.recycle(prev, id);
            value.recycle(prev, id);
            memory.recycle(prev, id);
        }
        this.size++;
        this.modCount++;
        return id;
    }

    public void removeNode(int id) {
        if (id >= nodes.size() || availableIds.contains(id)) {
            throw new NoSuchElementException("node to remove");
        }
        if (!isOrphan(id)) {
            throw new IllegalStateException("Attempt to delete live node");
        }
        // We never remove a node. We do like this so we recycle node lists,
        // which may save some spaces.
        nodes.set(id, null);
        availableIds.add(id);
        this.size--;
        this.modCount++;
    }

    public void replaceNode(int id, NodeState state) {
        this.nodes.set(id, state);
        this.modCount++;
    }

    public EdgeAccessor getControl() {
        return control;
    }

    public EdgeAccessor getValue() {
        return value;
    }

    public EdgeAccessor getMemory() {
        return memory;
    }

    public List<NodeState> nodes() {
        return ReferenceLists.unmodifiable(nodes);
    }

    public int size() {
        return size;
    }

    public int modCount() {
        return modCount;
    }

    private void compressAndCreateMapping(IntList mapping) {
        // Every time a node is deleted, decrease delta by 1
        // The final list is the index mapping before and after cleaning
        int delta = 0;
        for (int i = 0; i < nodes.size(); i++) {
            final NodeState node = nodes.get(i);
            if (node != null) {
                // Put corresponding delta
                mapping.add(i + delta);
                nodes.set(i + delta, node);
                continue;
            }
            --delta;
            mapping.add(-1); // Use invalid value
        }
        this.availableIds.clear();
        nodes.trim(this.size);
    }

    public boolean isOrphan(int id) {
        return control.isOrphan(id) && memory.isOrphan(id) && value.isOrphan(id);
    }

    public void compress() {
        final int size = this.size;
        if (size == this.nodes.size()) {
            this.control.purge();
            this.value.purge();
            this.memory.purge();
            return;
        }
        // Create an id mapping
        final var mapping = new IntArrayList(nodes.size());
        compressAndCreateMapping(mapping);
        for (int i = 0; i < programs.size(); i++) {
            final long entry = programs.getLong(i);
            final int originalBegin = LongUtil.getLow(entry);
            final int originalEnd = LongUtil.getHigh(entry);
            final int newBegin = mapping.getInt(originalBegin);
            final int newEnd = mapping.getInt(originalEnd);
            programs.set(i, LongUtil.make(newBegin, newEnd));
        }
        final var immutable = IntLists.unmodifiable(mapping);
        this.control.compress(immutable, size);
        this.value.compress(immutable, size);
        this.memory.compress(immutable, size);
        this.modCount++;
    }
}
