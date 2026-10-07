package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.magician.MagicianPuppetAim;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.agmas.noellesroles.demonhunter.DemonHunterPistolItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client Demon Hunter pistol target (NoellesRoles): {@code getGunTarget} only sees alive survival players, so a
 * Magician puppet would be shot through. RETURN-based: NoellesRoles' result (inside the Wraith pass-through wrapper) is
 * kept unless a puppet lies strictly nearer on the 5-block look ray (the shared {@code MagicianPuppetAim} rule of the
 * Wathe gun and knife pickers), then the puppet's {@code EntityHitResult} is sent
 * ({@code resolveTargetFromHitResult} forwards any entity id); the Seeker device wrapper in
 * {@code SeekerDemonHunterTargetMixin} then still prefers a nearer device. Client prediction only: the server receiver
 * ({@code MagicianPuppetHits.onDemonHunterPayload}) validates everything. {@code remap = false}: NoellesRoles member.
 * 客户端猎魔枪目标（NoellesRoles）：{@code getGunTarget} 只识别存活的生存模式玩家，魔术师皮套会被直接打穿。本注入基于返回值：
 * 保留 NoellesRoles 的结果（处于冤魂穿透包装之内），除非 5 格视线上有严格更近的皮套（与 Wathe 枪械、刀选靶共用的
 * {@code MagicianPuppetAim} 规则），此时改为发送该皮套的
 * {@code EntityHitResult}（{@code resolveTargetFromHitResult} 会转发任意实体 id）；随后 {@code SeekerDemonHunterTargetMixin}
 * 的设备包装仍会优先更近的设备。仅为客户端预测，一切由服务端接收器（{@code MagicianPuppetHits.onDemonHunterPayload}）校验。
 * {@code remap = false}：NoellesRoles 成员。
 */
@Mixin(value = DemonHunterPistolItem.class, remap = false)
public abstract class MagicianDemonHunterTargetMixin {
    /** NoellesRoles' {@code getGunTarget} reach (5 blocks). / 与 NoellesRoles {@code getGunTarget} 的 5 格射程一致。 */
    @Unique
    private static final double SPARKWITCH$PISTOL_RANGE = 5.0D;

    @Inject(method = "getGunTarget", at = @At("RETURN"), cancellable = true)
    private static void sparkwitch$preferNearerPuppet(PlayerEntity user, CallbackInfoReturnable<HitResult> cir) {
        HitResult original = cir.getReturnValue();
        // Invisible puppets count, as NoellesRoles' pick counts invisible players. / 隐身皮套同样计入，与 NoellesRoles 计入隐身玩家一致。
        HitResult preferred = MagicianPuppetAim.preferNearerPuppet(user, original, SPARKWITCH$PISTOL_RANGE, false);
        if (preferred != original) {
            cir.setReturnValue(preferred);
        }
    }
}
