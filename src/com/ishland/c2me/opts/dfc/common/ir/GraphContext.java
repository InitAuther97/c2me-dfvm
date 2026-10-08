package com.ishland.c2me.opts.dfc.common.ir;

import com.ishland.c2me.opts.dfc.common.ir.graph.GraphVerifier;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.EntryNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ProgramNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ReturnNode;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.DataType;
import com.ishland.flowsched.util.Assertions;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceList;

import java.util.BitSet;

/// Compilation context owning one IR graph. Each context holds exactly one
/// program begin ({@link ProgramNode#START}) and one program exit
/// ({@link ProgramNode#END}): over control edges, START fans out to the
/// {@link EntryNode} of every executable function, and every
/// {@link ReturnNode} flows into END. These structural invariants are
/// checked by {@link GraphVerifier}.
public class GraphContext {
    private final IrGraph node = new IrGraph();
    private final int start, end;
    private final ReferenceList<DataType> aliasMapping = new ReferenceArrayList<>();
    private final BitSet enabledFeatures = new BitSet();

    public GraphContext() {
        this.start = this.node.addNode(ProgramNode.START);
        this.end = this.node.addNode(ProgramNode.END);
    }

    public int getProgramStart() {
        return this.start;
    }

    public int getProgramEnd() {
        return this.end;
    }

    public int allocateAlias(DataType dataType) {
        final int result = aliasMapping.size();
        aliasMapping.add(dataType);
        return result;
    }

    public DataType getDataType(int alias) {
        Assertions.assertTrue(alias >= 0, "Cannot parse invalid alias %d", alias);
        return aliasMapping.get(alias);
    }

    public IrGraph getIrGraph() {
        return node;
    }

    public void toggleFeature(CompileFeature feature) {
        enabledFeatures.set(feature.ordinal());
    }

    public boolean isFeatureEnabled(CompileFeature feature) {
        return enabledFeatures.get(feature.ordinal());
    }
}
