package dev.caecorthus.sparkwitch.client.savior;

import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Savior lightsaber stack that an entity's hand is drawing right now, so the model predicates can tell a hand render
 * from a GUI one. Item model predicates get no transformation mode, and GUI slots pass the local player as the entity,
 * so the hotbar, inventory and shop icons would otherwise look like the held saber. Set around Minecraft's
 * {@code ItemRenderer.renderItem(LivingEntity, ...)}, the path first- and third-person hand items take, by
 * {@code client/mixin/savior/SaviorLightsaberHandRenderMixin}; GUI slots, dropped items and item frames resolve their
 * model elsewhere and never see it. Render thread only.
 * 实体手部当前正在绘制的救世主光剑物品堆，让模型谓词能区分手部渲染与 GUI 渲染。物品模型谓词拿不到变换模式，而 GUI 格子
 * 会把本地玩家作为实体传入，否则快捷栏、物品栏与商店图标看起来都像手中的光剑。由
 * {@code client/mixin/savior/SaviorLightsaberHandRenderMixin} 包在 Minecraft 的
 * {@code ItemRenderer.renderItem(LivingEntity, ...)} 两侧设置，第一人称与第三人称手持物品都走这条路径；GUI 格子、掉落物与
 * 物品展示框在别处解析模型，不会看到它。仅渲染线程。
 */
public final class SaviorHandRenderScope {
    private static @Nullable ItemStack rendering;

    private SaviorHandRenderScope() {
    }

    /** Marks {@code stack} as drawn in a hand; returns the previous mark for {@link #exit}. / 标记手中绘制的物品堆，返回之前的标记。 */
    public static @Nullable ItemStack enter(ItemStack stack) {
        ItemStack previous = rendering;
        rendering = stack;
        return previous;
    }

    /** Restores the mark {@link #enter} returned. / 恢复 {@link #enter} 返回的标记。 */
    public static void exit(@Nullable ItemStack previous) {
        rendering = previous;
    }

    /** Whether this exact stack is being drawn in a hand. / 该物品堆本身是否正在手中绘制。 */
    public static boolean isRendering(ItemStack stack) {
        return rendering == stack;
    }

    /** Connection edges: drops a mark a crashed frame could have left. / 连接边界：丢弃崩溃帧可能遗留的标记。 */
    public static void reset() {
        rendering = null;
    }
}
