package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHits;
import dev.doctor4t.wathe.entity.GrenadeEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

/**
 * Wathe grenade on a Raven Form crow (plan v2 §2.5): the grenade kills every player whose feet lie in its box grown by
 * 3, in one loop over {@code getPlayers(predicate)}. This wraps that single lookup and appends the substituted owner of
 * every crow inside the same cube ({@link BlackRavenCrowHits#withGrenadeCrows}), so Wathe's own loop kills the Black
 * Raven ({@code wathe:grenade}, the thrower credited; the kill link ends the session first). Never the thrower's own
 * crow. A grenade already explodes on a crow it touches (the crow is hittable by projectiles).
 * 化鸦乌鸦上的 Wathe 手雷（计划 v2 §2.5）：手雷在一次 {@code getPlayers(predicate)} 循环中击杀脚下位于其箱体扩大 3 格范围内的
 * 所有玩家。本 mixin 包装这唯一一次查找，并追加同一立方体内每只乌鸦被替身的主人（{@link BlackRavenCrowHits#withGrenadeCrows}），
 * 因此由 Wathe 自身的循环处死黑羽鸦（{@code wathe:grenade}，记在投掷者名下；击杀链接先结束会话）。从不包括投掷者自己的乌鸦。
 * 手雷碰到乌鸦时本就会爆炸（乌鸦可被投射物命中）。
 */
@Mixin(GrenadeEntity.class)
public abstract class BlackRavenCrowGrenadeMixin extends ThrownItemEntity {
    protected BlackRavenCrowGrenadeMixin(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    @WrapOperation(method = "onCollision",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;getPlayers(Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List<ServerPlayerEntity> sparkwitch$crowsInBlast(ServerWorld world,
                                                            Predicate<? super ServerPlayerEntity> predicate,
                                                            Operation<List<ServerPlayerEntity>> original) {
        return BlackRavenCrowHits.withGrenadeCrows(this, getOwner(), original.call(world, predicate));
    }
}
