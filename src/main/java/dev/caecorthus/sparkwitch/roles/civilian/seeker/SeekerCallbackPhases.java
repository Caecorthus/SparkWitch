package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Stable contract: Fabric callback phases for the Seeker. {@code seeker_session_lock} runs before
 * {@code seeker_device}, which runs before the default phase, on all five player callbacks. Listeners register with
 * {@code EVENT.register(SeekerCallbackPhases.SESSION_LOCK, ...)} after calling {@link #ensureOrdered()}.
 * 稳定契约：搜寻者的 Fabric 回调阶段。在五个玩家回调上，{@code seeker_session_lock} 先于 {@code seeker_device}，
 * 后者又先于默认阶段。监听器先调用 {@link #ensureOrdered()}，再以对应阶段注册。
 */
public final class SeekerCallbackPhases {
    public static final Identifier SESSION_LOCK = SparkWitch.id("seeker_session_lock");
    public static final Identifier DEVICE = SparkWitch.id("seeker_device");

    private static final Set<Event<?>> ORDERED = Collections.newSetFromMap(new IdentityHashMap<>());

    private SeekerCallbackPhases() {
    }

    /** Idempotent; safe on both sides of the same JVM. / 幂等；同一 JVM 两端调用均安全。 */
    public static synchronized void ensureOrdered() {
        order(UseItemCallback.EVENT);
        order(UseBlockCallback.EVENT);
        order(UseEntityCallback.EVENT);
        order(AttackEntityCallback.EVENT);
        order(AttackBlockCallback.EVENT);
    }

    private static void order(Event<?> event) {
        if (ORDERED.add(event)) {
            event.addPhaseOrdering(SESSION_LOCK, DEVICE);
            event.addPhaseOrdering(DEVICE, Event.DEFAULT_PHASE);
        }
    }
}
