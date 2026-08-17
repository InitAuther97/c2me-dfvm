package com.ishland.c2me.opts.dfc.common.ir.graph.cfg;

import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeList;
import com.ishland.flowsched.util.Assertions;
import it.unimi.dsi.fastutil.ints.*;

import java.util.Arrays;

public class DomTree {

    /// See this reference for detail: Cooper, Keith D.; Harvey, Timothy J; Kennedy, Ken (2001).
    /// ["A Simple, Fast Dominance Algorithm"](http://www.hipersoft.rice.edu/grand-publications/dom14.pdf) (PDF).
    /// @param control The edge accessor containing target program's control info the dominator tree represents
    /// @param begin The id of the program's Entry
    /// @return A built dominator tree
    public static DomTree createDomTree(EdgeAccessor control, IntList po, int begin) {
        final var result = new DomTree(po.size(), begin);
        // Nodes are numbered by their position in the post-order list. The
        // numbering is required by lca during the idom computation below.
        for (int i = 0; i < po.size(); i++) {
            result.order[po.getInt(i)] = i;
        }
        result.buildIDom(control, po, begin);
        result.buildEdges(begin);
        return result;
    }

    private final int size, root;
    private final int[] idom;
    private final int[] order;
    private final EdgeList domTree = new EdgeList();

    private DomTree(int size, int root) {
        this.size = size;
        this.root = root;
        this.idom = new int[size];
        this.order = new int[size];
        this.domTree.allocate(size);
        Arrays.fill(this.idom, -1);
    }

    private void checkNode(int node) {
        Assertions.assertTrue(node >= 0, "Query not existing nodes");
    }

    /// @return the position of the node in the post-order list the tree was
    /// built from. Dominators always have a higher order than the nodes they
    /// dominate, so walking up the idom chain strictly increases the order.
    public int getOrder(int node) {
        checkNode(node);
        return this.order[node];
    }

    public int getIDom(int node) {
        checkNode(node);
        return this.idom[node];
    }

    public boolean isDominatedBy(int thisNode, int thatNode) {
        checkNode(thisNode);
        checkNode(thatNode);
        int current = thisNode;
        while (current != -1) {
            if (current == thatNode) return true;
            current = this.idom[current];
        }
        return false;
    }

    private int find(int node) {
        return node;
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
                int newIdom = -1;
                for (int j = 0; j < predecessors.size(); j++) {
                    final int node2 = predecessors.getInt(j);
                    if (node2 == node) continue; // Self edge: not yet processed
                    if (node2 != begin && getIDom(node2) == -1) continue; // Not yet processed
                    newIdom = newIdom == -1 ? node2 : lca(node2, newIdom);
                }
                if (newIdom == -1) continue; // No processed predecessor yet
                changed |= changeIDom(node, newIdom);
            }
        }
    }

    private void buildEdges(int begin) {
        for (int block = 0; block < this.size; block++) {
            if (block == begin) continue;
            final int dom = this.idom[block];
            Assertions.assertTrue(dom >= 0, "Some begin node is not inside dominator tree");
            final int idxDom = find(dom);
            this.domTree.appendEdge(idxDom, block);
        }
    }
}