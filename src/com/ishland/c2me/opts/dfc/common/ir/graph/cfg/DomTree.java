package com.ishland.c2me.opts.dfc.common.ir.graph.cfg;

import com.ishland.c2me.opts.dfc.common.ir.LongUtil;
import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeList;
import com.ishland.flowsched.util.Assertions;
import it.unimi.dsi.fastutil.ints.*;

import java.util.Arrays;
import java.util.function.IntConsumer;

public class DomTree {

    /// See this reference for detail: Cooper, Keith D.; Harvey, Timothy J; Kennedy, Ken (2001).
    /// ["A Simple, Fast Dominance Algorithm"](http://www.hipersoft.rice.edu/grads/publications/dom14.pdf) (PDF).
    /// @param control The edge accessor containing target program's control info the dominator tree represents
    /// @param begin The id of the program's Entry
    /// @return A built dominator tree
    public static DomTree createDomTree(EdgeAccessor control, IntList po, int begin) {
        final var result = new DomTree(po.size(), begin);
        result.buildIDom(control, po, begin);
        result.buildEdges(begin);
        result.buildEndOrder();
        return result;
    }

    private int index = 0;
    private final int size, root;
    private final int[] idom;
    private final long[] orderAndEndOrder;
    private final EdgeList domTree = new EdgeList();

    private DomTree(int size, int root) {
        this.size = size;
        this.root = root;
        this.idom = new int[size];
        this.orderAndEndOrder = new long[size];
        Arrays.fill(this.idom, -1);
        Arrays.fill(this.orderAndEndOrder, -1L);
    }

    private void checkNode(int node) {
        Assertions.assertTrue(node >= 0, "Query not existing nodes");
    }

    public int getOrder(int node) {
        checkNode(node);
        return LongUtil.getLow(this.orderAndEndOrder[node]);
    }

    public int getEndOrder(int node) {
        checkNode(node);
        return LongUtil.getHigh(this.orderAndEndOrder[node]);
    }

    public int getIDom(int node) {
        checkNode(node);
        return this.idom[node];
    }

    public boolean isDominatedBy(int thisNode, int thatNode) {
        checkNode(thisNode);
        checkNode(thatNode);
        long orderAndEndOrder = this.orderAndEndOrder[thatNode];
        final int order = LongUtil.getLow(orderAndEndOrder);
        final int endOrder = LongUtil.getHigh(orderAndEndOrder);
        final int thisOrder = getOrder(thisNode);
        return order <= thisOrder && thisOrder <= endOrder;
    }

    private int find(int node) {
        return node;
    }

    private void setEndOrder(int node, int idx) {
        Assertions.assertTrue(node >= 0, "Query not existing nodes");
        final long orig = this.orderAndEndOrder[node];
        this.orderAndEndOrder[node] = orig & Integer.toUnsignedLong(-1) | (long) idx << 32;
    }

    private boolean changeIDom(int node, int dom) {
        Assertions.assertTrue(node >= 0, "Query not existing nodes");
        final int origIdom = this.idom[node];
        if (origIdom == dom) {
            return false;
        }
        this.idom[node] = dom;
        return true;
    }

    public int lca(int n1, int n2) {
        int left = n1;
        int right = n2;
        while (left != right) {
            final int lDepth = getOrder(left);
            final int rDepth = getOrder(right);
            if (lDepth < rDepth) {
                left = getIDom(left);
            } else {
                right = getIDom(right);
            }
        }
        return left;
    }   

    public void walk(EdgeAccessor.DepthFirstVisitor visitor) {
        domTree.walk(this.root, visitor);
    }

    private void buildIDom(EdgeAccessor control, IntList po, int begin) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < po.size(); i++) { // Exclude entry node
                final int node = po.getInt(i);
                final var predecessors = control.getOperands(node);
                if (node == begin) continue;
                Assertions.assertTrue(!predecessors.isEmpty(), "Found control node with no upstream but it's not Entry node");
                int newIdom = predecessors.getInt(0);
                for (int j = 1; j < predecessors.size(); j++) {
                    final int node2 = predecessors.getInt(j);
                    if (getIDom(node2) != -1)
                        newIdom = lca(node2, newIdom);
                }
                changed |= changeIDom(node, newIdom);
            }
        }
    }

    private void buildEdges(int begin) {
        Assertions.assertTrue(this.index == this.size, "Some begin node is not inside dominator tree");
        for (int block = 0; block < this.size; block++) {
            if (block == begin) continue;
            final int dom = this.idom[block];
            final int idxDom = find(dom);
            this.domTree.appendEdge(idxDom, block);
        }
    }

    private void buildEndOrder() {
        domTree.walk(this.root, new EndOrderVisitor());
    }

    class EndOrderVisitor implements EdgeAccessor.DepthFirstVisitor {
        private int endOrder;

        @Override
        public boolean enter(int node) {
            endOrder = getOrder(node);
            return true;
        }

        @Override
        public void leave(int node) {
            setEndOrder(node, endOrder);
        }
    }
}
