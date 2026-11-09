package dev.caecorthus.sparkwitch.client.ferryman;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityController;
import dev.caecorthus.sparkwitch.client.text.HudFontOptions;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanPlayerComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

import java.util.Objects;

/**
 * Draws the Charon's two-row readout ({@link FerrymanHudRules}) in the shared bottom-right corner slot: the pardon on
 * the shared ability key above, the ferry on the secondary key below. Reads only the local player's raw synced role and
 * the owner-only {@link FerrymanPlayerComponent} cooldowns, which the owner's client counts down locally between syncs;
 * both key names come from the real bindings. Gated by {@link FerrymanClient#isActiveLocalCharon} (confirmed server,
 * ACTIVE round, Charon playing and alive in the world) plus Wathe's fade, train HUD and F1. Presentation only; the
 * server decides every press.
 * 在共享右下角位置绘制卡戎的两行读数（{@link FerrymanHudRules}）：上行为共享技能键上的宽恕，下行为第二技能键上的摆渡。
 * 只读取本地玩家真实同步的职业与仅同步给本人的 {@link FerrymanPlayerComponent} 冷却（两次同步之间由拥有者客户端本地
 * 倒数）；两个键名都取自真实键位。由 {@link FerrymanClient#isActiveLocalCharon}（已确认服务器、ACTIVE 回合、卡戎在局
 * 存活且身处世界中）加上 Wathe 淡入、列车 HUD 与 F1 门控。仅为展示；每次按键都由服务端决定。
 */
public final class FerrymanHud {
    // Render-thread cache, as FiendDashHud: the Texts and widths depend only on both lines, both key texts, the active
    // Language (replaced on every resource reload), the font renderer and the glyph options.
    // 渲染线程缓存（同 FiendDashHud）：文本与宽度只取决于两行、两个键名文本、当前 Language（每次资源重载都会换新）、
    // 字体渲染器与字形选项。
    private static FerrymanHudRules.Line cachedPardonLine;
    private static FerrymanHudRules.Line cachedFerryLine;
    private static Text cachedAbilityKey;
    private static Text cachedSecondaryKey;
    private static Language cachedLanguage;
    private static TextRenderer cachedRenderer;
    private static int cachedFontOptions = -1;
    private static Text cachedPardonText;
    private static Text cachedFerryText;
    private static int cachedPardonWidth;
    private static int cachedFerryWidth;
    private static int cachedMeasured;

    private FerrymanHud() {
    }

    static void register() {
        HudRenderCallback.EVENT.register(FerrymanHud::render);
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || !FerrymanClient.isActiveLocalCharon(player)) {
            return;
        }
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD and fade.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 与淡入状态。
        boolean trainHudActive = WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud();
        if (!FerrymanHudRules.showsHud(client.options.hudHidden,
                GameWorldComponent.KEY.get(client.world).getFade(), trainHudActive)) {
            return;
        }
        FerrymanPlayerComponent component = FerrymanPlayerComponent.KEY.get(player);
        FerrymanHudRules.Line pardon = FerrymanHudRules.pardonLine(component.cooldownTicks());
        FerrymanHudRules.Line ferry = FerrymanHudRules.ferryLine(component.ferryCooldownTicks());
        TextRenderer renderer = client.textRenderer;
        Text abilityKey = SparkWitchClient.abilityKeyText();
        Text secondaryKey = SecondaryAbilityController.secondaryKeyText();
        Language language = Language.getInstance();
        int fontOptions = HudFontOptions.current();
        if (!pardon.equals(cachedPardonLine) || !ferry.equals(cachedFerryLine)
                || !Objects.equals(abilityKey, cachedAbilityKey) || !Objects.equals(secondaryKey, cachedSecondaryKey)
                || language != cachedLanguage || renderer != cachedRenderer || fontOptions != cachedFontOptions) {
            cachedPardonText = text(pardon, pardon.seconds(), abilityKey);
            cachedFerryText = text(ferry, ferry.seconds(), secondaryKey);
            cachedPardonWidth = renderer.getWidth(cachedPardonText);
            cachedFerryWidth = renderer.getWidth(cachedFerryText);
            cachedMeasured = Math.max(
                    renderer.getWidth(text(pardon, FerrymanHudRules.measuredSeconds(pardon.seconds()), abilityKey)),
                    renderer.getWidth(text(ferry, FerrymanHudRules.measuredSeconds(ferry.seconds()), secondaryKey)));
            cachedPardonLine = pardon;
            cachedFerryLine = ferry;
            cachedAbilityKey = abilityKey;
            cachedSecondaryKey = secondaryKey;
            cachedLanguage = language;
            cachedRenderer = renderer;
            cachedFontOptions = fontOptions;
        }
        int width = context.getScaledWindowWidth();
        int lowerY = FerrymanHudRules.lowerRowY(width, context.getScaledWindowHeight(), cachedMeasured,
                renderer.fontHeight);
        int upperY = FerrymanHudRules.upperRowY(lowerY, renderer.fontHeight);
        context.drawTextWithShadow(renderer, cachedPardonText, FerrymanHudRules.rowX(width, cachedPardonWidth), upperY,
                pardon.color());
        context.drawTextWithShadow(renderer, cachedFerryText, FerrymanHudRules.rowX(width, cachedFerryWidth), lowerY,
                ferry.color());
    }

    private static Text text(FerrymanHudRules.Line line, int seconds, Text keyText) {
        return line.hasSeconds()
                ? Text.translatable(line.key(), keyText, seconds)
                : Text.translatable(line.key(), keyText);
    }
}
