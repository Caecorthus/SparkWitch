package dev.caecorthus.sparkwitch.client.mixin.scope;

import dev.caecorthus.sparkwitch.client.compat.SparkDlcSettingsBridge;
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
 * Adds "Scope View", "Lens Resolution" and "Scoped Sensitivity" at the end of vanilla Options → Accessibility (owner
 * decision Q3; Sodium replaces Video Settings). It appends rows to the option list at {@code addOptions} TAIL rather
 * than changing {@code getOptions}' return: older SparkAssist versions append their own options there with a
 * cancellable RETURN {@code @Inject}, and a second cancellable or return-modifying hook on that return could be
 * skipped, depending on apply order. The rows are left out while SparkAssist's SparkDLC settings screen hosts these settings
 * ({@link SparkDlcSettingsBridge#hostsScopeSettings()}, asked at each screen init); without SparkAssist, or when that
 * check fails, they are added as before. Client-only; the options are never written to {@code options.txt}.
 * 在原版「选项 → 辅助功能」末尾加入「开镜画面」「镜内分辨率」与「开镜灵敏度」（所有者决定 Q3；Sodium 会替换视频设置）。
 * 在 {@code addOptions} 的 TAIL 向选项列表追加选项行，而不修改 {@code getOptions} 的返回值：旧版 SparkAssist 在那里用可取消的
 * RETURN {@code @Inject} 追加自己的选项，同一返回点上的第二个可取消或改返回值的钩子可能因应用顺序被跳过。当 SparkAssist 的
 * SparkDLC 设置界面承载这些设置时（{@link SparkDlcSettingsBridge#hostsScopeSettings()}，每次界面初始化时询问）不加入这些行；
 * 未安装 SparkAssist 或该检查失败时照旧加入。纯客户端；这些选项从不写入 {@code options.txt}。
 */
@Mixin(AccessibilityOptionsScreen.class)
public abstract class ScopeAccessibilityOptionsMixin extends GameOptionsScreen {
    private ScopeAccessibilityOptionsMixin(Screen parent, GameOptions gameOptions, Text title) {
        super(parent, gameOptions, title);
    }

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void sparkwitch$appendScopeOptions(CallbackInfo ci) {
        if (this.body != null && !SparkDlcSettingsBridge.hostsScopeSettings()) {
            ScopeOptions.addTo(this.body);
        }
    }
}
