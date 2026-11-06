package dev.caecorthus.sparkwitch.client.bear.launcher;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.scope.ScopeClient;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.bear.BearRules;
import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.M32CursorLoading;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.Identifier;

/**
 * BEAR WP1 client entry (called once from {@code client.bear.BearClientModule.register()}): the M2A1 sight on the
 * shared {@code client/scope} module ({@code ScopeClient.register()} is idempotent, so the init order against the
 * launcher and USEC does not matter), the chamber HUD, the fire latch's tick and resets, the range dial's session
 * tick, the creative-screen probe of cursor loading (the client writes only creative-screen clicks, see
 * {@link M32CursorLoading}), the standalone projectile model, the 2-D inventory icon of the held model and the aimed
 * launcher's body and sight models ({@link M32SightRenderer}). The input
 * mixins in {@code client/mixin/bear/M32*} read the state kept here. Presentation only; never the witch skill panel.
 * BEAR WP1 客户端入口（由 {@code client.bear.BearClientModule.register()} 调用一次）：共享 {@code client/scope} 模块上的 M2A1 瞄具
 * （{@code ScopeClient.register()} 是幂等的，因此与炮筒、USEC 的初始化顺序无关）、弹膛 HUD、开火闩锁的逐刻与重置、射程刻度的会话
 * 逐刻、光标装填的创造界面探针（客户端只写入创造界面中的点击，见 {@link M32CursorLoading}）、独立投射物模型、手持模型的 2D 背包
 * 图标以及瞄准中发射器的机身与瞄具模型（{@link M32SightRenderer}）。{@code client/mixin/bear/M32*} 中的输入 mixin 读取这里维护的状态。仅为表现；从不使用魔女技能面板。
 */
public final class M32LauncherClient {
    public static final ModelIdentifier INVENTORY_MODEL_ID = ModelIdentifier.ofInventoryVariant(BearRules.M32A1_ITEM_ID);
    public static final Identifier ICON_MODEL_ID = SparkWitch.id("item/m32a1_icon");
    private static boolean initialized;

    private M32LauncherClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ScopeClient.register();
        ScopeClient.registerProvider(M32ScopeProfile::provide);
        HudRenderCallback.EVENT.register(M32Hud::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            M32RangeDial.tick(client);
            if (!SparkWitchServerConnection.isConfirmedServer()) {
                M32FireInput.reset();
                return;
            }
            M32FireInput.tick(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            M32FireInput.reset();
            M32RangeDial.reset();
            M32SightState.reset();
        });
        M32CursorLoading.installCreativeScreenProbe(M32LauncherClient::creativeScreenOpen);
        SmokeRoundEntityRenderer.registerModels();
        registerModels();
    }

    /**
     * Client authority seam of cursor loading: true while the vanilla creative inventory resolves the click (it syncs
     * whole stacks, so the server never sees a load made there). / 光标装填的客户端权威接缝：原版创造模式物品栏结算点击时为
     * true（它同步整个物品堆，因此服务端看不到在那里进行的装填）。
     */
    private static boolean creativeScreenOpen() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.currentScreen instanceof CreativeInventoryScreen
                && client.interactionManager != null && client.interactionManager.hasCreativeInventory();
    }

    /**
     * The held 3-D model ({@code models/item/m32a1.json}, no overrides) becomes the 2-D icon in GUI, ground and item
     * frames ({@link M32A1IconSwapModel}), as the art package recommends; the aimed launcher's two standalone halves
     * ({@code item/m32a1_body}, {@code item/m32a1_sight}, same texture and display block) are baked beside it for
     * {@link M32SightRenderer}. / 手持 3D 模型（{@code models/item/m32a1.json}，无覆盖）在背包、掉落物与物品展示框中换成 2D 图标
     * （{@link M32A1IconSwapModel}），按美术包的建议；瞄准中发射器的两个独立部分（{@code item/m32a1_body}、
     * {@code item/m32a1_sight}，贴图与显示块相同）一并烘焙，供 {@link M32SightRenderer} 使用。
     */
    private static void registerModels() {
        ModelLoadingPlugin.register(context -> {
            context.addModels(ICON_MODEL_ID, M32SightRenderer.BODY_MODEL_ID, M32SightRenderer.SIGHT_MODEL_ID);
            context.modifyModelOnLoad().register((model, loadContext) ->
                    INVENTORY_MODEL_ID.equals(loadContext.topLevelId()) && !(model instanceof M32A1IconSwapModel)
                            ? new M32A1IconSwapModel(model, ICON_MODEL_ID)
                            : model);
        });
    }
}
