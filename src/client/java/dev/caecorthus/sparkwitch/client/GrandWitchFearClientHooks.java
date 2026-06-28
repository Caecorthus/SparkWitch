package dev.caecorthus.sparkwitch.client;

import dev.caecorthus.sparkwitch.impl.GrandWitchFearService;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Client-side Fear gates for local keybinds and outgoing role-skill packets.
 * 客户端恐惧拦截：本地按键和发往服务端的角色技能包。
 */
public final class GrandWitchFearClientHooks {
    private static int instinctMessageCooldownTicks;
    private static int skillMessageCooldownTicks;

    private GrandWitchFearClientHooks() {
    }

    public static void tick() {
        if (instinctMessageCooldownTicks > 0) {
            instinctMessageCooldownTicks--;
        }
        if (skillMessageCooldownTicks > 0) {
            skillMessageCooldownTicks--;
        }
    }

    public static boolean shouldBlockInstinct() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (!GrandWitchFearService.isPlayerFeared(player)) {
            return false;
        }
        if (WatheClient.instinctKeybind != null && WatheClient.instinctKeybind.isPressed()) {
            sendInstinctBlocked(player);
        }
        return true;
    }

    public static boolean shouldBlockRoleAbilityKey(KeyBinding keyBinding, boolean originalPressed) {
        if (!originalPressed
                || keyBinding == null
                || !WitchAbilityKeyBridge.SHARED_ABILITY_TRANSLATION_KEY.equals(keyBinding.getTranslationKey())) {
            return false;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (!GrandWitchFearService.isPlayerFeared(player)) {
            return false;
        }
        sendSkillBlocked(player);
        return true;
    }

    public static boolean shouldBlockRoleSkillPayload(CustomPayload payload) {
        if (payload == null || payload.getId() == null) {
            return false;
        }
        Identifier payloadId = payload.getId().id();
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (!GrandWitchFearService.shouldBlockRoleSkillPayload(player, payloadId)) {
            return false;
        }
        sendSkillBlocked(player);
        return true;
    }

    private static void sendSkillBlocked(ClientPlayerEntity player) {
        if (player == null || skillMessageCooldownTicks > 0) {
            return;
        }
        skillMessageCooldownTicks = 20;
        player.sendMessage(Text.translatable(GrandWitchFearService.SKILL_BLOCKED_KEY), true);
    }

    private static void sendInstinctBlocked(ClientPlayerEntity player) {
        if (player == null || instinctMessageCooldownTicks > 0) {
            return;
        }
        instinctMessageCooldownTicks = 20;
        player.sendMessage(Text.translatable(GrandWitchFearService.INSTINCT_BLOCKED_KEY), true);
    }
}
