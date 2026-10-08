package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecMagazineContents;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecMagazineItem;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRifleState;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.util.Identifier;

/**
 * Stable client contract: the {@code usec_rifle} model predicates the art package's model overrides read.
 * {@code sparkwitch:usec_magazine} is 1 with a magazine attached (empty or not), {@code sparkwitch:usec_suppressor} is
 * 1 with a suppressor fitted; both 0 otherwise. They read only the stack's synced {@link UsecRifleState}.
 * 稳定客户端契约：美术包的模型覆盖读取的 {@code usec_rifle} 模型谓词。装有弹匣（无论是否为空）时
 * {@code sparkwitch:usec_magazine} 为 1，装有消音器时 {@code sparkwitch:usec_suppressor} 为 1，否则均为 0。只读取物品
 * 已同步的 {@link UsecRifleState}。
 * <p>
 * {@code sparkwitch:usec_loaded} on a loose {@code usec_magazine} is 1 when it holds at least one round, so its model
 * shows the loaded or the empty icon (owner, 2026-10-07).
 * 散装 {@code usec_magazine} 上的 {@code sparkwitch:usec_loaded}：至少有一发子弹时为 1，使模型显示有弹或无弹图标（所有者 2026-10-07）。
 */
public final class UsecModelPredicates {
    public static final Identifier MAGAZINE_PREDICATE_ID = SparkWitch.id("usec_magazine");
    public static final Identifier SUPPRESSOR_PREDICATE_ID = SparkWitch.id("usec_suppressor");
    public static final Identifier LOADED_PREDICATE_ID = SparkWitch.id("usec_loaded");

    private UsecModelPredicates() {
    }

    static void register() {
        ModelPredicateProviderRegistry.register(SparkWitchItems.usecRifle(), MAGAZINE_PREDICATE_ID,
                (stack, world, entity, seed) -> magazineValue(UsecRifleState.read(stack)));
        ModelPredicateProviderRegistry.register(SparkWitchItems.usecRifle(), SUPPRESSOR_PREDICATE_ID,
                (stack, world, entity, seed) -> suppressorValue(UsecRifleState.read(stack)));
        ModelPredicateProviderRegistry.register(SparkWitchItems.usecMagazine(), LOADED_PREDICATE_ID,
                (stack, world, entity, seed) -> loadedValue(UsecMagazineItem.contents(stack)));
    }

    public static float loadedValue(UsecMagazineContents contents) {
        return contents != null && !contents.isEmpty() ? 1.0F : 0.0F;
    }

    public static float magazineValue(UsecRifleState state) {
        return state != null && state.hasMagazine() ? 1.0F : 0.0F;
    }

    public static float suppressorValue(UsecRifleState state) {
        return state != null && state.suppressor() ? 1.0F : 0.0F;
    }
}
