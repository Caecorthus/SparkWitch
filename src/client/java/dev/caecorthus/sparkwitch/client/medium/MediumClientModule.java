package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumRules;
import dev.caecorthus.sparkwitch.roles.civilian.medium.OpenMediumHauntSelectS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.medium.OpenMediumSeanceInputS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.medium.UseMediumSecondSkillC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

/**
 * The Medium's client hub: the two server menus open their screens, the corner HUD draws the two skill lines and the
 * dead see the three-line vote prompt, and the Role Skill 2 key sends the empty 招魂 request.
 * 通灵师客户端注册中心：服务端的两个菜单打开对应界面，右下角 HUD 绘制两条技能行，死者看到三行投票提示，
 * 职业技能 2 键发送空的招魂请求。
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
        ClientPlayNetworking.registerGlobalReceiver(OpenMediumSeanceInputS2CPacket.ID, (payload, context) ->
                context.client().setScreen(new MediumSeanceScreen(payload.price())));
        ClientPlayNetworking.registerGlobalReceiver(OpenMediumHauntSelectS2CPacket.ID, (payload, context) ->
                context.client().setScreen(new MediumHauntScreen(
                        payload.targets(), payload.names(), payload.price())));
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MediumHudRenderer.render(context);
            MediumSeanceHudRenderer.render(context);
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
}
