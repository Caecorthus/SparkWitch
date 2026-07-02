package dev.caecorthus.sparkwitch.client;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.client.screen.CriminologistScreen;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.net.OpenCriminologistScreenS2CPacket;
import dev.caecorthus.sparkwitch.net.UseWitchSkillC2SPacket;
import dev.doctor4t.ratatouille.client.util.ambience.AmbienceUtil;
import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbience;
import dev.doctor4t.wathe.api.event.CanSeePoison;
import dev.doctor4t.wathe.api.event.ShouldShowCohort;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

public final class SparkWitchClient implements ClientModInitializer {
    private static final String LAMBDYNAMICLIGHTS_MOD_ID = "lambdynlights";

    @Override
    public void onInitializeClient() {
        requireLambDynamicLights();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> SparkWitchRoles.refreshAssassinGuessRoleOrder());
        NoellesRoleEnhancementClientHooks.register();
        registerGrandWitchCeremonialSwordBgm();

        ClientPlayNetworking.registerGlobalReceiver(OpenCriminologistScreenS2CPacket.ID,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreen(new CriminologistScreen(payload.victimUuid()))
                ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            GrandWitchFearClientHooks.tick();
            DeathRayClientHooks.tick(client);
            if (client.player != null
                    && client.getNetworkHandler() != null
                    && WitchPlayerComponent.KEY.get(client.player).hasSkill()
                    && WitchAbilityKeyBridge.wasPressed()) {
                ClientPlayNetworking.send(new UseWitchSkillC2SPacket());
            }
        });

        ShouldShowCohort.EVENT.register((viewer, target) -> {
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

    private static void registerGrandWitchCeremonialSwordBgm() {
        AmbienceUtil.registerBackgroundAmbience(new BackgroundAmbience(
                SparkWitchSounds.GRAND_WITCH_CEREMONIAL_SWORD_BGM,
                player -> WitchWorldComponent.KEY.get(player.getWorld()).hasGrandWitchCeremonialSwordBgm(),
                20
        ));
    }

    private static void requireLambDynamicLights() {
        if (!FabricLoader.getInstance().isModLoaded(LAMBDYNAMICLIGHTS_MOD_ID)) {
            throw new IllegalStateException(
                    "SparkWitch requires LambDynamicLights on the client for flashlight lighting. "
                            + "Install LambDynamicLights for Minecraft 1.21.1."
            );
        }
    }
}
