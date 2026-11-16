package dev.caecorthus.sparkwitch.client.sherry;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityController;
import dev.caecorthus.sparkwitch.client.hud.CachedHudText;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.client.WatheClient;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * Draws 橘雪莉's two-row readout ({@link SherryHudRules}) in the shared bottom-right corner slot: 推演 on the shared
 * ability key above, 巨力魔法 on the Role Skill 2 key below. Reads only the local player's raw synced role, the
 * owner-only {@link SherryComponent} timers (counted down locally between syncs), the synced shop balance and the
 * synced mana; both key names come from the real bindings. Her mana itself stays on the shared top-right mana row
 * under Wathe's money, far from this corner. Presentation only; the server decides every press.
 * 在共享右下角位置绘制橘雪莉的两行读数（{@link SherryHudRules}）：上行为共享技能键上的推演，下行为职业技能 2 键上的巨力魔法。
 * 只读取本地玩家真实同步的职业、仅同步给本人的 {@link SherryComponent} 计时（两次同步之间本地倒数）、同步的金币余额与
 * 同步的魔力；两个键名都取自真实键位。魔力数值本身仍显示在右上角 Wathe 金币下方的共享魔力行，远离这个角落。仅为展示；
 * 每次按键都由服务端决定。
 */
public final class SherryHud {
    // Render-thread cache, one per row: each row's Text and widths depend only on its line, its key text, the active
    // Language (replaced on every resource reload), the font renderer and the glyph options (see CachedHudText).
    // 渲染线程缓存，每行一个：每行的文本与宽度只取决于该行、其键名文本、当前 Language（每次资源重载都会换新）、
    // 字体渲染器与字形选项（见 CachedHudText）。
    private static final CachedHudText<SherryHudRules.Line> DEDUCTION = new CachedHudText<>(SherryHud::text,
            SherryHudRules.Line::number, SherryHudRules.Line::measuredNumber);
    private static final CachedHudText<SherryHudRules.Line> MIGHTY = new CachedHudText<>(SherryHud::text,
            SherryHudRules.Line::number, SherryHudRules.Line::measuredNumber);

    private SherryHud() {
    }

    static void register() {
        HudRenderCallback.EVENT.register(SherryHud::render);
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        if (player == null || client.world == null || !SherryHudRules.showsHud(
                SherryClient.isActiveSherry(player),
                client.options.hudHidden,
                WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud(),
                client.currentScreen != null)) {
            return;
        }
        SherryComponent component = SherryComponent.KEY.get(player);
        SherryHudRules.Line deduction = SherryHudRules.deductionLine(
                component.getDeductionCooldownTicks(), PlayerShopComponent.KEY.get(player).getBalance());
        SherryHudRules.Line mighty = SherryHudRules.mightyLine(component.isMightyForfeited(),
                component.getMightyWindowTicks(), component.getMightyCooldownTicks(),
                WitchPlayerComponent.KEY.get(player).getMana());
        TextRenderer renderer = client.textRenderer;
        DEDUCTION.update(deduction, SparkWitchClient.abilityKeyText(), renderer);
        MIGHTY.update(mighty, SecondaryAbilityController.secondaryKeyText(), renderer);
        // Both rows rise together: measured with the wider row. / 两行一同抬升：按较宽的一行测量。
        int measured = Math.max(DEDUCTION.measuredWidth(), MIGHTY.measuredWidth());
        int width = context.getScaledWindowWidth();
        int lowerY = SherryHudRules.lowerRowY(width, context.getScaledWindowHeight(), measured,
                renderer.fontHeight);
        int upperY = SherryHudRules.upperRowY(lowerY, renderer.fontHeight);
        context.drawTextWithShadow(renderer, DEDUCTION.text(), SherryHudRules.rowX(width, DEDUCTION.width()),
                upperY, deduction.color());
        context.drawTextWithShadow(renderer, MIGHTY.text(), SherryHudRules.rowX(width, MIGHTY.width()), lowerY,
                mighty.color());
    }

    /** Key name first when the row has one, then its number when it has one. / 有按键时按键名在前，其后是数字（如果有）。 */
    private static Text text(SherryHudRules.Line line, int number, Text keyText) {
        if (line.keyHint()) {
            return line.hasNumber()
                    ? Text.translatable(line.key(), keyText, number)
                    : Text.translatable(line.key(), keyText);
        }
        return line.hasNumber() ? Text.translatable(line.key(), number) : Text.translatable(line.key());
    }
}
