package dev.caecorthus.sparkwitch.client.knight;

import dev.caecorthus.sparkwitch.net.KnightDuelStateS2CPayload;
import dev.caecorthus.sparkwitch.net.KnightDuelStopSoundS2CPayload;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightRules;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelRules;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelClientState;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Client wiring: G key, bottom-right HUD and the stop-track receiver. / 客户端接线：G 键、右下角 HUD 与停止音乐接收器。 */
public final class KnightDuelClient {
    public static KeyBinding key;
    private static boolean registered;

    private KnightDuelClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        key = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.sparkwitch.knight_duel",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.wathe.keybinds"));
        ClientTickEvents.END_CLIENT_TICK.register(KnightDuelClient::tick);
        HudRenderCallback.EVENT.register(KnightDuelClient::renderHud);
        ClientPlayNetworking.registerGlobalReceiver(KnightDuelStopSoundS2CPayload.ID, (payload, context) ->
                context.client().execute(() -> context.client().getSoundManager()
                        .stopSounds(KnightDuelRules.SF_SOUND_ID, SoundCategory.MASTER)));
        ClientPlayNetworking.registerGlobalReceiver(KnightDuelStateS2CPayload.ID, (payload, context) ->
                context.client().execute(() -> KnightDuelClientState.apply(payload)));
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player != null && client.world != null) {
            KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(player.getWorld());
            if (duel.active() && duel.isDuelist(player.getUuid())) {
                KnightDuelClientState.forceFrom(duel);
            }
        }
        if (!key.wasPressed()) {
            return;
        }
        if (player == null || client.world == null) {
            return;
        }
        if (!GameFunctions.isPlayerPlayingAndAlive(player)
                || !KnightRules.isKnight(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(player.getWorld());
        if (duel.isDuelist(player.getUuid())) {
            return;
        }
        if (PlayerShopComponent.KEY.get(player).getBalance() < KnightDuelRules.COST) {
            return;
        }
        client.setScreen(new KnightDuelScreen(player));
    }

    private static void renderHud(net.minecraft.client.gui.DrawContext context,
                                  net.minecraft.client.render.RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(player.getWorld());
        Text text;
        if (duel.isDuelist(player.getUuid())) {
            text = Text.translatable("hud.sparkwitch.knight.duel");
        } else if (KnightRules.isKnight(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            text = PlayerShopComponent.KEY.get(player).getBalance() >= KnightDuelRules.COST
                    ? Text.translatable("hud.sparkwitch.knight.hint", key.getBoundKeyLocalizedText())
                    : Text.translatable("hud.sparkwitch.knight.need_money");
        } else {
            return;
        }
        float pulse = (float) (0.75D + 0.15D * Math.sin(System.currentTimeMillis() % 2000L / 2000.0D * Math.PI * 2.0D));
        int alpha = (int) (170.0F * pulse);
        int color = (alpha << 24) | 0xFFC800;
        int x = context.getScaledWindowWidth() - 5 - client.textRenderer.getWidth(text);
        int y = context.getScaledWindowHeight() - 5 - client.textRenderer.fontHeight;
        context.drawTextWithShadow(client.textRenderer, text, x, y, color);
    }
}
