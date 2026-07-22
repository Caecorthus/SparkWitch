package dev.caecorthus.sparkwitch.client.hooks;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedAbilityPressStateTest {
    private static final String CATEGORY = "category.wathe.keybinds";

    private KeyBinding originalAbilityBinding;
    private KeyBinding noellesAbilityBinding;

    @BeforeEach
    void installNoellesPrimaryAbilityBinding() {
        originalAbilityBinding = NoellesrolesClient.abilityBind;
        noellesAbilityBinding = binding("key.noellesroles.ability", GLFW.GLFW_KEY_G);
        NoellesrolesClient.abilityBind = noellesAbilityBinding;
        WitchAbilityKeyBridge.reset();
    }

    @AfterEach
    void restoreNoellesPrimaryAbilityBinding() {
        WitchAbilityKeyBridge.reset();
        NoellesrolesClient.abilityBind = originalAbilityBinding;
    }

    @Test
    void capturesTheConcreteNoellesWasPressedEventWithoutPollingHeldState() {
        WitchAbilityKeyBridge.captureSharedAbilityPress(noellesAbilityBinding, true);

        assertTrue(WitchAbilityKeyBridge.wasPressed());
        assertFalse(WitchAbilityKeyBridge.wasPressed());
    }

    @Test
    void ignoresOtherBindingsEvenWhenTheirTranslationMatchesAndIgnoresFalseResults() {
        KeyBinding sameTranslationButDifferentBinding = binding(
                "key.noellesroles.ability",
                GLFW.GLFW_KEY_H
        );
        KeyBinding sparkWitchSecondaryBinding = binding(
                "key.sparkwitch.secondary_skill",
                GLFW.GLFW_KEY_N
        );

        WitchAbilityKeyBridge.captureSharedAbilityPress(sameTranslationButDifferentBinding, true);
        WitchAbilityKeyBridge.captureSharedAbilityPress(sparkWitchSecondaryBinding, true);
        WitchAbilityKeyBridge.captureSharedAbilityPress(noellesAbilityBinding, false);

        assertFalse(WitchAbilityKeyBridge.wasPressed());
    }

    @Test
    void queuesMultipleObservedPrimaryPressesAndClearsThemOnReset() {
        WitchAbilityKeyBridge.captureSharedAbilityPress(noellesAbilityBinding, true);
        WitchAbilityKeyBridge.captureSharedAbilityPress(noellesAbilityBinding, true);

        assertTrue(WitchAbilityKeyBridge.wasPressed());
        assertTrue(WitchAbilityKeyBridge.wasPressed());
        WitchAbilityKeyBridge.captureSharedAbilityPress(noellesAbilityBinding, true);
        WitchAbilityKeyBridge.reset();
        assertFalse(WitchAbilityKeyBridge.wasPressed());
    }

    private static KeyBinding binding(String translationKey, int keyCode) {
        return new KeyBinding(
                translationKey,
                InputUtil.Type.KEYSYM,
                keyCode,
                CATEGORY
        );
    }
}
