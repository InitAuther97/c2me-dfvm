package com.ishland.c2me.opts.dfc.common.ir.stamp;

import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelRootNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelNode;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.flowsched.util.Assertions;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/// Metadata of a kernel implemented outside this graph: a foreign kernel has
/// only a {@link KernelNode}, no {@link KernelRootNode} or value DAG.
/// Calls into it are emitted as calls into the foreign target, a
/// native C function or a Java method.
///
/// The runtime parameters are supplied by each call in the declared order;
/// the constants are compile-time arguments baked into the descriptor (for
/// example the xzScale, yScale and noise parameter table of a noise kernel).
/// The foreign target receives the sources `[runtimeParameters...,
/// constants...]` arranged by `argumentPermutation`: `argumentPermutation[j]`
/// is the source feeding the foreign function's parameter `j`.
public record ForeignKernelStamp(
        Kind kind,
        String target,
        List<Parameter> runtimeParameters,
        List<Constant> constants,
        int[] argumentPermutation
) {

    public ForeignKernelStamp {
        runtimeParameters = List.copyOf(runtimeParameters);
        constants = List.copyOf(constants);
        argumentPermutation = argumentPermutation.clone();
        final int sources = runtimeParameters.size() + constants.size();
        for (final int source : argumentPermutation) {
            Assertions.assertTrue(source >= 0 && source < sources,
                    "argument permutation entry " + source + " out of source range [0, " + sources + ")");
        }
    }

    /// A foreign kernel whose argument permutation is the identity.
    public ForeignKernelStamp(Kind kind, String target, List<Parameter> runtimeParameters, List<Constant> constants) {
        this(kind, target, runtimeParameters, constants,
                identity(runtimeParameters.size() + constants.size()));
    }

    private static int[] identity(int size) {
        final int[] permutation = new int[size];
        for (int i = 0; i < size; i++) {
            permutation[i] = i;
        }
        return permutation;
    }

    /// A runtime parameter supplied by each call.
    public record Parameter(String name, IrType type) {
    }

    /// A compile-time constant argument baked into the descriptor.
    public record Constant(String name, Object value) {
    }

    public enum Kind {
        /// A native function implemented in C.
        C_FUNCTION,
        /// A Java method.
        JAVA_METHOD
    }

    // The record-generated equals/hashCode compare the permutation array by
    // identity; compare it structurally so equivalent metadata deduplicates.
    @Override
    public boolean equals(Object o) {
        return o instanceof ForeignKernelStamp other
                && kind == other.kind
                && Objects.equals(target, other.target)
                && runtimeParameters.equals(other.runtimeParameters)
                && constants.equals(other.constants)
                && Arrays.equals(argumentPermutation, other.argumentPermutation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, target, runtimeParameters, constants) * 31
                + Arrays.hashCode(argumentPermutation);
    }
}
