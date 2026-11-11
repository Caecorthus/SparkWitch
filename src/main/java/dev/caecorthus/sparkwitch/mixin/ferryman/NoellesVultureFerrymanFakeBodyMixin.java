package dev.caecorthus.sparkwitch.mixin.ferryman;

import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanFeignService;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.VultureEatC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * External seam (pinned NoellesRoles {@code 1.7.6-h1.5.6-spark}): a Vulture that eats the fake corpse of a player
 * feigning before the Ferryman kills that player. Injects right after the eat receiver records its single
 * {@code recordSkillUse}, where the eat is already certain (cooldown set, body counted toward the Vulture win, body
 * discarded), and hands the receiver's only {@code PlayerBodyEntity} local to
 * {@link FerrymanFeignService#onBodyDevoured}; every other body returns there at once. Purely additive. The full
 * descriptor with {@code require = 1} fails at load if NoellesRoles is rebuilt instead of silently dropping the rule.
 * Class-level {@code remap = false} (NoellesRoles/Wathe members). Server only.
 * 外部接缝（固定 NoellesRoles {@code 1.7.6-h1.5.6-spark}）：秃鹫吃掉在摆渡人面前假死者的假尸体时杀死该玩家。注入点紧跟在
 * 进食接收器唯一一次 {@code recordSkillUse} 之后，此时进食已确定（冷却已设置、尸体已计入秃鹫胜利进度、尸体已移除），
 * 并把接收器中唯一的 {@code PlayerBodyEntity} 局部变量交给 {@link FerrymanFeignService#onBodyDevoured}；其他尸体在那里
 * 立即返回。纯叠加。完整描述符加 {@code require = 1}，NoellesRoles 重编后会在加载时报错，而不是悄悄失去规则。
 * 类级 {@code remap = false}（NoellesRoles/Wathe 成员）。仅服务端。
 */
@Mixin(value = Noellesroles.class, remap = false)
public abstract class NoellesVultureFerrymanFakeBodyMixin {
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
    private static void sparkwitch$vultureDevoursFeigningMarked(
            VultureEatC2SPacket payload,
            ServerPlayNetworking.Context context,
            CallbackInfo ci,
            @Local PlayerBodyEntity body
    ) {
        FerrymanFeignService.onBodyDevoured(body, context.player());
    }
}
