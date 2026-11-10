package dev.caecorthus.sparkwitch.client.bear.hearing;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.bear.hearing.BearHearingRules;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Frozen contract (client thread only): the local player's BEAR hearing state that other BEAR packages call.
 * <ul>
 *   <li>{@link #factor()}: the volume multiplier applied to every scaled in-game sound and to voice chat
 *   ({@code BearHearingRules.factorFor} of the local player on a confirmed SparkWitch server, times any active
 *   {@link #muffle}). Which sounds are scaled: {@link BearHearingSoundRules}.</li>
 *   <li>{@link #muffle}: a ricochet daze muffles world sounds for {@code ticks} (WP3 calls it); see
 *   {@link BearMuffleEnvelope}.</li>
 *   <li>{@link #EXEMPT_SOUND_IDS}: the wearer's own helmet ricochet / visor hit / tinnitus sounds ignore the factor.</li>
 * </ul>
 * Pure volume, never range (H7). Once per client tick the factor is snapshotted for Simple Voice Chat's audio thread
 * ({@link #voiceFactor()}) and, when it changed, playing sources are re-volumed through vanilla's per-sound recompute
 * (gain only: this push never stops a source, see {@link #isGainOnlyPush()}), so taking the earplugs out or a muffle
 * easing back is heard at once.
 * 冻结契约（仅客户端线程）：本地玩家的 BEAR 听力状态，供其他 BEAR 工作包调用。{@link #factor()}：作用于所有受影响的游戏内声音与
 * 语音的音量倍数（已确认 SparkWitch 服务器上本地玩家的 {@code BearHearingRules.factorFor}，再乘以生效中的 {@link #muffle}；哪些
 * 声音受影响见 {@link BearHearingSoundRules}）；{@link #muffle}：跳弹眩晕让世界声音在 {@code ticks} 内发闷（由 WP3 调用，见
 * {@link BearMuffleEnvelope}）；{@link #EXEMPT_SOUND_IDS}：佩戴者自己的头盔跳弹、面罩命中与耳鸣声不受该倍数影响。只改音量不改范围
 * （H7）。每个客户端刻把倍数快照给 Simple Voice Chat 的音频线程（{@link #voiceFactor()}），倍数变化时经由原版逐声音重算为正在播放
 * 的声源重设音量（只改增益：这次推送从不停止声源，见 {@link #isGainOnlyPush()}），因此取下耳塞或发闷缓回都会立即听到。
 */
public final class BearHearingClient {
    /** Sound event ids the factor never scales. / 不受倍数影响的声音事件 id。 */
    public static final Set<Identifier> EXEMPT_SOUND_IDS = Set.of(
            SparkWitch.id("bear.rys_t.ricochet"),
            SparkWitch.id("bear.rys_t.visor_hit"),
            SparkWitch.id("bear.rys_t.tinnitus"));

    private static final BearMuffleEnvelope MUFFLE = new BearMuffleEnvelope();
    private static volatile float voiceFactor = 1.0F;
    private static long clientTicks;
    private static float lastApplied = 1.0F;
    private static int ticksSinceReapply;
    /** Client thread only; see {@link #isGainOnlyPush()}. / 仅客户端线程；见 {@link #isGainOnlyPush()}。 */
    private static boolean gainOnlyPush;
    private static boolean registered;

    private BearHearingClient() {
    }

    /** Called once from {@code BearClientModule}. / 由 {@code BearClientModule} 调用一次。 */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(BearHearingClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    /** Current volume multiplier for the local player (1 when unaffected). / 本地玩家当前的音量倍数（不受影响时为 1）。 */
    public static float factor() {
        MinecraftClient client = MinecraftClient.getInstance();
        float gear = client != null && SparkWitchServerConnection.isConfirmedServer()
                ? BearHearingRules.factorFor(client.player) : 1.0F;
        return gear * MUFFLE.factorAt(clientTicks);
    }

    /**
     * Muffles world sounds to {@code volume} (0..1) for {@code ticks}, easing back afterwards; a longer or deeper call
     * wins over a running one. / 让世界声音在 {@code ticks} 内降到 {@code volume}（0..1），之后渐渐恢复；更长或更深的调用
     * 覆盖正在进行的。
     */
    public static void muffle(int ticks, float volume) {
        MUFFLE.muffle(clientTicks, ticks, volume);
    }

    /**
     * Multiplier for one vanilla sound at the volume seams: {@link #factor()} when scaled, else 1. Render thread.
     * 音量接缝处单个原版声音的倍数：受缩放时为 {@link #factor()}，否则为 1。渲染线程。
     */
    public static float volumeFactor(@Nullable SoundInstance sound) {
        if (sound == null || !BearHearingSoundRules.scales(sound.getId(), sound.getCategory())) {
            return 1.0F;
        }
        return factor();
    }

    /** Last tick's factor for incoming voice; any thread. / 上一刻供传入语音使用的倍数；任意线程。 */
    public static float voiceFactor() {
        return voiceFactor;
    }

    /**
     * True only inside this class's own re-volume push (client thread, synchronous). Vanilla's
     * {@code updateSoundVolume} stops every source whose recomputed volume is {@code <= 0}; a looping "always play"
     * tickable sound that is silent right now (skateboard, drone, minecart) would be stopped and then dropped by the
     * sound engine, heard as a dropout or lost for good. The hearing factor is never 0, so a volume of 0 here is the
     * sound's own; {@code HearingSoundSystemMixin} therefore only sets the gain during this push and never stops a
     * source. Vanilla's own option-slider pushes and other features' pushes are untouched.
     * 仅在本类自己的重设音量推送期间为 true（客户端线程，同步）。原版 {@code updateSoundVolume} 会停止每个重算音量
     * {@code <= 0} 的声源；此刻恰好无声的循环“始终播放”可 tick 声音（滑板、无人机、矿车）会被停止，随后被声音引擎丢弃，
     * 表现为声音中断或彻底消失。听力倍数从不为 0，因此这里的 0 音量来自声音本身；{@code HearingSoundSystemMixin} 在这次推送中
     * 只设增益、从不停止声源。原版自身的音量滑块推送与其他功能的推送不受影响。
     */
    public static boolean isGainOnlyPush() {
        return gainOnlyPush;
    }

    static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            voiceFactor = 1.0F;
            return;
        }
        if (!client.isPaused()) {
            clientTicks++;
        }
        float now = factor();
        voiceFactor = now;
        ticksSinceReapply++;
        if (BearHearingSoundRules.shouldReapply(lastApplied, now, ticksSinceReapply)) {
            lastApplied = now;
            ticksSinceReapply = 0;
            SoundManager sounds = client.getSoundManager();
            if (sounds != null) {
                // Holy Flash precedent: a non-master category re-volumes every playing source through
                // getAdjustedVolume(SoundInstance) and ignores the passed volume, so no option is written.
                // 沿用圣光弹做法：非主音量类别会经 getAdjustedVolume(SoundInstance) 重设所有正在播放的声源，并忽略传入的
                // 音量，因此不写入任何选项。
                // Vanilla's recompute stops a source whose volume is 0; the flag makes HearingSoundSystemMixin set the
                // gain instead for this one synchronous call (see isGainOnlyPush).
                // 原版重算会停止音量为 0 的声源；该标志让 HearingSoundSystemMixin 在这一次同步调用中改为只设增益
                // （见 isGainOnlyPush）。
                gainOnlyPush = true;
                try {
                    sounds.updateSoundVolume(SoundCategory.AMBIENT,
                            client.options.getSoundVolume(SoundCategory.AMBIENT));
                } finally {
                    gainOnlyPush = false;
                }
            }
        }
    }

    static void reset() {
        MUFFLE.reset();
        voiceFactor = 1.0F;
        lastApplied = 1.0F;
        ticksSinceReapply = 0;
    }
}
