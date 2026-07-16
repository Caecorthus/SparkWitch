package dev.caecorthus.sparkwitch.client.ability;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/** Registers key 2 once and dispatches it by the current role id. / 只注册一次技能键 2，并按当前角色 id 分发。 */
public final class SecondaryAbilityController {
    private static final String TRANSLATION_KEY = "key.sparkwitch.secondary_skill";
    private static final String CATEGORY = "category.wathe.keybinds";
    private static KeyBinding keyBinding;
    private static Identifier activeRoleId;

    private SecondaryAbilityController() {
    }

    public static void registerKeyBinding() {
        if (keyBinding != null) {
            return;
        }
        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                TRANSLATION_KEY,
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                CATEGORY
        ));
    }

    public static void tick(MinecraftClient client) {
        boolean pressed = consumePresses();
        Identifier roleId = currentRoleId(client);
        if (!Objects.equals(activeRoleId, roleId)) {
            SecondaryAbilityHandler previous = activeRoleId == null ? null : SecondaryAbilityRegistry.get(activeRoleId);
            if (previous != null) {
                previous.reset();
            }
            activeRoleId = roleId;
        }

        SecondaryAbilityHandler handler = roleId == null ? null : SecondaryAbilityRegistry.get(roleId);
        if (handler == null) {
            return;
        }
        handler.tick(client);
        if (pressed) {
            handler.onPressed(client);
        }
    }

    public static Text secondaryKeyText() {
        return keyBinding == null
                ? Text.translatable(TRANSLATION_KEY)
                : keyBinding.getBoundKeyLocalizedText();
    }

    public static void reset() {
        activeRoleId = null;
        SecondaryAbilityRegistry.resetAll();
        if (keyBinding != null) {
            keyBinding.setPressed(false);
            consumePresses();
        }
    }

    private static boolean consumePresses() {
        boolean pressed = false;
        if (keyBinding != null) {
            while (keyBinding.wasPressed()) {
                pressed = true;
            }
        }
        return pressed;
    }

    private static @Nullable Identifier currentRoleId(MinecraftClient client) {
        if (!SparkWitchServerConnection.isConfirmedServer() || client.player == null || client.world == null) {
            return null;
        }
        Role role = GameWorldComponent.KEY.get(client.world).getRole(client.player);
        return role == null ? null : role.identifier();
    }
}
