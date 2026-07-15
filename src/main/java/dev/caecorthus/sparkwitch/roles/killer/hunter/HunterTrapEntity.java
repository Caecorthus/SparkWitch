package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** SparkWitch-owned placed Hunter trap. Trigger authority and poison attribution stay server-side. */
public final class HunterTrapEntity extends Entity {
    public static final Identifier EVENT_TRIGGERED = SparkWitch.id("hunter_trap_triggered");
    public static final Identifier POISON_SOURCE = SparkWitch.id("trap");
    public static final String POISON_PLACER_NBT_KEY = "SparkWitchTrapPlacer";
    private static final TrackedData<Optional<UUID>> OWNER_UUID = DataTracker.registerData(
            HunterTrapEntity.class,
            TrackedDataHandlerRegistry.OPTIONAL_UUID
    );
    private static final TrackedData<Boolean> POISONED = DataTracker.registerData(
            HunterTrapEntity.class,
            TrackedDataHandlerRegistry.BOOLEAN
    );
    private static final double TRIGGER_EXPAND_XZ = 0.35D;
    private static final double TRIGGER_EXPAND_Y = 0.15D;

    private @Nullable UUID ownerUuid;
    private @Nullable UUID poisonerUuid;
    private int armTicks = HunterRules.TRAP_ARM_TICKS;
    private @Nullable BlockPos supportPos;
    private @Nullable BlockPos cachedSupportPos;
    private @Nullable BlockState cachedSupportState;
    private @Nullable VoxelShape cachedSupportShape;

    public HunterTrapEntity(EntityType<? extends HunterTrapEntity> type, World world) {
        super(type, world);
        noClip = true;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(OWNER_UUID, Optional.empty());
        builder.add(POISONED, false);
    }

    public void setOwner(@Nullable PlayerEntity owner) {
        ownerUuid = owner == null ? null : owner.getUuid();
        dataTracker.set(OWNER_UUID, Optional.ofNullable(ownerUuid));
    }

    @Nullable
    public UUID getOwnerUuid() {
        Optional<UUID> tracked = dataTracker.get(OWNER_UUID);
        if (tracked.isPresent()) {
            ownerUuid = tracked.get();
        }
        return ownerUuid;
    }

    public boolean isPoisoned() {
        return dataTracker.get(POISONED);
    }

    public void poison(UUID poisonerUuid) {
        this.poisonerUuid = poisonerUuid;
        dataTracker.set(POISONED, true);
    }

    public void setPoisoned(boolean poisoned) {
        dataTracker.set(POISONED, poisoned);
        if (!poisoned) {
            poisonerUuid = null;
        }
    }

    @Nullable
    public UUID getPoisonerUuid() {
        return poisonerUuid;
    }

    public void setSupportPos(BlockPos supportPos) {
        this.supportPos = supportPos.toImmutable();
    }

    @Nullable
    public BlockPos getSupportPos() {
        return supportPos;
    }

    public double getSupportTopY() {
        if (supportPos == null) {
            return getY();
        }
        VoxelShape collisionShape = getCachedSupportShape();
        return collisionShape.isEmpty()
                ? supportPos.getY()
                : supportPos.getY() + collisionShape.getMax(Direction.Axis.Y);
    }

