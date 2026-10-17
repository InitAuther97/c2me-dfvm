package com.ishland.c2me.opts.dfc.common.ir.graph;

import com.ishland.c2me.opts.dfc.common.util.FastCompareIntArrayList;
import com.ishland.c2me.opts.dfc.common.util.UnmodifiableFastCompareIntArrayList;
import com.ishland.flowsched.util.Assertions;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.util.NoSuchElementException;

public class EdgeList implements EdgeAccessor {

    private int size;
    private final ReferenceArrayList<IntList> outgoingEdges = new ReferenceArrayList<>();
    private final ReferenceArrayList<IntList> incomingEdges = new ReferenceArrayList<>();

    private IntList getOrCreate(ReferenceArrayList<IntList> list, int id) {
        if (id >= size) {
            throw new NoSuchElementException();
        }
        final var result = list.get(id);
        if (result != IntLists.EMPTY_LIST) {
            return result;
        }
        final var result2 = new FastCompareIntArrayList();
        list.set(id, result2);
        return result2;
    }

    public void recycle(int previous, int next) {
        final var prevIn = incomingEdges.get(previous);
        final var prevOut = outgoingEdges.get(previous);
        final var nextIn = incomingEdges.get(next);
        final var nextOut = outgoingEdges.get(next);
        Assertions.assertTrue(prevIn.isEmpty() && prevOut.isEmpty() && nextIn == IntLists.EMPTY_LIST && nextOut == IntLists.EMPTY_LIST);
        incomingEdges.set(next, prevIn);
        outgoingEdges.set(next, prevOut);
        incomingEdges.set(previous, nextIn);
        outgoingEdges.set(previous, nextOut);
    }

    @Override
    public int allocate() {
        outgoingEdges.add(IntLists.EMPTY_LIST);
        incomingEdges.add(IntLists.EMPTY_LIST);
        return size++;
    }

    @Override
    public int allocate(int count) {
        outgoingEdges.ensureCapacity(size + count);
        incomingEdges.ensureCapacity(size + count);
        for (int i = 0; i < count; i++) {
            outgoingEdges.add(IntLists.EMPTY_LIST);
            incomingEdges.add(IntLists.EMPTY_LIST);
        }
        final int res = size;
        size += count;
        return res;
    }

    @Override
    public IntList getUsages(int id) {
        return UnmodifiableFastCompareIntArrayList.unmodifiable(outgoingEdges.get(id));
    }

    @Override
    public IntList getOperands(int id) {
        return UnmodifiableFastCompareIntArrayList.unmodifiable(incomingEdges.get(id));
    }

    @Override
    public void appendEdge(int from, int to) {
        final var list1 = getOrCreate(incomingEdges, to);
        final var list2 = getOrCreate(outgoingEdges, from);
        list2.add(to);
        list1.add(from);
    }

    @Override
    public void removeEdge(int from, int to) {
        final var list1 = getOrCreate(incomingEdges, to);
        final var list2 = getOrCreate(outgoingEdges, from);
        if (!list2.rem(to)) {
            throw new IllegalStateException("Outgoing edge not found");
        }
        if (!list1.rem(from)) {
            throw new IllegalStateException("Incoming edge not found");
        }
    }

    @Override
    public void setIncoming(int node, int index, int operand) {
        final var list1 = getOrCreate(incomingEdges, node);
        final var list2 = getOrCreate(outgoingEdges, operand);
        list2.add(node);
        list1.set(index, operand);
    }

    @Override
    public void addEdgePair(int from, int idx, int to, int idx2) {
        final var list1 = getOrCreate(incomingEdges, to);
        final var list2 = getOrCreate(outgoingEdges, from);
        list2.add(idx, to);
        list1.add(idx2, from);
    }

    @Override
    public void replaceEdge(int node, int originalOp, int newOp) {
        final var list1 = getOrCreate(incomingEdges, node);
        final var list2 = getOrCreate(outgoingEdges, originalOp);
        final var list3 = getOrCreate(outgoingEdges, newOp);
        if (!list1.contains(originalOp)) {
            throw new IllegalStateException("Incoming edge not found");
        }
        list1.set(list1.indexOf(originalOp), newOp);
        list2.rem(node);
        list3.add(node);
    }

