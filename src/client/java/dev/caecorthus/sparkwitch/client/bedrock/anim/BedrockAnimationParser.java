package dev.caecorthus.sparkwitch.client.bedrock.anim;

import com.google.gson.JsonElement;
import dev.caecorthus.sparkwitch.client.bedrock.BedrockModelException;

import java.util.Map;

/**
 * Reads a Bedrock {@code .animation.json}: every entry of {@code animations}, keyed by its full name, in file order.
 * 读取 Bedrock {@code .animation.json}：按文件顺序返回 {@code animations} 中的每一项，以完整名字为键。
 */
public final class BedrockAnimationParser {
    private BedrockAnimationParser() {
    }

    public static Map<String, BedrockAnimation> parse(JsonElement root) throws BedrockModelException {
        throw new UnsupportedOperationException("TODO");
    }
}
