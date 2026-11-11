package dev.caecorthus.sparkwitch.client.hunter;

import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterRules;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterTrapEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Client view of the class-1 dismantle channel. Once per client tick it reads the channel every loaded trap syncs
 * (dismantler and start tick): the local player's own channel drives the HUD ("拆除中", ten boxes and the remaining
 * seconds with one decimal, just above the hotbar), and the set of dismantlers answers the Hunter's highlight. Nothing
 * here decides anything: the server owns the channel, so the bar restarts and ends exactly when the server's does.
 * 第一类拆除引导的客户端视图。每个客户端刻读取一次所有已加载夹子同步的引导（拆除者与开始刻）：本地玩家自己的引导驱动 HUD
 * （物品栏上方的“拆除中”、十个方框与一位小数的剩余秒数），拆除者集合供猎人的高亮查询。这里不做任何判定：引导归服务端所有，
 * 因此进度条的重新开始与结束都与服务端完全一致。
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
    private static final long NO_CHANNEL = -1L;

    // Client thread only (tick event, HUD and outline queries). / 仅在客户端线程读写（刻事件、HUD 与描边查询）。
    private static final Set<UUID> DISMANTLERS = new HashSet<>();
    private static long ownChannelStartTick = NO_CHANNEL;
    private static boolean registered;

    private HunterTrapDismantleHud() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(HunterTrapDismantleHud::scanChannels);
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }

    /** Whether a loaded trap reports this player as its dismantler. / 是否有已加载的夹子把该玩家记为拆除者。 */
    public static boolean isDismantling(UUID playerUuid) {
        return !DISMANTLERS.isEmpty() && DISMANTLERS.contains(playerUuid);
    }

    private static void scanChannels(MinecraftClient client) {
        DISMANTLERS.clear();
        ownChannelStartTick = NO_CHANNEL;
        ClientWorld world = client.world;
        ClientPlayerEntity player = client.player;
        if (world == null || player == null) {
            return;
        }
        UUID self = player.getUuid();
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof HunterTrapEntity trap)) {
                continue;
            }
            UUID dismantler = trap.getDismantlerUuid();
            if (dismantler == null) {
                continue;
            }
            DISMANTLERS.add(dismantler);
            if (dismantler.equals(self)) {
                ownChannelStartTick = trap.getDismantleStartTick();
            }
        }
    }

    private static void render(DrawContext context) {
        long startTick = ownChannelStartTick;
        if (startTick == NO_CHANNEL) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        ClientPlayerEntity player = client.player;
        if (world == null || player == null || player.isSpectator()) {
            return;
        }

        long elapsed = Math.clamp(world.getTime() - startTick, 0L, (long) HunterRules.DISMANTLE_CHANNEL_TICKS);
        long remainingTicks = HunterRules.DISMANTLE_CHANNEL_TICKS - elapsed;
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
}
