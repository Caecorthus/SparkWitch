package dev.caecorthus.sparkwitch.client.stowaway;

import dev.caecorthus.sparkwitch.client.mixin.stowaway.StowawayInGameHudOverlayAccessor;
import dev.caecorthus.sparkwitch.roles.neutral.stowaway.StowawayRules;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Stowaway client senses (owner request 2026-11-02):
 * <ul>
 *   <li>A living Stowaway feels every player within 10 blocks: the action bar shows "you feel a sudden unease", and
 *       a soft low heartbeat plays locally. The line takes the action bar only while it is free or already shows the
 *       line, so other action-bar messages are never cut short; it is cleared only while it is still on screen.</li>
 *   <li>The beat rate rises linearly from 80 rpm at 10 blocks to 160 rpm at 0 blocks, using the nearest player.</li>
 *   <li>Instinct ({@link StowawayClientVisibility}): a living viewer without line of sight to a living Stowaway gets
 *       the Survival Master's {@code skip()}.</li>
 * </ul>
 * 偷渡客客户端感知（所有者 2026-11-02 需求）：
 * <ul>
 *   <li>存活的偷渡客能感到 10 格内的每名玩家：动作栏显示「你感到一阵不安」，并在本地播放柔和低沉的心跳声。该提示只在
 *       动作栏空闲或已显示该提示时占用动作栏，因此不会打断其他动作栏消息；也只在它仍显示时才被清除。</li>
 *   <li>心跳频率随最近玩家距离从 10 格的 80 rpm 线性升到 0 格的 160 rpm。</li>
 *   <li>本能（{@link StowawayClientVisibility}）：存活的观察者对存活的偷渡客没有视线时，得到与生存大师相同的
 *       {@code skip()}。</li>
 * </ul>
 */
public final class StowawaySensesClient {
    private static final double SENSE_RANGE = 10.0;
    private static final double MIN_RPM = 80.0;
    private static final double MAX_RPM = 160.0;
    private static final float HEARTBEAT_VOLUME = 0.35F;
    /** Low pitch turns the note-block bass drum into a soft heartbeat thump. / 低音高把音符盒底鼓变成柔和的心跳闷响。 */
    private static final float HEARTBEAT_PITCH = 0.65F;

    private static boolean registered;
    private static double beatTimer;
    private static boolean inRange;
    private static @Nullable Text shownLine;

    private StowawaySensesClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(StowawaySensesClient::tick);
        GetInstinctHighlight.EVENT.register(StowawaySensesClient::highlight);
    }

    /**
     * The Survival Master's instinct rule: {@code skip()} (priority 100) for an unseen living Stowaway.
     * 生存大师的本能规则：对没有视线的存活偷渡客返回 {@code skip()}（优先级 100）。
     */
    private static GetInstinctHighlight.HighlightResult highlight(Entity entity) {
        return StowawayClientVisibility.shouldHideFromLocalViewer(entity)
                ? GetInstinctHighlight.HighlightResult.skip()
                : null;
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            reset(client);
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
        if (game == null
                || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !StowawayRules.isStowaway(game.getRole(player))
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || player.isSpectator()) {
            reset(client);
            return;
        }

        double nearestSquared = Double.MAX_VALUE;
        for (PlayerEntity other : player.getWorld().getPlayers()) {
            if (other == player
                    || other.isSpectator()
                    || other.isCreative()
                    || !GameFunctions.isPlayerPlayingAndAlive(other)
                    || !game.hasAnyRole(other)) {
                continue;
            }
            double squared = player.squaredDistanceTo(other);
            if (squared < nearestSquared) {
                nearestSquared = squared;
            }
        }

        if (nearestSquared > SENSE_RANGE * SENSE_RANGE) {
            reset(client);
            return;
        }

        double distance = Math.min(SENSE_RANGE, Math.sqrt(nearestSquared));
        // 160 rpm at 0 blocks -> 80 rpm at 10 blocks. / 0 格 160 rpm -> 10 格 80 rpm。
        double rpm = MAX_RPM - (MAX_RPM - MIN_RPM) * (distance / SENSE_RANGE);
        double intervalTicks = 1200.0 / rpm;
        if (!inRange) {
            inRange = true;
            beatTimer = 0.0;
            playHeartbeat(player);
        } else {
            beatTimer += 1.0;
            if (beatTimer >= intervalTicks) {
                // Keep the fractional remainder so 160 rpm really averages 7.5 ticks. / 保留小数余量，使 160 rpm 真正平均 7.5 tick。
                beatTimer -= intervalTicks;
                playHeartbeat(player);
            }
        }

        showUneaseLine(client);
    }

    /**
     * Writes the line only while the action bar is free or still shows this line (refreshing it keeps it up).
     * 只在动作栏空闲或仍显示本提示时写入（刷新即可保持显示）。
     */
    private static void showUneaseLine(MinecraftClient client) {
        StowawayInGameHudOverlayAccessor overlay = (StowawayInGameHudOverlayAccessor) client.inGameHud;
        Text current = overlay.sparkwitch$getOverlayMessage();
        if (current != null && overlay.sparkwitch$getOverlayRemaining() > 0 && current != shownLine) {
            return;
        }
        shownLine = Text.translatable("hud.sparkwitch.stowaway.unease").formatted(Formatting.GRAY);
        client.inGameHud.setOverlayMessage(shownLine, false);
    }

    private static void playHeartbeat(ClientPlayerEntity player) {
        // Client-side playSound is local: only this player hears it. / 客户端 playSound 只在本地播放，仅本人可闻。
        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(), HEARTBEAT_VOLUME, HEARTBEAT_PITCH);
    }

    public static void reset() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isOnThread()) {
            client.execute(() -> reset(client));
            return;
        }
        reset(client);
    }

    private static void reset(MinecraftClient client) {
        if (shownLine != null && client.inGameHud != null
                && ((StowawayInGameHudOverlayAccessor) client.inGameHud).sparkwitch$getOverlayMessage() == shownLine) {
            client.inGameHud.setOverlayMessage(Text.empty(), false);
        }
        shownLine = null;
        inRange = false;
        beatTimer = 0.0;
    }
}
