package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Server-side Raven Form lifecycle hooks for the other crow packages (crow-world: Homing Feather, landing, blackout,
 * voice; crow-hits). Both fire on the server thread inside {@link BlackRavenCrowSessions}; listeners must not start or
 * end a session themselves.
 * 供其他化鸦分包（crow-world：归羽、落地、熄灯、语音；crow-hits）使用的服务端化鸦生命周期钩子。两者都在服务端线程上、
 * {@link BlackRavenCrowSessions} 内部触发；监听器不得自行开始或结束会话。
 */
public final class BlackRavenCrowEvents {
    /**
     * After a session started: the crow is spawned, the body is invisible and the component is synced.
     * 会话开始之后：乌鸦已生成、本体已隐身、组件已同步。
     */
    public static final Event<Started> STARTED = EventFactory.createArrayBacked(Started.class,
            listeners -> (owner, crow) -> {
                for (Started listener : listeners) {
                    listener.onStarted(owner, crow);
                }
            });

    /**
     * After a session ended: the crow is discarded, the body's invisibility is restored, the session fields are
     * cleared (the Homing Feather fields are left for the listener) and the cooldown started when
     * {@link BlackRavenCrowEndReason#startsCooldown()}. {@code crowPos} is the crow's last position. crow-world teleports
     * to a dropped feather only when {@link BlackRavenCrowEndReason#usesHomingFeather()}.
     * 会话结束之后：乌鸦已移除、本体隐身已恢复、会话字段已清空（归羽字段留给监听器处理），且在 startsCooldown 时已开始冷却。
     * {@code crowPos} 是乌鸦最后的位置。只有 usesHomingFeather 为真时，crow-world 才传送到已丢出的归羽。
     */
    public static final Event<Ended> ENDED = EventFactory.createArrayBacked(Ended.class,
            listeners -> (owner, reason, crowPos) -> {
                for (Ended listener : listeners) {
                    listener.onEnded(owner, reason, crowPos);
                }
            });

    private BlackRavenCrowEvents() {
    }

    @FunctionalInterface
    public interface Started {
        void onStarted(ServerPlayerEntity owner, BlackRavenCrowEntity crow);
    }

    @FunctionalInterface
    public interface Ended {
        void onEnded(ServerPlayerEntity owner, BlackRavenCrowEndReason reason, Vec3d crowPos);
    }
}
