package com.ishland.c2me.opts.dfc.common.ir.type;

public class BranchType extends IrType implements ControlLike {

    public static final BranchType TOP = new BranchType((byte) 0);
    public static final BranchType TRUE = new BranchType((byte) 1);
    public static final BranchType FALSE = new BranchType((byte) 2);
    public static final BranchType BOT = new BranchType((byte) 3);

    private static final BranchType[] TYPES = new BranchType[]{TOP, TRUE, FALSE, BOT};

    private static final byte TYPE_BRANCH = TypeRegistry.register(BranchType.class);

    public static BranchType forIf(BooleanType type) {
        return TYPES[type.getData()];
    }

    public static BranchType forMerge(ControlType type1, ControlType type2) {
        final int i1 = type1.isAlive() ? 1 : 0;
        final int i2 = type2.isAlive() ? 1 : 0;
        return TYPES[i1 << 1 | i2];
    }

    private final byte branch;

    private BranchType(byte branch) {
        super(TYPE_BRANCH);
        this.branch = branch;
    }

    public byte getBranch() {
        return branch;
    }

    public boolean isConstant() {
        return this.branch == 1 || this.branch == 2;
    }

    public boolean isTrue() {
        return this.branch == 1;
    }

    @Override
    protected IrType xmeet(IrType another) {
        return TYPES[this.branch | ((BranchType)another).branch];
    }

    @Override
    protected IrType dual() {
        return TYPES[~this.branch & 3];
    }

    @Override
    public boolean isDetermined() {
        return branch != 0;
    }

    @Override
    protected boolean isSubType(IrType another) {
        final var that = (BranchType) another;
        return this.branch == 0 || that.branch == 3 || this.branch == that.branch;
    }

    @Override
    protected int hash() {
        return 17 * Byte.hashCode(this.branch);
    }

    @Override
    public IrType top() {
        return TOP;
    }

    @Override
    public ControlType toControl() {
        return this.branch == 0 ? ControlType.DEAD_CONTROL : ControlType.CONTROL;
    }
}
