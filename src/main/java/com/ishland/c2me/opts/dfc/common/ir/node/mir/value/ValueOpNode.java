package com.ishland.c2me.opts.dfc.common.ir.node.mir.value;

import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.MIRNode;
import com.ishland.c2me.opts.dfc.common.ir.type.FormalType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

import java.util.logging.Level;

/// A ValueOpNode represents a value that can be computed.
/// The computation is bound to its upstream cfg nodes.
/// A node can have multiple cfg nodes, which means they
/// may be emitted multiple times.
public interface ValueOpNode extends MIRNode {
    static IrType verifyValue(IrType type) {
        if (type == FormalType.BOT) {
            final var error = new IllegalStateException("ValueNode produces FormalType.BOT");
            LOGGER.log(Level.SEVERE, "ValueNode produces FormalType.BOT! Please check the IR graph.", error);
            throw error;
        }
        if (!type.isDetermined()) {
            final var error = new IllegalStateException("ValueNode produces non-determined type");
            LOGGER.log(Level.SEVERE, "ValueNode produces non-determined type! Please check the IR graph.", error);
            throw error;
        }
        return type;
    }

    @Override
    default boolean isEqual(IrNode that) {
        return that == this;
    }

    default boolean isPure(IrGraph graph, int id) {
        return true;
    }
}