    @Override
    public void tick() {
        super.tick();
        if (armTicks > 0) {
            armTicks--;
        }
        if (getWorld().isClient) {
            return;
        }

        setVelocity(Vec3d.ZERO);
        velocityModified = true;
        if (supportPos == null) {
            supportPos = getBlockPos().down().toImmutable();
        }

        VoxelShape supportShape = getCachedSupportShape();
        if (supportShape.isEmpty()) {
            discardTrap();
            return;
        }
        setPosition(getX(), supportPos.getY() + supportShape.getMax(Direction.Axis.Y), getZ());

        if (age >= HunterRules.TRAP_LIFESPAN_TICKS) {
            discardTrap();
            return;
        }
        if (armTicks > 0) {
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
        recordTrigger(player);
        HunterPlayerComponent injuryComponent = HunterPlayerComponent.KEY.get(player);
        injuryComponent.applyTrapInjury();

        if (isPoisoned()) {
            UUID placerUuid = getOwnerUuid();
            UUID actualPoisonerUuid = poisonerUuid != null ? poisonerUuid : placerUuid;
            PlayerPoisonComponent poison = PlayerPoisonComponent.KEY.get(player);
            NbtCompound poisonExtra = new NbtCompound();
            if (placerUuid != null) {
                poisonExtra.putUuid(POISON_PLACER_NBT_KEY, placerUuid);
            }
            poison.setPoisonTicks(
                    HunterRules.TRAP_POISON_TICKS,
                    actualPoisonerUuid,
                    POISON_SOURCE,
                    poisonExtra
            );
        }

        getWorld().playSound(
                null,
                getBlockPos(),
                SoundEvents.BLOCK_CHAIN_BREAK,
                SoundCategory.PLAYERS,
                0.8F,
                0.8F
        );
        if (getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                    ParticleTypes.CRIT,
                    getX(),
                    getY() + 0.05D,
                    getZ(),
                    8,
                    0.2D,
                    0.05D,
                    0.2D,
                    0.05D
            );
        }
        discardTrap();
    }

    private VoxelShape getCachedSupportShape() {
        if (supportPos == null) {
            return net.minecraft.util.shape.VoxelShapes.empty();
        }
        BlockState state = getWorld().getBlockState(supportPos);
        if (cachedSupportShape == null
                || !supportPos.equals(cachedSupportPos)
                || state != cachedSupportState) {
            cachedSupportPos = supportPos;
            cachedSupportState = state;
            cachedSupportShape = state.getCollisionShape(getWorld(), supportPos);
        }
        return cachedSupportShape;
    }

    public void unregisterFromOwner() {
        UUID owner = getOwnerUuid();
        if (!getWorld().isClient && owner != null) {
            PlayerEntity player = getWorld().getPlayerByUuid(owner);
            if (player != null) {
                HunterPlayerComponent.KEY.get(player).unregisterTrap(getUuid());
            }
        }
    }

    public void discardTrap() {
        unregisterFromOwner();
        discard();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // Removal is deliberately limited to the owner/dismantler interaction contract.
        // 移除必须走放置者回收或指定身份拆除，普通攻击不能破坏捕兽夹。
        return false;
    }

    private void recordTrigger(PlayerEntity player) {
        if (!(getWorld() instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity target)) {
            return;
        }
        NbtCompound data = new NbtCompound();
        data.putUuid("target", target.getUuid());
        UUID owner = getOwnerUuid();
        PlayerEntity ownerPlayer = owner == null ? null : getWorld().getPlayerByUuid(owner);
        if (poisonerUuid != null) {
            data.putUuid("poisoner", poisonerUuid);
        }
        data.putBoolean("poisoned", isPoisoned());
        GameRecordManager.putPos(data, "pos", getPos());
        GameRecordManager.recordGlobalEvent(
                serverWorld,
                EVENT_TRIGGERED,
                ownerPlayer instanceof ServerPlayerEntity serverOwner ? serverOwner : null,
                data
        );
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        ownerUuid = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        dataTracker.set(OWNER_UUID, Optional.ofNullable(ownerUuid));
        poisonerUuid = nbt.containsUuid("Poisoner") ? nbt.getUuid("Poisoner") : null;
        dataTracker.set(POISONED, nbt.getBoolean("Poisoned"));
        armTicks = nbt.getInt("ArmTicks");
        supportPos = nbt.contains("SupportPos") ? BlockPos.fromLong(nbt.getLong("SupportPos")) : null;
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        UUID owner = getOwnerUuid();
        if (owner != null) {
            nbt.putUuid("Owner", owner);
        }
        if (poisonerUuid != null) {
            nbt.putUuid("Poisoner", poisonerUuid);
        }
        nbt.putBoolean("Poisoned", isPoisoned());
        nbt.putInt("ArmTicks", armTicks);
        if (supportPos != null) {
            nbt.putLong("SupportPos", supportPos.asLong());
        }
    }
}
