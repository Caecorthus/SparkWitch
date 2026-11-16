package dev.caecorthus.sparkwitch.client.savior;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.OptionalStaticMethod;
import dev.caecorthus.sparkwitch.net.SaviorDomainS2CPayload;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Client half of the Savior's domain and the psycho-instinct recolor. The domain outline needs no instinct key: members
 * are white, the Savior is the role color. A psycho killer using instinct sees the Savior in the role color as well.
 * 救世主领域的客户端部分与疯魔本能改色。领域描边无需本能键：成员白色，救世主职业色；疯魔杀手按住本能键时，
 * 救世主同样显示为职业色。
 */
public final class SaviorDomainClient {
    /**
     * The Seeker mark's and Dominator mark's slot ({@code SeekerRules.MARK_OUTLINE_PRIORITY}): above SparkWitch's 90
     * outlines, below {@code skip()} (100), the Black Raven (101) and the Grand Witch suppression (102), so every
     * hiding skip and veto still wins over the domain outline.
     * 与搜寻者标记、支配者标记同级（{@code SeekerRules.MARK_OUTLINE_PRIORITY}）：高于 SparkWitch 的 90 级描边，低于
     * {@code skip()}（100）、黑羽鸦（101）与大魔女压制（102），因此所有隐藏用的 skip 与否决仍压过领域描边。
     */
    private static final int DOMAIN_HIGHLIGHT_PRIORITY = 95;
    /**
     * Just below the domain outline (both paint the Savior in the role color, so a psycho killer inside the domain keeps
     * the key-free domain answer) and, like it, below {@code skip()} (100), Black Raven (101) and suppression (102).
     * 略低于领域描边（两者都把救世主画成职业色，领域内的疯魔杀手因此保留无需按键的领域答复），与领域描边一样低于
     * {@code skip()}（100）、黑羽鸦（101）与压制（102）。
     */
    private static final int PSYCHO_HIGHLIGHT_PRIORITY = 94;
    /**
     * External seam (optional SparkTraits): the Savior's own copy of the public {@code SparkTraitsApi
     * .isInstinctHidden(viewer, target)} facade read, like the Seeker and Dominator marks. An absent mod, older build,
     * wrong type or throwing call hides nothing (fail closed), so the outline then follows the vanilla gates only.
     * 外部接缝（可选 SparkTraits）：救世主自有的一份公共 {@code SparkTraitsApi.isInstinctHidden(viewer, target)} 门面读取，
     * 与搜寻者、支配者标记相同。模组缺失、旧版本、类型不符或调用抛异常时都不隐藏任何人（失败关闭），描边只按原版门槛判断。
     */
    private static final OptionalStaticMethod IS_INSTINCT_HIDDEN = OptionalStaticMethod.of("sparktraits",
            "dev.caecorthus.sparktraits.api.SparkTraitsApi", "isInstinctHidden",
            PlayerEntity.class, PlayerEntity.class).returning(boolean.class);
    private static int saviorEntityId = -1;
    private static long domainEndWorldTime;
    private static boolean registered;

