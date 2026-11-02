package dev.caecorthus.sparkwitch.client.witchmaiden.skin;

import dev.caecorthus.sparkwitch.client.bedrock.BedrockBounds;
import dev.caecorthus.sparkwitch.client.bedrock.anim.BedrockAnimation;
import dev.caecorthus.sparkwitch.client.bedrock.geo.BedrockGeometry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * One loaded model of a skin (the Mini Wither body, or its skull), ready to draw: geometry, registered textures,
 * animations by name, the animations each state plays (in order, all layered), the aim bone and the bind-pose bounds
 * used for the auto-fit.
 * 皮肤中一个已加载、可直接绘制的模型（小凋灵本体或其头颅）：几何、已注册的贴图、按名字索引的动画、每种状态播放的动画
 * （按顺序全部叠加）、瞄准骨骼，以及用于自动缩放的绑定姿态包围盒。
 *
 * @param blessedTexture skull only: the Conscience skull's texture; null tints {@code texture} / 仅头颅：善良头颅贴图；
 *                       null 时给 {@code texture} 染色
 * @param crimsonTexture skull only: the crimson skull's texture; null tints / 仅头颅：赤首贴图；null 时染色
 * @param headBone       bone turned toward the last shot, -1 for none / 朝上一次射击方向转动的骨骼，-1 表示没有
 */
public record MiniWitherSkinModel(BedrockGeometry geometry, Identifier texture, @Nullable Identifier blessedTexture,
                                  @Nullable Identifier crimsonTexture, Map<String, BedrockAnimation> animations,
                                  Map<MiniWitherAnimationState, List<String>> states, int headBone,
                                  boolean translucent, BedrockBounds restBounds) {
}
