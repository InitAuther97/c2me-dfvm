package com.ishland.c2me.opts.dfc.common.ir.test;

import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant.IntConstantNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.MulINode;
import com.ishland.c2me.opts.dfc.common.ir.type.IntegerType;
import com.ishland.c2me.opts.dfc.common.ir.test.framework.Assert;

/// Verifies canonicalizer behavior: type propagation, constant folding,
/// algebraic simplification and dead code elimination.
public class CanonicalizerTest {

    public void testConstantFolding() {
        final GraphFixture f = new GraphFixture();
        final int root = f.keepAlive(f.iadd(f.intConst(1), f.intConst(2)));
        f.canonicalize();
        Assert.assertFalse(f.isAlive(root), "folded add should be dead");
        final int folded = f.returnValue();
        Assert.assertTrue(f.nodeAt(folded) instanceof IntConstantNode, "folded result should be a constant");
        Assert.assertEquals(3, ((IntConstantNode) f.nodeAt(folded)).constant(), "1 + 2 folds to 3");
    }

    public void testNestedConstantFolding() {
        final GraphFixture f = new GraphFixture();
        // (1 + 2) * (10 - 4) should fold to 18
        final int add = f.iadd(f.intConst(1), f.intConst(2));
        final int sub = f.isub(f.intConst(10), f.intConst(4));
        final int root = f.keepAlive(f.imul(add, sub));
        f.canonicalize();
        Assert.assertFalse(f.isAlive(root), "folded mul should be dead");
        final int folded = f.returnValue();
        Assert.assertTrue(f.nodeAt(folded) instanceof IntConstantNode, "folded result should be a constant");
        Assert.assertEquals(18, ((IntConstantNode) f.nodeAt(folded)).constant(), "(1 + 2) * (10 - 4) folds to 18");
    }

    public void testAddZeroIdentity() {
        final GraphFixture f = new GraphFixture();
        final int x = f.input(IntegerType.range(0, 100));
        final int root = f.keepAlive(f.iadd(x, f.intConst(0)));
        f.canonicalize();
        Assert.assertFalse(f.isAlive(root), "x + 0 should be eliminated");
        Assert.assertEquals(x, f.returnValue(), "x + 0 should fold to x");
    }

    public void testMulOneIdentity() {
        final GraphFixture f = new GraphFixture();
        final int x = f.input(IntegerType.range(0, 100));
        final int root = f.keepAlive(f.imul(x, f.intConst(1)));
        f.canonicalize();
        Assert.assertFalse(f.isAlive(root), "x * 1 should be eliminated");
        Assert.assertEquals(x, f.returnValue(), "x * 1 should fold to x");
    }

    public void testAddSameBecomesMul2() {
        final GraphFixture f = new GraphFixture();
        final int x = f.input(IntegerType.range(0, 100));
        final int root = f.keepAlive(f.iadd(x, x));
        f.canonicalize();
        Assert.assertFalse(f.isAlive(root), "x + x should be rewritten");
        final int mul = f.returnValue();
        Assert.assertSame(MulINode.MUL_I, f.nodeAt(mul), "x + x should become x * 2");
        Assert.assertEquals(2, f.valueOperands(mul).size(), "mul operand count");
        Assert.assertEquals(x, f.valueOperands(mul).getInt(0), "mul lhs should be x");
        final int two = f.valueOperands(mul).getInt(1);
        Assert.assertTrue(f.nodeAt(two) instanceof IntConstantNode, "mul rhs should be a constant");
        Assert.assertEquals(2, ((IntConstantNode) f.nodeAt(two)).constant(), "mul rhs should be 2");
    }

    public void testTypePropagatesThroughCanonicalization() {
        final GraphFixture f = new GraphFixture();
        final int x = f.input(IntegerType.range(0, 10));
        final int y = f.input(IntegerType.range(0, 10));
        final int root = f.keepAlive(f.iadd(x, y));
        f.canonicalize();
        Assert.assertTrue(f.isAlive(root), "non-foldable add should survive");
        Assert.assertEquals(IntegerType.range(0, 20), f.typeOf(root), "add range type");
    }

    public void testDeadCodeElimination() {
        final GraphFixture f = new GraphFixture();
        final int orphan = f.intConst(5);
        final int root = f.keepAlive(f.intConst(1));
        f.canonicalize();
        Assert.assertFalse(f.isAlive(orphan), "orphan constant should be eliminated");
        Assert.assertTrue(f.isAlive(root), "kept-alive constant should survive");
    }

    public void testControlSpineIsNeverEliminated() {
        final GraphFixture f = new GraphFixture();
        f.canonicalize();
        Assert.assertTrue(f.isAlive(f.context().getProgramStart()), "START should survive");
        Assert.assertTrue(f.isAlive(f.context().getProgramEnd()), "END should survive");
        Assert.assertTrue(f.isAlive(f.entry()), "entry should survive");
        Assert.assertTrue(f.isAlive(f.returnNode()), "return should survive");
    }
}