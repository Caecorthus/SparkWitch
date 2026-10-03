package dev.caecorthus.sparkwitch.client.riftwalker.gate;

import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;

/**
 * Client only: loads the standalone placed-gate model {@link RiftGatePresentationRules#PLACED_MODEL_ID} through
 * Fabric's ModelLoadingPlugin (no item points at it), as the Hunter trap and Seeker devices do, so the renderer can
 * draw it with the item renderer. Its textures sit under {@code textures/item/}, so they are stitched into the block
 * atlas and the swirl's {@code .png.mcmeta} animates with no code.
 * 仅客户端：通过 Fabric ModelLoadingPlugin 加载独立的放置门模型（没有物品引用它），与猎人捕兽夹、搜寻者设备相同，
 * 供渲染器用物品渲染器绘制。贴图位于 {@code textures/item/}，会拼入方块图集，因此旋涡的 {@code .png.mcmeta} 动画无需代码。
 */
public final class RiftGateModels {
    private static boolean registered;

    private RiftGateModels() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ModelLoadingPlugin.register(context -> context.addModels(RiftGatePresentationRules.PLACED_MODEL_ID));
    }

    static BakedModel placedModel() {
        return ((FabricBakedModelManager) MinecraftClient.getInstance().getBakedModelManager())
                .getModel(RiftGatePresentationRules.PLACED_MODEL_ID);
    }
}
