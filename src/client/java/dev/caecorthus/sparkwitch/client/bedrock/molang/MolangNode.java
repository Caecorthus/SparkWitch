package dev.caecorthus.sparkwitch.client.bedrock.molang;

import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

/**
 * Compiled Molang syntax tree. Nodes are immutable and shareable across runtimes; {@link #eval} never allocates and
 * never throws. {@link #height} bounds evaluation recursion so a hostile file cannot overflow the render thread stack.
 * 编译后的 Molang 语法树。节点不可变，可在多个运行时间共享；{@link #eval} 从不分配内存也从不抛出异常。{@link #height}
 * 限制求值递归深度，防止恶意文件撑爆渲染线程栈。
 */
abstract class MolangNode {
    final int height;

    MolangNode(int height) {
        this.height = height;
    }

    abstract double eval(MolangRuntime rt);

    /** Pure and fully known at compile time; {@code eval(null)} is then safe. / 纯函数且编译期可知。 */
    boolean isConstant() {
        return false;
    }

    /** Whether {@code ??} takes this value rather than its fallback. / ?? 是否采用该值而非备选值。 */
    boolean isResolvable(MolangRuntime rt) {
        return true;
    }

    static int heightOf(MolangNode a) {
        return a.height + 1;
    }

    static int heightOf(MolangNode a, MolangNode b) {
        return Math.max(a.height, b.height) + 1;
    }

    static int heightOf(MolangNode a, MolangNode b, MolangNode c) {
        return Math.max(a.height, Math.max(b.height, c.height)) + 1;
    }

    static double bool(boolean value) {
        return value ? 1.0 : 0.0;
    }

    @FunctionalInterface
    interface DoubleTernaryOperator {
        double apply(double a, double b, double c);
    }

    static final class Const extends MolangNode {
        final double value;

        Const(double value) {
            super(1);
            this.value = value;
        }

        @Override
        double eval(MolangRuntime rt) {
            return value;
        }

        @Override
        boolean isConstant() {
            return true;
        }
    }

    /**
     * An unknown or unsupported name ({@code context.*}, {@code this}, {@code ysm.*}, queries with arguments): reads
     * 0 and makes {@code ??} take its fallback.
     * 未知或不支持的名字（context.*、this、ysm.*、带参数的查询）：读作 0，并让 ?? 采用备选值。
     */
    static final class Undefined extends MolangNode {
        static final Undefined INSTANCE = new Undefined();

        private Undefined() {
            super(1);
        }

        @Override
        double eval(MolangRuntime rt) {
            return 0.0;
        }

        @Override
        boolean isConstant() {
            return true;
        }

        @Override
        boolean isResolvable(MolangRuntime rt) {
            return false;
        }
    }

    static final class QueryRead extends MolangNode {
        private final String name;

        QueryRead(String name) {
            super(1);
            this.name = name;
        }

        @Override
        double eval(MolangRuntime rt) {
            return rt.queryValue(name);
        }

        @Override
        boolean isResolvable(MolangRuntime rt) {
            return rt.hasQuery(name);
        }
    }

    static final class VariableRead extends MolangNode {
        final String name;

        VariableRead(String name) {
            super(1);
            this.name = name;
        }

        @Override
        double eval(MolangRuntime rt) {
            return rt.variableValue(name);
        }

        @Override
        boolean isResolvable(MolangRuntime rt) {
            return rt.hasVariable(name);
        }
    }

    static final class TempRead extends MolangNode {
        final String name;

        TempRead(String name) {
            super(1);
            this.name = name;
        }

        @Override
        double eval(MolangRuntime rt) {
            return rt.tempValue(name);
        }

        @Override
        boolean isResolvable(MolangRuntime rt) {
            return rt.hasTemp(name);
        }
    }

    static final class VariableAssign extends MolangNode {
        private final String name;
        private final MolangNode value;

        VariableAssign(String name, MolangNode value) {
            super(heightOf(value));
            this.name = name;
            this.value = value;
        }

        @Override
        double eval(MolangRuntime rt) {
            double v = value.eval(rt);
            rt.storeVariable(name, v);
            return v;
        }
    }

    static final class TempAssign extends MolangNode {
        private final String name;
        private final MolangNode value;

        TempAssign(String name, MolangNode value) {
            super(heightOf(value));
            this.name = name;
            this.value = value;
        }

        @Override
        double eval(MolangRuntime rt) {
            double v = value.eval(rt);
            rt.storeTemp(name, v);
            return v;
        }
    }

    static final class Negate extends MolangNode {
        private final MolangNode operand;

        Negate(MolangNode operand) {
            super(heightOf(operand));
            this.operand = operand;
        }

