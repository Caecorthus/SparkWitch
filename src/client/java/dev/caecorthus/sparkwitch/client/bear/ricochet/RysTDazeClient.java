package dev.caecorthus.sparkwitch.client.bear.ricochet;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.client.bear.hearing.BearHearingClient;
import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.BearRicochetRules;
import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.RysTRicochetS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Client only: the Rys-T wearer's D5 daze after a server-side ricochet ({@code sparkwitch:bear_rys_t_ricochet}, sent to
 * the wearer alone). Presentation only: the server already cancelled the kill and plays the public ping; this side
 * starts the view jolt ({@code RicochetDazeCameraMixin}) and double vision ({@code RicochetDazeHudMixin}), plays the
 * tinnitus listener-relative so nobody else hears it, and muffles world sounds through {@link BearHearingClient}.
 * State lives on the client thread only; a new ricochet restarts the daze, a disconnect clears it.
 * 仅客户端：服务端判定跳弹后 Rys-T 佩戴者的 D5 眩晕（{@code sparkwitch:bear_rys_t_ricochet}，只发给佩戴者）。纯表现：
 * 服务端已取消击杀并播放公开声音；本端启动视角震动（{@code RicochetDazeCameraMixin}）与重影（{@code RicochetDazeHudMixin}），
 * 以相对听者方式播放耳鸣（他人听不到），并通过 {@link BearHearingClient} 让世界声音发闷。状态只在客户端线程读写；新的
 * 跳弹会重新开始眩晕，断线时清除。
 */
public final class RysTDazeClient {
    private static final Random RANDOM = Random.create();
    private static boolean registered;
    private static RysTDazeCurve.Kick kick;
    private static int age;
    private static SoundInstance tinnitus;

    private RysTDazeClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(RysTRicochetS2CPayload.ID, (payload, context) ->
                context.client().execute(() -> start(context.client(), payload)));
        ClientTickEvents.END_CLIENT_TICK.register(RysTDazeClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(RysTDazeClient::reset));
        // Also on JOIN: a proxy backend switch re-joins without a disconnect, and play state only arrives after JOIN.
        // JOIN 时同样重置：代理切换后端时不会断线而是重新加入，而 play 阶段的状态只会在 JOIN 之后到达。
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(RysTDazeClient::reset));
    }

    /** Whether a daze is running (render thread). / 是否正在眩晕（渲染线程）。 */
    public static boolean active() {
        return kick != null;
    }

    /**
     * This frame's jolt and ghost, read side-effect free by both mixins; {@link RysTDazeCurve.Pose#NONE} when idle.
     * 本帧的震动与重影，两个 mixin 均无副作用地读取；空闲时为 {@link RysTDazeCurve.Pose#NONE}。
     */
    public static RysTDazeCurve.Pose pose(float tickDelta) {
        RysTDazeCurve.Kick current = kick;
        if (current == null) {
            return RysTDazeCurve.Pose.NONE;
        }
        float distortion = MinecraftClient.getInstance().options.getDistortionEffectScale().getValue().floatValue();
        return RysTDazeCurve.pose(current, age + tickDelta, distortion);
    }

    private static void start(MinecraftClient client, RysTRicochetS2CPayload payload) {
        ClientPlayerEntity player = client.player;
        if (player == null || payload == null) {
            return;
        }
        kick = RysTDazeCurve.kick(payload.dx(), payload.dy(), payload.dz(), player.getYaw(), player.getPitch(),
                payload.glass(), RANDOM.nextFloat() * MathHelper.TAU);
        age = 0;
        if (tinnitus != null) {
            client.getSoundManager().stop(tinnitus);
        }
        // Listener-relative at the origin with no attenuation: only this client plays it. Its id is in
        // BearHearingClient.EXEMPT_SOUND_IDS, so the muffle below never dims it.
        // 相对听者、位于原点且无衰减：只有本客户端播放。其 id 在 BearHearingClient.EXEMPT_SOUND_IDS 中，下面的发闷不会压低它。
        tinnitus = new PositionedSoundInstance(SparkWitchSounds.BEAR_RYS_T_TINNITUS_ID, SoundCategory.PLAYERS,
                BearRicochetRules.TINNITUS_VOLUME, 1.0F, SoundInstance.createRandom(), false, 0,
                SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true);
        client.getSoundManager().play(tinnitus);
        BearHearingClient.muffle(BearRicochetRules.MUFFLE_TICKS, BearRicochetRules.MUFFLE_VOLUME);
    }

    private static void tick(MinecraftClient client) {
        if (kick == null) {
            return;
        }
        if (client.player == null) {
            reset();
            return;
        }
        if (!client.isPaused()) {
            age++;
        }
        if (age >= BearRicochetRules.DAZE_TICKS) {
            kick = null;
            age = 0;
            RysTDazeGhostRenderer.release();
        }
    }

    /** Clears the daze and frees the ghost copy (client thread). / 清除眩晕并释放重影副本（客户端线程）。 */
    static void reset() {
        kick = null;
        age = 0;
        if (tinnitus != null) {
            MinecraftClient.getInstance().getSoundManager().stop(tinnitus);
            tinnitus = null;
        }
        RysTDazeGhostRenderer.release();
    }
}
