package dev.caecorthus.sparkwitch.client.sphinxhao;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchEntities;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.renderer.SphinxHaoFistEntityRenderer;
import dev.caecorthus.sparkwitch.entity.SphinxHaoFistEntity;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun.SphinxHaoFistMotion;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun.SphinxHaoHandGunItem;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client wiring of the 「“手”枪」 (GUN work package), called once from {@code SparkWitchClient#registerEntityRenderers}:
 * the two model predicates the gun's overrides read, the local fire-pending window, the standalone fist model and the
 * spring-fist renderer. The arm pose and the no-re-equip rule live in {@code client/mixin/sphinxhao/}.
 * 「“手”枪」的客户端接线（GUN 工作包），由 {@code SparkWitchClient#registerEntityRenderers} 调用一次：手枪模型覆盖读取的两个
 * 模型谓词、本地开火待定窗口、独立的拳头模型与弹簧拳头渲染器。持枪姿势与不重播换手动画规则在 {@code client/mixin/sphinxhao/} 中。
 */
public final class SphinxHaoGunClient {
    /**
     * Stable client contract: 1 while the gun holds a fist, else 0; {@code models/item/sphinx_hao_hand_gun.json}
     * shows {@code sphinx_hao_hand_gun_empty} at 0.
     * 稳定客户端契约：手枪装有拳头时为 1，否则为 0；{@code models/item/sphinx_hao_hand_gun.json} 在 0 时显示
     * {@code sphinx_hao_hand_gun_empty}。
     */
    public static final Identifier LOADED_PREDICATE_ID = SparkWitch.id("sphinx_hao_loaded");
    /**
     * Stable client contract: 1 while the holder's spring fist is out (or the local shot is pending), else 0; the last
     * override shows the body-only {@code sphinx_hao_hand_gun_fired}, so no second fist or folded lattice is drawn on
     * the gun while the flying one is out, for the shooter and for everyone watching.
     * 稳定客户端契约：持有者的弹簧拳头伸出期间（或本地射击待定期间）为 1，否则为 0；最后一条覆盖显示只有枪身的
     * {@code sphinx_hao_hand_gun_fired}，因此飞出的拳头在外时，射手与旁观者都不会在枪上再看到第二个拳头或折叠的剪刀架。
     */
    public static final Identifier FIRED_PREDICATE_ID = SparkWitch.id("sphinx_hao_fired");
    /** A live fist sits at its shooter's eyes at fire time; this covers a Speed III sprint. / 覆盖速度 III 冲刺的搜索半径。 */
    private static final double FIST_SEARCH_RADIUS = 8.0D;
    /**
     * Local shots awaiting the server (holder UUID → client world tick of the use). Client thread only; written by the
     * item's client use, read by the predicate; entries expire on their own.
     * 等待服务端的本地射击（持有者 UUID → 使用时的客户端世界刻）。仅客户端线程；由物品的客户端使用写入、谓词读取；条目自行过期。
     */
    private static final Map<UUID, Long> PENDING = new HashMap<>();
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
        ModelPredicateProviderRegistry.register(SparkWitchItems.sphinxHaoHandGun(), FIRED_PREDICATE_ID,
                (stack, world, entity, seed) -> firedValue(stack, entity));
        SphinxHaoHandGunItem.installClientFire(SphinxHaoGunClient::markFirePending);
        SphinxHaoFistEntityRenderer.registerFistModel();
        EntityRendererRegistry.register(SparkWitchEntities.sphinxHaoFist(), SphinxHaoFistEntityRenderer::new);
    }

    public static float loadedValue(ItemStack stack) {
        return SphinxHaoHandGunItem.isLoaded(stack) ? 1.0F : 0.0F;
    }

    /** The local client fired a loaded gun. / 本地客户端开火了一把已装填的枪。 */
    static void markFirePending(PlayerEntity player) {
        PENDING.put(player.getUuid(), player.getWorld().getTime());
    }

    static float firedValue(ItemStack stack, @Nullable LivingEntity holder) {
        if (!(holder instanceof PlayerEntity player)) {
            return 0.0F;
        }
        boolean fistAlive = hasLiveFist(player);
        Long pendingTick = PENDING.get(player.getUuid());
        long now = player.getWorld().getTime();
        if (pendingTick != null && SphinxHaoFiredModelRules.clearsPending(pendingTick, now,
                SphinxHaoHandGunItem.isLoaded(stack), fistAlive)) {
            PENDING.remove(player.getUuid());
            pendingTick = null;
        }
        return SphinxHaoFiredModelRules.showsFired(fistAlive, pendingTick, now) ? 1.0F : 0.0F;
    }

    /** A spring fist of {@code player} still in its out-hold-back timeline. / 该玩家仍在伸出-停留-收回时间线中的拳头。 */
    private static boolean hasLiveFist(PlayerEntity player) {
        return !player.getWorld().getEntitiesByClass(SphinxHaoFistEntity.class,
                player.getBoundingBox().expand(FIST_SEARCH_RADIUS),
                fist -> !fist.isRemoved() && !SphinxHaoFistMotion.expired(fist.age)
                        && fist.getOwnerPlayer() == player).isEmpty();
    }
}
