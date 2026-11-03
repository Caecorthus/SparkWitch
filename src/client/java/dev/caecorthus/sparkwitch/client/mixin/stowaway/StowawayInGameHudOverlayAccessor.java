package dev.caecorthus.sparkwitch.client.mixin.stowaway;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only view of the vanilla action-bar slot, so the Stowaway's unease line is written only while the slot is free
 * or already shows that line, and cleared only when it is still the line on screen. Every other action-bar message
 * (poison, Taotie and Survival countdowns, role hints) keeps its full display time.
 * 原版动作栏槽位的只读视图：偷渡客的不安提示只在槽位空闲或已显示该提示时写入，也只在屏幕上仍是该提示时清除。其他所有
 * 动作栏消息（中毒、饕餮与生存倒计时、职业提示）保持完整的显示时长。
 */
@Mixin(InGameHud.class)
public interface StowawayInGameHudOverlayAccessor {
    @Accessor("overlayMessage")
    @Nullable Text sparkwitch$getOverlayMessage();

    @Accessor("overlayRemaining")
    int sparkwitch$getOverlayRemaining();
}
