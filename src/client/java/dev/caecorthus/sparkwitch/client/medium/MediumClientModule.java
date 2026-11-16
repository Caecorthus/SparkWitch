package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumRules;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumSeanceResultS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.medium.OpenMediumSeanceInputS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.medium.UseMediumBlessingC2SPacket;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

/**
 * The Medium's client hub: the 通灵 question box opens on the server's menu packet, the caster-only result of either
 * window becomes a HUD card, the corner HUD draws the 通灵 / 幽灵的加护 rows, the dead get the vote screen of the live
 * window's kind (once per window, then on the shared ability key) with a three-line HUD prompt, the SparkWitch skill
 * key casts 幽灵的加护, and the corpse-sight outlines are registered. 招魂 is the inventory drawer
 * ({@link MediumHauntDrawer}, attached by its own mixin).
 * 通灵师客户端注册中心：服务端的菜单数据包打开通灵提问框，任一窗口仅发给施放者的结果显示为 HUD 卡片，右下角 HUD 绘制通灵 /
 * 幽灵的加护两行，死者获得与进行中窗口种类相同的投票界面（每个窗口自动弹出一次，之后按共享技能键打开）与三行 HUD 提示，
 * SparkWitch 技能键施放幽灵的加护，并注册尸体透视描边。招魂是背包抽屉（{@link MediumHauntDrawer}，由其自有 mixin 挂载）。
 */
public final class MediumClientModule {
    private static boolean registered;

    private MediumClientModule() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(OpenMediumSeanceInputS2CPacket.ID, (payload, context) -> {
            if (mayOpenMenu(context.client())) {
                context.client().setScreen(new MediumSeanceScreen(payload.price()));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(MediumSeanceResultS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> MediumResultCard.receive(context.client(), payload)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            MediumVoteClient.tick(client);
            MediumResultCard.tick(client);
        });
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MediumHudRenderer.render(context);
            MediumSeanceHudRenderer.render(context);
            MediumResultCard.render(context);
        });
        MediumCorpseSightHighlights.register();
        SecondaryAbilityRegistry.register(MediumRules.ROLE_ID, new SecondaryAbilityHandler() {
            /**
             * 幽灵的加护: the empty request IS the paid act (no confirm box, owner 2026-11-13); the server checks every
             * gate, the price, both cooldowns and that no window is live. Never sent over an open screen.
             * 幽灵的加护：空请求即付费行为（没有确认框，所有者 2026-11-13）；服务端检查全部门槛、价格、两个冷却以及当前没有
             * 进行中的窗口。界面打开时绝不发送。
             */
            @Override
            public void onPressed(MinecraftClient client) {
                if (client.player == null
                        || client.currentScreen != null
                        || !ClientPlayNetworking.canSend(UseMediumBlessingC2SPacket.ID)) {
                    return;
                }
                ClientPlayNetworking.send(new UseMediumBlessingC2SPacket());
            }
        });
    }

    /**
     * A menu packet opens its screen only on a confirmed SparkWitch server, for a local player whose synced role is
     * the Medium, and never over a screen that is already open (chat, the vote screen, a typed question).
     * 菜单数据包只在已确认的 SparkWitch 服务器上、本地玩家同步身份为通灵师时打开界面，且绝不覆盖已经打开的界面
     * （聊天、投票界面、正在输入的提问）。
     */
    private static boolean mayOpenMenu(MinecraftClient client) {
        return SparkWitchServerConnection.isConfirmedServer()
                && client.player != null
                && client.currentScreen == null
                && MediumRules.isMedium(client.player);
    }
}
