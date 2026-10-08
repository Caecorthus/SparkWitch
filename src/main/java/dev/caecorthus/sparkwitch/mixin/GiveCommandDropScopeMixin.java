package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.util.GiveCommandDropScope;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.GiveCommand;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The single SparkWitch wrapper around vanilla {@code /give}'s cosmetic drop. In 1.21.1 {@code GiveCommand.execute}
 * calls {@code dropItem(ItemStack, boolean)} twice: ordinal 0 drops what did not fit (a real item, left untouched) and
 * ordinal 1 drops the shared count-1 template after a full insert, then {@code setDespawnImmediately()} on the entity
 * if one came back (null is already handled). Only ordinal 1 runs inside {@link GiveCommandDropScope}, so bound-item
 * guards drop that copy instead of restoring it. Common config: the integrated server runs commands too. The call
 * and its result reach vanilla unchanged.
 * SparkWitch 唯一包装原版 {@code /give} 装饰性丢弃的位置。1.21.1 的 {@code GiveCommand.execute} 调用两次
 * {@code dropItem(ItemStack, boolean)}：序号 0 丢出放不下的部分（真实物品，不做改动），序号 1 在完全放入后丢出共用的数量为 1
 * 的模板，若返回实体则调用 {@code setDespawnImmediately()}（原版已处理返回 null）。只有序号 1 在
 * {@link GiveCommandDropScope} 内执行，因此绑定物品防护会直接丢弃该副本而不放回。放在通用配置中：集成服务端同样执行命令。
 * 调用本身及其结果原样交还原版。
 */
@Mixin(GiveCommand.class)
public abstract class GiveCommandDropScopeMixin {
    @WrapOperation(
            method = "execute(Lnet/minecraft/server/command/ServerCommandSource;"
                    + "Lnet/minecraft/command/argument/ItemStackArgument;Ljava/util/Collection;I)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;dropItem("
                            + "Lnet/minecraft/item/ItemStack;Z)Lnet/minecraft/entity/ItemEntity;",
                    ordinal = 1
            )
    )
    private static ItemEntity sparkwitch$markCosmeticGiveDrop(ServerPlayerEntity player, ItemStack stack,
                                                              boolean retainOwnership,
                                                              Operation<ItemEntity> original) {
        return GiveCommandDropScope.cosmeticDrop(stack, () -> original.call(player, stack, retainOwnership));
    }
}
