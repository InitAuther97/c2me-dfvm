package com.ishland.c2me.opts.dfc.common.ir.test;

import com.ishland.c2me.opts.dfc.common.ir.type.mir.DoubleType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.DoubleUtil;
import com.ishland.c2me.opts.dfc.common.ir.test.framework.Assert;

@SuppressWarnings("unused")
public class DoubleUtilTest {

    public void testAdd() {
        Assert.assertEquals(DoubleType.range(4.0, 12.0),
                DoubleUtil.add(DoubleType.range(1.0, 5.0), DoubleType.range(3.0, 7.0)), "add");
        Assert.assertEquals(DoubleType.constant(3.75),
                DoubleUtil.add(DoubleType.constant(1.5), DoubleType.constant(2.25)), "add constants");
    }

    public void testAddOverflowRetainsInfinity() {
        Assert.assertEquals(DoubleType.constant(Double.POSITIVE_INFINITY),
                DoubleUtil.add(DoubleType.constant(Double.MAX_VALUE), DoubleType.constant(Double.MAX_VALUE)),
                "add overflow");
    }

    public void testSub() {
        Assert.assertEquals(DoubleType.range(-6.0, 2.0),
                DoubleUtil.sub(DoubleType.range(1.0, 5.0), DoubleType.range(3.0, 7.0)), "sub");
    }

    public void testMul() {
        Assert.assertEquals(DoubleType.range(3.0, 35.0),
                DoubleUtil.mul(DoubleType.range(1.0, 5.0), DoubleType.range(3.0, 7.0)), "mul same sign");
        // corners: (-2)(-4)=8, (-2)(5)=-10, (3)(-4)=-12, (3)(5)=15
        Assert.assertEquals(DoubleType.range(-12.0, 15.0),
                DoubleUtil.mul(DoubleType.range(-2.0, 3.0), DoubleType.range(-4.0, 5.0)), "mul sign crossing");
    }

    public void testDiv() {
        Assert.assertEquals(DoubleType.range(0.25, 1.0),
                DoubleUtil.div(DoubleType.range(1.0, 2.0), DoubleType.range(2.0, 4.0)), "div");
        Assert.assertEquals(DoubleType.range(-6.0, -2.0),
                DoubleUtil.div(DoubleType.range(6.0, 12.0), DoubleType.range(-3.0, -2.0)), "div negative divisor");
    }

    public void testDivByZeroContainingRangeWidensToAll() {
        Assert.assertSame(DoubleType.ALL,
                DoubleUtil.div(DoubleType.range(1.0, 2.0), DoubleType.range(-1.0, 1.0)), "div crossing zero");
        Assert.assertSame(DoubleType.ALL,
                DoubleUtil.div(DoubleType.range(1.0, 2.0), DoubleType.constant(0.0)), "div by zero constant");
    }

    public void testSqrt() {
        Assert.assertEquals(DoubleType.range(0.0, 2.0),
                DoubleUtil.sqrt(DoubleType.range(0.0, 4.0)), "sqrt from zero");
        Assert.assertEquals(DoubleType.range(1.0, 2.0),
                DoubleUtil.sqrt(DoubleType.range(1.0, 4.0)), "sqrt positive");
        Assert.assertSame(DoubleType.ALL,
                DoubleUtil.sqrt(DoubleType.range(-1.0, 4.0)), "sqrt possibly negative");
    }

    public void testMinMax() {
        Assert.assertEquals(DoubleType.range(1.0, 5.0),
                DoubleUtil.min(DoubleType.range(1.0, 5.0), DoubleType.range(3.0, 7.0)), "min");
        Assert.assertEquals(DoubleType.range(3.0, 7.0),
                DoubleUtil.max(DoubleType.range(1.0, 5.0), DoubleType.range(3.0, 7.0)), "max");
    }

    public void testTopDelaysEvaluation() {
        final DoubleType top = DoubleType.range(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
        final DoubleType determined = DoubleType.range(1.0, 2.0);
        Assert.assertSame(DoubleType.TOP, DoubleUtil.add(top, determined), "add top");
        Assert.assertSame(DoubleType.TOP, DoubleUtil.sub(determined, top), "sub top");
        Assert.assertSame(DoubleType.TOP, DoubleUtil.mul(top, determined), "mul top");
        Assert.assertSame(DoubleType.TOP, DoubleUtil.div(determined, top), "div top");
        Assert.assertSame(DoubleType.TOP, DoubleUtil.sqrt(top), "sqrt top");
        Assert.assertSame(DoubleType.TOP, DoubleUtil.min(top, determined), "min top");
        Assert.assertSame(DoubleType.TOP, DoubleUtil.max(determined, top), "max top");
    }
}
