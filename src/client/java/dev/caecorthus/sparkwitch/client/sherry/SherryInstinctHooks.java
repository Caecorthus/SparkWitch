package dev.caecorthus.sparkwitch.client.sherry;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.OptionalStaticMethod;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryComponent;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Client seam: 橘雪莉's single Wathe {@link GetInstinctHighlight} listener. Only her own client answers, from the
 * owner-synced {@link SherryComponent}, so no other client can learn who she suspects or who carries her badge. Both
 * outlines are {@code always} results: Wathe shows them through walls without the instinct key, and the keyed-instinct
 * blockers (the Control Expert Disruptor, the SparkAssist instinct toggle) do not touch them. What still hides them:
 * the outermost Fear / Obscure / swallow veto and every HEAD veto on {@code WatheClient.getInstinctHighlight}, any
 * {@code skip()} (100) or higher answer, and the target gates of {@link #isShownTo}.
 * 客户端接缝：橘雪莉唯一的 Wathe {@link GetInstinctHighlight} 监听器。只有她自己的客户端作答，数据来自仅同步给本人的
 * {@link SherryComponent}，其他客户端无从得知她怀疑谁、谁持有她的徽章。两种描边都是 {@code always} 结果：Wathe 无需本能键
 * 即隔墙显示，按键本能的屏蔽（控场专家干扰器、SparkAssist 本能开关）不影响它们。仍会隐藏它们的有：最外层的恐惧 / 障眼 /
 * 吞噬否决与 {@code WatheClient.getInstinctHighlight} 上的所有 HEAD 否决、任何 {@code skip()}（100）及更高优先级的答复，
 * 以及 {@link #isShownTo} 的目标门槛。
 */
public final class SherryInstinctHooks {
    /**
     * External seam (optional SparkTraits): 橘雪莉's own copy of the public {@code SparkTraitsApi
     * .isInstinctHidden(viewer, target)} facade read, like the Seeker, Dominator and Savior outlines. An absent mod,
     * older build, wrong type or throwing call hides nothing (fail closed).
     * 外部接缝（可选 SparkTraits）：橘雪莉自有的一份公共 {@code SparkTraitsApi.isInstinctHidden(viewer, target)} 门面读取，
     * 与搜寻者、支配者、救世主的描边相同。模组缺失、旧版本、类型不符或调用抛异常时都不隐藏任何人（失败关闭）。
     */
    private static final OptionalStaticMethod IS_INSTINCT_HIDDEN = OptionalStaticMethod.of("sparktraits",
            "dev.caecorthus.sparktraits.api.SparkTraitsApi", "isInstinctHidden",
            PlayerEntity.class, PlayerEntity.class).returning(boolean.class);
    private static boolean registered;

    private SherryInstinctHooks() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(SherryInstinctHooks::highlight);
    }

    /**
     * Wathe listener; {@code null} means "no opinion". The component read comes first: it is empty on every client
     * but hers, so this per-entity, per-frame path leaves at once for everyone else.
     * Wathe 监听器；{@code null} 表示不作答。先读组件：除她本人外所有客户端的组件都是空的，这条逐实体、逐帧的路径对其他人
     * 立即返回。
     */
    private static @Nullable GetInstinctHighlight.HighlightResult highlight(Entity target) {
        if (!(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || targetPlayer == viewer) {
            return null;
        }
        SherryComponent component = SherryComponent.KEY.get(viewer);
        UUID uuid = targetPlayer.getUuid();
        Integer color = SherryHighlightRules.outlineColor(component.isRevealed(uuid), component.isBadgeHolder(uuid));
        if (color == null || !SherryClient.isActiveSherry(viewer) || !isShownTo(viewer, targetPlayer)) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.always(color, SherryHighlightRules.OUTLINE_PRIORITY);
    }

    /**
     * The target gates of the other always-on outlines (Seeker mark, Dominator mark, Savior domain): a living
     * participant who is not an alive spectator (Rift occupant) or creative, not invisible or invisible to the viewer,
     * not swallowed by a Taotie and not hidden from the viewer's instinct by SparkTraits. A failing target gets no
     * answer, so Wathe's own rules decide it exactly as without her.
     * 与其他常亮描边（搜寻者标记、支配者标记、救世主领域）相同的目标门槛：存活的参与者，不是存活旁观者（裂隙门内）或创造
     * 模式，未隐身且对观察者不隐身，未被饕餮吞下，未被 SparkTraits 从观察者本能中隐藏。不满足的目标不作答，由 Wathe 自身
     * 规则判定，与没有她时完全相同。
     */
    private static boolean isShownTo(ClientPlayerEntity viewer, PlayerEntity target) {
        return GameFunctions.isPlayerPlayingAndAlive(target)
                && !GameFunctions.isPlayerSpectatingOrCreative(target)
                && !target.isInvisible()
                && !target.isInvisibleTo(viewer)
                && !NoellesTaotieSeekerBridge.isSwallowed(target)
                && !IS_INSTINCT_HIDDEN.callBoolean(false, false, false, viewer, target);
    }
}
