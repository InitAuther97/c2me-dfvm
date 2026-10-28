package com.ishland.c2me.opts.dfc.common.ir;

import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.FindFirstNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelArgumentNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelRootNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.group.GroupNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.volume.DensityVolumeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.volume.VolumeUtil;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelInvokeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.PackedVolumeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.value.LerpNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.value.TestFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.value.TestINode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant.DoubleConstantNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant.FloatConstantNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant.IntConstantNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.AddFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.AddINode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.DivFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.DivINode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.FPConvertNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.MaxFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.MaxINode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.MinFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.MinINode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.ModINode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.MulFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.MulINode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.RoundFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.SubFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.SubINode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.cond.SelectNode;
import net.minecraft.util.math.Direction;
import net.minecraft.world.gen.densityfunction.BinaryOperationDensityFunction;
import net.minecraft.world.gen.densityfunction.CacheDensityFunction;
import net.minecraft.world.gen.densityfunction.ClampDensityFunction;
import net.minecraft.world.gen.densityfunction.ConstantDensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;
import net.minecraft.world.gen.densityfunction.FindTopSurfaceDensityFunction;
import net.minecraft.world.gen.densityfunction.GradientDensityFunction;
import net.minecraft.world.gen.densityfunction.InterpolatedDensityFunction;
import net.minecraft.world.gen.densityfunction.LerpDensityFunction;
import net.minecraft.world.gen.densityfunction.SliceDensityFunction;
import net.minecraft.world.gen.densityfunction.UnaryOperationDensityFunction;

import java.util.IdentityHashMap;
import java.util.Map;

/// Lowers a vanilla {@link DensityFunction} tree into our IR.
///
/// Value types map to ordinary IR operations, and borders have no dedicated
/// node types: a split is marked solely by a {@link KernelInvokeNode}, which
/// samples the callee at one explicit work item — a degenerate volume whose
/// origin is the mapped coordinate, derived from the enclosing kernel's
/// arguments. A slice is a single invocation with one substituted
/// coordinate; an interpolation samples the eight corners of the lane's
/// containing cell with eight invocations and combines them with vanilla's
/// lerp3. The invocation lowering batches invokes of the same callee into one
/// volume fill with per-lane loads; bit-exactness with vanilla's bulk fill
/// accumulation is intentionally dropped for interpolation.
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
/// elimination to prune. Each kernel is a pure value DAG attached to a
/// KernelRootNode; the root kernel is the first root under START. Executable
/// control flow is introduced only during MIR lowering. Descriptors consume their
/// symbolic sampling volume, derived from a non-numbered group marker.
///
/// Kernels may also declare custom formats: a predicate kernel used by
/// {@link FindFirstNode} takes `(x, y, z)` and returns whether the density is
/// positive; the scan forwards captured coordinates through the node's
/// constant argument permutation. Shared density function instances are
/// mapped once per kernel; sharing across a kernel boundary is intentionally
/// not preserved because the work-item space differs. Coordinate-consuming
/// types (noise, gradients, shifts, ...) are not supported yet only because
/// no IR node exists for them; unsupported types throw
/// {@link UnsupportedOperationException}.
public class AstToIR {

    private static final String[] ARGUMENT_NAMES = {
            "sizeX", "sizeY", "sizeZ",
            "minBlockX", "minBlockY", "minBlockZ",
            "stepBlockX", "stepBlockY", "stepBlockZ"
    };

    private static final int ARG_MIN_X = 3;
    private static final int ARG_MIN_Y = 4;
    private static final int ARG_MIN_Z = 5;

    /// The work-item coordinate of the kernel being mapped: coordinate-
    /// consuming types read these nodes instead of assuming an argument
    /// layout, so kernels can declare custom formats.
    private record Coordinates(int x, int y, int z, int volume) {
    }

    private record Kernel(int descriptor, int root) {
    }

    public static GraphContext lower(DensityFunction function) {
        final AstToIR lowering = new AstToIR();
        final int group = lowering.graph.addNode(GroupNode.TAG);
        final int volume = lowering.graph.addNode(DensityVolumeNode.UNKNOWN);
        lowering.graph.getMemory().appendEdge(group, volume);
        lowering.buildKernel(function, volume);
        return lowering.context;
    }

