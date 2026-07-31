package com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg;

import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;

public sealed interface ControlNode extends IrNode permits BeginBlockNode, EndBlockNode, ProgramNode {
}
