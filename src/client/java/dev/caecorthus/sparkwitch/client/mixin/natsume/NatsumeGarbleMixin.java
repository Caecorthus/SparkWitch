package dev.caecorthus.sparkwitch.client.mixin.natsume;

import dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

/**
 * A hypnotised player sees every HUD line as gibberish.
 * <p>Owner 2026-11-03: only the HUD is garbled, item names and tooltips are left alone. The two hypnosis lines stay
 * readable, otherwise she could not tell how long is left or why her items refuse to work.
 * 被催眠者看到的每一行 HUD 都是乱码。
 * <p>所有者 2026-11-03：只乱 HUD，物品名与 tooltip 不动。两条催眠提示保持可读，
 * 否则她无法知道还剩几秒、也看不懂道具为什么用不了。
 */
@Mixin(DrawContext.class)
public abstract class NatsumeGarbleMixin {

    private static final Random RANDOM = new Random();
    private static final ThreadLocal<Boolean> REDRAWING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(
            method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$garbleHudText(TextRenderer renderer, Text text, int x, int y, int color, boolean shadow,
                                          CallbackInfoReturnable<Integer> cir) {
        if (Boolean.TRUE.equals(REDRAWING.get())) {
            return;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !NatsumeComponent.KEY.get(player).isHypnotised()) {
            return;
        }
        String raw = text.getString();
        if (raw.isEmpty() || isExempt(raw)) {
            return;
        }
        StringBuilder garbled = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            garbled.append(c == ' ' ? ' ' : (char) ('!' + RANDOM.nextInt(94)));
        }
        REDRAWING.set(Boolean.TRUE);
        try {
            cir.setReturnValue(((DrawContext) (Object) this).drawText(
                    renderer, Text.literal(garbled.toString()), x, y, color, shadow));
        } finally {
            REDRAWING.set(Boolean.FALSE);
        }
    }

    private static boolean isExempt(String raw) {
        return raw.contains("催眠") || raw.contains("hypnosis") || raw.contains("Hypnosis");
    }
}
