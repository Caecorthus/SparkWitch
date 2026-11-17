package dev.caecorthus.sparkwitch.client.sphinxhao;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchEntities;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.renderer.SphinxHaoFistEntityRenderer;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun.SphinxHaoHandGunItem;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Client wiring of the 「“手”枪」 (GUN work package), called once from {@code SparkWitchClient#registerEntityRenderers}:
 * the model predicate the gun's overrides read, the standalone fist model and the spring-fist renderer. The arm pose and
 * the no-re-equip rule live in {@code client/mixin/sphinxhao/}.
 * 「“手”枪」的客户端接线（GUN 工作包），由 {@code SparkWitchClient#registerEntityRenderers} 调用一次：手枪模型覆盖读取的
 * 模型谓词、独立的拳头模型与弹簧拳头渲染器。持枪姿势与不重播换手动画规则在 {@code client/mixin/sphinxhao/} 中。
 */
public final class SphinxHaoGunClient {
    /**
     * Stable client contract: 1 while the gun holds a fist, else 0; {@code models/item/sphinx_hao_hand_gun.json}
     * shows {@code sphinx_hao_hand_gun_empty} at 0. A fired gun is already empty, so the held gun shows no fist while
     * the spring fist is out.
     * 稳定客户端契约：手枪装有拳头时为 1，否则为 0；{@code models/item/sphinx_hao_hand_gun.json} 在 0 时显示
     * {@code sphinx_hao_hand_gun_empty}。开过火的枪已经是空膛，因此弹簧拳头伸出期间手中的枪不会再显示拳头。
     */
    public static final Identifier LOADED_PREDICATE_ID = SparkWitch.id("sphinx_hao_loaded");
    private static boolean registered;

    private SphinxHaoGunClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ModelPredicateProviderRegistry.register(SparkWitchItems.sphinxHaoHandGun(), LOADED_PREDICATE_ID,
                (stack, world, entity, seed) -> loadedValue(stack));
        SphinxHaoFistEntityRenderer.registerFistModel();
        EntityRendererRegistry.register(SparkWitchEntities.sphinxHaoFist(), SphinxHaoFistEntityRenderer::new);
    }

    public static float loadedValue(ItemStack stack) {
        return SphinxHaoHandGunItem.isLoaded(stack) ? 1.0F : 0.0F;
    }
}
