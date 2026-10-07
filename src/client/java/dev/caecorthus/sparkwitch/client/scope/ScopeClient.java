package dev.caecorthus.sparkwitch.client.scope;

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
 * calls the weapon's reticle. USEC registers first; the Potion Gunner launcher is meant to migrate onto it later.
 * Presentation only: no state here is synced, and the server never trusts it.
 * <p>
 * A consumer calls {@link #registerProvider} once at client init with a supplier that returns its profile while its
 * own scope is up (for example: the local player is using that weapon) and {@code null} otherwise. Providers are asked
 * in registration order and the first non-null answer wins, but only after the generic view gate holds (a local player,
 * first person, the camera is that player, no screen open, not a spectator), so a provider never repeats those checks.
 * Providers are polled several times per frame and must be cheap and side-effect free.
 * 仅客户端。可复用开镜模块的公共入口（WP4）：武器注册提供者，只要某个提供者返回 {@link ScopeProfile}，本模块就放大 FOV、
 * 缩放鼠标视角、隐藏手、渲染镜片与镜框并调用武器自己的分划。USEC 最先注册；药炮手炮筒之后将迁移到这里。仅负责展示：
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
     * Called once from {@code UsecClientModule.register()}: loads the settings and wires the lens filter's reload and
     * connection resets.
     * 由 {@code UsecClientModule.register()} 调用一次：加载设置，并接好镜片滤镜的资源重载与连接重置。
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ScopeSettingsStore.load();
        ScopeLensFilter.register();
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
     * The mode that actually renders: the player's choice after the shader-pack fallback (and ZOOM_BLUR until PiP is
     * available).
     * 实际渲染的模式：玩家的选择经过光影包回退之后的结果（画中画可用前始终为全画面放大）。
     */
    public static ScopeMode effectiveMode() {
        return ScopeRules.effectiveMode(ScopeSettingsStore.current().mode(), ScopeLensFilter.shaderPackInUse(),
                ScopeRules.PICTURE_IN_PICTURE_AVAILABLE);
    }
}
