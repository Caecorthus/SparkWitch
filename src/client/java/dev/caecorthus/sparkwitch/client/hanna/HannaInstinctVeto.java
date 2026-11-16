package dev.caecorthus.sparkwitch.client.hanna;

import dev.caecorthus.sparkwitch.client.render.LocalViewerSnapshot;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Hanna's float is invisible to instinct (owner 2026-11-11): while a player floats, no viewer's instinct outlines them,
 * through walls or in plain sight. The float's remaining ticks are synced to every client
 * ({@link HannaComponent#isFloating()}), so this is a pure read; the server alone starts and ends a float, and no role
 * check is needed here.
 * <p>Exemptions, mirrored from {@code BlackRavenInstinctVeto}: the floating player's own client, and viewers who may
 * see spectator information (Wathe's {@code canSeeSpectatorInformation}: spectator or creative AND not a living
 * participant, i.e. dead spectators, spectating non-participants, creative admins), who see everything as before.
 * Living participants in spectator mode (a Rift Gate occupant, a feign-dead Fiend) keep their living instinct and are
 * vetoed like anyone else. Fail closed: an unconfirmed server, a missing viewer or a missing component never vetoes.
 * Applied by the {@code getInstinctHighlight} wrap {@code hanna.HannaInstinctVetoMixin} only; vanilla GLOWING and other
 * non-instinct outlines are left alone.
 * 汉娜的浮游无法被本能透视（所有者 2026-11-11）：玩家浮游期间，任何观察者的本能都描不出其轮廓，无论隔墙还是直视。浮游剩余刻
 * 同步给每个客户端（{@link HannaComponent#isFloating()}），因此这里只是读取；浮游的开始与结束只由服务端决定，此处无需判断职业。
 * <p>豁免沿用 {@code BlackRavenInstinctVeto}：浮游者本人的客户端，以及可查看旁观信息的观察者（Wathe 的
 * {@code canSeeSpectatorInformation}：旁观或创造模式，且不是存活的参与者，即死亡旁观者、旁观中的非参与者、创造模式管理员），
 * 他们照旧看到一切。处于旁观模式的存活参与者（传送门内的玩家、假死的魔人）仍保有存活时的本能，与其他人一样被否决。失败即关闭：
 * 服务端未确认、无观察者或组件缺失时从不否决。仅由 {@code getInstinctHighlight} 包装 {@code hanna.HannaInstinctVetoMixin} 执行；
 * 原版发光与其他非本能描边不受影响。
 */
public final class HannaInstinctVeto {
    private HannaInstinctVeto() {
    }

    /**
     * Whether {@code viewer}'s instinct must not highlight {@code target}. Asked for every rendered entity every frame:
     * the type test and one component lookup come first, and the viewer facts (memoized per world pass by
     * {@link LocalViewerSnapshot}) are read only for a floating player.
     * 观察者的本能是否不得高亮目标。每帧对每个渲染实体调用：先做类型判断与一次组件查询，只有目标正在浮游时才读取观察者事实
     * （由 {@link LocalViewerSnapshot} 按世界 pass 缓存）。
     */
    public static boolean hides(@Nullable PlayerEntity viewer, @Nullable Entity target) {
        if (viewer == null || !(target instanceof PlayerEntity targetPlayer) || targetPlayer == viewer) {
            return false;
        }
        HannaComponent state = HannaComponent.KEY.getNullable(targetPlayer);
        if (state == null || !state.isFloating()) {
            return false;
        }
        return vetoes(
                SparkWitchServerConnection.isConfirmedServer(),
                false,
                seesSpectatorInformation(LocalViewerSnapshot.spectatingOrCreative(viewer),
                        LocalViewerSnapshot.playingAndAlive(viewer)),
                true
        );
    }

    /** Pure exemption: spectator or creative, and not a living participant. / 纯豁免判定：旁观或创造模式，且不是存活参与者。 */
    public static boolean seesSpectatorInformation(boolean viewerSpectatingOrCreative, boolean viewerPlayingAndAlive) {
        return viewerSpectatingOrCreative && !viewerPlayingAndAlive;
    }

    /** Pure veto decision. / 纯否决判定。 */
    public static boolean vetoes(boolean confirmedServer, boolean self, boolean viewerSeesSpectatorInformation,
                                 boolean targetFloating) {
        return confirmedServer && !self && !viewerSeesSpectatorInformation && targetFloating;
    }
}
