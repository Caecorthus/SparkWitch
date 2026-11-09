package dev.caecorthus.sparkwitch.client.bear.launcher;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Client only: the M2A1 sight's range dial, 25 to 200 m in 25 m steps ({@link M32DialRules}). While aiming, the wheel
 * turns it ({@link M32ScopeProfile#onWheel}): fractional deltas accumulate, each whole notch steps one setting
 * (positive = range up) and plays one local {@link SparkWitchSounds#BEAR_M32_DIAL} tick (pitch
 * {@link M32DialRules#PITCH_UP} up, {@link M32DialRules#PITCH_DOWN} down); a step clamped at either end is silent. The
 * tick uses {@code ClientPlayerEntity#playSound}, which plays only on this client, at the player, in the Players sound
 * category.
 * <p>
 * Session rule: the dial belongs to one holding session, the main hand continuously holding an M32A1 in the same
 * selected hotbar slot. When the session ends (main hand no longer an M32A1, another slot selected, no player or a new
 * player or world object) the dial returns to 25 m and the wheel fraction is dropped. It is never keyed on the
 * ItemStack, which the server's component sync replaces after every shot. Presentation only: the server never sees
 * the dial (the round always flies the same ballistics; the dial only moves the aim point). Client thread only.
 * 仅客户端：M2A1 瞄具的射程刻度，25 到 200 米，每档 25 米（{@link M32DialRules}）。瞄准时由滚轮转动
 * （{@link M32ScopeProfile#onWheel}）：小数增量会累加，每满一格步进一档（正数为射程增大），并在本地播放一声
 * {@link SparkWitchSounds#BEAR_M32_DIAL}（增大音调 {@link M32DialRules#PITCH_UP}，减小 {@link M32DialRules#PITCH_DOWN}）；
 * 在两端被钳制的步进不发声。刻度音使用 {@code ClientPlayerEntity#playSound}，只在本客户端、于玩家位置、按「玩家」音量类别
 * 播放。
 * <p>
 * 会话规则：刻度属于一次持握会话，即主手在同一个选中快捷栏格中持续持有 M32A1。会话结束时（主手不再是 M32A1、选中其他格、
 * 没有玩家或玩家/世界对象已更换）刻度回到 25 米，并丢弃滚轮小数。从不以 ItemStack 为键：服务端的组件同步在每次射击后都会
 * 替换它。纯展示：服务端从不知道刻度（榴弹始终按同一弹道飞行，刻度只移动瞄准点）。仅客户端线程。
 */
public final class M32RangeDial {
    private static int metres = M32DialRules.DEFAULT_METRES;
    private static double accumulated;
    /** The session's key objects; cleared as soon as the session ends. / 会话键对象；会话结束即清除。 */
    @Nullable
    private static ClientPlayerEntity sessionPlayer;
    @Nullable
    private static ClientWorld sessionWorld;
    private static int sessionSlot = -1;

    private M32RangeDial() {
    }

    /** The dialled range in metres (25..200). / 当前刻度（米，25..200）。 */
    public static int metres() {
        return metres;
    }

    /** A wheel delta while aiming (positive = range up). / 瞄准时的滚轮增量（正数为射程增大）。 */
    public static void scroll(double notches) {
        MinecraftClient client = MinecraftClient.getInstance();
        // A slot switch earlier in this tick must not carry the old setting into the new session.
        // 本刻早些时候的切格不得把旧刻度带入新会话。
        observeSession(client);
        M32DialRules.Step step = M32DialRules.step(metres, accumulated, notches);
        metres = step.metres();
        accumulated = step.remainder();
        playTicks(client.player, step.moved());
    }

    /** END_CLIENT_TICK: ends the holding session when its key changes. / 每刻末尾：会话键变化时结束持握会话。 */
    public static void tick(MinecraftClient client) {
        observeSession(client);
    }

    /** Disconnect: 25 m, no fraction, no session. / 断线：25 米、无小数、无会话。 */
    public static void reset() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isOnThread()) {
            client.execute(M32RangeDial::reset);
            return;
        }
        resetDial();
        endSession();
    }

    private static void observeSession(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        boolean holding = player != null && world != null && M32ClientState.holdsLauncherInMainHand(player);
        int slot = holding ? player.getInventory().selectedSlot : -1;
        if (M32DialRules.keepsSession(sessionPlayer, sessionWorld, sessionSlot, holding, player, world, slot)) {
            return;
        }
        resetDial();
        if (holding) {
            sessionPlayer = player;
            sessionWorld = world;
            sessionSlot = slot;
        } else {
            endSession();
        }
    }

    private static void resetDial() {
        metres = M32DialRules.DEFAULT_METRES;
        accumulated = 0.0;
    }

    private static void endSession() {
        sessionPlayer = null;
        sessionWorld = null;
        sessionSlot = -1;
    }

    private static void playTicks(@Nullable ClientPlayerEntity player, int moved) {
        SoundEvent sound = SparkWitchSounds.BEAR_M32_DIAL;
        if (player == null || sound == null || moved == 0) {
            return;
        }
        float pitch = moved > 0 ? M32DialRules.PITCH_UP : M32DialRules.PITCH_DOWN;
        for (int i = Math.abs(moved); i > 0; i--) {
            // Client-side playSound is local: only this player hears it. / 客户端 playSound 只在本地播放，仅本人可闻。
            player.playSound(sound, 1.0F, pitch);
        }
    }
}
