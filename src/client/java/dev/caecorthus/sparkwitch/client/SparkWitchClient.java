package dev.caecorthus.sparkwitch.client;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchEntities;
import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.client.hooks.DeathRayClientHooks;
import dev.caecorthus.sparkwitch.client.hooks.GrandWitchFearClientHooks;
import dev.caecorthus.sparkwitch.client.hooks.HunterTrapClientHooks;
import dev.caecorthus.sparkwitch.client.hooks.OrthopedistClientHooks;
import dev.caecorthus.sparkwitch.client.hooks.WitchAbilityKeyBridge;
import dev.caecorthus.sparkwitch.client.hooks.WitchCohortClientHooks;
import dev.caecorthus.sparkwitch.client.hooks.WitchInstinctSuppressionClientHooks;
import dev.caecorthus.sparkwitch.client.hooks.WitchPoisonVisionClientHooks;
import dev.caecorthus.sparkwitch.client.net.version.SparkWitchClientVersionHandshake;
import dev.caecorthus.sparkwitch.client.renderer.HunterTrapEntityRenderer;
import dev.caecorthus.sparkwitch.client.screen.TarotDivinationSelectorScreen;
import dev.caecorthus.sparkwitch.client.tarot.TarotDivinationClientState;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.net.OpenTarotDivinationSelectorS2CPacket;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.net.TarotDivinationSnapshotS2CPacket;
import dev.caecorthus.sparkwitch.net.UseWitchSkillC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistRules;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.UseOrthopedistSkillC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintRules;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterEntities;
import dev.doctor4t.ratatouille.client.util.ambience.AmbienceUtil;
import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbience;
import dev.doctor4t.wathe.api.event.CanSeePoison;
import dev.doctor4t.wathe.api.event.ShouldShowCohort;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

public final class SparkWitchClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SparkWitch.LOGGER.info("Initializing SparkWitch client hooks.");
        SparkWitchServerConnection.reset();
        SparkWitchClientVersionHandshake.registerClient();
        registerEntityRenderers();
        registerTarotDivinationNetworking();

        // Reset on every connection lifecycle edge so failed login attempts cannot leak confirmed state.
        // 在每个连接生命周期节点清理状态，避免失败的登录尝试残留已确认标记。
        ClientLoginConnectionEvents.INIT.register((handler, client) -> resetConnectionState());
        ClientLoginConnectionEvents.DISCONNECT.register((handler, client) -> resetConnectionState());
        ClientPlayConnectionEvents.INIT.register((handler, client) -> resetConnectionState());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetConnectionState());
        WitchInstinctSuppressionClientHooks.register();
        HunterTrapClientHooks.register();
        OrthopedistClientHooks.register();
        registerGrandWitchCeremonialSwordBgm();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TarotDivinationClientState.tick(client);
            if (!SparkWitchServerConnection.isConfirmedServer()) {
                DeathRayClientHooks.reset();
                return;
            }
            GrandWitchFearClientHooks.tick();
            DeathRayClientHooks.tick(client);
            if (client.player != null
                    && client.getNetworkHandler() != null
                    && WitchAbilityKeyBridge.wasPressed()) {
                var role = GameWorldComponent.KEY.get(client.player.getWorld()).getRole(client.player);
                if (role != null && OrthopedistRules.ROLE_ID.equals(role.identifier())) {
                    ClientPlayNetworking.send(new UseOrthopedistSkillC2SPacket());
                } else if (WitchPlayerComponent.KEY.get(client.player).hasSkill()
                        || SaintRules.isSaint(role)) {
                    ClientPlayNetworking.send(new UseWitchSkillC2SPacket());
                }
            }
        });

        ShouldShowCohort.EVENT.register((viewer, target) -> {
            if (!SparkWitchServerConnection.isConfirmedServer()) {
                return null;
            }
            if (WitchCohortClientHooks.isGrandWitchCohortPair(viewer, target)) {
                return ShouldShowCohort.CohortResult.hide(110);
            }
            return null;
        });
        CanSeePoison.EVENT.register(WitchPoisonVisionClientHooks::canSeeHiddenPoison);
    }

    public static Text abilityKeyText() {
        return WitchAbilityKeyBridge.keyText();
    }

    private static void registerEntityRenderers() {
        // The projectile renders its synced shuriken item stack, never the upstream generic knife texture.
        // 投射物始终渲染同步的手里剑物品模型，不复用上游通用飞刀贴图。
        EntityRendererRegistry.register(
                SparkWitchEntities.ninjaShuriken(),
                context -> new FlyingItemEntityRenderer<>(context, 1.0F, true)
        );
        EntityRendererRegistry.register(HunterEntities.hunterTrap(), HunterTrapEntityRenderer::new);
    }

    private static void registerGrandWitchCeremonialSwordBgm() {
        AmbienceUtil.registerBackgroundAmbience(new BackgroundAmbience(
                SparkWitchSounds.GRAND_WITCH_CEREMONIAL_SWORD_BGM,
                player -> SparkWitchServerConnection.isConfirmedServer()
                        && WitchWorldComponent.KEY.get(player.getWorld()).hasGrandWitchCeremonialSwordBgm(),
                20
        ));
    }

    private static void registerTarotDivinationNetworking() {
        ClientPlayNetworking.registerGlobalReceiver(TarotDivinationSnapshotS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (!SparkWitchServerConnection.isConfirmedServer()) {
                        return;
                    }
                    TarotDivinationClientState.snapshotState().overwrite(
                            payload.civilianCount(),
                            payload.killerCount(),
                            payload.neutralCount(),
                            payload.witchCount()
                    );
                }));
        ClientPlayNetworking.registerGlobalReceiver(OpenTarotDivinationSelectorS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (!SparkWitchServerConnection.isConfirmedServer()) {
                        return;
                    }
                    context.client().setScreen(new TarotDivinationSelectorScreen(
                            payload.mode(),
                            payload.playerIds(),
                            payload.playerNames()
                    ));
                }));
    }

    private static void resetConnectionState() {
        SparkWitchServerConnection.reset();
        TarotDivinationClientState.clear();
    }
}
