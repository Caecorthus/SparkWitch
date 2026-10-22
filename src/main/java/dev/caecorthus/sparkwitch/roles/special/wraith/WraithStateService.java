package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/** Read-only access to synchronized Wraith runtime state. */
public final class WraithStateService {
    private WraithStateService() {
    }

    // Hot (collision, movement and door-shape mixins): getNullable avoids the Optional and method reference of
    // maybeGet. Every PlayerEntity is a CCA component provider, so the result is unchanged.
    // 热路径（碰撞、移动与门形状 mixin）：getNullable 免去 maybeGet 的 Optional 与方法引用。每个 PlayerEntity 都是
    // CCA 组件提供者，因此结果不变。
    public static boolean isActive(@Nullable PlayerEntity player) {
        WraithPlayerComponent component = component(player);
        return component != null && component.isActive();
    }

    public static boolean isRestricted(@Nullable PlayerEntity player) {
        WraithPlayerComponent component = component(player);
        return component != null && component.isRestricted();
    }

    public static boolean isPromoted(@Nullable PlayerEntity player) {
        WraithPlayerComponent component = component(player);
        return component != null && component.isPromoted();
    }

    private static @Nullable WraithPlayerComponent component(@Nullable PlayerEntity player) {
        return player == null ? null : WraithPlayerComponent.KEY.getNullable(player);
    }
}