        @Override
        double eval(MolangRuntime rt) {
            return -operand.eval(rt);
        }

        @Override
        boolean isConstant() {
            return operand.isConstant();
        }
    }

    static final class Not extends MolangNode {
        private final MolangNode operand;

        Not(MolangNode operand) {
            super(heightOf(operand));
            this.operand = operand;
        }

        @Override
        double eval(MolangRuntime rt) {
            return bool(operand.eval(rt) == 0.0);
        }

        @Override
        boolean isConstant() {
            return operand.isConstant();
        }
    }

    /** Arithmetic and comparison; comparisons yield 1.0/0.0. / 算术与比较；比较结果为 1.0/0.0。 */
    static final class Binary extends MolangNode {
        static final int ADD = 0;
        static final int SUB = 1;
        static final int MUL = 2;
        static final int DIV = 3;
        static final int LT = 4;
        static final int LE = 5;
        static final int GT = 6;
        static final int GE = 7;
        static final int EQ = 8;
        static final int NE = 9;

        private final int op;
        private final MolangNode left;
        private final MolangNode right;

        Binary(int op, MolangNode left, MolangNode right) {
            super(heightOf(left, right));
            this.op = op;
            this.left = left;
            this.right = right;
        }

        @Override
        double eval(MolangRuntime rt) {
            double a = left.eval(rt);
            double b = right.eval(rt);
            return switch (op) {
                case ADD -> a + b;
                case SUB -> a - b;
                case MUL -> a * b;
                // Java double semantics: x/0 is ±Infinity or NaN; the caller drops non-finite channel values.
                // Java 浮点语义：除以 0 得到 ±Infinity 或 NaN，由调用方丢弃非有限值。
                case DIV -> a / b;
                case LT -> bool(a < b);
                case LE -> bool(a <= b);
                case GT -> bool(a > b);
                case GE -> bool(a >= b);
                case EQ -> bool(a == b);
                default -> bool(a != b);
            };
        }

        @Override
        boolean isConstant() {
            return left.isConstant() && right.isConstant();
        }
    }

    static final class And extends MolangNode {
        private final MolangNode left;
        private final MolangNode right;

        And(MolangNode left, MolangNode right) {
            super(heightOf(left, right));
            this.left = left;
            this.right = right;
        }

        @Override
        double eval(MolangRuntime rt) {
            return bool(left.eval(rt) != 0.0 && right.eval(rt) != 0.0);
        }

        @Override
        boolean isConstant() {
            return left.isConstant() && right.isConstant();
        }
    }

    static final class Or extends MolangNode {
        private final MolangNode left;
        private final MolangNode right;

        Or(MolangNode left, MolangNode right) {
            super(heightOf(left, right));
            this.left = left;
            this.right = right;
        }

        @Override
        double eval(MolangRuntime rt) {
            return bool(left.eval(rt) != 0.0 || right.eval(rt) != 0.0);
        }

        @Override
        boolean isConstant() {
            return left.isConstant() && right.isConstant();
        }
    }

    /** {@code a ? b : c}; the binary form {@code a ? b} has a constant 0 else-branch. / 二元形式的 else 分支为 0。 */
    static final class Conditional extends MolangNode {
        private final MolangNode condition;
        private final MolangNode then;
        private final MolangNode otherwise;

        Conditional(MolangNode condition, MolangNode then, MolangNode otherwise) {
            super(heightOf(condition, then, otherwise));
            this.condition = condition;
            this.then = then;
            this.otherwise = otherwise;
        }

        @Override
        double eval(MolangRuntime rt) {
            return condition.eval(rt) != 0.0 ? then.eval(rt) : otherwise.eval(rt);
        }
    }

    static final class Coalesce extends MolangNode {
        private final MolangNode left;
        private final MolangNode right;

        Coalesce(MolangNode left, MolangNode right) {
            super(heightOf(left, right));
            this.left = left;
            this.right = right;
        }

        @Override
        double eval(MolangRuntime rt) {
            return left.isResolvable(rt) ? left.eval(rt) : right.eval(rt);
        }

        @Override
        boolean isResolvable(MolangRuntime rt) {
            return left.isResolvable(rt) || right.isResolvable(rt);
        }
    }

    static final class Call1 extends MolangNode {
        private final DoubleUnaryOperator function;
        private final MolangNode a;

        Call1(DoubleUnaryOperator function, MolangNode a) {
            super(heightOf(a));
            this.function = function;
            this.a = a;
        }

        @Override
        double eval(MolangRuntime rt) {
            return function.applyAsDouble(a.eval(rt));
        }

        @Override
        boolean isConstant() {
            return a.isConstant();
        }
    }

