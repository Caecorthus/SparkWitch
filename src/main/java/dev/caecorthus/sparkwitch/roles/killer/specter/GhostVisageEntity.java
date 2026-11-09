package dev.caecorthus.sparkwitch.roles.killer.specter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Short-lived floating player-model hallucination spawned in front of the ghost-trigger victim. Only its viewer's
 * client renders it (the renderer checks the synced viewer UUID), it ignores collision/damage, and the server removes
 * it after the 2 s fade.
 * 鬼魂触发器受害者面前生成的浮空玩家模型幻影。只有受害者的客户端会渲染它（渲染器检查同步的 viewer UUID），
 * 它无视碰撞与伤害，2 秒渐变结束后由服务端移除。
 */
public final class GhostVisageEntity extends LivingEntity {
    public static final int LIFETIME_TICKS = 40;
    private static final TrackedData<Optional<UUID>> VIEWER = DataTracker.registerData(
            GhostVisageEntity.class,
            TrackedDataHandlerRegistry.OPTIONAL_UUID
    );

    public GhostVisageEntity(EntityType<? extends GhostVisageEntity> type, World world) {
        super(type, world);
        // Gravity and block collision are wanted: the visage falls and stands on the block under its spawn point.
        // 需要重力与方块碰撞：幻影会下落并踩在生成点下方的方块上。
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0D);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(VIEWER, Optional.empty());
    }

    public void setViewer(LivingEntity viewer) {
        dataTracker.set(VIEWER, Optional.of(viewer.getUuid()));
    }

    @Nullable
    public UUID getViewerUuid() {
        return dataTracker.get(VIEWER).orElse(null);
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient && age >= LIFETIME_TICKS) {
            discard();
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) {
            discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.isOf(DamageTypes.GENERIC_KILL) && !source.isOf(DamageTypes.OUT_OF_WORLD);
    }

    @Override
    public boolean canTakeDamage() {
        return false;
    }

    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    @Override
    protected void pushAway(Entity entity) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public Iterable<ItemStack> getArmorItems() {
        return List.of();
    }

    @Override
    public ItemStack getEquippedStack(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public Arm getMainArm() {
        return Arm.RIGHT;
    }

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
    }

    /** Normalised yaw facing from the entity back to the victim. / 从实体回望受害者的归一化偏航。 */
    public static float facingYaw(float viewerYaw) {
        return MathHelper.wrapDegrees(viewerYaw + 180.0F);
    }
}
