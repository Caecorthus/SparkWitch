package dev.caecorthus.sparkwitch.client.scope;

import dev.caecorthus.sparkwitch.client.blind.BlindView;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Client only. The reusable scope module's public entry (WP4): weapons register a provider, and while one returns a
 * {@link ScopeProfile} the module zooms the FOV, scales mouse look, hides the hand, renders the lens and the rim, and
 * calls the weapon's reticle. The USEC rifle and the Potion Gunner launcher are its consumers.
 * Presentation only: no state here is synced, and the server never trusts it.
 * <p>
 * A consumer calls {@link #registerProvider} once at client init with a supplier that returns its profile while its
 * own scope is up (for example: the local player is using that weapon) and {@code null} otherwise. Providers are asked
 * in registration order and the first non-null answer wins, but only after the generic view gate holds (a local player,
 * first person, the camera is that player, no screen open, not a spectator), so a provider never repeats those checks.
 * Providers are polled several times per frame and must be cheap and side-effect free.
 * 仅客户端。可复用开镜模块的公共入口（WP4）：武器注册提供者，只要某个提供者返回 {@link ScopeProfile}，本模块就放大 FOV、
 * 缩放鼠标视角、隐藏手、渲染镜片与镜框并调用武器自己的分划。USEC 步枪与药炮手炮筒是它的使用方。仅负责展示：
 * 这里的状态从不同步，服务端也从不信任。
 * <p>
 * 使用方在客户端初始化时调用一次 {@link #registerProvider}，传入的 supplier 在自己的瞄准镜开启时（例如本地玩家正在使用该
 * 武器）返回配置，否则返回 {@code null}。提供者按注册顺序询问、第一个非 null 结果生效，但前提是通用视角条件成立（存在本地
 * 玩家、第一人称、相机即该玩家、未打开界面、不是旁观者），因此提供者无需重复这些检查。提供者每帧会被轮询多次，必须开销小
 * 且无副作用。
 */
public final class ScopeClient {
    private static final List<Supplier<@Nullable ScopeProfile>> PROVIDERS = new CopyOnWriteArrayList<>();
    private static boolean registered;

    private ScopeClient() {
    }

    /**
     * Loads the settings and wires the lens filter's reload and connection resets. Idempotent: every consumer's client
     * init calls it before registering its provider ({@code PotionGunnerClient.init()} and
     * {@code UsecClientModule.register()}), and only the first call registers anything.
     * 加载设置，并接好镜片滤镜的资源重载与连接重置。幂等：每个使用方的客户端初始化都会在注册提供者之前调用它
     * （{@code PotionGunnerClient.init()} 与 {@code UsecClientModule.register()}），只有第一次调用会注册。
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ScopeSettingsStore.load();
        ScopeLensFilter.register();
        ScopePictureInPicture.register();
        ClientLoginConnectionEvents.INIT.register((handler, client) -> ScopeRuntime.reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ScopeRuntime.reset());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ScopeRuntime.reset());
    }

    /** Adds a profile provider; the first non-null answer wins. / 添加配置提供者；第一个非 null 结果生效。 */
    public static void registerProvider(Supplier<@Nullable ScopeProfile> provider) {
        PROVIDERS.add(Objects.requireNonNull(provider, "provider"));
    }

    /** The profile the local player looks through this frame, or null. / 本地玩家本帧正在使用的配置，或 null。 */
    @Nullable
    public static ScopeProfile activeProfile() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (!ScopeRules.viewAllowsScope(player != null, client.options.getPerspective().isFirstPerson(),
                player != null && client.getCameraEntity() == player, client.currentScreen != null,
                player != null && player.isSpectator())) {
            return null;
        }
        for (Supplier<@Nullable ScopeProfile> provider : PROVIDERS) {
            ScopeProfile profile = provider.get();
            if (profile != null) {
                return profile;
            }
        }
        return null;
    }

    public static boolean isScoped() {
        return activeProfile() != null;
    }

    /**
     * The mode that actually renders: the player's choice after the fallbacks of {@link ScopeRules#effectiveMode}
     * (an Iris shader pack, Fabulous! graphics, the Blind echo view, or a PiP renderer backing off after a failure all
     * mean ZOOM_BLUR).
     * 实际渲染的模式：玩家的选择经过 {@link ScopeRules#effectiveMode} 的回退之后的结果（Iris 光影包、「极佳」画质、盲人回声视图，
     * 或画中画渲染器失败后的退避期间，都按全画面放大渲染）。
     */
    public static ScopeMode effectiveMode() {
        ScopeMode selected = ScopeSettingsStore.current().mode();
        if (selected == ScopeMode.ZOOM_BLUR) {
            return ScopeMode.ZOOM_BLUR;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        // The Blind echo view captures the world render once per frame (a BEFORE_DEBUG_RENDER listener on
        // client.getFramebuffer()); a second, lens-sized pass would hijack it. / 盲人回声视图每帧只捕获一次世界渲染
        // （作用于 client.getFramebuffer() 的 BEFORE_DEBUG_RENDER 监听器），第二次镜内尺寸的渲染会劫持它。
        return ScopeRules.effectiveMode(selected, ScopeLensFilter.shaderPackInUse(),
                MinecraftClient.isFabulousGraphicsOrBetter(), BlindView.isActive(client), ScopePictureInPicture.ready());
    }

    /**
     * True only while the PiP lens pass re-renders the world. World-render hooks that must run once per frame, or must
     * stay out of the magnified lens, can check it.
     * 仅在画中画镜内渲染重新渲染世界期间为 true。每帧只能运行一次、或不应出现在放大镜内的世界渲染钩子可以检查它。
     */
    public static boolean isRenderingLens() {
        return ScopePictureInPicture.isRenderingLens();
    }

    /**
     * Screen-pixel scale of the world pass now rendering: on-screen pixels per unit of vertical NDC for a window
     * framebuffer of this size ({@link ScopeRules#screenPixelsPerNdcY}). Multiply by the pass's projection m11 (for
     * example {@code WorldRenderContext.projectionMatrix().m11()}) for on-screen pixels per unit tangent. Inside the PiP
     * lens pass the target is a square sized by the player's Lens Resolution and the projection is narrowed, yet this
     * still answers in screen pixels, so a world sprite sized in screen pixels shows in the lens exactly as large as in
     * Full-Screen Zoom; in the main pass it is half the height, as before.
     * 当前世界渲染的屏幕像素比例：对给定尺寸的窗口帧缓冲，竖直 NDC 每单位对应的屏幕像素（{@link ScopeRules#screenPixelsPerNdcY}）。
     * 乘以该次渲染投影的 m11（例如 {@code WorldRenderContext.projectionMatrix().m11()}）即为每单位正切的屏幕像素。画中画镜内渲染
     * 的目标是按玩家镜内分辨率确定大小的方形且投影已收窄，但这里仍以屏幕像素作答，因此以屏幕像素设定大小的世界精灵在镜内与
     * 全画面放大中一样大；主渲染中即为高度的一半，与以往相同。
     */
    public static double screenPixelsPerNdcY(double screenWidth, double screenHeight) {
        return ScopeRules.screenPixelsPerNdcY(isRenderingLens(), screenWidth, screenHeight);
    }
}