    static final class Call2 extends MolangNode {
        private final DoubleBinaryOperator function;
        private final MolangNode a;
        private final MolangNode b;

        Call2(DoubleBinaryOperator function, MolangNode a, MolangNode b) {
            super(heightOf(a, b));
            this.function = function;
            this.a = a;
            this.b = b;
        }

        @Override
        double eval(MolangRuntime rt) {
            return function.applyAsDouble(a.eval(rt), b.eval(rt));
        }

        @Override
        boolean isConstant() {
            return a.isConstant() && b.isConstant();
        }
    }

    static final class Call3 extends MolangNode {
        private final DoubleTernaryOperator function;
        private final MolangNode a;
        private final MolangNode b;
        private final MolangNode c;

        Call3(DoubleTernaryOperator function, MolangNode a, MolangNode b, MolangNode c) {
            super(heightOf(a, b, c));
            this.function = function;
            this.a = a;
            this.b = b;
            this.c = c;
        }

        @Override
        double eval(MolangRuntime rt) {
            return function.apply(a.eval(rt), b.eval(rt), c.eval(rt));
        }

        @Override
        boolean isConstant() {
            return a.isConstant() && b.isConstant() && c.isConstant();
        }
    }

    /**
     * {@code math.random}, {@code random_integer}, {@code die_roll}, {@code die_roll_integer}: never folded. Rolls are
     * capped at {@link #MAX_ROLLS} per call, because every animation of a state is evaluated every frame.
     * 随机函数：从不折叠。每次调用的掷骰次数上限为 {@link #MAX_ROLLS}，因为一个状态的所有动画每帧都会求值。
     */
    static final class Random extends MolangNode {
        static final int RANDOM = 0;
        static final int RANDOM_INTEGER = 1;
        static final int DIE_ROLL = 2;
        static final int DIE_ROLL_INTEGER = 3;
        static final int MAX_ROLLS = 64;

        private final int kind;
        private final MolangNode count;
        private final MolangNode low;
        private final MolangNode high;

        /** {@code count} is null for the two-argument forms. */
        Random(int kind, MolangNode count, MolangNode low, MolangNode high) {
            super(count == null ? heightOf(low, high) : heightOf(count, low, high));
            this.kind = kind;
            this.count = count;
            this.low = low;
            this.high = high;
        }

        @Override
        double eval(MolangRuntime rt) {
            double rolls = count == null ? 1.0 : count.eval(rt);
            double lo = low.eval(rt);
            double hi = high.eval(rt);
            if (kind == RANDOM || kind == RANDOM_INTEGER) {
                return roll(rt, lo, hi);
            }
            int n = rolls >= MAX_ROLLS ? MAX_ROLLS : rolls > 0.0 ? (int) rolls : 0;
            double sum = 0.0;
            for (int i = 0; i < n; i++) {
                sum += roll(rt, lo, hi);
            }
            return sum;
        }

        private double roll(MolangRuntime rt, double lo, double hi) {
            if (kind == RANDOM || kind == DIE_ROLL) {
                return lo + rt.nextRandom() * (hi - lo);
            }
            // Integers in [ceil(low), floor(high)], both inclusive (molangjs MathUtil.randomInt).
            // 取 [ceil(low), floor(high)] 内的整数，两端包含（同 molangjs MathUtil.randomInt）。
            double a = Math.ceil(lo);
            double b = Math.floor(hi);
            if (!(b > a)) {
                return a;
            }
            return a + Math.floor(rt.nextRandom() * (b - a + 1.0));
        }
    }

    static final class Return extends MolangNode {
        private final MolangNode value;

        Return(MolangNode value) {
            super(heightOf(value));
            this.value = value;
        }

        @Override
        double eval(MolangRuntime rt) {
            double v = value.eval(rt);
            rt.returning = true;
            rt.returnValue = v;
            return v;
        }
    }

    /**
     * A statement list ({@code a; b; return c;}) or a brace scope: 0 unless a {@code return} runs inside it, which
     * stops the list and propagates through enclosing lists via {@link MolangRuntime#returning}.
     * 语句序列或花括号作用域：除非其中执行了 return，否则值为 0；return 会终止序列并通过 returning 标记向外层传播。
     */
    static final class Statements extends MolangNode {
        private final MolangNode[] statements;

        Statements(MolangNode[] statements, int height) {
            super(height);
            this.statements = statements;
        }

        @Override
        double eval(MolangRuntime rt) {
            for (MolangNode statement : statements) {
                statement.eval(rt);
                if (rt.returning) {
                    return rt.returnValue;
                }
            }
            return 0.0;
        }
    }
}
