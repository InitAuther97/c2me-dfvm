package com.ishland.c2me.opts.dfc.common.ir;

import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelArgumentNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelBeginNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelDescriptorNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelInvokeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.PackedVolumeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.border.SliceNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ReturnNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant.FloatConstantNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant.IntConstantNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.AddFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.DivFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.MaxFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.MinFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.MulFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.SubFPNode;
import net.minecraft.util.math.Direction;
import net.minecraft.world.gen.densityfunction.BinaryOperationDensityFunction;
import net.minecraft.world.gen.densityfunction.CacheDensityFunction;
import net.minecraft.world.gen.densityfunction.ClampDensityFunction;
import net.minecraft.world.gen.densityfunction.ConstantDensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;
import net.minecraft.world.gen.densityfunction.SliceDensityFunction;
import net.minecraft.world.gen.densityfunction.UnaryOperationDensityFunction;

import java.util.IdentityHashMap;
import java.util.Map;

/// Lowers a vanilla {@link DensityFunction} tree into our IR. Simple nodes are
/// mapped into the enclosing kernel; border nodes (currently only slices) are
/// split: the wrapped density function is compiled into its own kernel and the
/// border node consumes a {@link KernelInvokeNode} at the split point.
///
/// Every generated kernel declares the nine sampling region fields of
/// {@link net.minecraft.util.math.noise.SamplingRegion} as i32
/// {@link KernelArgumentNode}s, in its record order (`sizeX` .. `stepBlockZ`),
/// and callers adapt their volume by packing matching expressions into a
/// {@link PackedVolumeNode}. The work-item coordinate is not a separate
/// input: a kernel is always invoked from within another kernel, so its
/// coordinate is derived from its own arguments (the packed volume's
/// origin), and coordinate-consuming nodes read it structurally from the
/// argument nodes. Arguments unused by a kernel are left for dead argument
/// elimination to prune. The root kernel is the first program defined on the
/// returned context.
///
/// Shared density function instances are mapped once per kernel; sharing
/// across a kernel boundary is intentionally not preserved because the
/// work-item space differs. Coordinate-consuming types (noise, gradients,
/// shifts, ...) are not supported yet only because no IR node exists for
/// them, and types requiring volume algebra the IR cannot express yet (e.g.
/// interpolation cell sizes needing a select for the aligned-cell edge case)
/// are not supported and throw {@link UnsupportedOperationException}.
public class AstToIR {

    private static final String[] ARGUMENT_NAMES = {
            "sizeX", "sizeY", "sizeZ",
            "minBlockX", "minBlockY", "minBlockZ",
            "stepBlockX", "stepBlockY", "stepBlockZ"
    };

    private static final int ARG_SIZE_X = 0;
    private static final int ARG_SIZE_Y = 1;
    private static final int ARG_SIZE_Z = 2;
    private static final int ARG_MIN_X = 3;
    private static final int ARG_MIN_Y = 4;
    private static final int ARG_MIN_Z = 5;

    public static GraphContext lower(DensityFunction function) {
        final AstToIR lowering = new AstToIR();
        lowering.buildKernel(function);
        return lowering.context;
    }

    private final GraphContext context = new GraphContext();
    private final IrGraph graph = context.getIrGraph();
    private int nextKernelId;

    private AstToIR() {
    }

    /// Builds one kernel for the given function and returns the callee's
    /// descriptor id.
    private int buildKernel(DensityFunction function) {
        final String name = "df/" + this.nextKernelId++ + ": " + function.getClass().getSimpleName();
        final int descriptor = graph.addNode(new KernelDescriptorNode(name));
        final int begin = graph.addNode(KernelBeginNode.BEGIN);
        final int ret = graph.addNode(ReturnNode.RET);
        graph.getMemory().appendEdge(descriptor, begin);
        graph.getControl().appendEdge(context.getProgramStart(), begin);
        graph.getControl().appendEdge(begin, ret);
        graph.getControl().appendEdge(ret, context.getProgramEnd());
        graph.defineProgram(begin, ret);

        final int[] arguments = new int[ARGUMENT_NAMES.length];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = graph.addNode(new KernelArgumentNode(ARGUMENT_NAMES[i]));
        }

