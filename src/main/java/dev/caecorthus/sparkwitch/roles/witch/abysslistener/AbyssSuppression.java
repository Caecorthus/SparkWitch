package dev.caecorthus.sparkwitch.roles.witch.abysslistener;

import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Role-private suppression toolkit shared by the Warden's Shriek, the Shriek Gun and the Deep Dark Zone: target
 * eligibility, ally detection, sanity drain, forced cooldowns and status effects. Server-only.
 * Frozen stub (L0): every method fails closed (affects nobody, changes nothing) until L1 implements it.
 * 监守之啸、啸音铳与深暗领域共用的职业私有压制工具：目标资格、队友判定、理智扣除、强制冷却与状态效果。仅服务端。
 * 冻结桩（L0）：在 L1 实现之前，所有方法一律安全失败（不影响任何人、不改变任何状态）。
 */
public final class AbyssSuppression {
    private AbyssSuppression() {
    }

    /** L1 implements: playing, alive, not spectating, not an active Wraith. / L1 实现：参与中、存活、非旁观、非激活冤魂。 */
    public static boolean isParticipantTarget(ServerPlayerEntity player) {
        return false;
    }

    /** L1 implements: witch-faction member (an ally of the Abyss Listener). / L1 实现：魔女阵营成员（聆渊者的队友）。 */
    public static boolean isAlly(ServerPlayerEntity player) {
        return false;
    }

    /** L1 implements: full eligibility plus the SFA veto for {@code actionId}. / L1 实现：完整资格判定加 SFA 否决。 */
    public static boolean canAffect(@Nullable ServerPlayerEntity actor, ServerPlayerEntity target, Identifier actionId) {
        return false;
    }

    /** L1 implements: the target's sanity is real (SparkTraits Kind / Impostor aware). / L1 实现：目标是否拥有真实理智。 */
    public static boolean hasRealSanity(ServerPlayerEntity player) {
        return false;
    }

    /** L1 implements: lowers Wathe mood by {@code amount} (1 point = 0.01). / L1 实现：降低 Wathe 理智。 */
    public static void drainSanity(ServerPlayerEntity player, float amount) {
    }

    /** L1 implements: SFA {@code ForcedCooldowns.raiseAll} for role skills and items. / L1 实现：强制冷却。 */
    public static void forceCooldowns(ServerPlayerEntity player, int ticks) {
    }

    /** L1 implements: icon shown, no particles, source recorded. / L1 实现：显示图标、不显示粒子、记录来源。 */
    public static void addEffect(
            ServerPlayerEntity player,
            RegistryEntry<StatusEffect> effect,
            int ticks,
            int amplifier,
            @Nullable Entity source
    ) {
    }
}
