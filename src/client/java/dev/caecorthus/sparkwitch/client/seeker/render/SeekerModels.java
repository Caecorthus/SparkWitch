package dev.caecorthus.sparkwitch.client.seeker.render;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.Identifier;

/**
 * Client only: loads the placed car/camera Blockbench models ({@link SeekerRules#CAR_PLACED_MODEL_ID},
 * {@link SeekerRules#CAMERA_PLACED_MODEL_ID}) through Fabric's ModelLoadingPlugin, as the Hunter trap does, so the
 * entity renderers can draw them with the item renderer.
 * 仅客户端：通过 Fabric ModelLoadingPlugin 加载小车与摄像头的放置模型（与猎人捕兽夹相同），
 * 供实体渲染器用物品渲染器绘制。
 */
public final class SeekerModels {
    private static boolean registered;

    private SeekerModels() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ModelLoadingPlugin.register(context -> context.addModels(
                SeekerRules.CAR_PLACED_MODEL_ID, SeekerRules.CAMERA_PLACED_MODEL_ID));
    }

    static BakedModel model(Identifier id) {
        return ((FabricBakedModelManager) MinecraftClient.getInstance().getBakedModelManager()).getModel(id);
    }
}
