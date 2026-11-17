package dev.caecorthus.sparkwitch.client.sphinxhao;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoPlayerComponent;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoRules;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoWorldComponent;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.net.UseSphinxHaoInfectC2SPayload;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.net.UseSphinxHaoRoarC2SPayload;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;

/**
 * Registers only Sphinx Hao-owned client presentation and sends its two skill requests. Presentation: its four HUD
 * rows ({@link SphinxHaoHud}) from one confirmed-server-gated HUD callback, its keyed instinct outline of the players it
 * infected ({@link SphinxHaoInstinctHooks}) and 《Fade》 for every client during a window ({@link SphinxHaoFadeBgm}).
 * The crawler instinct veto is the mixin {@code sphinxhao.SphinxHaoInstinctVetoMixin}, and the forced crawl is the
 * common {@code PlayerEntitySphinxHaoCrawlMixin}. Server authority: the client never predicts an infection or a window;
 * the keys only send the empty {@code sparkwitch:use_sphinx_hao_infect} (G) and {@code sparkwitch:use_sphinx_hao_roar}
 * (N) requests, trimmed by {@link SphinxHaoKeyRules}. Never touches the {@code gui.sparkwitch.skills} panel.
 * 只注册狮身豪自有的客户端展示，并发送它的两个技能请求。展示：四行 HUD（{@link SphinxHaoHud}，使用一个仅在已确认服务器上生效的
 * HUD 回调）、对其感染者的按键本能描边（{@link SphinxHaoInstinctHooks}），以及窗口期间每个客户端播放的《Fade》
 * （{@link SphinxHaoFadeBgm}）。陆泳者的本能否决是 mixin {@code sphinxhao.SphinxHaoInstinctVetoMixin}，强制陆泳是通用的
 * {@code PlayerEntitySphinxHaoCrawlMixin}。服务端权威：客户端从不预测感染或窗口；按键只发送空的
 * {@code sparkwitch:use_sphinx_hao_infect}（G）与 {@code sparkwitch:use_sphinx_hao_roar}（N）请求，并由 {@link SphinxHaoKeyRules}
 * 过滤。从不触及 {@code gui.sparkwitch.skills} 面板。
 */
public final class SphinxHaoClient {
    private static boolean initialized;

    private SphinxHaoClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        SphinxHaoInstinctHooks.register();
        SphinxHaoFadeBgm.register();
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (SparkWitchServerConnection.isConfirmedServer()) {
                SphinxHaoHud.render(context);
            }
        });
        // 豪气冲天 on the secondary skill key (N); the controller only dispatches on a confirmed server and by real role.
        // 豪气冲天走第二技能键（N）；控制器只在已确认的服务器上按真实职业分发。
        SecondaryAbilityRegistry.register(SphinxHaoRules.ROLE_ID, new SecondaryAbilityHandler() {
            @Override
            public void onPressed(MinecraftClient client) {
                onRoarKey(client);
            }
        });
    }

    /**
     * Called by the shared ability-key dispatcher in {@code SparkWitchClient} when the local player's real role is the
     * Sphinx Hao: 嘉豪病毒.
     * 当本地玩家的真实职业为狮身豪时，由 {@code SparkWitchClient} 的共享技能键分发调用：嘉豪病毒。
     */
    public static void onAbilityKey(MinecraftClient client) {
        ClientPlayerEntity player = client == null ? null : client.player;
        SphinxHaoPlayerComponent state = player == null ? null : SphinxHaoPlayerComponent.KEY.getNullable(player);
        if (state == null) {
            return;
        }
        int cooldown = state.infectCooldownRemaining(player.getWorld().getTime());
        switch (SphinxHaoKeyRules.infect(isLivingSphinxHao(player), cooldown)) {
            case SEND -> send(UseSphinxHaoInfectC2SPayload.ID, new UseSphinxHaoInfectC2SPayload());
            case COOLDOWN_HINT -> hint(player, SphinxHaoHudRules.INFECT_COOLDOWN_KEY, cooldown);
            default -> {
            }
        }
    }

    private static void onRoarKey(MinecraftClient client) {
        ClientPlayerEntity player = client == null ? null : client.player;
        SphinxHaoPlayerComponent state = player == null ? null : SphinxHaoPlayerComponent.KEY.getNullable(player);
        SphinxHaoWorldComponent world = player == null ? null : SphinxHaoWorldComponent.KEY.getNullable(player.getWorld());
        if (state == null || world == null) {
            return;
        }
        long now = player.getWorld().getTime();
        int crawl = world.isCrawling(player.getUuid(), now) ? world.roarRemaining(now) : 0;
        int cooldown = state.roarCooldownRemaining(now);
        switch (SphinxHaoKeyRules.roar(isLivingSphinxHao(player), crawl, cooldown)) {
            case SEND -> send(UseSphinxHaoRoarC2SPayload.ID, new UseSphinxHaoRoarC2SPayload());
            case ACTIVE_HINT -> hint(player, SphinxHaoHudRules.ROAR_ACTIVE_KEY, crawl);
            case COOLDOWN_HINT -> hint(player, SphinxHaoHudRules.ROAR_COOLDOWN_KEY, cooldown);
            default -> {
            }
        }
    }

    /** Real role (getRole stays raw), playing, alive and not swallowed. / 真实职业（getRole 为真实身份）、参与、存活且未被吞下。 */
    private static boolean isLivingSphinxHao(ClientPlayerEntity player) {
        return SphinxHaoRules.isSphinxHao(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !NoellesTaotieSeekerBridge.isSwallowed(player);
    }

    /** Skips the send only when the server has no receiver (fail closed). / 仅在服务端没有接收器时跳过发送（失败即关闭）。 */
    private static void send(CustomPayload.Id<?> id, CustomPayload payload) {
        if (ClientPlayNetworking.canSend(id)) {
            ClientPlayNetworking.send(payload);
        }
    }

    /** Local action-bar line, whole seconds rounded up. / 本地动作栏提示，整秒向上取整。 */
    private static void hint(ClientPlayerEntity player, String key, int ticks) {
        player.sendMessage(Text.translatable(key, SphinxHaoHudRules.seconds(ticks)), true);
    }
}
