package com.ishland.c2me.opts.dfc.common.ir.test;

import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.FloatUtil;
import com.ishland.c2me.opts.dfc.common.ir.test.framework.Assert;

@SuppressWarnings("unused")
public class FloatUtilTest {

    public void testAdd() {
        Assert.assertEquals(FloatType.range(4.0f, 12.0f),
                FloatUtil.add(FloatType.range(1.0f, 5.0f), FloatType.range(3.0f, 7.0f)), "add");
    }

    public void testAddOverflowRetainsInfinity() {
        Assert.assertEquals(FloatType.constant(Float.POSITIVE_INFINITY),
                FloatUtil.add(FloatType.constant(Float.MAX_VALUE), FloatType.constant(Float.MAX_VALUE)),
                "add overflow");
    }

    public void testSub() {
        Assert.assertEquals(FloatType.range(-6.0f, 2.0f),
                FloatUtil.sub(FloatType.range(1.0f, 5.0f), FloatType.range(3.0f, 7.0f)), "sub");
    }

    public void testMul() {
        Assert.assertEquals(FloatType.range(-12.0f, 15.0f),
                FloatUtil.mul(FloatType.range(-2.0f, 3.0f), FloatType.range(-4.0f, 5.0f)), "mul sign crossing");
    }

    public void testDiv() {
        Assert.assertEquals(FloatType.range(0.25f, 1.0f),
                FloatUtil.div(FloatType.range(1.0f, 2.0f), FloatType.range(2.0f, 4.0f)), "div");
        Assert.assertSame(FloatType.ALL,
                FloatUtil.div(FloatType.range(1.0f, 2.0f), FloatType.range(-1.0f, 1.0f)), "div crossing zero");
    }

    public void testSqrt() {
        Assert.assertEquals(FloatType.range(1.0f, 2.0f),
                FloatUtil.sqrt(FloatType.range(1.0f, 4.0f)), "sqrt positive");
        Assert.assertSame(FloatType.ALL,
                FloatUtil.sqrt(FloatType.range(-1.0f, 4.0f)), "sqrt possibly negative");
    }

    public void testMinMax() {
        Assert.assertEquals(FloatType.range(1.0f, 5.0f),
                FloatUtil.min(FloatType.range(1.0f, 5.0f), FloatType.range(3.0f, 7.0f)), "min");
        Assert.assertEquals(FloatType.range(3.0f, 7.0f),
                FloatUtil.max(FloatType.range(1.0f, 5.0f), FloatType.range(3.0f, 7.0f)), "max");
    }

    public void testTopDelaysEvaluation() {
        final FloatType top = FloatType.range(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
        final FloatType determined = FloatType.range(1.0f, 2.0f);
        Assert.assertSame(FloatType.TOP, FloatUtil.add(top, determined), "add top");
        Assert.assertSame(FloatType.TOP, FloatUtil.sub(determined, top), "sub top");
        Assert.assertSame(FloatType.TOP, FloatUtil.mul(top, determined), "mul top");
        Assert.assertSame(FloatType.TOP, FloatUtil.div(determined, top), "div top");
        Assert.assertSame(FloatType.TOP, FloatUtil.sqrt(top), "sqrt top");
        Assert.assertSame(FloatType.TOP, FloatUtil.min(top, determined), "min top");
        Assert.assertSame(FloatType.TOP, FloatUtil.max(determined, top), "max top");
    }
}
