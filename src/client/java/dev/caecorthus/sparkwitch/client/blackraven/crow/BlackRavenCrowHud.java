package dev.caecorthus.sparkwitch.client.blackraven.crow;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityController;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenFeatherRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The Raven's own Raven Form HUD: the status line in {@code BlackRavenHudRenderer} (never the witch skills panel), and
 * while flying the one-slot crow hotbar (the Homing Feather, {@code sparkwitch:homing_feather}) in place of the real
 * hotbar plus a plain crosshair in place of Wathe's body-based one. The real inventory is never touched.
 * 黑羽鸦自己的化鸦 HUD：BlackRavenHudRenderer 中的状态行（绝不进入魔女技能面板）；飞行期间以只有一格（归羽）的乌鸦快捷栏取代真实快捷栏，
 * 并以普通准星取代 Wathe 基于本体的准星。真实物品栏从不改动。
 */
public final class BlackRavenCrowHud {
    private static final Identifier HOTBAR = Identifier.of("wathe", "hud/hotbar");
    private static final Identifier HOTBAR_SELECTION = Identifier.of("wathe", "hud/hotbar_selection");
    private static final Identifier CROSSHAIR = Identifier.of("wathe", "hud/crosshair");
    private static final Identifier CROSSHAIR_TARGET = Identifier.of("wathe", "hud/crosshair_target");
    private static final int HOTBAR_TEXTURE_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int SLOT_WIDTH = 21;
    private static final int CROSSHAIR_SIZE = 3;
    private static final int HINT_COLOR = 0xFFD8D0E8;

    private BlackRavenCrowHud() {
    }

    /** The status line for the local Raven, or null without the component. / 本地黑羽鸦的状态行；没有组件时为 null。 */
    public static @Nullable Text statusLine(ClientPlayerEntity player) {
        BlackRavenCrowPlayerComponent crow = BlackRavenCrowPlayerComponent.KEY.getNullable(player);
        if (crow == null) {
            return null;
        }
        long now = player.getWorld().getTime();
        long landing = Math.max(0L, crow.getLandingHoldUntilTick() - now);
        Text key = SecondaryAbilityController.secondaryKeyText();
        return switch (BlackRavenCrowClientRules.hudState(crow.isUnlocked(), crow.isActive(), landing,
                crow.getCooldownTicks())) {
            case FLYING -> Text.translatable("hud.sparkwitch.black_raven_crow.flying",
                    BlackRavenCrowClientRules.seconds(
                            BlackRavenCrowRules.remainingSessionTicks(crow.getSessionEndTick(), now)), key);
            case LANDING -> Text.translatable("hud.sparkwitch.black_raven_crow.landing",
                    BlackRavenCrowClientRules.seconds(landing));
            case LOCKED -> Text.translatable("hud.sparkwitch.black_raven_crow.locked", crow.getCompletedTasks(),
                    BlackRavenCrowRules.UNLOCK_TASKS);
            case COOLDOWN -> Text.translatable("hud.sparkwitch.black_raven_crow.cooldown",
                    BlackRavenCrowClientRules.seconds(crow.getCooldownTicks()));
            case READY -> Text.translatable("hud.sparkwitch.black_raven_crow.ready", key);
        };
    }

    /**
     * {@code renderHotbar} HEAD while flying: one slot holding the Homing Feather until it is dropped, with the drop-key
     * hint above it. Until crow-world registers the item it resolves to air and the slot stays empty.
     * 飞行时的 renderHotbar 开头：一格快捷栏，丢出前放着归羽，上方显示丢弃键提示。crow-world 注册物品之前它解析为空气，格子保持为空。
     */
    public static void renderHotbar(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        int x = width / 2 - (SLOT_WIDTH + 1) / 2;
        int y = height - HOTBAR_HEIGHT;
        context.drawGuiTexture(HOTBAR, HOTBAR_TEXTURE_WIDTH, HOTBAR_HEIGHT, 0, 0, x, y, SLOT_WIDTH, HOTBAR_HEIGHT);
        context.drawGuiTexture(HOTBAR, HOTBAR_TEXTURE_WIDTH, HOTBAR_HEIGHT, HOTBAR_TEXTURE_WIDTH - 1, 0,
                x + SLOT_WIDTH, y, 1, HOTBAR_HEIGHT);
        context.drawGuiTexture(HOTBAR_SELECTION, x - 1, y - 1, 24, 23);
        ItemStack feather = homingFeather();
        if (feather.isEmpty() || !BlackRavenCrowPilotClient.showsFeather(client.player)) {
            return;
        }
        context.drawItem(feather, x + 3, y + 3);
        TextRenderer renderer = client.textRenderer;
        Text hint = Text.translatable("hud.sparkwitch.black_raven_crow.drop_hint",
                client.options.dropKey.getBoundKeyLocalizedText());
        context.drawTextWithShadow(renderer, hint, (width - renderer.getWidth(hint)) / 2,
                y - renderer.fontHeight - 3, HINT_COLOR);
    }

    /**
     * {@code renderCrosshair} while flying: Wathe's plain crosshair, or its target variant over a block the crow can
     * right-click. Wathe's own crosshair would show the body's knife/gun hints. / 飞行时的准星：Wathe 普通准星，乌鸦可右键的方块
     * 上显示目标样式。Wathe 自己的准星会显示本体的刀/枪提示。
     */
    public static void renderCrosshair(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.options.getPerspective().isFirstPerson()) {
            return;
        }
        Identifier sprite = BlackRavenCrowPilotClient.targetsBlock(client) ? CROSSHAIR_TARGET : CROSSHAIR;
        context.drawGuiTexture(sprite, (context.getScaledWindowWidth() - CROSSHAIR_SIZE) / 2,
                (context.getScaledWindowHeight() - CROSSHAIR_SIZE) / 2, CROSSHAIR_SIZE, CROSSHAIR_SIZE);
    }

    private static ItemStack homingFeather() {
        return new ItemStack(BlackRavenFeatherRegistry.homingFeather());
    }
}
