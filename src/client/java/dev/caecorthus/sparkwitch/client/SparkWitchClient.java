package dev.caecorthus.sparkwitch.client;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.net.UseWitchSkillC2SPacket;
import dev.doctor4t.wathe.api.event.CanSeePoison;
import dev.doctor4t.wathe.api.event.ShouldShowCohort;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;

public final class SparkWitchClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> SparkWitchRoles.refreshAssassinGuessRoleOrder());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            GrandWitchFearClientHooks.tick();
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
}
