package dev.caecorthus.sparkwitch.client.magician;

import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A puppet never wears its copied player's Wathe psycho mode (forum T-0016, 2026-10-25). Psycho is a timed live state of
 * another body somewhere else, and the replay does not record it (not even the Magician's own psycho while recording),
 * so mirroring it would show every viewer that the copied player is a killer in psycho right now. While a puppet look
 * query runs against its stand-in, that stand-in's {@code PlayerPsychoComponent#getPsychoTicks} reads 0
 * ({@code MagicianPuppetPsychoMaskMixin}), so Wathe's psycho skin, and any skin rule that reads Wathe psycho, stays off
 * the puppet. Client render thread only; the real player keeps its psycho everywhere else.
 * 皮套从不穿上被复制玩家的 Wathe 疯魔（论坛 T-0016，2026-10-25）。疯魔是另一具身体在别处的限时实时状态，回放也不记录它
 * （录制时魔术师自身的疯魔同样不记录），镜像它会让所有观察者看出被复制玩家此刻是疯魔中的杀手。对替身执行皮套外观查询期间，
 * 该替身的 {@code PlayerPsychoComponent#getPsychoTicks} 读数为 0（{@code MagicianPuppetPsychoMaskMixin}），因此 Wathe 疯魔皮肤
 * 及任何读取 Wathe 疯魔的皮肤规则都不会落到皮套上。仅限客户端渲染线程；真实玩家在其他地方仍保持疯魔。
 */
public final class MagicianPuppetPsychoMask {
    // The stand-in's psycho component while a look query runs, else null. / 外观查询期间替身的疯魔组件，否则为 null。
    private static @Nullable Object masked;

    private MagicianPuppetPsychoMask() {
    }

    /** Runs a look query with the stand-in out of psycho. / 在替身脱离疯魔的状态下执行外观查询。 */
    static <T> T withoutPsycho(PlayerEntity standIn, Supplier<T> query) {
        return withMasked(PlayerPsychoComponent.KEY.getNullable(standIn), query);
    }

    /** Masks one component for the query, restoring the previous mask after it. / 查询期间屏蔽一个组件，结束后恢复先前的屏蔽。 */
    static <T> T withMasked(@Nullable Object component, Supplier<T> query) {
        Object previous = masked;
        masked = component;
        try {
            return query.get();
        } finally {
            masked = previous;
        }
    }

    /** Whether this component belongs to the stand-in being looked up. / 该组件是否属于正在查询外观的替身。 */
    public static boolean masks(Object component) {
        return component != null && component == masked;
    }
}
