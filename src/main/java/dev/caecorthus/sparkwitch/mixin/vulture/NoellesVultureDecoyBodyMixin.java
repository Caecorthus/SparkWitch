package dev.caecorthus.sparkwitch.mixin.vulture;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.compat.NoellesVultureDecoyGuard;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.VultureEatC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * External seam (pinned NoellesRoles {@code 1.7.6-h1.5.6-spark}), owner decision 2026-10-07 D2: the Vulture eat
 * receiver {@code lambda$registerPackets$2} never eats a Magician decoy body. It wraps the receiver's single
 * {@code World#getEntitiesByType} body lookup, which runs only after NoellesRoles' role, life, Taotie and cooldown
 * gates, and hands the result to {@link NoellesVultureDecoyGuard}; a decoy-only result becomes empty, so NoellesRoles
 * runs its own no-body path. Additive {@code @WrapOperation} (never {@code @Redirect}); SparkTraits' HEAD silence guard
 * on the same lambda still decides first. The full descriptor with {@code require = 1} makes a rebuilt NoellesRoles jar
 * fail at load instead of silently dropping the guard. Class-level {@code remap = false} (NoellesRoles/Fabric
 * members); the vanilla {@code World} target says {@code remap = true}. Server only.
 * 外部接缝（固定 NoellesRoles {@code 1.7.6-h1.5.6-spark}），所有者 2026-10-07 决定 D2：秃鹫进食接收器
 * {@code lambda$registerPackets$2} 永远不会吃掉魔术师的诱饵尸体。包装接收器中唯一一次 {@code World#getEntitiesByType}
 * 尸体查找（只在 NoellesRoles 的职业、存活、饕餮与冷却检查之后执行），并交给 {@link NoellesVultureDecoyGuard}；
 * 结果只剩诱饵时变为空列表，NoellesRoles 随后走自己的“没有尸体”分支。叠加式 {@code @WrapOperation}（绝不用
 * {@code @Redirect}）；同一 lambda 上 SparkTraits 的 HEAD 沉默守卫仍先行裁决。完整描述符加 {@code require = 1}，
 * NoellesRoles jar 重编后会在加载时报错，而不是悄悄失去守卫。类级 {@code remap = false}（NoellesRoles/Fabric 成员）；
 * 原版 {@code World} 目标显式声明 {@code remap = true}。仅服务端。
 */
@Mixin(value = Noellesroles.class, remap = false)
public abstract class NoellesVultureDecoyBodyMixin {
    @WrapOperation(
            method = "lambda$registerPackets$2(Lorg/agmas/noellesroles/packet/VultureEatC2SPacket;"
                    + "Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getEntitiesByType(Lnet/minecraft/util/TypeFilter;"
                            + "Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;)Ljava/util/List;",
                    remap = true
            ),
            require = 1,
            allow = 1
    )
    private static List<PlayerBodyEntity> sparkwitch$vultureSkipsMagicianDecoys(
            World world,
            TypeFilter<Entity, PlayerBodyEntity> filter,
            Box box,
            Predicate<? super PlayerBodyEntity> predicate,
            Operation<List<PlayerBodyEntity>> original,
            VultureEatC2SPacket payload,
            ServerPlayNetworking.Context context
    ) {
        return NoellesVultureDecoyGuard.withoutDecoys(context.player(), original.call(world, filter, box, predicate));
    }
}