    @Override
    public void insertEdgeMiddleman(int from, int to, int mid) {
        final var list1 = getOrCreate(outgoingEdges, from);
        final var list2 = getOrCreate(incomingEdges, to);
        final var list3 = getOrCreate(outgoingEdges, mid);
        final var list4 = getOrCreate(incomingEdges, mid);
        final int idx1 = list1.indexOf(to);
        final int idx2 = list2.indexOf(from);
        list1.set(idx1, mid);
        list2.set(idx2, mid);
        list3.add(to);
        list4.add(from);
    }

    @Override
    public void insertEdge(int node, int index, int operand) {
        final var list1 = getOrCreate(incomingEdges, node);
        final var list2 = getOrCreate(outgoingEdges, operand);
        list1.add(index, operand);
        list2.add(node);
    }

    @Override
    public void bulkReplaceSrc(int node, Int2IntFunction replace) {
        final var list1 = getOrCreate(incomingEdges, node);
        for (int i = 0; i < list1.size(); i++) {
            final int op = list1.getInt(i);
            final int replacement = replace.getOrDefault(op, op);
            final var list2 = getOrCreate(outgoingEdges, op);
            final var list3 = getOrCreate(outgoingEdges, replacement);
            list1.set(i, replacement);
            list2.rem(node);
            list3.add(node);
        }
    }

    @Override
    public void copyFrom(EdgeList other) {
        if (!this.outgoingEdges.isEmpty() || !this.incomingEdges.isEmpty()) {
            throw new IllegalStateException("Cannot copy content into an occupied EdgeList");
        }
        this.outgoingEdges.addAll(other.outgoingEdges);
        this.incomingEdges.addAll(other.incomingEdges);
    }

    @Override
    public void purge() {
        this.outgoingEdges.trim();
        this.incomingEdges.trim();
    }

    @Override
    public void compress(IntList mapping, int actualSize) {
        Assertions.assertTrue(actualSize > 0, "actualSize must be > 0");
        // Begin edge rewrite
        final int size = this.outgoingEdges.size();
        Assertions.assertTrue(size == mapping.size(), "Mapping size mismatch");
        for (int i = 0; i < size; i++) {
            final int mapped = mapping.getInt(i);
            if (mapped == -1) continue;
            final var outgoing = this.outgoingEdges.get(i);
            final var incoming = this.incomingEdges.get(i);
            outgoingEdges.set(mapped, outgoing);
            incomingEdges.set(mapped, incoming);
            Assertions.assertTrue(outgoing != null && incoming != null, "Why will there be null lists?");
            assert outgoing != null && incoming != null;
            for (int j = 0; j < outgoing.size(); j++) {
                final int origO = outgoing.getInt(j);
                final int origI = incoming.getInt(j);
                final int newO = mapping.getInt(origO);
                final int newI = mapping.getInt(origI);
                Assertions.assertTrue(newI != -1 && newO != -1, "Found edges from/to dead slots");
                outgoing.set(j, newO);
                incoming.set(j, newI);
            }
        }
        this.outgoingEdges.removeElements(actualSize - 1, size);
        this.incomingEdges.removeElements(actualSize - 1, size);
        purge();
    }

    public void sortOperands(int node, IntComparator comparator) {
        final var result = incomingEdges.get(node);
        if (result == IntLists.EMPTY_LIST) {
            return;
        }
        result.sort(comparator);
    }

    public void resetUnsafe() {
        for (int i = 0; i < this.outgoingEdges.size(); i++) {
            this.outgoingEdges.get(i).clear(); // EmptyCollection does nothing when cleared
        }

        for (int i = 0; i < this.incomingEdges.size(); i++) {
            this.incomingEdges.get(i).clear();
        }
    }

    public int size() {
        return this.size;
    }
}
