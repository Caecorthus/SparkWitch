package dev.caecorthus.sparkwitch.client.bedrock.molang;

import dev.caecorthus.sparkwitch.client.bedrock.BedrockModelException;

/**
 * A Molang source the compiler rejects. The message names the 1-based column and quotes the source, because the skin
 * settings screen shows it as is.
 * 编译器拒绝的 Molang 源码。消息包含从 1 开始的列号并引用源码，因为皮肤设置界面会原样显示它。
 */
public final class MolangSyntaxException extends BedrockModelException {
    private static final int SNIPPET_LIMIT = 80;

    private final int position;

    MolangSyntaxException(String source, int position, String detail) {
        super("Molang syntax error at column " + (position + 1) + ": " + detail + " in \"" + snippet(source) + "\"");
        this.position = position;
    }

    /** 0-based character index of the offending token. / 出错记号的字符下标（从 0 开始）。 */
    public int position() {
        return position;
    }

    private static String snippet(String source) {
        return source.length() <= SNIPPET_LIMIT ? source : source.substring(0, SNIPPET_LIMIT) + "...";
    }
}
