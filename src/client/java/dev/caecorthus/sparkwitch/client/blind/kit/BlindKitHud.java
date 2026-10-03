package dev.caecorthus.sparkwitch.client.blind.kit;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.blind.BlindClientStatus;
import dev.caecorthus.sparkwitch.client.blind.BlindView;
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
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/**
 * Draws the Blind's owner-only status lines ({@link BlindKitHudRules}) after the world, so they sit over the black
 * screen. Reads only the local player's own synced {@code sparkwitch:blind} countdowns, own vanilla cane cooldown (a
 * forced cooldown such as the Fiend aura can outlast the component's) and own inventory.
 * 在世界之后绘制盲人仅本人可见的状态行（{@link BlindKitHudRules}），因此显示在黑屏之上。只读取本地玩家自己同步的
 * {@code sparkwitch:blind} 倒计时、自己的原版盲杖冷却（魔人光环等强制冷却可能长于组件冷却）以及自己的背包。
 */
final class BlindKitHud {
    private static boolean registered;

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
        int width = context.getScaledWindowWidth();
        int widest = 0;
        for (BlindKitHudRules.Line line : lines) {
            widest = Math.max(widest, renderer.getWidth(text(line, BlindKitHudRules.measuredSeconds(line.seconds()))));
        }
        int bottomOffset = BlindKitHudRules.bottomOffset(width, widest, renderer.fontHeight, sharedLineOccupied);
        for (int index = 0; index < lines.size(); index++) {
            BlindKitHudRules.Line line = lines.get(index);
            Text text = text(line, line.seconds());
            int x = BlindKitHudRules.rowX(width, renderer.getWidth(text));
            int y = BlindKitHudRules.rowY(context.getScaledWindowHeight(), renderer.fontHeight, index, lines.size(),
                    bottomOffset);
            context.drawTextWithShadow(renderer, text, x, y, line.color());
        }
    }

    private static Text text(BlindKitHudRules.Line line, int seconds) {
        List<Object> args = new ArrayList<>(3);
        if (line.nameKey() != null) {
            args.add(Text.translatable(line.nameKey()));
        }
        if (line.keyHint()) {
            args.add(SparkWitchClient.abilityKeyText());
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
}
