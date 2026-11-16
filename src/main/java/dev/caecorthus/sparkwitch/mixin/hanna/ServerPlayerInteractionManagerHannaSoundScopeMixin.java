package dev.caecorthus.sparkwitch.mixin.hanna;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaSoundScope;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Hanna float silence, scope seam 1 of 4: the server's block use and item use. Wathe's doors, locks, buttons, vents and
 * most item uses play their sound without a source ({@code world.playSound(null, ...)}), so the server cannot tell
 * whose sound it is unless the interaction names its player in {@link HannaSoundScope}. Both methods run on the server
 * thread only. The vanilla call always runs exactly once with its original arguments and result; only the thread-local
 * actor is set around it, for every player and without reading any state (see {@link HannaSoundScope}).
 * <p>Block breaking is left out on purpose: its sound is a {@code syncWorldEvent}, which the mute gate never sees.
 * 汉娜浮游静音，作用域接缝 1/4：服务端的方块使用与物品使用。Wathe 的门、锁、按钮、通风口以及大多数物品使用都以无来源方式播放
 * 声音（{@code world.playSound(null, ...)}），除非交互在 {@link HannaSoundScope} 中标明其玩家，否则服务端无法判断声音属于谁。
 * 两个方法都只在服务端线程运行。原版调用始终以原参数恰好执行一次并返回原结果；仅在其前后设置线程局部的行动者，对所有玩家生效且
 * 不读取任何状态（见 {@link HannaSoundScope}）。
 * <p>有意不包含方块破坏：其声音是 {@code syncWorldEvent}，静音闸门根本看不到。
 */
@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerHannaSoundScopeMixin {
    @WrapMethod(
            method = "interactBlock(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)"
                    + "Lnet/minecraft/util/ActionResult;"
    )
    private ActionResult sparkwitch$scopeHannaBlockUseSounds(ServerPlayerEntity player, World world, ItemStack stack,
                                                             Hand hand, BlockHitResult hitResult,
                                                             Operation<ActionResult> original) {
        ServerPlayerEntity previous = HannaSoundScope.enter(player);
        try {
            return original.call(player, world, stack, hand, hitResult);
        } finally {
            HannaSoundScope.exit(previous);
        }
    }

    @WrapMethod(
            method = "interactItem(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;"
    )
    private ActionResult sparkwitch$scopeHannaItemUseSounds(ServerPlayerEntity player, World world, ItemStack stack,
                                                            Hand hand, Operation<ActionResult> original) {
        ServerPlayerEntity previous = HannaSoundScope.enter(player);
        try {
            return original.call(player, world, stack, hand);
        } finally {
            HannaSoundScope.exit(previous);
        }
    }
}
