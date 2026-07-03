package dev.caecorthus.sparkwitch.client;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.client.net.SparkWitchClientVersionHandshake;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.net.UseWitchSkillC2SPacket;
import dev.doctor4t.ratatouille.client.util.ambience.AmbienceUtil;
import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbience;
import dev.doctor4t.wathe.api.event.CanSeePoison;
import dev.doctor4t.wathe.api.event.ShouldShowCohort;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;

public final class SparkWitchClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SparkWitch.LOGGER.info("Initializing SparkWitch client hooks.");
        SparkWitchServerConnection.reset();
        SparkWitchClientVersionHandshake.registerClient();

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SparkWitchServerConnection.reset());
        WitchInstinctSuppressionClientHooks.register();
        registerGrandWitchCeremonialSwordBgm();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!SparkWitchServerConnection.isConfirmedServer()) {
                DeathRayClientHooks.reset();
                return;
            }
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

    private static void registerGrandWitchCeremonialSwordBgm() {
        AmbienceUtil.registerBackgroundAmbience(new BackgroundAmbience(
                SparkWitchSounds.GRAND_WITCH_CEREMONIAL_SWORD_BGM,
                player -> SparkWitchServerConnection.isConfirmedServer()
                        && WitchWorldComponent.KEY.get(player.getWorld()).hasGrandWitchCeremonialSwordBgm(),
                20
        ));
    }
}
