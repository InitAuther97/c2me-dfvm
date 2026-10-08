package com.ishland.c2me.opts.dfc.common.ir.test;

import com.ishland.c2me.opts.dfc.common.ir.type.mir.IntegerType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.IntUtil;
import com.ishland.c2me.opts.dfc.common.ir.test.framework.Assert;

@SuppressWarnings("unused")
public class IntUtilTest {

    public void testAdd() {
        Assert.assertEquals(IntegerType.range(4, 12),
                IntUtil.add(IntegerType.range(1, 5), IntegerType.range(3, 7)), "add");
        Assert.assertEquals(IntegerType.constant(3),
                IntUtil.add(IntegerType.constant(1), IntegerType.constant(2)), "add constants");
    }

    public void testAddOverflowWidensToBot() {
        Assert.assertSame(IntegerType.BOT,
                IntUtil.add(IntegerType.range(0, Integer.MAX_VALUE), IntegerType.range(0, 1)), "add overflow");
        Assert.assertSame(IntegerType.BOT,
                IntUtil.add(IntegerType.range(Integer.MIN_VALUE, 0), IntegerType.range(-1, 0)), "add underflow");
    }

    public void testSub() {
        Assert.assertEquals(IntegerType.range(-6, 2),
                IntUtil.sub(IntegerType.range(1, 5), IntegerType.range(3, 7)), "sub");
        Assert.assertSame(IntegerType.BOT,
                IntUtil.sub(IntegerType.range(Integer.MIN_VALUE, 0), IntegerType.constant(1)), "sub underflow");
    }

    public void testMul() {
        Assert.assertEquals(IntegerType.range(3, 35),
                IntUtil.mul(IntegerType.range(1, 5), IntegerType.range(3, 7)), "mul same sign");
        // corners: (-2)(-4)=8, (-2)(5)=-10, (3)(-4)=-12, (3)(5)=15
        Assert.assertEquals(IntegerType.range(-12, 15),
                IntUtil.mul(IntegerType.range(-2, 3), IntegerType.range(-4, 5)), "mul sign crossing");
        Assert.assertSame(IntegerType.BOT,
                IntUtil.mul(IntegerType.range(0, Integer.MAX_VALUE), IntegerType.constant(2)), "mul overflow");
    }

    public void testDiv() {
        Assert.assertEquals(IntegerType.range(2, 6),
                IntUtil.div(IntegerType.range(6, 12), IntegerType.range(2, 3)), "div");
        Assert.assertEquals(IntegerType.range(-6, -2),
                IntUtil.div(IntegerType.range(6, 12), IntegerType.range(-3, -2)), "div negative divisor");
        Assert.assertEquals(IntegerType.constant(-3),
                IntUtil.div(IntegerType.constant(7), IntegerType.constant(-2)), "div truncates toward zero");
    }

    public void testDivByZeroContainingRangeWidensToBot() {
        Assert.assertSame(IntegerType.BOT,
                IntUtil.div(IntegerType.range(1, 2), IntegerType.range(-1, 1)), "div crossing zero");
        Assert.assertSame(IntegerType.BOT,
                IntUtil.div(IntegerType.range(1, 2), IntegerType.constant(0)), "div by zero constant");
    }

    public void testDivMinValueByMinusOneWidensToBot() {
        // Integer.MIN_VALUE / -1 overflows int
        Assert.assertSame(IntegerType.BOT,
                IntUtil.div(IntegerType.constant(Integer.MIN_VALUE), IntegerType.constant(-1)), "div overflow");
    }

    public void testTopDelaysEvaluation() {
        final IntegerType determined = IntegerType.range(1, 2);
        Assert.assertSame(IntegerType.TOP, IntUtil.add(IntegerType.TOP, determined), "add top");
        Assert.assertSame(IntegerType.TOP, IntUtil.sub(determined, IntegerType.TOP), "sub top");
        Assert.assertSame(IntegerType.TOP, IntUtil.mul(IntegerType.TOP, determined), "mul top");
        Assert.assertSame(IntegerType.TOP, IntUtil.div(determined, IntegerType.TOP), "div top");
    }
}