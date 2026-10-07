package dev.caecorthus.sparkwitch.client.hooks;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.agmas.noellesroles.client.NoellesrolesClient;

/**
 * Reuses the shared Wathe-role ability key instead of registering a SparkWitch-only keybind.
 * 复用列车职业体系已有的技能控制键，不在 SparkWitch 里新增独立按键项。
 */
public final class WitchAbilityKeyBridge {
    static final String SHARED_ABILITY_TRANSLATION_KEY = "key.noellesroles.ability";

    private static final SharedAbilityPressState PRESS_STATE = new SharedAbilityPressState();
    private static boolean sharedKeyDown;
    private static boolean directPressCaptured;

    private WitchAbilityKeyBridge() {
    }

    /**
     * Consumes a press observed from NoellesRoles' own {@link KeyBinding#wasPressed()} call.
     * This preserves short taps even after NoellesRoles consumes the shared key's press counter.
     */
    public static boolean wasPressed() {
        return PRESS_STATE.consume();
    }

    /**
     * NoellesRoles 会在自己的客户端 tick 中先消费 wasPressed 队列，
     * 因此仅依赖队列会让 SparkWitch 偶尔收不到 G 键。这里额外做一次按下沿检测，
     * 作为共享按键的稳定兜底，同时保留原有捕获队列以兼容短按。
     */
    public static boolean pollPressed() {
        KeyBinding key = sharedAbilityKeyBinding();
        boolean down = key != null && key.isPressed();
        boolean rising = down && !sharedKeyDown;
        sharedKeyDown = down;
        return PRESS_STATE.consume() || rising;
    }

    public static void captureSharedAbilityPress(KeyBinding keyBinding, boolean pressed) {
        if (pressed
                && keyBinding != null
                && keyBinding == NoellesrolesClient.abilityBind) {
            // setKeyPressed 已经捕获过同一次物理按下时，避免 wasPressed 再生成一份事件。
            if (directPressCaptured) {
                directPressCaptured = false;
                return;
            }
            PRESS_STATE.record();
        }
    }

    /**
     * 在 Minecraft 更新按键状态的瞬间捕获共享技能键。
     * 这条路径不依赖任何模组客户端 tick 的注册顺序，短按 G 也不会因
     * NoellesRoles 先调用 wasPressed() 而丢失。
     */
    public static void captureSharedAbilityKey(InputUtil.Key key, boolean pressed) {
        KeyBinding binding = sharedAbilityKeyBinding();
        if (pressed && binding != null && key != null
                && binding.matchesKey(key.getCode(), -1)
                && !GrandWitchFearClientHooks.shouldBlockRoleAbilityKey(binding, true)) {
            PRESS_STATE.record();
            directPressCaptured = true;
        }
    }

    public static void reset() {
        PRESS_STATE.reset();
        sharedKeyDown = false;
        directPressCaptured = false;
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
