package dev.caecorthus.sparkwitch.mixin.hiro;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.killer.hiro.returning.HiroFakeBodies;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.agmas.noellesroles.Noellesroles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * External seam (pinned NoellesRoles {@code 1.7.6-h1.5.6-spark}), owner rule 4 of 二阶堂希罗's 死亡回归: the Vulture eat
 * receiver {@code lambda$registerPackets$2} never eats the fake corpse of a fake-dead victim (it would count toward
 * the Vulture's win and free the victim's camera). Same seam as {@code vulture/NoellesVultureDecoyBodyMixin}: it wraps
 * the receiver's single {@code World#getEntitiesByType} body lookup, which runs only after NoellesRoles' role, life,
 * Taotie and cooldown gates, and drops the fake corpse from the result, so NoellesRoles runs its own silent no-body
 * path (no cooldown, no message; unlike a Magician decoy, the refusal must not name the corpse as fake). Additive
 * {@code @WrapOperation} (never {@code @Redirect}); the decoy filter, SparkStrength's Partygoer filter and the
 * Fiend's / Charon's post-eat hooks on the same lambda are independent. The full descriptor with {@code require = 1}
 * makes a rebuilt NoellesRoles jar fail at load instead of silently dropping the guard. Class-level
 * {@code remap = false} (NoellesRoles/Fabric members); the vanilla {@code World} target says {@code remap = true}.
 * Server only.
 * 外部接缝（固定 NoellesRoles {@code 1.7.6-h1.5.6-spark}），二阶堂希罗「死亡回归」所有者规则 4：秃鹫进食接收器
 * {@code lambda$registerPackets$2} 永远不会吃掉假死受害者的假尸体（否则会计入秃鹫胜利进度并放开受害者的视角）。与
 * {@code vulture/NoellesVultureDecoyBodyMixin} 相同的接缝：包装接收器中唯一一次 {@code World#getEntitiesByType} 尸体查找
 * （只在 NoellesRoles 的职业、存活、饕餮与冷却检查之后执行），并从结果中移除假尸体，于是 NoellesRoles 走它自己的静默
 * “没有尸体”分支（不进冷却、不提示；与魔术师诱饵不同，拒绝时不能点明尸体是假的）。叠加式 {@code @WrapOperation}（绝不使用
 * {@code @Redirect}）；同一 lambda 上的诱饵过滤、SparkStrength 派对客过滤以及魔人 / 卡戎的进食后钩子互不影响。完整描述符加
 * {@code require = 1}，NoellesRoles jar 重编后会在加载时报错，而不是悄悄失去守卫。类级 {@code remap = false}
 * （NoellesRoles/Fabric 成员）；原版 {@code World} 目标显式写 {@code remap = true}。仅服务端。
 */
@Mixin(value = Noellesroles.class, remap = false)
public abstract class NoellesVultureHiroFakeBodyMixin {
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
    private static List<PlayerBodyEntity> sparkwitch$vultureSkipsHiroFakeBodies(
            World world,
            TypeFilter<Entity, PlayerBodyEntity> filter,
            Box box,
            Predicate<? super PlayerBodyEntity> predicate,
            Operation<List<PlayerBodyEntity>> original
    ) {
        return HiroFakeBodies.withoutFakes(original.call(world, filter, box, predicate));
    }
}
