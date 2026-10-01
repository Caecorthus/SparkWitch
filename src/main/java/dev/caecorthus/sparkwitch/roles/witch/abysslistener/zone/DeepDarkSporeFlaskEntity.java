package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/**
 * Thrown Deep Dark Spore Flask (Shock Device template). Server-authoritative: only the server resolves the landing
 * and opens the Deep Dark Zone; the client merely predicts the flight. It never outlives the round and is never
 * saved with the world.
 * 投出的深暗孢瓶（以电击装置为模板）。由服务端权威决定：只有服务端结算落点并展开深暗领域，客户端只预测飞行轨迹。
 * 它永不跨出本局，也从不随世界保存。
 */
public final class DeepDarkSporeFlaskEntity extends ThrownItemEntity {
    public DeepDarkSporeFlaskEntity(EntityType<? extends DeepDarkSporeFlaskEntity> type, World world) {
        super(type, world);
    }

    public DeepDarkSporeFlaskEntity(World world, LivingEntity owner) {
        super(AbyssListenerEntities.deepDarkSporeFlask(), owner, world);
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        // L3 implements: landing opens the Deep Dark Zone. Until then the flask simply breaks on the server.
        // L3 实现：落地后展开深暗领域。在此之前孢瓶仅在服务端碎裂消失。
        super.onCollision(hitResult);
        if (!getWorld().isClient() && !isRemoved()) {
            discard();
        }
    }

    @Override
    protected Item getDefaultItem() {
        return SparkWitchItems.deepDarkSporeFlask();
    }

    @Override
    public boolean shouldSave() {
        return false;
    }
}
