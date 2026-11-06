package dev.caecorthus.sparkwitch.roles.civilian.bear.launcher;

import dev.caecorthus.sparkwitch.roles.civilian.bear.BearItems;
import dev.caecorthus.sparkwitch.roles.civilian.bear.BearRules;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/**
 * A fired 40 mm smoke round ({@code sparkwitch:smoke_round_40mm}). Server-authoritative: only the server will decide
 * the burst; the client only draws the flight. Never saved. WP0 stub: it flies with vanilla thrown-item physics and is
 * discarded on its first impact or after {@link BearRules#BACKUP_FUSE_TICKS}, without any smoke. WP1 replaces the
 * ballistics, the 4-block arming, the backup fuse burst and the {@code SmokeCloudService.spawn} call.
 * 发射出的 40 mm 烟雾榴弹（{@code sparkwitch:smoke_round_40mm}）。服务端权威：只有服务端决定爆开；客户端只绘制飞行。从不存盘。
 * WP0 桩：以原版投掷物物理飞行，首次撞击或 {@link BearRules#BACKUP_FUSE_TICKS} 刻后直接移除，不产生烟雾。WP1 负责替换弹道、
 * 4 格解除保险、备用引信爆开与 {@code SmokeCloudService.spawn} 调用。
 */
public class SmokeRoundEntity extends ThrownItemEntity {
    public SmokeRoundEntity(EntityType<? extends SmokeRoundEntity> type, World world) {
        super(type, world);
    }

    public SmokeRoundEntity(World world, LivingEntity owner) {
        super(BearEntities.SMOKE_ROUND, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        return BearItems.SMOKE_ROUND;
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient() && age >= BearRules.BACKUP_FUSE_TICKS) {
            discard();
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!getWorld().isClient()) {
            discard();
        }
    }

    /** Rounds in flight never outlive a save. / 飞行中的榴弹从不存盘。 */
    @Override
    public boolean shouldSave() {
        return false;
    }
}
