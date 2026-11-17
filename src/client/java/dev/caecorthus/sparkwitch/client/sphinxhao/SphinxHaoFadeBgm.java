package dev.caecorthus.sparkwitch.client.sphinxhao;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoWorldComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;

/**
 * 《Fade》 during 豪气冲天, client presentation only (Specter Otherworld BGM pattern, rules in
 * {@link SphinxHaoFadeBgmRules}): the server opens and ends the window and syncs it to every client
 * ({@code sparkwitch:sphinx_hao_world}); every client in the world, alive, dead or spectating, then plays the 10 s
 * clip once per window (owner Q2: everyone hears it). Relative, non-attenuated, AMBIENT category (Wathe locks MUSIC;
 * SparkAssist's event-sound volume row samples it there), full volume from the first tick, faded out over 10 ticks
 * when the window ends first. A client that joins mid-window starts the clip from its beginning. JOIN and DISCONNECT
 * forget the played window (vanilla stops every sound on disconnect).
 * 豪气冲天期间的《Fade》，仅客户端展示（沿用幽魂里世界 BGM 的做法，规则见 {@link SphinxHaoFadeBgmRules}）：窗口的开启与结束由服务端
 * 决定并同步给每个客户端（{@code sparkwitch:sphinx_hao_world}）；世界中的每个客户端（存活、死亡或旁观）每个窗口播放一次 10 秒片段
 * （所有者 Q2：所有人都听得到）。相对玩家、无衰减、AMBIENT 类别（Wathe 锁定了 MUSIC；SparkAssist 的事件音量行在该类别取样），
 * 第一刻即满音量，窗口先结束时在 10 刻内淡出。窗口中途加入的客户端从头播放。加入与断开连接时忘掉已播放的窗口（原版断线时会停止
 * 所有声音）。
 */
public final class SphinxHaoFadeBgm {
    private static boolean registered;
    /** End tick of the window whose clip already started, or none. / 已开始播放的窗口的结束刻，或无。 */
    private static long playedWindow = SphinxHaoFadeBgmRules.NO_WINDOW;

    private SphinxHaoFadeBgm() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(SphinxHaoFadeBgm::tick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> playedWindow = SphinxHaoFadeBgmRules.NO_WINDOW);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> playedWindow = SphinxHaoFadeBgmRules.NO_WINDOW);
    }

    private static void tick(MinecraftClient client) {
        long active = activeWindow(client.world);
        SphinxHaoFadeBgmRules.Step step = SphinxHaoFadeBgmRules.step(active, playedWindow);
        playedWindow = step.playedWindow();
        if (step.start() && SparkWitchSounds.SPHINX_HAO_FADE != null) {
            client.getSoundManager().play(new Clip(active));
        }
    }

    /** The open window's end tick, or none. / 开启窗口的结束刻，或无。 */
    private static long activeWindow(ClientWorld world) {
        if (world == null) {
            return SphinxHaoFadeBgmRules.NO_WINDOW;
        }
        SphinxHaoWorldComponent window = SphinxHaoWorldComponent.KEY.getNullable(world);
        if (window == null) {
            return SphinxHaoFadeBgmRules.NO_WINDOW;
        }
        return SphinxHaoFadeBgmRules.activeWindow(SparkWitchServerConnection.isConfirmedServer(),
                GameWorldComponent.KEY.get(world).isRunning(), window.isRoarActive(world.getTime()),
                window.roarEndTick());
    }

    /** One play-through for one window; fades out once that window is gone. / 一个窗口播放一次；该窗口结束后淡出。 */
    private static final class Clip extends MovingSoundInstance {
        private final long window;
        private int fade = SphinxHaoFadeBgmRules.nextFade(true, 0);

        private Clip(long window) {
            super(SparkWitchSounds.SPHINX_HAO_FADE, SoundCategory.AMBIENT, SoundInstance.createRandom());
            this.window = window;
            this.repeat = false;
            this.repeatDelay = 0;
            this.relative = true;
            this.attenuationType = SoundInstance.AttenuationType.NONE;
            this.x = 0.0D;
            this.y = 0.0D;
            this.z = 0.0D;
            this.volume = 1.0F;
        }

        @Override
        public void tick() {
            fade = SphinxHaoFadeBgmRules.nextFade(activeWindow(MinecraftClient.getInstance().world) == window, fade);
            if (SphinxHaoFadeBgmRules.done(fade)) {
                setDone();
                return;
            }
            volume = SphinxHaoFadeBgmRules.volume(fade);
        }
    }
}
