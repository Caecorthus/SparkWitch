package dev.caecorthus.sparkwitch.client.mixin.witchmaiden;

import dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen.MiniWitherSkinEntryButton;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.SkinOptionsScreen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a "Mini Wither Appearance..." button at the end of vanilla Options → Skin Customization (owner 2026-11-02),
 * opening the client-only Mini Wither skin settings. Like {@code ScopeAccessibilityOptionsMixin}, it appends one row
 * to the option list at {@code addOptions} TAIL, after vanilla's single {@code addAll}, and never cancels or changes
 * the vanilla rows. Client-only; nothing is written to {@code options.txt} or sent to the server.
 * 在原版「选项 → 皮肤自定义」末尾加入「小凋灵外观...」按钮（所有者 2026-11-02），打开纯客户端的小凋灵皮肤设置。与
 * {@code ScopeAccessibilityOptionsMixin} 一样，在 {@code addOptions} 的 TAIL（原版唯一一次 {@code addAll} 之后）向选项列表追加
 * 一行，从不取消或改动原版的选项行。纯客户端；不写入 {@code options.txt}，也不发送到服务器。
 */
@Mixin(SkinOptionsScreen.class)
public abstract class MiniWitherSkinOptionsMixin extends GameOptionsScreen {
    private MiniWitherSkinOptionsMixin(Screen parent, GameOptions gameOptions, Text title) {
        super(parent, gameOptions, title);
    }

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void sparkwitch$appendMiniWitherSkinButton(CallbackInfo ci) {
        if (this.body != null) {
            MiniWitherSkinEntryButton.addTo(this.body, this);
        }
    }
}
