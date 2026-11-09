package dev.caecorthus.sparkwitch.client.bear.helmet;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.bear.BearItems;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTHelmetItem;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTVisor;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Stable client contract: the {@code rys_t} model predicate the art package's override reads.
 * {@code sparkwitch:visor_up} is 1 while the visor is raised and 0 while it is down (the default), so
 * {@code models/item/rys_t.json} (visor down) switches to {@code models/item/rys_t_visor_up.json}. It reads only the
 * stack's synced {@link RysTVisor}, so the worn model (vanilla {@code HeadFeatureRenderer}), the GUI icon and the held
 * item all follow the server's visor for every viewer.
 * 稳定客户端契约：美术包模型覆盖读取的 {@code rys_t} 模型谓词。面罩抬起时 {@code sparkwitch:visor_up} 为 1，放下时（默认）为 0，
 * 因此 {@code models/item/rys_t.json}（面罩放下）切换为 {@code models/item/rys_t_visor_up.json}。只读取物品已同步的
 * {@link RysTVisor}，因此穿戴模型（原版 {@code HeadFeatureRenderer}）、GUI 图标与手持物品对所有观察者都跟随服务端的面罩状态。
 */
public final class RysTModelPredicates {
    public static final Identifier VISOR_UP_PREDICATE_ID = SparkWitch.id("visor_up");

    private RysTModelPredicates() {
    }

    static void register() {
        ModelPredicateProviderRegistry.register(BearItems.RYS_T, VISOR_UP_PREDICATE_ID,
                (stack, world, entity, seed) -> visorUpValue(RysTHelmetItem.visor(stack)));
    }

    public static float visorUpValue(@Nullable RysTVisor visor) {
        return visor != null && !visor.down() ? 1.0F : 0.0F;
    }
}
