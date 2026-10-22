package dev.caecorthus.sparkwitch.client.blind.kit;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.blind.BlindClientStatus;
import dev.caecorthus.sparkwitch.client.blind.BlindView;
import dev.caecorthus.sparkwitch.client.text.HudFontOptions;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownEntryAccessor;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindParticipants;
import dev.caecorthus.sparkwitch.roles.civilian.blind.kit.BlindInventoryRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

/**
 * Draws the Blind's owner-only status lines ({@link BlindKitHudRules}) after the world, so they sit over the black
 * screen. Reads only the local player's own synced {@code sparkwitch:blind} countdowns, own vanilla cane cooldown (a
 * forced cooldown such as the Fiend aura can outlast the component's) and own inventory.
 * 在世界之后绘制盲人仅本人可见的状态行（{@link BlindKitHudRules}），因此显示在黑屏之上。只读取本地玩家自己同步的
 * {@code sparkwitch:blind} 倒计时、自己的原版盲杖冷却（魔人光环等强制冷却可能长于组件冷却）以及自己的背包。
 */
final class BlindKitHud {
    private static boolean registered;
    // Render-thread cache per line slot: a line's Texts and widths are a pure function of the line, the ability key
    // text, the active Language (replaced on every resource reload) and the font renderer, so they are rebuilt only
    // when one changes (about once a second while a countdown runs).
    // 渲染线程上按行位置的缓存：一行的文本与宽度只取决于该行、技能按键文本、当前 Language（每次资源重载都会换新）与
    // 字体渲染器，因此只在其中之一变化时重建（倒计时期间约每秒一次）。
    // The key also holds the font glyph options (`HudFontOptions`), which a toggle changes without a reload.
    // 键还包含字形选项（`HudFontOptions`），切换它们不会触发资源重载。
    private static BuiltLine[] cachedLines = new BuiltLine[0];
    private static Language cachedLanguage;
    private static TextRenderer cachedRenderer;
    private static int cachedFontOptions = -1;

    private BlindKitHud() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (SparkWitchServerConnection.isConfirmedServer()) {
                render(context);
            }
        });
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        boolean trainHudActive = WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud();
        GameWorldComponent game = GameWorldComponent.KEY.get(client.world);
        boolean gameActive = game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
        if (!BlindKitHudRules.showsHud(client.options.hudHidden, gameActive, game.getFade(), trainHudActive,
                BlindView.isActive(client), NoellesTaotieSeekerBridge.isSwallowed(player))) {
            return;
        }
        BlindClientStatus status = BlindClientStatus.of(player);
        List<BlindKitHudRules.Line> lines = BlindKitHudRules.lines(
                Math.max(status.caneCooldownTicks(), caneItemCooldown(player)),
                status.caneActiveTicks(),
                status.attuneCooldownTicks(),
                status.attuneActiveTicks(),
                comTacState(player));
        // Defensive: the shared bottom-right line draws only for an active witch skill id, which a Blind lacks.
        // 防御性处理：共享右下角技能行只在存在魔女技能 id 时绘制，而盲人没有。
        boolean sharedLineOccupied = WitchPlayerComponent.KEY.get(player).getActiveSkillId() != null;
        TextRenderer renderer = client.textRenderer;
        BuiltLine[] built = build(lines, renderer);
        int width = context.getScaledWindowWidth();
        int widest = 0;
        for (BuiltLine line : built) {
            widest = Math.max(widest, line.measuredWidth());
        }
        int bottomOffset = BlindKitHudRules.bottomOffset(width, widest, renderer.fontHeight, sharedLineOccupied);
        for (int index = 0; index < built.length; index++) {
            BuiltLine line = built[index];
            int x = BlindKitHudRules.rowX(width, line.width());
            int y = BlindKitHudRules.rowY(context.getScaledWindowHeight(), renderer.fontHeight, index, built.length,
                    bottomOffset);
            context.drawTextWithShadow(renderer, line.text(), x, y, line.line().color());
        }
    }

    private static BuiltLine[] build(List<BlindKitHudRules.Line> lines, TextRenderer renderer) {
        Language language = Language.getInstance();
        int fontOptions = HudFontOptions.current();
        if (language != cachedLanguage || renderer != cachedRenderer || fontOptions != cachedFontOptions) {
            cachedLanguage = language;
            cachedRenderer = renderer;
            cachedFontOptions = fontOptions;
            Arrays.fill(cachedLines, null);
        }
        if (cachedLines.length != lines.size()) {
            cachedLines = Arrays.copyOf(cachedLines, lines.size());
        }
        Text keyText = null;
        for (int index = 0; index < lines.size(); index++) {
            BlindKitHudRules.Line line = lines.get(index);
            if (line.keyHint() && keyText == null) {
                keyText = SparkWitchClient.abilityKeyText();
            }
            Text key = line.keyHint() ? keyText : null;
            BuiltLine cached = cachedLines[index];
            if (cached != null && cached.line().equals(line) && Objects.equals(cached.keyText(), key)) {
                continue;
            }
            Text text = text(line, line.seconds(), key);
            int textWidth = renderer.getWidth(text);
            int measuredSeconds = BlindKitHudRules.measuredSeconds(line.seconds());
            int measuredWidth = measuredSeconds == line.seconds()
                    ? textWidth
                    : renderer.getWidth(text(line, measuredSeconds, key));
            cachedLines[index] = new BuiltLine(line, key, text, textWidth, measuredWidth);
        }
        return cachedLines;
    }

    private static Text text(BlindKitHudRules.Line line, int seconds, Text keyText) {
        List<Object> args = new ArrayList<>(3);
        if (line.nameKey() != null) {
            args.add(Text.translatable(line.nameKey()));
        }
        if (line.keyHint()) {
            args.add(keyText);
        }
        if (line.hasSeconds()) {
            args.add(seconds);
        }
        return Text.translatable(line.key(), args.toArray());
    }

    private static BlindKitHudRules.ComTac comTacState(ClientPlayerEntity player) {
        if (BlindParticipants.wearsComTac(player)) {
            return BlindKitHudRules.ComTac.WORN;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (BlindInventoryRules.isComTac(inventory.getStack(slot))) {
                return BlindKitHudRules.ComTac.NOT_WORN;
            }
        }
        return BlindKitHudRules.ComTac.NONE;
    }

    private static int caneItemCooldown(ClientPlayerEntity player) {
        ItemCooldownManager manager = player.getItemCooldownManager();
        if (!(manager instanceof ItemCooldownManagerAccessor accessor)
                || !(accessor.sparkwitch$getEntries().get(SparkWitchItems.whiteCane())
                        instanceof ItemCooldownEntryAccessor entry)) {
            return 0;
        }
        return Math.max(0, entry.sparkwitch$getEndTick() - accessor.sparkwitch$getTick());
    }

    /** One built line: its Text, drawn width and lift-measuring width. / 一行构建结果：文本、绘制宽度与抬升测量宽度。 */
    private record BuiltLine(BlindKitHudRules.Line line, Text keyText, Text text, int width, int measuredWidth) {
    }
}
