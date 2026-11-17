package dev.caecorthus.sparkwitch.client.sphinxhao;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.OptionalStaticMethod;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoRules;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoWorldComponent;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Client seam: the Sphinx Hao's single Wathe {@link GetInstinctHighlight} listener (rules in
 * {@link SphinxHaoInstinctRules}): holding the instinct key outlines the players it infected in Team Jiahao gold. Only
 * a Sphinx Hao's client answers: the infected set is empty on every other player's client (per-recipient sync of
 * {@code sparkwitch:sphinx_hao_world}), and spectators who do receive it fail the living-Sphinx-Hao gate. An ordinary
 * keyed instinct: the keyed-instinct blockers and every {@code skip()} (100) or higher answer still hide it; the
 * crawler veto ({@code SphinxHaoInstinctVetoMixin}) exempts a living Sphinx Hao viewer, so its infected crawlers stay
 * gold during its window. Presentation only; the server alone infects and clears.
 * 客户端接缝：狮身豪唯一的 Wathe {@link GetInstinctHighlight} 监听器（规则见 {@link SphinxHaoInstinctRules}）：按住本能键时，
 * 被它感染的玩家以嘉豪阵营金色描边。只有狮身豪的客户端作答：其他玩家客户端上的感染集合为空（{@code sparkwitch:sphinx_hao_world}
 * 按接收者同步），确实收到该集合的旁观者也过不了“存活狮身豪”门槛。它是普通的按键本能：按键本能的屏蔽与任何 {@code skip()}
 * （100）及更高的答复仍会隐藏它；陆泳者否决（{@code SphinxHaoInstinctVetoMixin}）豁免存活的狮身豪观察者，因此窗口期间其被感染的
 * 陆泳者仍显示金色。仅用于展示；感染与清除只由服务端决定。
 */
public final class SphinxHaoInstinctHooks {
    /**
     * External seam (optional SparkTraits): its own read of the public {@code SparkTraitsApi.isInstinctHidden(viewer,
     * target)} facade, like Koko's. An absent mod, older build, wrong type or throwing call hides nothing (fail closed).
     * 外部接缝（可选 SparkTraits）：自有的一份公共 {@code SparkTraitsApi.isInstinctHidden(viewer, target)} 门面读取，同可可。
     * 模组缺失、旧版本、类型不符或调用抛异常时都不隐藏任何人（失败关闭）。
     */
    private static final OptionalStaticMethod IS_INSTINCT_HIDDEN = OptionalStaticMethod.of("sparktraits",
            "dev.caecorthus.sparktraits.api.SparkTraitsApi", "isInstinctHidden",
            PlayerEntity.class, PlayerEntity.class).returning(boolean.class);
    private static boolean registered;

    private SphinxHaoInstinctHooks() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(SphinxHaoInstinctHooks::highlight);
    }

    /**
     * Wathe listener; {@code null} means "no opinion". The infected-set read comes first: it is empty on every client
     * but a Sphinx Hao's (and spectators'), so this per-entity, per-frame path leaves at once for everyone else.
     * Wathe 监听器；{@code null} 表示不作答。先读感染集合：除狮身豪（与旁观者）外，所有客户端上它都为空，因此这条逐实体、逐帧的
     * 路径对其他人立即返回。
     */
    private static @Nullable GetInstinctHighlight.HighlightResult highlight(Entity target) {
        if (!(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || targetPlayer == viewer || viewer.getWorld() == null) {
            return null;
        }
        SphinxHaoWorldComponent world = SphinxHaoWorldComponent.KEY.getNullable(viewer.getWorld());
        if (world == null || !world.isInfected(targetPlayer.getUuid())) {
            return null;
        }
        boolean answers = SphinxHaoInstinctRules.viewerAnswers(
                SparkWitchServerConnection.isConfirmedServer(),
                WatheClient.isInstinctEnabled(),
                SphinxHaoRules.isSphinxHao(GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer)),
                GameFunctions.isPlayerPlayingAndAlive(viewer),
                GameFunctions.isPlayerSpectatingOrCreative(viewer),
                NoellesTaotieSeekerBridge.isSwallowed(viewer));
        if (!answers || !SphinxHaoInstinctRules.outlinesTarget(
                false,
                true,
                GameFunctions.isPlayerPlayingAndAlive(targetPlayer),
                GameFunctions.isPlayerSpectatingOrCreative(targetPlayer),
                targetPlayer.isInvisible() || targetPlayer.isInvisibleTo(viewer),
                NoellesTaotieSeekerBridge.isSwallowed(targetPlayer),
                IS_INSTINCT_HIDDEN.callBoolean(false, false, false, viewer, targetPlayer))) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.withKeybind(SphinxHaoInstinctRules.OUTLINE_COLOR,
                SphinxHaoInstinctRules.OUTLINE_PRIORITY);
    }
}
