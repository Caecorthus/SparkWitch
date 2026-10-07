package dev.caecorthus.sparkwitch.client.seeker;

import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Client seam: while the owner's client is in a car or camera view, the owner's own body gets an always-on outline
 * ({@link SeekerRules#OWN_BODY_COLOR}), drawn by vanilla's entity outline pass and therefore visible through walls.
 * {@code SeekerRemoteWorldRendererMixin} already renders the body during the view; this listener only makes
 * {@code MinecraftClient#hasOutline} true for it and gives the colour. It never answers for another entity and
 * {@link SeekerRemoteViewClient#mode()} is NONE as soon as the view ends, so nothing is outlined outside the view and
 * no other client sees anything. {@code always(...)} is required because the instinct key is suppressed while viewing.
 * 客户端接缝：本地客户端处于小车或摄像头视角时，拥有者自己的本体获得常亮描边（{@link SeekerRules#OWN_BODY_COLOR}），
 * 由原版实体描边流程绘制，因此可以透墙看到。{@code SeekerRemoteWorldRendererMixin} 已在视角期间渲染本体；此监听器只让
 * {@code MinecraftClient#hasOutline} 对本体返回 true 并提供颜色。它从不对其他实体作答，视角结束后
 * {@link SeekerRemoteViewClient#mode()} 立即为 NONE，因此视角之外不描边，其他客户端也看不到任何东西。
 * 视角期间本能键被屏蔽，所以必须使用 {@code always(...)}。
 */
public final class SeekerBodyClientHooks {
    /**
     * Above every event result that could name the local player: {@code skip()} (100), suppression (102), the
     * 240-300 role outlines and invisibility skips, and the 1000 faction skip constants. Those rules keep other
     * players' positions secret; outlining your own body on your own client reveals nothing, so none of them should
     * hide it. Real vetoes run before the event (HEAD mixins for fear/obscure/swallow, the Wraith veto, SparkTraits'
     * Final Moment colour) and still apply.
     * 高于所有可能针对本地玩家的事件结果：{@code skip()}（100）、压制（102）、240-300 的职业描边与隐身跳过，以及 1000 的
     * 阵营跳过常量。这些规则是为了隐藏其他玩家的位置；在自己的客户端描边自己的本体不透露任何信息，因此不应被它们隐藏。
     * 真正的否决在事件之前执行（恐惧/障眼/吞噬的 HEAD mixin、冤魂否决、SparkTraits 终局时刻颜色），仍然生效。
     */
    public static final int HIGHLIGHT_PRIORITY = 2_000;
    private static boolean registered;

    private SeekerBodyClientHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(SeekerBodyClientHooks::sparkwitch$ownBodyHighlight);
    }

    @Nullable
    private static GetInstinctHighlight.HighlightResult sparkwitch$ownBodyHighlight(Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || !SparkWitchServerConnection.isConfirmedServer()) {
            return null;
        }
        return SeekerInstinctRules.outlinesOwnBody(SeekerRemoteViewClient.mode(), target == viewer,
                viewer.isSpectator())
                ? GetInstinctHighlight.HighlightResult.always(SeekerRules.OWN_BODY_COLOR, HIGHLIGHT_PRIORITY)
                : null;
    }
}
