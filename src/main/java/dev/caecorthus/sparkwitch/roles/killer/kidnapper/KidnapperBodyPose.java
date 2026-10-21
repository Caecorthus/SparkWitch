package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

/**
 * Client render pose for a body carried by a Kidnapper. / 绑架者携带尸体的客户端渲染姿势。
 * Keeps the corpse level while its horizontal facing follows the carrier.
 * 保持尸体贴地，仅让水平朝向跟随携带者。
 */
public record KidnapperBodyPose(float bodyYaw, float relativeHeadYaw, float headPitch) {
    /** Requires both the direct carrier shape and exact role identity. / 同时要求直接玩家载具与精确职业身份。 */
    public static boolean isEligible(boolean hasDirectPlayerCarrier, boolean isExactKidnapper) {
        return hasDirectPlayerCarrier && isExactKidnapper;
    }

    /**
     * Wathe sets the vanilla riding flag on the skin model only, so a carried corpse's skin sat up while
     * its skeleton model stayed flat. A Kidnapper-carried corpse keeps the flat pose so skin and bones line up.
     * Wathe 只给皮肤模型设置原版骑乘标记，被携带尸体的皮肤会坐起而骨架仍平躺；绑架者携带的尸体保持平躺，使皮肤与骨架重合。
     */
    public static boolean skinModelRiding(boolean watheRiding, boolean isKidnapperCarried) {
        return watheRiding && !isKidnapperCarried;
    }

    public static KidnapperBodyPose fromCarrierRotation(
            float previousYaw,
            float yaw,
            float previousPitch,
            float pitch,
            float tickDelta
    ) {
        return new KidnapperBodyPose(
                interpolateAngle(previousYaw, yaw, tickDelta),
                0.0F,
                previousPitch + (pitch - previousPitch) * tickDelta
        );
    }

    private static float interpolateAngle(float previous, float current, float tickDelta) {
        float delta = (current - previous) % 360.0F;
        if (delta < -180.0F) {
            delta += 360.0F;
        }
        if (delta >= 180.0F) {
            delta -= 360.0F;
        }
        return previous + delta * tickDelta;
    }
}
