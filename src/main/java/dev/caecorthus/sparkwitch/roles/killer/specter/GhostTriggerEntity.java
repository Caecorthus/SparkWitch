package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Invisible placed ghost trigger. It never renders, cannot be damaged, pushed or reclaimed, and is spent as soon as
 * any living player walks into its box. This step only consumes the trigger; the jump-scare/sanity/mark effect is
 * added in a later step at the marked point.
 * 放置后隐形的鬼魂触发器。它不渲染、不可破坏、不可推动或回收，任何存活玩家走进判定框即消耗。
 * 本步只做消耗；突脸/理智/标记效果下一步在注释处接入。
 */
public final class GhostTriggerEntity extends Entity {
    public static final Identifier EVENT_TRIGGERED = SparkWitch.id("ghost_trigger_triggered");
    /** Same arm delay as the Hunter trap: 0.5 s. / 与猎人夹子相同的 0.5 秒布防延迟。 */
    private static final int ARM_TICKS = 10;
    private static final double TRIGGER_EXPAND_XZ = 0.35D;
    private static final double TRIGGER_EXPAND_Y = 0.15D;

    private int armTicks = ARM_TICKS;
    private @Nullable UUID ownerUuid;

    public GhostTriggerEntity(EntityType<? extends GhostTriggerEntity> type, World world) {
        super(type, world);
        noClip = true;
    }

    public void setOwner(@Nullable PlayerEntity owner) {
        ownerUuid = owner == null ? null : owner.getUuid();
    }

    @Nullable
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            return;
        }
        // The trigger never moves itself; zero any velocity a fluid or explosion gave it.
        // 触发器从不自行移动；被流体或爆炸推动时清零速度。
        if (!getVelocity().equals(Vec3d.ZERO)) {
            setVelocity(Vec3d.ZERO);
            velocityModified = true;
        }
        if (!GameWorldComponent.KEY.get(getWorld()).isRunning()) {
            discard();
            return;
        }
        if (armTicks > 0) {
            armTicks--;
            return;
        }
        Box triggerBox = getBoundingBox().expand(TRIGGER_EXPAND_XZ, TRIGGER_EXPAND_Y, TRIGGER_EXPAND_XZ);
        for (PlayerEntity player : getWorld().getEntitiesByClass(
                PlayerEntity.class,
                triggerBox,
                GameFunctions::isPlayerAliveAndSurvival
        )) {
            trigger(player);
            break;
        }
    }

    private void trigger(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            recordTrigger(serverPlayer);
            SpecterTriggerService.applyTrigger(serverPlayer, ownerUuid);
        }
        discard();
    }

    private void recordTrigger(ServerPlayerEntity victim) {
        if (!(getWorld() instanceof ServerWorld world)) {
            return;
        }
        NbtCompound data = new NbtCompound();
        data.putUuid("target", victim.getUuid());
        PlayerEntity owner = ownerUuid == null ? null : world.getPlayerByUuid(ownerUuid);
        if (owner != null) {
            data.putUuid("actor", owner.getUuid());
        }
        GameRecordManager.putPos(data, "pos", getPos());
        GameRecordManager.recordGlobalEvent(
                world,
                EVENT_TRIGGERED,
                owner instanceof ServerPlayerEntity serverOwner ? serverOwner : null,
                data
        );
    }

    /** The placed trigger is unbreakable until it fires. / 已放置的触发器在触发前不可破坏。 */
    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        ownerUuid = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        armTicks = nbt.getInt("ArmTicks");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerUuid != null) {
            nbt.putUuid("Owner", ownerUuid);
        }
        nbt.putInt("ArmTicks", armTicks);
    }
}
