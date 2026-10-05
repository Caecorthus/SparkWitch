package dev.caecorthus.sparkwitch.compat.recruitment;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Public recruitment seam to the optional SparkTraits facade; the reflection stays package-private.
 * 招募事务调用可选 SparkTraits 门面的公共入口；反射细节保持包内私有。 */
public final class RecruitmentTraits {
    private RecruitmentTraits() { }

    /** Server-only target gate: true while SparkTraits Depression psycho holds the target (true on provider failure).
     * 仅服务端的目标判定：SparkTraits 抑郁狂暴持有目标时为 true（提供方出错时亦为 true）。 */
    public static boolean isDepressionPsychoActive(PlayerEntity target) {
        return SparkTraitsRecruitmentBridge.isDepressionPsychoActive(target);
    }

    /** Server-only, after the recruit role is assigned and the converted balance is written (Well Supplied, kept or
     * redrawn, multiplies it again); never throws and returns the visible lost and redrawn trait names.
     * 仅服务端、在新身份分配且写入折算余额之后调用（持有物资充沛时，无论保留还是补抽到，都会再加成一次）；绝不抛出，
     * 返回可见的失去与补抽词条名。 */
    public static RecruitmentTraitChange replaceIneligibleTraits(ServerPlayerEntity recruit) {
        return SparkTraitsRecruitmentBridge.replaceTraitsIneligibleForRecruitRole(recruit);
    }
}
