package dev.caecorthus.sparkwitch.client.sherry;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryRules;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.net.OpenSherryDeductionS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.net.RequestSherryDeductionC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.net.UseSherryMightyForceC2SPacket;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 橘雪莉's client hub (PR #206): the server's candidate packet opens the 推演 panel, the Role Skill 2 key sends the
 * empty 巨力魔法 request, the private outlines answer Wathe's instinct event and the corner HUD draws the two skill
 * rows. The shared ability key is dispatched by {@code SparkWitchClient} to {@link #onAbilityKey}. Everything here is
 * presentation or a request: the server owns every check. Never touches the {@code gui.sparkwitch.skills} panel.
 * 橘雪莉客户端注册中心（PR #206）：服务端的候选数据包打开推演面板，职业技能 2 键发送空的巨力魔法请求，私有描边应答 Wathe
 * 的本能事件，右下角 HUD 绘制两条技能行。共享技能键由 {@code SparkWitchClient} 分发到 {@link #onAbilityKey}。这里只有展示
 * 与请求：所有校验都在服务端。从不触及 {@code gui.sparkwitch.skills} 面板。
 */
public final class SherryClient {
    private static boolean registered;

    private SherryClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(OpenSherryDeductionS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    MinecraftClient client = context.client();
                    if (mayOpenPanel(client)) {
                        client.setScreen(new SherryDeductionScreen(
                                payload.targets(), payload.names(), payload.price()));
                    }
                }));
        SecondaryAbilityRegistry.register(SherryRules.ROLE_ID, new SecondaryAbilityHandler() {
            @Override
            public void onPressed(MinecraftClient client) {
                if (client.player == null
                        || client.currentScreen != null
                        || !ClientPlayNetworking.canSend(UseSherryMightyForceC2SPacket.ID)) {
                    return;
                }
                ClientPlayNetworking.send(new UseSherryMightyForceC2SPacket());
            }
        });
        SherryInstinctHooks.register();
        SherryHud.register();
    }

    /**
     * Shared ability key press, dispatched by {@code SparkWitchClient} for the exact raw role only: the empty 推演
     * request. Opening is free; the server checks the corpse, cooldown and coins and answers with the candidates.
     * 共享技能键按下，由 {@code SparkWitchClient} 仅按真实职业分发：空的推演请求。打开面板不收费；服务端检查尸体、冷却与金币后
     * 以候选名单应答。
     */
    public static void onAbilityKey(MinecraftClient client) {
        if (client.player == null || !ClientPlayNetworking.canSend(RequestSherryDeductionC2SPacket.ID)) {
            return;
        }
        ClientPlayNetworking.send(new RequestSherryDeductionC2SPacket());
    }

    /**
     * The local player is 橘雪莉 (raw synced role, never an acting overlay) on a confirmed SparkWitch server in an
     * ACTIVE round, playing and alive, not an alive spectator (Rift occupant), not creative and not Taotie-swallowed:
     * the client-visible part of the server's {@code SherryDeductionService.mayUse}. Role check first, so every other
     * client leaves at once.
     * 本地玩家在已确认的 SparkWitch 服务器上是橘雪莉（真实同步身份，绝非扮演覆盖层），回合为 ACTIVE、在局存活，不是存活
     * 旁观者（裂隙门内）、创造模式或被饕餮吞下：即服务端 {@code SherryDeductionService.mayUse} 中客户端可见的部分。
     * 先查职业，其他客户端立即返回。
     */
    public static boolean isActiveSherry(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return SherryRules.isSherry(game.getRole(player))
                && SparkWitchServerConnection.isConfirmedServer()
                && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !GameFunctions.isPlayerSpectatingOrCreative(player)
                && !NoellesTaotieSeekerBridge.isSwallowed(player);
    }

    /**
     * The candidate packet opens its panel only for an active local 橘雪莉 and never over a screen that is already
     * open (chat, the inventory, another role's menu).
     * 候选数据包只为活动中的本地橘雪莉打开面板，且绝不覆盖已经打开的界面（聊天、背包、其他职业的菜单）。
     */
    private static boolean mayOpenPanel(MinecraftClient client) {
        return client.currentScreen == null && isActiveSherry(client.player);
    }
}
