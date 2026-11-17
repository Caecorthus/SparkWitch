package dev.caecorthus.sparkwitch.client.sphinxhao;

import dev.caecorthus.sparkwitch.client.render.LocalViewerSnapshot;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoRules;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoWorldComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * 豪气冲天's crawlers are invisible to instinct for the window (owner spec 2026-11-17, Tono Hanna's float pattern): no
 * viewer's instinct outlines a crawler, through walls or in plain sight. The crawler set and end tick are synced to
 * every client ({@link SphinxHaoWorldComponent#isCrawling}), so this is a pure read against the client world clock;
 * the server alone opens, ends and releases a window.
 * <p>Exemptions: the crawler's own client, a living Sphinx Hao viewer (coordinator 2026-11-17: the caster keeps its
 * gold outline of its infected crawlers during the window; enemies still cannot see them), and, mirrored from
 * {@code HannaInstinctVeto}, viewers who may see spectator information (spectator or creative AND not a living
 * participant). Living participants in spectator mode (a Rift Gate
 * occupant, a feign-dead Fiend) keep their living instinct and are vetoed like anyone else. Fail closed: an unconfirmed
 * server, a missing viewer or a missing component never vetoes. Applied only by the {@code getInstinctHighlight} wrap
 * {@code sphinxhao.SphinxHaoInstinctVetoMixin}; vanilla GLOWING and other non-instinct outlines are left alone.
 * 豪气冲天的陆泳者在窗口期间无法被本能透视（所有者规格 2026-11-17，沿用远野汉娜浮游的做法）：任何观察者的本能都描不出陆泳者的
 * 轮廓，无论隔墙还是直视。陆泳者集合与结束刻同步给每个客户端（{@link SphinxHaoWorldComponent#isCrawling}），因此这里只是依据客户端
 * 世界时钟的读取；窗口的开启、结束与释放只由服务端决定。
 * <p>豁免：陆泳者本人的客户端、存活的狮身豪观察者（协调者 2026-11-17：施法者在窗口期间仍能以金色描边看到其被感染的陆泳者；敌人
 * 依然看不到），以及沿用 {@code HannaInstinctVeto} 的可查看旁观信息的观察者（旁观或创造模式，且不是存活参与者）。
 * 处于旁观模式的存活参与者（传送门内的玩家、假死的魔人）仍保有存活时的本能，与其他人一样被否决。失败即关闭：服务端未确认、无观察者
 * 或组件缺失时从不否决。仅由 {@code getInstinctHighlight} 包装 {@code sphinxhao.SphinxHaoInstinctVetoMixin} 执行；原版发光与其他
 * 非本能描边不受影响。
 */
public final class SphinxHaoInstinctVeto {
    private SphinxHaoInstinctVeto() {
    }

    /**
     * Whether {@code viewer}'s instinct must not highlight {@code target}. Asked for every rendered entity every frame:
     * the type test and the window test (a field read when no window runs) come first, and the viewer facts (memoized
     * per world pass by {@link LocalViewerSnapshot}) are read only for a crawling player.
     * 观察者的本能是否不得高亮目标。每帧对每个渲染实体调用：先做类型判断与窗口判断（没有窗口时只是一次字段读取），只有目标正在
     * 陆泳时才读取观察者事实（由 {@link LocalViewerSnapshot} 按世界 pass 缓存）。
     */
    public static boolean hides(@Nullable PlayerEntity viewer, @Nullable Entity target) {
        if (viewer == null || !(target instanceof PlayerEntity targetPlayer) || targetPlayer == viewer) {
            return false;
        }
        World world = targetPlayer.getWorld();
        SphinxHaoWorldComponent window = world == null ? null : SphinxHaoWorldComponent.KEY.getNullable(world);
        if (window == null || !window.isCrawling(targetPlayer.getUuid(), world.getTime())) {
            return false;
        }
        boolean viewerAlive = LocalViewerSnapshot.playingAndAlive(viewer);
        return vetoes(
                SparkWitchServerConnection.isConfirmedServer(),
                false,
                seesSpectatorInformation(LocalViewerSnapshot.spectatingOrCreative(viewer), viewerAlive),
                viewerAlive && SphinxHaoRules.isSphinxHao(LocalViewerSnapshot.role(viewer)),
                true
        );
    }

    /** Pure exemption: spectator or creative, and not a living participant. / 纯豁免判定：旁观或创造模式，且不是存活参与者。 */
    public static boolean seesSpectatorInformation(boolean viewerSpectatingOrCreative, boolean viewerPlayingAndAlive) {
        return viewerSpectatingOrCreative && !viewerPlayingAndAlive;
    }

    /**
     * Pure veto decision; {@code viewerLivingSphinxHao} is a playing, alive viewer whose raw role is the Sphinx Hao.
     * 纯否决判定；{@code viewerLivingSphinxHao} 指真实职业为狮身豪且参与、存活的观察者。
     */
    public static boolean vetoes(boolean confirmedServer, boolean self, boolean viewerSeesSpectatorInformation,
                                 boolean viewerLivingSphinxHao, boolean targetCrawling) {
        return confirmedServer && !self && !viewerSeesSpectatorInformation && !viewerLivingSphinxHao && targetCrawling;
    }
}
