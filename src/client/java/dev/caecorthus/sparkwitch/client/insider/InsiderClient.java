package dev.caecorthus.sparkwitch.client.insider;

import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.api.event.ShouldShowCohort;

/**
 * Insider client registration: one idempotent {@link #init()} called once from {@code SparkWitchClient}. It registers
 * the instinct listener and the killer-cohort listener. The remaining Insider presentation is wired statically:
 * {@code WatheClientInsiderImpostorHighlightMixin} (Impostor-viewer recolor), {@code InsiderCohortRoleNameMixin}
 * ("嘉豪同伙") and {@code WitchInstinctClientHooks} (killer-style instinct light), all reading the same pure rules.
 * 内应客户端注册：由 {@code SparkWitchClient} 调用一次的幂等 {@link #init()}，注册本能监听器与杀手同伙监听器。
 * 其余内应表现为静态接入：{@code WatheClientInsiderImpostorHighlightMixin}（内鬼观察者换色）、
 * {@code InsiderCohortRoleNameMixin}（“嘉豪同伙”）与 {@code WitchInstinctClientHooks}（杀手式本能夜视），均读取同一套纯规则。
 */
public final class InsiderClient {
    private static boolean initialized;

    private InsiderClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        GetInstinctHighlight.EVENT.register(InsiderInstinctClientHooks::highlight);
        ShouldShowCohort.EVENT.register(InsiderCohortClientHooks::killerCohort);
    }
}
