package dev.caecorthus.sparkwitch.api.client;

import dev.caecorthus.sparkwitch.client.blind.render.BlindSeeThroughOutlines;
import net.minecraft.util.math.Box;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

/**
 * Stable cross-mod contract (client only, 2026-10-18): lets another mod show see-through world outlines to the local
 * Blind, whose echo view paints over everything drawn at {@code WorldRenderEvents.LAST}. SparkStrength reaches it by
 * reflection, so this FQCN and the JDK/Minecraft-only signature must not change. Each frame in which the local Blind's
 * echo view is actually shown (never on its black fallback, never for any other player), every source is called once
 * on the render thread, until the per-frame cap is reached, with a sink and reports world-space boxes as {@code (box, 0xAARRGGBB)}. SparkWitch then draws
 * them over the echo frame as fog-free line boxes that walls never hide, slightly expanded and in the colour given;
 * null boxes, non-finite coordinates and alpha 0 are skipped, and at most 512 boxes are drawn per frame.
 * 稳定跨模组契约（仅客户端，2026-10-18）：允许其他模组向本地盲人显示透视的世界描边；盲人的回声画面会覆盖
 * {@code WorldRenderEvents.LAST} 绘制的一切。SparkStrength 通过反射访问本类，因此完整类名与仅使用 JDK/Minecraft
 * 类型的签名都不得更改。只有本地盲人的回声画面真正显示的那一帧（黑屏兜底时不会，其他玩家永远不会），每个来源才会在
 * 渲染线程被调用一次（达到每帧上限后不再调用），并向接收器以 {@code (box, 0xAARRGGBB)} 报告世界坐标的包围盒。SparkWitch 随后把它们以无雾、
 * 不被墙遮挡、略微外扩并保留原颜色的线框画在回声画面之上；null 包围盒、非有限坐标与 alpha 为 0 的条目会被跳过，
 * 每帧最多绘制 512 个。
 */
public final class BlindViewApi {
    private BlindViewApi() {
    }

    /**
     * Adds {@code source}. A source only writes to the sink while it is being called and must not render, bind GL
     * state or keep the sink (later writes are dropped). Sources are never removed, except one that throws, which is
     * dropped after a single warning together with the boxes it reported that frame. A null source throws
     * {@link NullPointerException}.
     * 添加 {@code source}。来源只能在被调用期间向接收器写入，不得自行渲染、改动 GL 状态或保留接收器（之后的写入会被丢弃）。
     * 来源不会被移除，除非它抛出异常：此时记录一次警告后丢弃它及其当帧报告的包围盒。参数为 null 时抛出
     * {@link NullPointerException}。
     */
    public static void addSeeThroughOutlines(Consumer<ObjIntConsumer<Box>> source) {
        BlindSeeThroughOutlines.add(Objects.requireNonNull(source, "source"));
    }
}
