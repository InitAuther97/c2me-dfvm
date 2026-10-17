package com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg;

/// The end of a basic block. Except for {@link MergeNode}, an EndBlockNode
/// has exactly one control operand: the {@link BeginBlockNode} starting its
/// block. Implementations: {@link IfBranchNode}, {@link MergeNode},
/// {@link ReturnNode}.
public non-sealed interface EndBlockNode extends ControlNode {
}
