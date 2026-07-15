package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/** Computes the corpse's ground-level follow point. / 只负责计算尸体贴地跟随位置。 */
public final class KidnapperPassengerPositioning {
    private KidnapperPassengerPositioning() {
    }

    public static Vec3d behind(Entity carrier) {
        return behind(carrier.getPos(), carrier.getYaw());
    }

    public static Vec3d behind(Vec3d carrierPosition, float carrierYaw) {
        double radians = Math.toRadians(carrierYaw);
        return carrierPosition.add(
                Math.sin(radians) * KidnapperRules.FOLLOW_DISTANCE,
                0.0D,
                -Math.cos(radians) * KidnapperRules.FOLLOW_DISTANCE
        );
    }
}
