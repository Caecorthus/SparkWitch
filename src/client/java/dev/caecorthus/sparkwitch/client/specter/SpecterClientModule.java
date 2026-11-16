package dev.caecorthus.sparkwitch.client.specter;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import dev.caecorthus.sparkwitch.client.hud.SpecterHudRenderer;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterEntities;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterParticles;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterRules;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterSecondSkillComponent;
import dev.caecorthus.sparkwitch.roles.killer.specter.UseSpecterSecondSkillC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

/**
 * The Specter's Role Skill 2 key sends the empty Otherworld request; the server owns unlock, window and cooldown.
 * 幽魂职业技能 2 键发送空的里世界请求；解锁、持续与冷却全部由服务端校验。
 */
public final class SpecterClientModule {
    private static boolean registered;

    private SpecterClientModule() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        EntityRendererRegistry.register(SpecterEntities.ghostTrigger(), GhostTriggerEntityRenderer::new);
        EntityRendererRegistry.register(SpecterEntities.ghostVisage(), GhostVisageEntityRenderer::new);
        ParticleFactoryRegistry.getInstance().register(SpecterParticles.jossPaper(), JossPaperParticle.Factory::new);
        // Bottom-right skill + Otherworld lines (the PR shipped the lang keys without a renderer).
        // 右下角技能与里世界提示行（原 PR 只提交了语言键，没有渲染器）。
        HudRenderCallback.EVENT.register((context, tickCounter) -> SpecterHudRenderer.render(context));
        GhostTriggerClientHooks.register();
        SpecterItemTooltips.register();
        SpecterOtherworldScreenEffects.register();
        SpecterOtherworldParticles.register();
        SpecterOtherworldBgm.register();
        SecondaryAbilityRegistry.register(SpecterRules.ROLE_ID, new SecondaryAbilityHandler() {
            @Override
            public void onPressed(MinecraftClient client) {
                if (client.player == null
                        || !SpecterSecondSkillComponent.KEY.get(client.player).canActivate()
                        || !ClientPlayNetworking.canSend(UseSpecterSecondSkillC2SPacket.ID)) {
                    return;
                }
                ClientPlayNetworking.send(new UseSpecterSecondSkillC2SPacket());
            }
        });
    }
}
