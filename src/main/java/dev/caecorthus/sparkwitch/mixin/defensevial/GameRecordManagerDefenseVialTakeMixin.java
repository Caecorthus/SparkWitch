package dev.caecorthus.sparkwitch.mixin.defensevial;

import dev.caecorthus.sparkwitch.item.defense.DefenseVialTrays;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "A serving left this tray" for the Defense Vial. Every take path records exactly one platter take right before it
 * hands the serving over: Wathe's random serving (its 4-arg overload delegates here), NoellesRoles' fine drink, base
 * spirit and Waiter takes, SparkStrength's timed bomb and Coroner Waiter takes, and SparkTraits' conscience poison.
 * Several of those cancel {@code FoodPlatterBlock#onUse} at HEAD, so a hook on {@code onUse} itself would miss them.
 * TAIL runs only after the record was written (an active match), so the replay shows the take before the shield.
 * 防御药剂的「一份食物或饮品离开了这个托盘」。每条取用路径都会在递出这一份之前恰好记录一次餐盘取用：Wathe 的随机取用（其四参
 * 重载委托到这里）、NoellesRoles 的佳酿、基酒与服务员取用、SparkStrength 的定时炸弹与验尸官服务员取用，以及 SparkTraits 的
 * 良心毒药。其中好几条会在 HEAD 取消 {@code FoodPlatterBlock#onUse}，挂在 {@code onUse} 上会漏掉它们。TAIL 只在记录已写入
 * （对局进行中）后执行，因此回放里取用排在护盾之前。
 */
@Mixin(value = GameRecordManager.class, remap = false)
public abstract class GameRecordManagerDefenseVialTakeMixin {
    @Inject(
            method = "recordPlatterTake(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Lnet/minecraft/util/math/BlockPos;Ljava/lang/String;Lnet/minecraft/nbt/NbtCompound;)V",
            at = @At("TAIL"),
            remap = false
    )
    private static void sparkwitch$shieldDefenseVialTaker(
            ServerPlayerEntity player,
            Identifier itemId,
            BlockPos platterPos,
            @Nullable String poisoner,
            @Nullable NbtCompound extra,
            CallbackInfo ci
    ) {
        DefenseVialTrays.onServingTaken(player, platterPos);
    }
}
