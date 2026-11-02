package dev.caecorthus.sparkwitch.client.bedrock.molang;

import dev.caecorthus.sparkwitch.client.bedrock.BedrockModelException;

/**
 * Compiles the Molang subset that Blockbench and YSM animations use. See the plan for the supported grammar.
 * 编译 Blockbench 与 YSM 动画使用的 Molang 子集。支持的语法见计划文档。
 */
public final class MolangCompiler {
    private MolangCompiler() {
    }

    public static MolangExpression compile(String source) throws BedrockModelException {
        throw new UnsupportedOperationException("TODO");
    }
}
