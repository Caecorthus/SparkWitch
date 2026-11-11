package dev.caecorthus.sparkwitch.client.hunter;

import dev.caecorthus.sparkwitch.compat.SparkTraitsMoodFlagBridge;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterRules;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterTrapEntity;
import dev.caecorthus.sparkfactionapi.api.PoliceRoles;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Locale;

/**
 * HUD for the class-1 dismantle channel: "拆除中", ten boxes and the remaining seconds with one decimal, just above the
 * hotbar, styled after the owner's reference image. The timer starts on the first accepted right-click and restarts
 * whenever a press gap exceeds {@link HunterRules#DISMANTLE_PRESS_TIMEOUT_TICKS}, matching the server session.
 * 第一类拆除引导的 HUD：物品栏上方显示“拆除中”、十个方框与一位小数的剩余秒数，样式参照所有者提供的图片。计时从第一次被
 * 接受的右键开始，按键间隔超过 {@link HunterRules#DISMANTLE_PRESS_TIMEOUT_TICKS} 时与服务器会话一样重新开始。
 */
public final class HunterTrapDismantleHud {
    private static final int SEGMENTS = 10;
    private static final int TEXT_COLOR = 0xFFFFAA00;
    private static final int FILLED_COLOR = 0xFF55FF55;
    private static final int EMPTY_COLOR = 0xFFFF5555;
    private static final String FILLED_BLOCK = "■";
    private static final String EMPTY_BLOCK = "□";
    private static final String BRACKET_OPEN = "[";
    private static final String BRACKET_CLOSE = "]";

    private static @Nullable HunterTrapEntity trap;
    private static long startTick;
    private static long lastPressTick;
    private static boolean registered;

    private HunterTrapDismantleHud() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        UseBlockCallback.EVENT.register(HunterTrapDismantleHud::interact);
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }

    private static ActionResult interact(
            PlayerEntity player,
            World world,
            Hand hand,
            BlockHitResult hitResult
    ) {
        if (!world.isClient || hand != Hand.MAIN_HAND || !player.isSneaking()
                || !(player instanceof ClientPlayerEntity)) {
            return ActionResult.PASS;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (!game.isRunning()) {
            return ActionResult.PASS;
        }
        Role role = game.getRole(player);
        if (role == null) {
            return ActionResult.PASS;
        }
        Identifier roleId = role.identifier();
        if (HunterRules.isTrapKiller(roleId, role.getFaction() == Faction.KILLER)) {
            // Class 2 removes the trap on the first press; there is no channel to show.
            // 第二类第一次按键就拆除，没有可显示的引导。
            return ActionResult.PASS;
        }
        if (!HunterRules.isTrapPolice(roleId, isRegisteredPolice(role))
                && !SparkTraitsMoodFlagBridge.isImpostorActive(player)) {
            return ActionResult.PASS;
        }
        HunterTrapEntity hit = nearestTrap(world, hitResult);
        if (hit == null || !player.canSee(hit)) {
            return ActionResult.PASS;
        }
        press(hit, world.getTime());
        return ActionResult.SUCCESS;
    }

    private static void press(HunterTrapEntity target, long now) {
        if (trap != target || now - lastPressTick > HunterRules.DISMANTLE_PRESS_TIMEOUT_TICKS) {
            trap = target;
            startTick = now;
        }
        lastPressTick = now;
    }

    private static boolean isRegisteredPolice(Role role) {
        try {
            return PoliceRoles.contains(role);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static @Nullable HunterTrapEntity nearestTrap(World world, BlockHitResult hitResult) {
        Box searchBox = new Box(hitResult.getBlockPos()).expand(1.5D);
        return world.getEntitiesByClass(
                        HunterTrapEntity.class,
                        searchBox,
                        candidate -> candidate.squaredDistanceTo(hitResult.getPos()) < 2.25D
                ).stream()
                .min(Comparator.comparingDouble((HunterTrapEntity candidate) ->
                                candidate.squaredDistanceTo(hitResult.getPos()))
                        .thenComparingInt(HunterTrapEntity::getId))
                .orElse(null);
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        HunterTrapEntity target = trap;
        if (target == null) {
            return;
        }
        World world = client.world;
        ClientPlayerEntity player = client.player;
        if (world == null || player == null || target.isRemoved() || player.isSpectator()) {
            reset();
            return;
        }
        long now = world.getTime();
        if (now - lastPressTick > HunterRules.DISMANTLE_PRESS_TIMEOUT_TICKS) {
            reset();
            return;
        }

        long elapsed = Math.max(0L, now - startTick);
        long remainingTicks = Math.max(0L, HunterRules.DISMANTLE_CHANNEL_TICKS - elapsed);
        int filled = (int) Math.min(SEGMENTS,
                (elapsed * SEGMENTS + HunterRules.DISMANTLE_CHANNEL_TICKS - 1) / HunterRules.DISMANTLE_CHANNEL_TICKS);

        Text label = Text.translatable("hud.sparkwitch.hunter_trap.dismantling");
        String filledBlocks = FILLED_BLOCK.repeat(filled);
        String emptyBlocks = EMPTY_BLOCK.repeat(SEGMENTS - filled);
        String time = String.format(Locale.ROOT, "%.1fs", remainingTicks / 20.0F);

        int width = client.textRenderer.getWidth(label)
                + client.textRenderer.getWidth(" " + BRACKET_OPEN + filledBlocks + emptyBlocks + BRACKET_CLOSE)
                + client.textRenderer.getWidth(" " + time);
        int x = (context.getScaledWindowWidth() - width) / 2;
        int y = context.getScaledWindowHeight() - 48;
        context.drawTextWithShadow(client.textRenderer, label, x, y, TEXT_COLOR);
        x += client.textRenderer.getWidth(label);
        context.drawTextWithShadow(client.textRenderer, Text.literal(" " + BRACKET_OPEN), x, y, TEXT_COLOR);
        x += client.textRenderer.getWidth(" " + BRACKET_OPEN);
        if (!filledBlocks.isEmpty()) {
            context.drawTextWithShadow(client.textRenderer, Text.literal(filledBlocks), x, y, FILLED_COLOR);
            x += client.textRenderer.getWidth(filledBlocks);
        }
        if (!emptyBlocks.isEmpty()) {
            context.drawTextWithShadow(client.textRenderer, Text.literal(emptyBlocks), x, y, EMPTY_COLOR);
            x += client.textRenderer.getWidth(emptyBlocks);
        }
        context.drawTextWithShadow(client.textRenderer, Text.literal(BRACKET_CLOSE), x, y, TEXT_COLOR);
        x += client.textRenderer.getWidth(BRACKET_CLOSE);
        context.drawTextWithShadow(client.textRenderer, Text.literal(" " + time), x, y, TEXT_COLOR);
    }

    private static void reset() {
        trap = null;
        startTick = 0L;
        lastPressTick = 0L;
    }
}
