package com.ishland.c2me.opts.dfc.common.ir.node.tree.group;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.MarkerTreeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.group.GroupNode;
import net.minecraft.util.Identifier;

/// Identifies a root that corresponds to a caller-supplied density function.
/// Input roots consume their identifier tag, so roots with different
/// identifiers stay distinct under GVN.
///
/// Roots created by splitting a tree at a volume change consume their
/// parent's {@link GroupNode} but carry no identifier, which lets equal
/// split-off subtrees deduplicate.
public record IdentifierTagTree(Identifier identifier) implements MarkerTreeNode {
}