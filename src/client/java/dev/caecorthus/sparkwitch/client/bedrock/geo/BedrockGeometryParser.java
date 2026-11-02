package dev.caecorthus.sparkwitch.client.bedrock.geo;

import com.google.gson.JsonElement;
import dev.caecorthus.sparkwitch.client.bedrock.BedrockModelException;
import org.jetbrains.annotations.Nullable;

/**
 * Reads Bedrock geometry JSON from a player's skin folder. Never trusts the file: limits, NaN checks and readable
 * {@link BedrockModelException} messages.
 * 从玩家皮肤文件夹读取 Bedrock 几何 JSON。从不信任文件内容：有数量上限、NaN 检查，并给出可读的
 * {@link BedrockModelException} 消息。
 */
public final class BedrockGeometryParser {
    public static final int MAX_BONES = 2048;
    public static final int MAX_CUBES = 16384;

    private BedrockGeometryParser() {
    }

    /** Whether {@code root} holds Bedrock geometry at its top level (also inside a Java item-model shell). */
    public static boolean isGeometryFile(JsonElement root) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * @param identifier which geometry to take when the file has several ({@code description.identifier}); null takes
     *                   the first / 文件含多个几何时选用哪个；null 取第一个
     * @param glowPrefix bones whose own name or an ancestor's name starts with it (ignoring case) draw full-bright;
     *                   empty disables / 自身或祖先名字以此开头（忽略大小写）的骨骼全亮绘制；空字符串表示关闭
     */
    public static BedrockGeometry parse(JsonElement root, @Nullable String identifier, String glowPrefix)
            throws BedrockModelException {
        throw new UnsupportedOperationException("TODO");
    }
}
