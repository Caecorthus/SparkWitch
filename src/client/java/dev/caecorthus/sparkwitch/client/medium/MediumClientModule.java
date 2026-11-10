package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumRules;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumSeanceResultS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.medium.OpenMediumHauntSelectS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.medium.OpenMediumSeanceInputS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.medium.UseMediumSecondSkillC2SPacket;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

/**
 * The Medium's client hub: the two server menus open their screens, the caster-only result becomes a HUD card, the
 * corner HUD draws the two skill lines, the dead get the vote screen (once per window, then on the shared ability
 * key) with a three-line HUD prompt, and the Role Skill 2 key sends the empty 招魂 request.
 * 通灵师客户端注册中心：服务端的两个菜单打开对应界面，仅发给施放者的结果显示为 HUD 卡片，右下角 HUD 绘制两条技能行，
 * 死者获得投票界面（每个窗口自动弹出一次，之后按共享技能键打开）与三行 HUD 提示，职业技能 2 键发送空的招魂请求。
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
        ClientPlayNetworking.registerGlobalReceiver(OpenMediumHauntSelectS2CPacket.ID, (payload, context) -> {
            if (mayOpenMenu(context.client())) {
                context.client().setScreen(new MediumHauntScreen(
                        payload.targets(), payload.names(), payload.price()));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(MediumSeanceResultS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> MediumSeanceResultCard.receive(context.client(), payload)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            MediumVoteClient.tick(client);
            MediumSeanceResultCard.tick(client);
        });
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MediumHudRenderer.render(context);
            MediumSeanceHudRenderer.render(context);
            MediumSeanceResultCard.render(context);
        });
        SecondaryAbilityRegistry.register(MediumRules.ROLE_ID, new SecondaryAbilityHandler() {
            @Override
            public void onPressed(MinecraftClient client) {
                if (client.player == null
                        || client.currentScreen != null
                        || !ClientPlayNetworking.canSend(UseMediumSecondSkillC2SPacket.ID)) {
                    return;
                }
                ClientPlayNetworking.send(new UseMediumSecondSkillC2SPacket());
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
