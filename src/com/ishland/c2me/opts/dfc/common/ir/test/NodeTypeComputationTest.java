package com.ishland.c2me.opts.dfc.common.ir.test;

import com.ishland.c2me.opts.dfc.common.ir.type.DoubleType;
import com.ishland.c2me.opts.dfc.common.ir.type.FloatType;
import com.ishland.c2me.opts.dfc.common.ir.type.IntegerType;
import com.ishland.c2me.opts.dfc.common.ir.test.framework.Assert;

/// Verifies that operator nodes compute their result type from the operand
/// types stored in the graph, without involving the canonicalizer.

@SuppressWarnings("unused")
public class NodeTypeComputationTest {

    public void testIntAddConstants() {
        final GraphFixture f = new GraphFixture();
        final int add = f.iadd(f.intConst(1), f.intConst(2));
        Assert.assertEquals(IntegerType.constant(3), f.computeType(add), "1 + 2");
    }

    public void testIntAddRanges() {
        final GraphFixture f = new GraphFixture();
        final int x = f.input(IntegerType.range(0, 10));
        final int y = f.input(IntegerType.range(0, 10));
        Assert.assertEquals(IntegerType.range(0, 20), f.computeType(f.iadd(x, y)), "range add");
    }

    public void testIntMulOverflowWidensToBot() {
        final GraphFixture f = new GraphFixture();
        final int mul = f.imul(f.intConst(Integer.MAX_VALUE), f.intConst(2));
        Assert.assertSame(IntegerType.BOT, f.computeType(mul), "overflowing mul");
    }

    public void testIntDivByZeroConstantWidensToBot() {
        final GraphFixture f = new GraphFixture();
        final int div = f.idiv(f.intConst(1), f.intConst(0));
        Assert.assertSame(IntegerType.BOT, f.computeType(div), "div by zero constant");
    }

    public void testIntUndeterminedInputYieldsTop() {
        final GraphFixture f = new GraphFixture();
        final int x = f.input(IntegerType.TOP);
        Assert.assertSame(IntegerType.TOP, f.computeType(f.iadd(x, f.intConst(1))), "add with TOP input");
        Assert.assertSame(IntegerType.TOP, f.computeType(f.isub(f.intConst(1), x)), "sub with TOP input");
        Assert.assertSame(IntegerType.TOP, f.computeType(f.imul(x, x)), "mul with TOP input");
        Assert.assertSame(IntegerType.TOP, f.computeType(f.idiv(f.intConst(1), x)), "div with TOP input");
    }

    public void testDoubleAddConstants() {
        final GraphFixture f = new GraphFixture();
        final int add = f.dadd(f.doubleConst(1.5), f.doubleConst(2.25));
        Assert.assertEquals(DoubleType.constant(3.75), f.computeType(add), "1.5 + 2.25");
    }

    public void testDoubleDivByZeroConstantWidensToAll() {
        final GraphFixture f = new GraphFixture();
        final int div = f.ddiv(f.doubleConst(1.0), f.doubleConst(0.0));
        Assert.assertSame(DoubleType.ALL, f.computeType(div), "div by zero constant");
    }

    public void testDoubleSqrt() {
        final GraphFixture f = new GraphFixture();
        Assert.assertEquals(DoubleType.constant(2.0),
                f.computeType(f.dsqrt(f.doubleConst(4.0))), "sqrt(4)");
        Assert.assertSame(DoubleType.ALL,
                f.computeType(f.dsqrt(f.doubleConst(-1.0))), "sqrt(-1)");
    }

    public void testDoubleMinMax() {
        final GraphFixture f = new GraphFixture();
        final int a = f.doubleConst(1.0), b = f.doubleConst(2.0);
        Assert.assertEquals(DoubleType.constant(1.0), f.computeType(f.dmin(a, b)), "min(1, 2)");
        Assert.assertEquals(DoubleType.constant(2.0), f.computeType(f.dmax(a, b)), "max(1, 2)");
    }

    public void testDoubleUndeterminedInputYieldsTop() {
        final GraphFixture f = new GraphFixture();
        final int x = f.input(DoubleType.TOP);
        Assert.assertSame(DoubleType.TOP, f.computeType(f.dadd(x, f.doubleConst(1.0))), "add with TOP input");
        Assert.assertSame(DoubleType.TOP, f.computeType(f.dsqrt(x)), "sqrt with TOP input");
    }

    public void testFloatAddRanges() {
        final GraphFixture f = new GraphFixture();
        final int x = f.input(FloatType.range(1.0f, 5.0f));
        final int y = f.input(FloatType.range(3.0f, 7.0f));
        Assert.assertEquals(FloatType.range(4.0f, 12.0f), f.computeType(f.fadd(x, y)), "range add");
    }

    public void testFloatSqrt() {
        final GraphFixture f = new GraphFixture();
        final int x = f.input(FloatType.range(1.0f, 4.0f));
        Assert.assertEquals(FloatType.range(1.0f, 2.0f), f.computeType(f.fsqrt(x)), "sqrt([1, 4])");
    }

    public void testFloatUndeterminedInputYieldsTop() {
        final GraphFixture f = new GraphFixture();
        final int x = f.input(FloatType.TOP);
        Assert.assertSame(FloatType.TOP, f.computeType(f.fmul(x, f.input(FloatType.ALL))), "mul with TOP input");
    }

    public void testInitialTypesAreTop() {
        final GraphFixture f = new GraphFixture();
        final int a = f.doubleConst(1.0), b = f.doubleConst(2.0);
        final int c = f.intConst(1), d = f.intConst(2);
        Assert.assertSame(DoubleType.TOP, f.typeOf(f.dadd(a, b)), "ADD_D initial type");
        Assert.assertSame(DoubleType.TOP, f.typeOf(f.dsqrt(a)), "SQRT_D initial type");
        Assert.assertSame(FloatType.TOP, f.typeOf(f.fdiv(f.input(FloatType.ALL), f.input(FloatType.ALL))),
                "DIV_F initial type");
        Assert.assertSame(IntegerType.TOP, f.typeOf(f.isub(c, d)), "SUB_I initial type");
    }
}