    private SaviorDomainClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(SaviorDomainS2CPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    MinecraftClient client = context.client();
                    if (client.world == null || !SparkWitchServerConnection.isConfirmedServer()) {
                        return;
                    }
                    saviorEntityId = payload.saviorEntityId();
                    domainEndWorldTime = client.world.getTime() + Math.max(0, payload.durationTicks());
                }));
        GetInstinctHighlight.EVENT.register(SaviorDomainClient::domainHighlight);
        GetInstinctHighlight.EVENT.register(SaviorDomainClient::psychoInstinctHighlight);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        // Also on JOIN: a proxy backend switch re-joins without a disconnect, and play state only arrives after JOIN.
        // JOIN 时同样重置：代理切换后端时不会断线而是重新加入，而 play 阶段的状态只会在 JOIN 之后到达。
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
    }

    public static void reset() {
        saviorEntityId = -1;
        domainEndWorldTime = 0L;
    }

    /**
     * Domain members see each other without holding instinct: white for everyone else and the Savior's role color for
     * the Savior. The viewer must also be inside the 15-block radius, so outsiders never receive the tags. The outline
     * never reveals a member an ordinary viewer could not see ({@link #isShownTo}).
     * 领域成员无需按本能键即可互相看见：他人白色，救世主职业色。观察者也必须位于 15 格内，领域外玩家不会看到标记。描边绝不
     * 暴露普通观察者看不到的成员（{@link #isShownTo}）。
     */
    private static @Nullable GetInstinctHighlight.HighlightResult domainHighlight(Entity target) {
        if (!SparkWitchServerConnection.isConfirmedServer()
                || !(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity viewer = client.player;
        if (viewer == null
                || targetPlayer == viewer
                || !GameFunctions.isPlayerPlayingAndAlive(viewer)
                || !isShownTo(viewer, targetPlayer)) {
            return null;
        }
        PlayerEntity savior = activeSavior(client);
        if (savior == null
                || viewer.squaredDistanceTo(savior) > SaviorRules.DOMAIN_RADIUS_SQUARED
                || targetPlayer.squaredDistanceTo(savior) > SaviorRules.DOMAIN_RADIUS_SQUARED) {
            return null;
        }
        int color = targetPlayer == savior
                ? SaviorRules.ROLE_COLOR
                : SaviorRules.DOMAIN_WHITE_HIGHLIGHT;
        return GetInstinctHighlight.HighlightResult.always(color, DOMAIN_HIGHLIGHT_PRIORITY);
    }

    /** A psycho killer holding instinct sees the Savior in the Savior's role color instead of the default target color. / 疯魔杀手本能下的救世主改显职业色。 */
    private static @Nullable GetInstinctHighlight.HighlightResult psychoInstinctHighlight(Entity target) {
        if (!SparkWitchServerConnection.isConfirmedServer()
                || !(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null
                || targetPlayer == viewer
                || !GameFunctions.isPlayerPlayingAndAlive(viewer)
                || PlayerPsychoComponent.KEY.get(viewer).getPsychoTicks() <= 0
                || !isShownTo(viewer, targetPlayer)) {
            return null;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(viewer.getWorld());
        if (!game.canUseKillerFeatures(viewer)) {
            return null;
        }
        Role targetRole = game.getRole(targetPlayer);
        if (!SaviorRules.isSavior(targetRole)) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.withKeybind(
                SaviorRules.ROLE_COLOR,
                PSYCHO_HIGHLIGHT_PRIORITY
        );
    }

    /**
     * The target gates of the other always-on outlines (Seeker mark, Dominator mark): a living, non-spectating,
     * non-creative participant, not invisible or invisible to the viewer, not swallowed by a Taotie, not hidden from the
     * viewer's instinct by SparkTraits, and a viewer who is not swallowed either. A failing target gets no answer, so
     * Wathe's own rules decide it exactly as without the Savior.
     * 与其他常亮描边（搜寻者标记、支配者标记）相同的目标门槛：存活、非旁观、非创造模式的参与者，未隐身且对观察者不隐身，
     * 未被饕餮吞下，未被 SparkTraits 从观察者本能中隐藏，且观察者自身也未被吞下。不满足的目标不作答，由 Wathe 自身规则
     * 判定，与没有救世主时完全相同。
     */
    private static boolean isShownTo(ClientPlayerEntity viewer, PlayerEntity target) {
        return GameFunctions.isPlayerPlayingAndAlive(target)
                && !GameFunctions.isPlayerSpectatingOrCreative(target)
                && !target.isInvisible()
                && !target.isInvisibleTo(viewer)
                && !NoellesTaotieSeekerBridge.isSwallowed(target)
                && !NoellesTaotieSeekerBridge.isSwallowed(viewer)
                && !IS_INSTINCT_HIDDEN.callBoolean(false, false, false, viewer, target);
    }

    private static @Nullable PlayerEntity activeSavior(MinecraftClient client) {
        if (saviorEntityId < 0
                || client.world == null
                || client.world.getTime() >= domainEndWorldTime) {
            return null;
        }
        Entity entity = client.world.getEntityById(saviorEntityId);
        // Mirrors the server's SaviorDomainService#holdsDomain (ACTIVE round, living, not spectating or swallowed), so
        // the outline never outlives a domain the server already stopped.
        // 与服务端 SaviorDomainService#holdsDomain 一致（ACTIVE 回合、存活、非旁观、未被吞），描边不会比服务端已结束的领域更久。
        GameWorldComponent game = GameWorldComponent.KEY.get(client.world);
        if (!(entity instanceof PlayerEntity savior)
                || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !GameFunctions.isPlayerPlayingAndAlive(savior)
                || savior.isSpectator()
                || NoellesTaotieSeekerBridge.isSwallowed(savior)
                || !SaviorRules.isSavior(game.getRole(savior))) {
            return null;
        }
        return savior;
    }
}
