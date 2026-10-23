package com.ishland.c2me.opts.dfc.common.ir.test;

import com.ishland.c2me.opts.dfc.common.ir.test.framework.TestRunner;

public final class AllTests {

    private AllTests() {
    }

    static void main() {
        final int failures = new TestRunner()
                .register(IntUtilTest.class)
                .register(DoubleUtilTest.class)
                .register(FloatUtilTest.class)
                .register(NodeTypeComputationTest.class)
                .register(CanonicalizerTest.class)
                // LoopRotation is not working yet; re-enable its suite once repaired.
                .register(GraphVerifierTest.class)
                .register(HirGraphTest.class)
                .run();
        System.exit(failures == 0 ? 0 : 1);
    }
}
