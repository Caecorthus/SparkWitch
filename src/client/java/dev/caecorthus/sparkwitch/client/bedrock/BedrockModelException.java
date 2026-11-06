package dev.caecorthus.sparkwitch.client.bedrock;

/**
 * A player-supplied Bedrock file (geometry, animation or Molang) that cannot be used. The message is short plain
 * English naming the file part at fault (bone, cube, animation, keyframe), because the skin settings screen shows it.
 * 玩家提供的 Bedrock 文件（几何、动画或 Molang）无法使用。消息为简短英文并指出出错的位置（骨骼、方块、动画、关键帧），
 * 因为皮肤设置界面会直接显示它。
 */
public class BedrockModelException extends Exception {
    public BedrockModelException(String message) {
        super(message);
    }

    public BedrockModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
