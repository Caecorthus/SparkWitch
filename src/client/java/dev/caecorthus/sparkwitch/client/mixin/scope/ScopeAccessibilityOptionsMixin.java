package dev.caecorthus.sparkwitch.client.mixin.scope;

import dev.caecorthus.sparkwitch.client.scope.ScopeOptions;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds "Scope View" and "Scoped Sensitivity" at the end of vanilla Options → Accessibility (owner decision Q3; Sodium
 * replaces Video Settings). It appends a row to the option list at {@code addOptions} TAIL rather than changing
 * {@code getOptions}' return: SparkAssist appends its own options there with a cancellable RETURN {@code @Inject}, and
 * a second cancellable or return-modifying hook on that return could be skipped, depending on apply order. Client-only;
 * the options are never written to {@code options.txt}.
 * 在原版「选项 → 辅助功能」末尾加入「开镜画面」与「开镜灵敏度」（所有者决定 Q3；Sodium 会替换视频设置）。在
 * {@code addOptions} 的 TAIL 向选项列表追加一行，而不修改 {@code getOptions} 的返回值：SparkAssist 在那里用可取消的
 * RETURN {@code @Inject} 追加自己的选项，同一返回点上的第二个可取消或改返回值的钩子可能因应用顺序被跳过。纯客户端；
 * 这些选项从不写入 {@code options.txt}。
 */
@Mixin(AccessibilityOptionsScreen.class)
public abstract class ScopeAccessibilityOptionsMixin extends GameOptionsScreen {
    private ScopeAccessibilityOptionsMixin(Screen parent, GameOptions gameOptions, Text title) {
        super(parent, gameOptions, title);
    }

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void sparkwitch$appendScopeOptions(CallbackInfo ci) {
        if (this.body != null) {
            this.body.addAll(ScopeOptions.all());
        }
    }
}