        final int value = mapFunction(function, new IdentityHashMap<>(), arguments);
        graph.getValue().appendEdge(value, ret);
        return descriptor;
    }

    private int mapFunction(DensityFunction function, Map<DensityFunction, Integer> mapped, int[] arguments) {
        final Integer existing = mapped.get(function);
        if (existing != null) {
            return existing;
        }
        final int id = switch (function) {
            case DensityFunctionTypes.RegistryEntryHolder holder ->
                    mapFunction(holder.function().value(), mapped, arguments);
            case ConstantDensityFunction constant ->
                    addNode(new FloatConstantNode(constant.value()));
            case BinaryOperationDensityFunction binary ->
                    mapBinary(binary, mapped, arguments);
            case UnaryOperationDensityFunction unary ->
                    mapUnary(unary, mapped, arguments);
            case ClampDensityFunction clamp ->
                    mapClamp(clamp, mapped, arguments);
            case CacheDensityFunction cache ->
                    mapFunction(cache.input(), mapped, arguments);
            case SliceDensityFunction slice ->
                    mapSlice(slice, arguments);
            default -> throw new UnsupportedOperationException(
                    "Unsupported density function type: " + function.getClass().getName());
        };
        mapped.put(function, id);
        return id;
    }

    private int mapBinary(BinaryOperationDensityFunction binary, Map<DensityFunction, Integer> mapped, int[] arguments) {
        final int left = mapFunction(binary.left(), mapped, arguments);
        final int right = mapFunction(binary.right(), mapped, arguments);
        return switch (binary.type()) {
            case ADD -> addNode(AddFPNode.ADD_F, left, right);
            case SUB -> addNode(SubFPNode.SUB_F, left, right);
            case MUL -> addNode(MulFPNode.MUL_F, left, right);
            case DIV -> addNode(DivFPNode.DIV_F, left, right);
            case MIN -> addNode(MinFPNode.MIN_F, left, right);
            case MAX -> addNode(MaxFPNode.MAX_F, left, right);
        };
    }

    private int mapUnary(UnaryOperationDensityFunction unary, Map<DensityFunction, Integer> mapped, int[] arguments) {
        final int input = mapFunction(unary.input(), mapped, arguments);
        return switch (unary.type()) {
            case SQUARE -> addNode(MulFPNode.MUL_F, input, input);
            case CUBE -> addNode(MulFPNode.MUL_F, addNode(MulFPNode.MUL_F, input, input), input);
            default -> throw new UnsupportedOperationException(
                    "Unsupported unary operation: " + unary.type());
        };
    }

    private int mapClamp(ClampDensityFunction clamp, Map<DensityFunction, Integer> mapped, int[] arguments) {
        final int input = mapFunction(clamp.input(), mapped, arguments);
        final int min = addNode(new FloatConstantNode(clamp.min()));
        final int max = addNode(new FloatConstantNode(clamp.max()));
        return addNode(MinFPNode.MIN_F, addNode(MaxFPNode.MAX_F, input, min), max);
    }

    /// A slice splits its input into its own kernel: the callee's volume
    /// degenerates on the sliced axis to size 1 at the fixed coordinate, while
    /// every other field passes through unchanged. The step of the sliced
    /// axis stays whatever the caller has, matching vanilla where a size of 1
    /// makes it irrelevant.
    private int mapSlice(SliceDensityFunction slice, int[] arguments) {
        final int callee = buildKernel(slice.input());
        final int sizeIndex, minIndex;
        final byte axisFlag;
        switch (slice.axis()) {
            case X -> {
                sizeIndex = ARG_SIZE_X;
                minIndex = ARG_MIN_X;
                axisFlag = SliceNode.AXIS_X;
            }
            case Y -> {
                sizeIndex = ARG_SIZE_Y;
                minIndex = ARG_MIN_Y;
                axisFlag = SliceNode.AXIS_Y;
            }
            case Z -> {
                sizeIndex = ARG_SIZE_Z;
                minIndex = ARG_MIN_Z;
                axisFlag = SliceNode.AXIS_Z;
            }
            default -> throw new IllegalStateException("Unknown axis: " + slice.axis());
        }
        final int[] volume = arguments.clone();
        volume[sizeIndex] = addNode(new IntConstantNode(1));
        volume[minIndex] = addNode(new IntConstantNode(slice.coordinate()));

        final int packedVolume = addNode(PackedVolumeNode.INSTANCE, volume);
        final int invoke = graph.addNode(KernelInvokeNode.INVOKE);
        graph.getMemory().appendEdge(callee, invoke);
        graph.getMemory().appendEdge(packedVolume, invoke);
        return addNode(new SliceNode(
                axisFlag,
                slice.axis() == Direction.Axis.X ? slice.coordinate() : 0,
                slice.axis() == Direction.Axis.Y ? slice.coordinate() : 0,
                slice.axis() == Direction.Axis.Z ? slice.coordinate() : 0
        ), invoke);
    }

    private int addNode(IrNode node, int... valueOperands) {
        final int id = graph.addNode(node);
        for (final int operand : valueOperands) {
            graph.getValue().appendEdge(operand, id);
        }
        return id;
    }
}