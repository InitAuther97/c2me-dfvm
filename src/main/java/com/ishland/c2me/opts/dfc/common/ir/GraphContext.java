package com.ishland.c2me.opts.dfc.common.ir;

import com.ishland.c2me.opts.dfc.common.ir.graph.GraphVerifier;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ConstantPoolNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.FunctionBeginNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ProgramNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ReturnNode;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.DataType;
import com.ishland.flowsched.util.Assertions;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceList;

import java.util.BitSet;

/// Compilation context owning one IR graph. Each context holds exactly one
/// graph start ({@link ProgramNode#START}) and one graph exit
/// ({@link ProgramNode#END}). HIR kernel roots sit between them as structural
/// anchors for pure value DAGs. In MIR, START fans out to the
/// {@link FunctionBeginNode} of every executable function, and every
/// {@link ReturnNode} flows into END. A {@link ConstantPoolNode} also sits
/// between START and END as the memory source every constant node consumes,
/// keeping constants reachable from the program start. These structural
/// invariants are checked by {@link GraphVerifier}.
public class GraphContext {
    private final IrGraph node = new IrGraph();
    private final int start, end, constantPool;
    private final ReferenceList<DataType> aliasMapping = new ReferenceArrayList<>();
    private final BitSet enabledFeatures = new BitSet();

    public GraphContext() {
        this.start = this.node.addNode(ProgramNode.START);
        this.end = this.node.addNode(ProgramNode.END);
        this.constantPool = this.node.addNode(ConstantPoolNode.POOL);
        this.node.getControl().appendEdge(this.start, this.constantPool);
        this.node.getControl().appendEdge(this.constantPool, this.end);
        this.node.setConstantPool(this.constantPool);
    }

    public int getProgramStart() {
        return this.start;
    }

    public int getProgramEnd() {
        return this.end;
    }

    /// The pool every constant node consumes through a memory operand.
    public int getConstantPool() {
        return this.constantPool;
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
