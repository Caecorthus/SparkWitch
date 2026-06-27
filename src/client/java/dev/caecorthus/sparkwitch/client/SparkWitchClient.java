package dev.caecorthus.sparkwitch.client;

import dev.caecorthus.sparkwitch.net.UseWitchSkillC2SPacket;
import dev.doctor4t.wathe.api.event.ShouldShowCohort;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class SparkWitchClient implements ClientModInitializer {
    public static KeyBinding skillKeyBinding;

    @Override
    public void onInitializeClient() {
        skillKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sparkwitch.skill",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.wathe.keybinds"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (skillKeyBinding.wasPressed()) {
                if (client.player != null && client.getNetworkHandler() != null) {
                    ClientPlayNetworking.send(new UseWitchSkillC2SPacket());
                }
            }
        });

        ShouldShowCohort.EVENT.register((viewer, target) -> {
            if (WitchCohortClientHooks.isGrandWitchCohortPair(viewer, target)) {
                return ShouldShowCohort.CohortResult.hide(110);
            }
            return null;
        });
    }
}
