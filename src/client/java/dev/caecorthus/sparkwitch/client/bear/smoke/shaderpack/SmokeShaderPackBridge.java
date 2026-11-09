package dev.caecorthus.sparkwitch.client.bear.smoke.shaderpack;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.bear.smoke.render.SmokeRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL33;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Primary BEAR smoke path under an Iris shader pack (client render thread only). A pack renders the world into its own
 * G-buffers and its final pass overwrites the main target, so the ray-marched smoke is drawn right AFTER Iris's final
 * pass, onto the pack's finished image, compared against Iris's own scene depths ({@code SmokeIrisFinalPassMixin} calls
 * {@link #afterFinalPass} at RETURN of {@code IrisRenderingPipeline#finalizeLevelRendering}). The depths are read by
 * reflection from the pipeline's {@code RenderTargets} (Iris 1.8.8: {@code getDepthTexture}, the pre-translucent and
 * pre-hand copies, {@code getCurrentWidth/Height}); nothing is copied into the main framebuffer.
 * <p>
 * Liveness, so the smoke fails CLOSED with an Iris version whose seam differs: Iris calls that method once per world
 * render, smoke or not. {@link #onMainWorldPassStart} turns "the hook ran and every reflected value was valid during the
 * previous frame, and the renderer is not in its failure backoff" into {@link #primaryHealthy()}. While it is false the
 * billboard fallback ({@code SmokeBillboards}) draws smoke through a translucent entity layer the pack renders itself.
 * A frame where the hook comes back draws both (more smoke, never less).
 * Iris 光影包下 BEAR 烟雾的主路径（仅客户端渲染线程）。光影包把世界渲染进自己的 G 缓冲，最终处理会覆盖主目标，因此光线步进的
 * 烟雾在 Iris 最终处理之后、画在光影包成品图上，并与 Iris 自己的场景深度比较（{@code SmokeIrisFinalPassMixin} 在
 * {@code IrisRenderingPipeline#finalizeLevelRendering} 的 RETURN 处调用 {@link #afterFinalPass}）。深度通过反射从管线的
 * {@code RenderTargets} 读取（Iris 1.8.8：{@code getDepthTexture}、半透明前与手部前的深度副本、{@code getCurrentWidth/Height}），
 * 不向主帧缓冲拷贝任何东西。
 * 存活判定，使接缝不同的 Iris 版本下烟雾失败即遮挡：无论有没有烟，Iris 每次世界渲染都调用该方法一次。
 * {@link #onMainWorldPassStart} 把“上一帧钩子运行过、反射得到的值全部有效、且渲染器不在失败退避中”转成
 * {@link #primaryHealthy()}。为 false 时由公告板兜底（{@code SmokeBillboards}）通过光影包自己渲染的半透明实体层画烟。
 * 钩子恢复的那一帧两者都画（烟只多不少）。
 */
public final class SmokeShaderPackBridge {
    private static boolean hookOkSinceStart;
    private static boolean primaryHealthy;
    private static boolean warned;
    private static boolean activeLogged;
    private static Class<?> pipelineClass;
    private static Field renderTargetsField;
    private static Class<?> targetsClass;
    private static Method depthTexture;
    private static Method depthNoTranslucents;
    private static Method depthNoHand;
    private static Method currentWidth;
    private static Method currentHeight;
    private static Method depthTextureId;
    private static final int[] SAMPLERS = new int[SmokeRenderer.SHADER_PACK_TEXTURE_UNITS];

    private SmokeShaderPackBridge() {
    }

    /**
     * {@code WorldRenderEvents.START} of a main-view world render: latches the previous frame's liveness.
     * 主画面世界渲染的 {@code WorldRenderEvents.START}：锁存上一帧的存活结果。
     */
    static void onMainWorldPassStart() {
        primaryHealthy = hookOkSinceStart && !SmokeRenderer.isFailing();
        hookOkSinceStart = false;
    }

    /**
     * True when the previous frame proved the after-final-pass smoke works, so no fallback is needed.
     * 上一帧证明最终处理之后的烟雾可用时为 true，此时无需兜底。
     */
    public static boolean primaryHealthy() {
        return primaryHealthy;
    }

    /**
     * Mixin entry at RETURN of {@code IrisRenderingPipeline#finalizeLevelRendering}; render thread, main target bound.
     * Never throws into Iris.
     * {@code IrisRenderingPipeline#finalizeLevelRendering} RETURN 处的 mixin 入口；渲染线程，主目标已绑定。从不向 Iris 抛异常。
     */
    public static void afterFinalPass(Object pipeline) {
        boolean ok = false;
        try {
            ok = run(pipeline);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce("BEAR smoke: cannot read Iris's render targets; using the billboard fallback", exception);
        }
        if (ok) {
            hookOkSinceStart = true;
        }
    }

    private static boolean run(Object pipeline) throws ReflectiveOperationException {
        Object targets = renderTargets(pipeline);
        int width = ((Number) currentWidth.invoke(targets)).intValue();
        int height = ((Number) currentHeight.invoke(targets)).intValue();
        int depthAll = ((Number) depthTexture.invoke(targets)).intValue();
        int depthOpaque = textureId(depthNoTranslucents.invoke(targets));
        int depthBeforeHand = textureId(depthNoHand.invoke(targets));
        if (width <= 0 || height <= 0 || depthAll <= 0 || depthOpaque <= 0 || depthBeforeHand <= 0) {
            warnOnce("BEAR smoke: Iris render targets are not ready; using the billboard fallback", null);
            return false;
        }
        if (!SmokeRenderer.hasShaderPackPass()) {
            return !SmokeRenderer.isFailing();
        }
        // Iris may leave sampler objects on the units the raw smoke programs use; they would override the smoke
        // textures' own filtering. / Iris 可能在烟雾原生程序使用的纹理单元上留有采样器对象，会覆盖烟雾纹理自身的过滤方式。
        int activeUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        for (int unit = 0; unit < SAMPLERS.length; unit++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            SAMPLERS[unit] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            if (SAMPLERS[unit] != 0) {
                GL33.glBindSampler(unit, 0);
            }
        }
        GL13.glActiveTexture(activeUnit);
        boolean drawn;
        try {
            drawn = SmokeRenderer.renderAfterShaderPack(depthAll, depthOpaque, depthBeforeHand, width, height);
        } finally {
            for (int unit = 0; unit < SAMPLERS.length; unit++) {
                if (SAMPLERS[unit] != 0) {
                    GL33.glBindSampler(unit, SAMPLERS[unit]);
                }
            }
        }
        if (drawn && !activeLogged) {
            activeLogged = true;
            SparkWitch.LOGGER.info("BEAR smoke: drawing after the Iris shader pack's final pass");
        }
        return drawn;
    }

    private static Object renderTargets(Object pipeline) throws ReflectiveOperationException {
        if (pipelineClass != pipeline.getClass()) {
            Field field = findField(pipeline.getClass(), "renderTargets");
            field.setAccessible(true);
            renderTargetsField = field;
            pipelineClass = pipeline.getClass();
        }
        Object targets = renderTargetsField.get(pipeline);
        if (targets == null) {
            throw new IllegalStateException("Iris pipeline has no render targets");
        }
        if (targetsClass != targets.getClass()) {
            Class<?> type = targets.getClass();
            depthTexture = type.getMethod("getDepthTexture");
            depthNoTranslucents = type.getMethod("getDepthTextureNoTranslucents");
            depthNoHand = type.getMethod("getDepthTextureNoHand");
            currentWidth = type.getMethod("getCurrentWidth");
            currentHeight = type.getMethod("getCurrentHeight");
            depthTextureId = null;
            targetsClass = type;
        }
        return targets;
    }

    private static int textureId(Object depth) throws ReflectiveOperationException {
        if (depth == null) {
            return 0;
        }
        if (depthTextureId == null || !depthTextureId.getDeclaringClass().isInstance(depth)) {
            depthTextureId = depth.getClass().getMethod("getTextureId");
        }
        return ((Number) depthTextureId.invoke(depth)).intValue();
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep looking in the superclass / 继续在父类中查找
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** Resource reload / disconnect: re-earn liveness, log again. / 资源重载、断线：重新判定存活并允许再次记录日志。 */
    static void reset() {
        hookOkSinceStart = false;
        primaryHealthy = false;
        warned = false;
        activeLogged = false;
    }

    private static void warnOnce(String message, Throwable throwable) {
        if (warned) {
            return;
        }
        warned = true;
        if (throwable == null) {
            SparkWitch.LOGGER.warn(message);
        } else {
            SparkWitch.LOGGER.warn(message, throwable);
        }
    }
}
