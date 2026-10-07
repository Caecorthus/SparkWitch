package dev.caecorthus.sparkwitch.client.hooks;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.agmas.noellesroles.client.NoellesrolesClient;

/**
 * Reuses the shared Wathe-role ability key instead of registering a SparkWitch-only keybind.
 * 复用列车职业体系已有的技能控制键，不在 SparkWitch 里新增独立按键项。
 */
public final class WitchAbilityKeyBridge {
    static final String SHARED_ABILITY_TRANSLATION_KEY = "key.noellesroles.ability";

    private static final SharedAbilityPressState PRESS_STATE = new SharedAbilityPressState();

    private WitchAbilityKeyBridge() {
    }

    /**
     * Consumes a press observed from NoellesRoles' own {@link KeyBinding#wasPressed()} call.
     * This preserves short taps even after NoellesRoles consumes the shared key's press counter.
     */
    public static boolean wasPressed() {
        return PRESS_STATE.consume();
    }

    public static void captureSharedAbilityPress(KeyBinding keyBinding, boolean pressed) {
        if (pressed
                && keyBinding != null
                && keyBinding == NoellesrolesClient.abilityBind) {
            PRESS_STATE.record();
        }
    }

    public static void reset() {
        PRESS_STATE.reset();
    }

    public static Text keyText() {
        KeyBinding keyBinding = sharedAbilityKeyBinding();
        return keyBinding == null
                ? Text.translatable(SHARED_ABILITY_TRANSLATION_KEY)
                : keyBinding.getBoundKeyLocalizedText();
    }

    private static KeyBinding sharedAbilityKeyBinding() {
        return NoellesrolesClient.abilityBind;
    }
}
