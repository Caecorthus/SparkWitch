package dev.caecorthus.sparkwitch.client.bear.helmet;

import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTVisorToggle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;

/**
 * Client registration of the Rys-T helmet (WP2), called once from {@code BearClientModule}: the {@code visor_up}
 * model predicate and the creative-screen probe of the visor toggle. The 3-D element model is shown everywhere,
 * including GUI, ground and item frames, through its own {@code display} entries (no 2-D icon; owner call
 * 2026-11-07). The visor overlay and the worn-model hooks are driven by the {@code client/mixin/bear/RysT*} mixins
 * and need no registration. Presentation only.
 * Rys-T 头盔的客户端注册（WP2），由 {@code BearClientModule} 调用一次：{@code visor_up} 模型谓词与面罩开合的创造界面探针。
 * 3D 元素模型在所有上下文中显示，包括背包、掉落物与物品展示框，由模型自身的 {@code display} 条目负责（不再有 2D 图标；
 * 2026-11-07 所有者决定）。面罩遮罩与穿戴模型钩子由 {@code client/mixin/bear/RysT*} mixin 驱动，无需注册。仅为展示。
 */
public final class RysTHelmetClient {
    private RysTHelmetClient() {
    }

    public static void register() {
        RysTModelPredicates.register();
        RysTVisorToggle.installCreativeScreenProbe(RysTHelmetClient::creativeScreenOpen);
    }

    /**
     * Client authority seam (the USEC rule): the creative inventory screen resolves its slot clicks on the client and
     * syncs whole stacks, so the server never sees a visor toggle made there; the toggle asks this probe to let the
     * client write exactly those clicks. / 客户端权威接缝（USEC 规则）：创造模式物品栏在客户端结算栏位点击并同步整个物品堆，
     * 服务端看不到在那里进行的面罩开合；开合询问此探针，只让客户端写入这类点击。
     */
    private static boolean creativeScreenOpen() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.currentScreen instanceof CreativeInventoryScreen
                && client.interactionManager != null && client.interactionManager.hasCreativeInventory();
    }
}
