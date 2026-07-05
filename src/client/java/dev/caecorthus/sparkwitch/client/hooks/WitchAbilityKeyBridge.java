package dev.caecorthus.sparkwitch.client.hooks;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

import java.lang.reflect.Field;

/**
 * Reuses the shared Wathe-role ability key instead of registering a SparkWitch-only keybind.
 * 复用列车职业体系已有的技能控制键，不在 SparkWitch 里新增独立按键项。
 */
public final class WitchAbilityKeyBridge {
    static final String SHARED_ABILITY_TRANSLATION_KEY = "key.noellesroles.ability";
    private static final String NOELLES_CLIENT_CLASS = "org.agmas.noellesroles.client.NoellesrolesClient";
    private static final String ABILITY_FIELD = "abilityBind";

    private static boolean wasDown;

    private WitchAbilityKeyBridge() {
    }

    public static boolean wasPressed() {
        KeyBinding keyBinding = sharedAbilityKeyBinding();
        if (keyBinding == null) {
            wasDown = false;
            return false;
        }

        boolean down = keyBinding.isPressed();
        boolean pressed = down && !wasDown;
        wasDown = down;
        return pressed;
    }

    public static Text keyText() {
        KeyBinding keyBinding = sharedAbilityKeyBinding();
        return keyBinding == null
                ? Text.translatable(SHARED_ABILITY_TRANSLATION_KEY)
                : keyBinding.getBoundKeyLocalizedText();
    }

    private static KeyBinding sharedAbilityKeyBinding() {
        return noellesRolesAbilityKeyBinding();
    }

    private static KeyBinding noellesRolesAbilityKeyBinding() {
        try {
            Class<?> clientClass = Class.forName(
                    NOELLES_CLIENT_CLASS,
                    false,
                    WitchAbilityKeyBridge.class.getClassLoader()
            );
            Field field = clientClass.getField(ABILITY_FIELD);
            Object value = field.get(null);
            return value instanceof KeyBinding keyBinding ? keyBinding : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
