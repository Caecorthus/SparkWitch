package dev.caecorthus.sparkwitch.client.compat;

import dev.caecorthus.sparkwitch.compat.OptionalStaticMethod;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Optional SparkTraits seam, client presentation only: whether SparkTraits' Depression grayscale is on the local
 * player's screen this frame, so a SparkWitch screen filter can yield to it (owner: Depression ignores the Otherworld
 * filter). SparkTraits draws that grayscale only for a non-creative, non-spectator Depression owner who is in its
 * psycho (which runs on Wathe's psycho timer) or below Wathe's mid mood; the facade has no grayscale query, so this
 * mirrors that rule from synced Wathe state and asks the public facade
 * {@code SparkTraitsApi.hasActiveTrait(PlayerEntity, Identifier)} only for the trait, never SparkTraits' internal
 * implementation or component packages. Last Escape and Wraith grayscales are SparkWitch's own compositor and are
 * answered by {@code BlackRavenPerceptionScreenEffects.isActiveFor}. Fails closed: absent SparkTraits, a missing method
 * or an exception read as "no grayscale", so the caller's own filter still draws.
 * 可选 SparkTraits 接缝，仅用于客户端展示：本帧本地玩家屏幕上是否有 SparkTraits 抑郁灰阶，以便 SparkWitch 画面滤镜
 * 让路（所有者决定：抑郁无视里世界滤镜）。SparkTraits 只为非创造、非旁观、处于抑郁疯魔（使用 Wathe 疯魔计时）或心情低于
 * Wathe 中等阈值的抑郁持有者绘制灰阶；门面没有灰阶查询，因此这里用同步的 Wathe 状态复刻该规则，只通过公共门面
 * {@code SparkTraitsApi.hasActiveTrait(PlayerEntity, Identifier)} 查询词条，从不触及 SparkTraits 的内部实现包或组件包。
 * 脱险与冤魂灰阶属于 SparkWitch 自己的合成器，由 {@code BlackRavenPerceptionScreenEffects.isActiveFor} 回答。
 * 失败关闭：未安装、方法缺失或抛出异常都视为“无灰阶”，调用方自己的滤镜照常绘制。
 */
public final class SparkTraitsDepressionVisionBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final Identifier DEPRESSION = Identifier.of(MOD_ID, "depression");
    private static final OptionalStaticMethod HAS_ACTIVE_TRAIT = OptionalStaticMethod.of(MOD_ID, API_CLASS,
            "hasActiveTrait", PlayerEntity.class, Identifier.class).returning(boolean.class);

    private SparkTraitsDepressionVisionBridge() {
    }

    /**
     * Render thread, local player. The cheap synced mood/psycho gate runs first, so the reflective trait query only
     * runs while a grayscale could actually be drawn.
     * 渲染线程、本地玩家。先做开销很小的同步心情/疯魔判断，只有可能绘制灰阶时才执行反射词条查询。
     */
    public static boolean drawsDepressionGrayscale(@Nullable PlayerEntity player) {
        if (player == null || player.isCreative() || player.isSpectator()) {
            return false;
        }
        if (PlayerPsychoComponent.KEY.get(player).getPsychoTicks() <= 0
                && PlayerMoodComponent.KEY.get(player).getMood() >= GameConstants.MID_MOOD_THRESHOLD) {
            return false;
        }
        return HAS_ACTIVE_TRAIT.callBoolean(false, false, false, player, DEPRESSION);
    }
}
