package dev.caecorthus.sparkwitch.mixin.fiend;

import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendFeignService;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.VultureEatC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * External seam (pinned NoellesRoles {@code 1.7.6-h1.5.6-spark}), owner request 2026-10-16: a Vulture that eats a
 * feigning Fiend's fake corpse kills that Fiend. Injects right after the eat receiver {@code lambda$registerPackets$2}
 * records its single {@code recordSkillUse}, which runs only once the eat is certain (cooldown set, body counted toward
 * the Vulture win, body discarded), and hands the receiver's only {@code PlayerBodyEntity} local to
 * {@link FiendFeignService#onBodyDevoured}; every other body returns at once there. Purely additive: nothing in the
 * eat changes, and the Magician decoy filter on the same lambda still decides which body is found. The full
 * descriptor with {@code require = 1} makes a rebuilt NoellesRoles jar fail at load instead of silently dropping the
 * rule. Class-level {@code remap = false} (NoellesRoles/Wathe members). Server only.
 * 外部接缝（固定 NoellesRoles {@code 1.7.6-h1.5.6-spark}），所有者 2026-10-16 需求：秃鹫吃掉正在假死的魔人的假尸体会杀死
 * 该魔人。注入点紧跟在进食接收器 {@code lambda$registerPackets$2} 唯一一次 {@code recordSkillUse} 之后，此时进食已确定
 * （冷却已设置、尸体已计入秃鹫胜利进度、尸体已移除），并把接收器中唯一的 {@code PlayerBodyEntity} 局部变量交给
 * {@link FiendFeignService#onBodyDevoured}；其他尸体在那里立即返回。纯叠加：进食本身不受任何改变，同一 lambda 上的魔术师
 * 诱饵过滤仍决定找到哪具尸体。完整描述符加 {@code require = 1}，NoellesRoles jar 重编后会在加载时报错，而不是悄悄失去规则。
 * 类级 {@code remap = false}（NoellesRoles/Wathe 成员）。仅服务端。
 */
@Mixin(value = Noellesroles.class, remap = false)
public abstract class NoellesVultureFiendFakeBodyMixin {
    @Inject(
            method = "lambda$registerPackets$2(Lorg/agmas/noellesroles/packet/VultureEatC2SPacket;"
                    + "Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/record/GameRecordManager;recordSkillUse("
                            + "Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;"
                            + "Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/nbt/NbtCompound;)V",
                    shift = At.Shift.AFTER
            ),
            require = 1,
            allow = 1
    )
    private static void sparkwitch$vultureDevoursFeigningFiend(
            VultureEatC2SPacket payload,
            ServerPlayNetworking.Context context,
            CallbackInfo ci,
            @Local PlayerBodyEntity body
    ) {
        FiendFeignService.onBodyDevoured(body, context.player());
    }
}
