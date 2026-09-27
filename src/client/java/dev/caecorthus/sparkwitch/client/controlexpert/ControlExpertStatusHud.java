package dev.caecorthus.sparkwitch.client.controlexpert;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStatusComponent;
import dev.doctor4t.wathe.client.WatheClient;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * Owner-only status lines for a disrupted or stunned player: remaining seconds of each effect, plus a flash, a
 * "disrupted right now" hint and a local sound when Alt is pressed while disrupted. It reads only the local player's
 * own synced counters (other clients never receive them) and is the primary notice channel, because blackout
 * re-sends the action bar every tick. It is role-owned presentation and never part of the witch skill inventory panel.
 * 被干扰或眩晕玩家自己可见的状态行：各效果剩余秒数；在受干扰时按下 Alt 会闪烁、显示“正受干扰”提示并播放
 * 本地音效。只读取本地玩家自己的同步计时（其他客户端永远收不到），并作为主要提示渠道，因为停电期间动作栏
 * 每刻都会被覆盖。它属于职业自有展示，从不属于魔女技能背包面板。
 */
public final class ControlExpertStatusHud {
    private static boolean registered;
    private static boolean wasInstinctPressed;
    private static int flashTicks;

    private ControlExpertStatusHud() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (SparkWitchServerConnection.isConfirmedServer()) {
                render(context, tickCounter);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(ControlExpertStatusHud::tick);
    }

    static void tick(MinecraftClient client) {
        // Raw key state: the gated WatheClient methods read false for the whole disruption.
        // 读取原始按键状态：被拦截的 WatheClient 方法在整个干扰期间都返回 false。
        boolean pressed = WatheClient.instinctKeybind != null && WatheClient.instinctKeybind.isPressed();
        ControlExpertStatusComponent status = ControlExpertStatusClient.localActiveStatus();
        ClientPlayerEntity player = client.player;
        if (status == null || player == null) {
            flashTicks = 0;
        } else if (ControlExpertStatusHudRules.startsFlash(status.isDisrupted(), status.isStunned(),
                wasInstinctPressed, pressed)) {
            flashTicks = ControlExpertStatusHudRules.FLASH_TICKS;
            // Played locally on this client only. / 只在本客户端本地播放。
            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.8F, 0.6F);
        } else {
            flashTicks = ControlExpertStatusHudRules.tickFlash(flashTicks);
        }
        wasInstinctPressed = pressed;
    }

    static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        // HudRenderCallback still fires under F1, unlike Wathe's own HUD layer.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同。
        if (client.player == null || client.world == null || client.options.hudHidden) {
            return;
        }
        // Follow Wathe's HUD visibility so the lines never float alone.
        // 跟随 Wathe 的 HUD 显示状态，避免文本单独悬浮。
        if (WatheClient.trainComponent == null || !WatheClient.trainComponent.hasHud()) {
            return;
        }
        ControlExpertStatusComponent status = ControlExpertStatusClient.localActiveStatus();
        if (status == null) {
            return;
        }
        List<ControlExpertStatusHudRules.Line> lines = ControlExpertStatusHudRules.lines(
                status.getStunTicks(), status.getDisruptTicks(), flashTicks);
        TextRenderer renderer = client.textRenderer;
        for (int index = 0; index < lines.size(); index++) {
            ControlExpertStatusHudRules.Line line = lines.get(index);
            Text text = line.hasSeconds()
                    ? Text.translatable(line.key(), line.seconds())
                    : Text.translatable(line.key());
            int x = (context.getScaledWindowWidth() - renderer.getWidth(text)) / 2;
            int y = ControlExpertStatusHudRules.lineY(context.getScaledWindowHeight(), renderer.fontHeight,
                    index, lines.size());
            context.drawTextWithShadow(renderer, text, x, y, line.color());
        }
    }
}
