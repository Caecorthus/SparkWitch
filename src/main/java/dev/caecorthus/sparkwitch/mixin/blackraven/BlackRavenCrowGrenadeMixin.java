package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.mixin.JudgeProjectileOwnerAccessor;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHits;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowTargets;
import dev.doctor4t.wathe.entity.GrenadeEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Set;
import java.util.UUID;

/**
 * Wathe grenade on a Raven Form crow (plan v2 §2.5), a crow pass of its own AFTER the grenade's collision: Wathe's kill
 * loop (and SparkStrength's {@code @ModifyExpressionValue} that refilters its victims by each player's own box, sphere
 * and ray) only ever sees players, so the crow is never routed through it. Once the original collision really exploded
 * on the server (the grenade was live before and Wathe's server branch discarded it; a cancelled collision does
 * nothing), {@link BlackRavenCrowHits#onGrenadeBlast} kills the owner of every crow in the 3-block sphere around the
 * grenade box centre with a clear line from it (the Magician puppet / Seeker device / M67 measure), credited to the
 * thrower inside its Judge attribution; owners the loop already tried to kill (their body was hit) and dead owners are
 * skipped. Never the thrower's own crow. NoellesRoles' Bomber and SparkTraits' Bomb Maniac throw this same entity.
 * Chains with SparkStrength's own {@code @WrapMethod} on this method (order irrelevant).
 * 化鸦乌鸦上的 Wathe 手雷（计划 v2 §2.5），在手雷碰撞之后单独扫描乌鸦：Wathe 的击杀循环（以及 SparkStrength 按每名玩家自身
 * 箱体、球体与射线重新筛选受害者的 {@code @ModifyExpressionValue}）只处理玩家，因此乌鸦从不经过它。原始碰撞确实在服务端爆炸后
 * （手雷此前存活，且 Wathe 的服务端分支将其移除；被取消的碰撞不做任何事），{@link BlackRavenCrowHits#onGrenadeBlast} 处死手雷
 * 箱体中心 3 格球内、且与其视线畅通的每只乌鸦的主人（与魔术师皮套 / 搜寻者设备 / M67 的量法相同），在投掷者的审判官归因内记在
 * 其名下；跳过循环已尝试击杀的主人（其本体已被命中）与已死亡的主人。从不包括投掷者自己的乌鸦。NoellesRoles 炸弹客与 SparkTraits
 * 炸弹狂人投掷的也是同一实体。与 SparkStrength 在该方法上的 {@code @WrapMethod} 串联（顺序无关）。
 */
@Mixin(GrenadeEntity.class)
public abstract class BlackRavenCrowGrenadeMixin extends ThrownItemEntity {
    protected BlackRavenCrowGrenadeMixin(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    @WrapMethod(method = "onCollision")
    private void sparkwitch$crowPassAfterBlast(HitResult hitResult, Operation<Void> original) {
        if (isRemoved() || !(getWorld() instanceof ServerWorld world)) {
            original.call(hitResult);
            return;
        }
        Set<UUID> attempted = BlackRavenCrowTargets.recordBlastAttempts(() -> original.call(hitResult));
        if (isRemoved()) {
            BlackRavenCrowHits.onGrenadeBlast(world, getBoundingBox().getCenter(),
                    getOwner() instanceof ServerPlayerEntity thrower ? thrower : null,
                    ((JudgeProjectileOwnerAccessor) this).sparkwitch$judgeOwnerUuid(), attempted);
        }
    }
}
