package dev.caecorthus.sparkwitch.client.bedrock.molang;

/**
 * A compiled Molang expression or statement list. Evaluation never throws: anything undefined is 0, and a NaN or
 * infinite result is the caller's to drop.
 * 已编译的 Molang 表达式或语句序列。求值从不抛出异常：未定义的一律为 0，NaN 或无穷结果由调用方丢弃。
 */
public interface MolangExpression {
    double evaluate(MolangRuntime runtime);

    /** True when the value never depends on the runtime (folded at compile time). / 值与运行时无关（编译期折叠）。 */
    default boolean isConstant() {
        return false;
    }

    static MolangExpression constant(double value) {
        return new MolangExpression() {
            @Override
            public double evaluate(MolangRuntime runtime) {
                return value;
            }

            @Override
            public boolean isConstant() {
                return true;
            }
        };
    }
}
