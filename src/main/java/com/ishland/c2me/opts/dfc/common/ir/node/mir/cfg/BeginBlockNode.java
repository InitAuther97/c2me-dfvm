package com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg;

/// The start of a basic block. A BeginBlockNode has exactly one control
/// edge to the {@link EndBlockNode} terminating its block; any further
/// control edges to non-control nodes are scheduling edges.
/// Implementations: {@link FunctionBeginNode}, {@link BranchBeginNode},
/// {@link MergeNode}.
public non-sealed interface BeginBlockNode extends ControlNode {
}
