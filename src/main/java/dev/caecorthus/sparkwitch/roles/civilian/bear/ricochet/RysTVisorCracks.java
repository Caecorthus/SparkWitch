package dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet;

import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTHelmetItem;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTVisorGeometry;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.VisorCrack;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * WP3's single crack call site (D4), delegating to the helmet package: {@code RysTVisorGeometry.nearestGlassPoint}
 * (any front shot lands on the glass, clamped to its edge) and {@code RysTHelmetItem.addCrack}. The crack is written
 * onto the WORN stack, so vanilla equipment sync shows it to every client and the slot sync to the wearer; it stays for
 * the helmet's life (oldest dropped past {@code MAX_VISOR_CRACKS}). When the geometry finds no glass point (a shot that
 * is in the front half-space horizontally but not in front of the face), WP3's angular mapping is the fallback.
 * WP3 唯一的裂纹调用点（D4），委托给头盔工作包：{@code RysTVisorGeometry.nearestGlassPoint}（任何来自前方的射击都落在玻璃上，
 * 夹到边缘）与 {@code RysTHelmetItem.addCrack}。裂纹写在佩戴中的物品上，因此原版装备同步会让所有客户端看到、槽位同步让
 * 佩戴者看到；在头盔存续期间保留（超过 {@code MAX_VISOR_CRACKS} 时丢弃最早的）。几何上找不到玻璃点时（水平方向在前方
 * 但不在面部正前方），退回 WP3 的角度映射。
 */
final class RysTVisorCracks {
    private RysTVisorCracks() {
    }

    /** Where on the glass a shot from {@code origin} lands. / 来自 {@code origin} 的射击落在玻璃上的位置。 */
    static VisorCrack glassPoint(ServerPlayerEntity wearer, @Nullable Vec3d origin) {
        Vec3d eye = wearer.getEyePos();
        RysTVisorGeometry.GlassPoint point = origin == null ? null
                : RysTVisorGeometry.nearestGlassPoint(eye.subtract(origin), wearer.getHeadYaw(), wearer.getPitch());
        if (point != null) {
            return RysTVisorGeometry.crackAt(point, wearer.getRandom());
        }
        return BearRicochetRules.glassCrack(wearer.getHeadYaw(), eye, origin, wearer.getRandom()::nextFloat);
    }

    static void addCrack(ServerPlayerEntity wearer, ItemStack helmet, VisorCrack crack) {
        if (helmet.getItem() instanceof RysTHelmetItem) {
            RysTHelmetItem.addCrack(wearer, crack);
        }
    }
}
