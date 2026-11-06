package dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet;

import dev.caecorthus.sparkwitch.registry.SparkWitchDataComponents;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTHelmetItem;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.VisorCrack;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * WP3's single crack call site (D4), a stand-in for the helmet package's {@code RysTVisorGeometry.glassPoint} and
 * {@code RysTHelmetItem.addCrack(ServerPlayerEntity, VisorCrack)}: integration swaps the two bodies below for those calls.
 * The crack is written onto the WORN stack, so vanilla equipment sync shows it to every client and the slot sync to
 * the wearer; it stays for the helmet's life (oldest dropped past {@code MAX_VISOR_CRACKS}).
 * WP3 唯一的裂纹调用点（D4），暂代头盔工作包的 {@code RysTVisorGeometry.glassPoint} 与
 * {@code RysTHelmetItem.addCrack(ServerPlayerEntity, VisorCrack)}：集成时把下方两个方法体换成这两个调用即可。裂纹写在
 * 佩戴中的物品上，因此原版装备同步会让所有客户端看到、槽位同步让佩戴者看到；在头盔存续期间保留（超过
 * {@code MAX_VISOR_CRACKS} 时丢弃最早的）。
 */
final class RysTVisorCracks {
    private RysTVisorCracks() {
    }

    /** Where on the glass a shot from {@code origin} lands. / 来自 {@code origin} 的射击落在玻璃上的位置。 */
    static VisorCrack glassPoint(ServerPlayerEntity wearer, @Nullable Vec3d origin) {
        return BearRicochetRules.glassCrack(wearer.getHeadYaw(), wearer.getEyePos(), origin,
                wearer.getRandom()::nextFloat);
    }

    static void addCrack(ServerPlayerEntity wearer, ItemStack helmet, VisorCrack crack) {
        if (helmet.getItem() instanceof RysTHelmetItem) {
            helmet.set(SparkWitchDataComponents.RYS_T_VISOR, RysTHelmetItem.visor(helmet).withCrack(crack));
        }
    }
}