    private final GraphContext context = new GraphContext();
    private final IrGraph graph = context.getIrGraph();
    private int nextKernelId;

    private AstToIR() {
    }

    /// Builds a standard density kernel with the volume format and returns
    /// the callee's descriptor id.
    private int buildKernel(DensityFunction function, int volume) {
        final Kernel kernel = createKernel("df/" + this.nextKernelId++ + ": " + function.getClass().getSimpleName(), volume);
        final int[] arguments = new int[ARGUMENT_NAMES.length];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = addArgument(kernel, ARGUMENT_NAMES[i]);
        }
        final int value = mapFunction(function, new IdentityHashMap<>(),
                new Coordinates(arguments[ARG_MIN_X], arguments[ARG_MIN_Y], arguments[ARG_MIN_Z], volume));
        finishKernel(kernel, value);
        return kernel.descriptor();
    }

    /// Builds a boolean predicate kernel over the given density function,
    /// returning whether it is positive. The callee format is `(x, y, z)`, so
    /// a caller can feed it captured coordinates through the argument
    /// permutation of a {@link FindFirstNode}.
    private int buildPredicateKernel(DensityFunction function, int volume) {
        final Kernel kernel = createKernel("predicate/" + this.nextKernelId++ + ": " + function.getClass().getSimpleName(), volume);
        final int x = addArgument(kernel, "x");
        final int y = addArgument(kernel, "y");
        final int z = addArgument(kernel, "z");
        final int value = mapFunction(function, new IdentityHashMap<>(), new Coordinates(x, y, z, volume));
        final int predicate = addNode(TestFPNode.MIN_INCLUSIVE_MAX_INCLUSIVE,
                addNode(FPConvertNode.F2D, value),
                addNode(new DoubleConstantNode(Math.nextUp(0.0))),
                addNode(new DoubleConstantNode(Double.MAX_VALUE)));
        finishKernel(kernel, predicate);
        return kernel.descriptor();
    }

    private Kernel createKernel(String name, int volume) {
        final int descriptor = graph.addNode(new KernelNode(name));
        graph.getMemory().appendEdge(volume, descriptor);
        final int root = graph.addNode(KernelRootNode.ROOT);
        graph.getMemory().appendEdge(descriptor, root);
        graph.getControl().appendEdge(context.getProgramStart(), root);
        graph.getControl().appendEdge(root, context.getProgramEnd());
        return new Kernel(descriptor, root);
    }

    private int addArgument(Kernel kernel, String name) {
        final int argument = graph.addNode(new KernelArgumentNode(name));
        graph.getMemory().appendEdge(kernel.descriptor(), argument);
        return argument;
    }

    private void finishKernel(Kernel kernel, int value) {
        graph.getValue().appendEdge(value, kernel.root());
    }

    private int mapFunction(DensityFunction function, Map<DensityFunction, Integer> mapped, Coordinates coordinates) {
        final Integer existing = mapped.get(function);
        if (existing != null) {
            return existing;
        }
        final int id = switch (function) {
            case DensityFunctionTypes.RegistryEntryHolder holder ->
                    mapFunction(holder.function().value(), mapped, coordinates);
            case ConstantDensityFunction constant ->
                    addNode(new FloatConstantNode(constant.value()));
            case BinaryOperationDensityFunction binary ->
                    mapBinary(binary, mapped, coordinates);
            case UnaryOperationDensityFunction unary ->
                    mapUnary(unary, mapped, coordinates);
            case ClampDensityFunction clamp ->
                    mapClamp(clamp, mapped, coordinates);
            case GradientDensityFunction gradient ->
                    mapGradient(gradient, coordinates);
            case CacheDensityFunction cache ->
                    mapFunction(cache.input(), mapped, coordinates);
            case LerpDensityFunction lerp ->
                    mapLerp(lerp, mapped, coordinates);
            case InterpolatedDensityFunction interpolated ->
                    mapInterpolated(interpolated, coordinates);
            case SliceDensityFunction slice ->
                    mapSlice(slice, coordinates);
            case FindTopSurfaceDensityFunction findTopSurface ->
                    mapFindTopSurface(findTopSurface, coordinates);
            default -> throw new UnsupportedOperationException(
                    "Unsupported density function type: " + function.getClass().getName());
        };
        mapped.put(function, id);
        return id;
    }

    private int mapBinary(BinaryOperationDensityFunction binary, Map<DensityFunction, Integer> mapped, Coordinates coordinates) {
        final int left = mapFunction(binary.left(), mapped, coordinates);
        final int right = mapFunction(binary.right(), mapped, coordinates);
        return switch (binary.type()) {
            case ADD -> addNode(AddFPNode.ADD_F, left, right);
            case SUB -> addNode(SubFPNode.SUB_F, left, right);
            case MUL -> addNode(MulFPNode.MUL_F, left, right);
            case DIV -> addNode(DivFPNode.DIV_F, left, right);
            case MIN -> addNode(MinFPNode.MIN_F, left, right);
            case MAX -> addNode(MaxFPNode.MAX_F, left, right);
        };
    }

    private int mapUnary(UnaryOperationDensityFunction unary, Map<DensityFunction, Integer> mapped, Coordinates coordinates) {
        final int input = mapFunction(unary.input(), mapped, coordinates);
        return switch (unary.type()) {
            case SQUARE -> addNode(MulFPNode.MUL_F, input, input);
            case CUBE -> addNode(MulFPNode.MUL_F, addNode(MulFPNode.MUL_F, input, input), input);
            default -> throw new UnsupportedOperationException(
                    "Unsupported unary operation: " + unary.type());
        };
    }

    private int mapClamp(ClampDensityFunction clamp, Map<DensityFunction, Integer> mapped, Coordinates coordinates) {
        final int input = mapFunction(clamp.input(), mapped, coordinates);
        final int min = addNode(new FloatConstantNode(clamp.min()));
        final int max = addNode(new FloatConstantNode(clamp.max()));
        return addNode(MinFPNode.MIN_F, addNode(MaxFPNode.MAX_F, input, min), max);
    }

    /// Mirrors vanilla's compiled gradient sampler: `coordinateFactor` is
    /// computed once from the endpoints, then the tiled (clamped, for
    /// CLAMP_TO_EDGE) relative coordinate is multiplied by it and offset by
    /// `fromValue`.
    private int mapGradient(GradientDensityFunction gradient, Coordinates coordinates) {
        final int coordinate = switch (gradient.axis()) {
            case X -> coordinates.x();
            case Y -> coordinates.y();
            case Z -> coordinates.z();
        };
        final int from = gradient.fromCoordinate();
        final int to = gradient.toCoordinate();
        final float factor = (gradient.toValue() - gradient.fromValue()) / (to - from);
        final int local = switch (gradient.tiling()) {
            case CLAMP_TO_EDGE -> {
                final int clamped = addNode(MinINode.MIN_I,
                        addNode(MaxINode.MAX_I, coordinate,
                                addNode(new IntConstantNode(Math.min(from, to)))),
                        addNode(new IntConstantNode(Math.max(from, to))));
                yield addNode(SubINode.SUB_I, clamped, addNode(new IntConstantNode(from)));
            }
            case REPEAT -> addNode(ModINode.MOD_FLOOR,
                    addNode(SubINode.SUB_I, coordinate, addNode(new IntConstantNode(from))),
                    addNode(new IntConstantNode(to - from)));
            case MIRRORED_REPEAT -> {
                final int relative = addNode(SubINode.SUB_I, coordinate, addNode(new IntConstantNode(from)));
                final int range = addNode(new IntConstantNode(to - from));
                final int tileIndex = addNode(DivINode.DIV_FLOOR, relative, range);
                final int localCoordinate = addNode(SubINode.SUB_I, relative,
                        addNode(MulINode.MUL_I, tileIndex, range));
                final int reversed = addNode(SubINode.SUB_I, range, localCoordinate);
                final int parity = addNode(ModINode.MOD_TRUNC, tileIndex, addNode(new IntConstantNode(2)));
                final int even = addNode(TestINode.MIN_INCLUSIVE_MAX_INCLUSIVE, parity,
                        addNode(new IntConstantNode(0)), addNode(new IntConstantNode(0)));
                yield addNode(SelectNode.SELECT, even, localCoordinate, reversed);
            }
        };
        return addNode(AddFPNode.ADD_F,
                addNode(new FloatConstantNode(gradient.fromValue())),
                addNode(MulFPNode.MUL_F,
                        addNode(FPConvertNode.I2F, local),
                        addNode(new FloatConstantNode(factor))));
    }

    /// A lerp blends first and second by alpha, matching MathHelper.lerp:
    /// `first + alpha * (second - first)`, with the sampler's exact
    /// `alpha == 1` fast path. The `alpha == 0` fast path is not modeled. The
    /// optimizer demotes the exact variant when `1.0F` cannot occur.
    private int mapLerp(LerpDensityFunction lerp, Map<DensityFunction, Integer> mapped, Coordinates coordinates) {
        final int alpha = mapFunction(lerp.alpha(), mapped, coordinates);
        final int first = mapFunction(lerp.first(), mapped, coordinates);
        final int second = mapFunction(lerp.second(), mapped, coordinates);
        return addNode(LerpNode.LERP_EXACT, alpha, first, second);
    }

    /// A slice substitutes one axis of the lane's coordinate and samples the
    /// input there: a single per-item invocation, with no combine step.
    private int mapSlice(SliceDensityFunction slice, Coordinates coordinates) {
        final int callee = buildKernel(slice.input(),
                VolumeUtil.slice(graph, coordinates.volume(), slice.axis(), slice.coordinate()));
        final int one = addNode(new IntConstantNode(1));
        final int coordinate = addNode(new IntConstantNode(slice.coordinate()));
        final int x = slice.axis() == Direction.Axis.X ? coordinate : coordinates.x();
        final int y = slice.axis() == Direction.Axis.Y ? coordinate : coordinates.y();
        final int z = slice.axis() == Direction.Axis.Z ? coordinate : coordinates.z();
        return invokeAt(callee, x, y, z, one);
    }

    /// An interpolator samples the input at the eight corners of the lane's
    /// containing cell and interpolates them with vanilla's lerp3. Each corner
    /// is a per-item invocation (the cell origin and the in-cell position are
    /// derived from the lane's coordinate); batching these invocations into a
    /// single volume fill is the invocation lowering's job.
    private int mapInterpolated(InterpolatedDensityFunction interpolated, Coordinates coordinates) {
        final int callee = buildKernel(interpolated.input(),
                VolumeUtil.interpolate(graph, coordinates.volume(), interpolated.cellSizeXz(), interpolated.cellSizeY()));
        final int cellSizeXzNode = addNode(new IntConstantNode(interpolated.cellSizeXz()));
        final int cellSizeYNode = addNode(new IntConstantNode(interpolated.cellSizeY()));
        final int one = addNode(new IntConstantNode(1));

        final int xInCell = addNode(ModINode.MOD_FLOOR, coordinates.x(), cellSizeXzNode);
        final int yInCell = addNode(ModINode.MOD_FLOOR, coordinates.y(), cellSizeYNode);
        final int zInCell = addNode(ModINode.MOD_FLOOR, coordinates.z(), cellSizeXzNode);
        final int cellX = addNode(SubINode.SUB_I, coordinates.x(), xInCell);
        final int cellY = addNode(SubINode.SUB_I, coordinates.y(), yInCell);
        final int cellZ = addNode(SubINode.SUB_I, coordinates.z(), zInCell);
        final int nextX = addNode(AddINode.ADD_I, cellX, cellSizeXzNode);
        final int nextY = addNode(AddINode.ADD_I, cellY, cellSizeYNode);
        final int nextZ = addNode(AddINode.ADD_I, cellZ, cellSizeXzNode);

        final int alphaX = floatDivide(xInCell, interpolated.cellSizeXz());
        final int alphaY = floatDivide(yInCell, interpolated.cellSizeY());
        final int alphaZ = floatDivide(zInCell, interpolated.cellSizeXz());

        final int v000 = invokeAt(callee, cellX, cellY, cellZ, one);
        final int v100 = invokeAt(callee, nextX, cellY, cellZ, one);
        final int v010 = invokeAt(callee, cellX, nextY, cellZ, one);
        final int v110 = invokeAt(callee, nextX, nextY, cellZ, one);
        final int v001 = invokeAt(callee, cellX, cellY, nextZ, one);
        final int v101 = invokeAt(callee, nextX, cellY, nextZ, one);
        final int v011 = invokeAt(callee, cellX, nextY, nextZ, one);
        final int v111 = invokeAt(callee, nextX, nextY, nextZ, one);

        // MathHelper.lerp3: lerp(alphaZ, lerp2(alphaX, alphaY, lower),
        // lerp2(alphaX, alphaY, upper))
        final int lower = addNode(LerpNode.LERP, alphaY,
                addNode(LerpNode.LERP, alphaX, v000, v100),
                addNode(LerpNode.LERP, alphaX, v010, v110));
        final int upper = addNode(LerpNode.LERP, alphaY,
                addNode(LerpNode.LERP, alphaX, v001, v101),
                addNode(LerpNode.LERP, alphaX, v011, v111));
        return addNode(LerpNode.LERP, alphaZ, lower, upper);
    }

    /// FindTopSurface samples the upper bound at (x, 0, z) (vanilla wraps the
    /// compiled sampler in a Y-slice at 0), floors it down onto the cell grid
    /// and scans from there toward lowerBound in cellHeight steps for the
    /// first position where the density is positive, falling back to
    /// lowerBound. The descent is expressed as a negative step; when the
    /// floored upper bound is at or below lowerBound the scan is empty and
    /// {@link FindFirstNode} returns lowerBound without probing, matching
    /// vanilla. The predicate kernel receives the captured coordinates
    /// through the argument permutation (x, y, z).
    private int mapFindTopSurface(FindTopSurfaceDensityFunction function, Coordinates coordinates) {
        final int predicate = buildPredicateKernel(function.density(),
                VolumeUtil.findTopSurface(graph, coordinates.volume()));
        final int upperCallee = buildKernel(function.upperBound(),
                VolumeUtil.slice(graph, coordinates.volume(), Direction.Axis.Y, 0));
        final int cellHeight = function.cellHeight();
        final int one = addNode(new IntConstantNode(1));
        final int upperBound = invokeAt(upperCallee, coordinates.x(),
                addNode(new IntConstantNode(0)), coordinates.z(), one);
        // topY = floor(upperBound / cellHeight) * cellHeight
        final int topY = addNode(MulINode.MUL_I,
                addNode(FPConvertNode.F2I, addNode(RoundFPNode.FLOOR_F,
                        addNode(DivFPNode.DIV_F, upperBound, addNode(new FloatConstantNode(cellHeight))))),
                addNode(new IntConstantNode(cellHeight)));
        final int findFirst = addNode(new FindFirstNode(new int[]{1, 0, 2}),
                addNode(new IntConstantNode(function.lowerBound())),
                topY,
                addNode(new IntConstantNode(-cellHeight)),
                coordinates.x(),
                coordinates.z());
        graph.getMemory().appendEdge(predicate, findFirst);
        return addNode(FPConvertNode.I2F, findFirst);
    }

    /// Samples the callee at one explicit work item: a degenerate volume whose
    /// minimum is the coordinate.
    private int invokeAt(int callee, int x, int y, int z, int one) {
        final int packedVolume = addNode(PackedVolumeNode.INSTANCE,
                one, one, one,
                x, y, z,
                one, one, one);
        final int invoke = graph.addNode(KernelInvokeNode.INVOKE);
        graph.getMemory().appendEdge(callee, invoke);
        graph.getMemory().appendEdge(packedVolume, invoke);
        return invoke;
    }

    /// `(float) value / cellSize`, matching vanilla's float division of the
    /// converted operands.
    private int floatDivide(int value, int cellSize) {
        return addNode(DivFPNode.DIV_F,
                addNode(FPConvertNode.I2F, value),
                addNode(new FloatConstantNode(cellSize)));
    }

    private int addNode(IrNode node, int... valueOperands) {
        final int id = graph.addNode(node);
        for (final int operand : valueOperands) {
            graph.getValue().appendEdge(operand, id);
        }
        return id;
    }
}
