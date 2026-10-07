package dev.caecorthus.sparkwitch.api.client;

import dev.caecorthus.sparkwitch.client.render.WraithViewerGates;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Stable cross-mod contract (client only, 2026-10-07): lets another mod reveal active Wraiths' bodies to chosen
 * viewers. SparkStrength reaches it by reflection, so this FQCN and the JDK-only signature must not change. A gate is
 * asked {@code (viewer, wraith)} on the render thread, only for an active Wraith (promoted ones included) and never for
 * the Wraith itself; any gate answering true draws that Wraith's body translucent for that viewer, as vanilla does for
 * a spectator. It reveals the body only: held items, outlines, instinct colour, name tags and aim pass-through keep
 * their ordinary-viewer rules.
 * 稳定跨模组契约（仅客户端，2026-10-07）：允许其他模组向指定观察者显示活跃冤魂（含晋升形态）的身体。SparkStrength
 * 通过反射访问本类，因此完整类名与仅使用 JDK 类型的签名都不得更改。闸门在渲染线程以 {@code (观察者, 冤魂)} 调用，
 * 只针对活跃冤魂且从不针对冤魂本人；任一闸门返回 true，该观察者就会像原版旁观者那样看到半透明的冤魂身体。
 * 只显示身体：手持物、描边、本能颜色、名字与准星穿透仍按普通观察者规则处理。
 */
public final class WraithViewerApi {
    private WraithViewerApi() {
    }

    /**
     * Adds {@code gate}; gates are OR'd and never removed, except one that throws, which is dropped after a warning.
     * A null gate throws {@link NullPointerException}.
     * 添加 {@code gate}；多个闸门按“或”合并且不会被移除，抛出异常的闸门在记录警告后被丢弃。参数为 null 时抛出
     * {@link NullPointerException}。
     */
    public static void addViewerGate(BiPredicate<PlayerEntity, PlayerEntity> gate) {
        WraithViewerGates.add(Objects.requireNonNull(gate, "gate"));
    }
}
