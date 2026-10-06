package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
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
        BlockPos effectiveSupportPos = getEffectiveSupportPos();
        if (effectiveSupportPos == null) {
            return getY();
        }
        VoxelShape collisionShape = getCachedSupportShape(effectiveSupportPos);
        return collisionShape.isEmpty()
                ? effectiveSupportPos.getY()
                : effectiveSupportPos.getY() + collisionShape.getMax(Direction.Axis.Y);
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

        BlockPos effectiveSupportPos = getEffectiveSupportPos();
        if (effectiveSupportPos == null) {
            discardTrap();
            return;
        }

        VoxelShape supportShape = getCachedSupportShape(effectiveSupportPos);
        if (supportShape.isEmpty()) {
            discardTrap();
            return;
        }
        setPosition(getX(), effectiveSupportPos.getY() + supportShape.getMax(Direction.Axis.Y), getZ());

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
                this::canCatch
        )) {
            trigger(player);
            break;
        }
    }

    private boolean canCatch(PlayerEntity player) {
        boolean activeWraith = WraithStateService.isActive(player);
        return HunterRules.canTrapCatch(
                GameFunctions.isPlayerAliveAndSurvival(player),
                activeWraith,
                activeWraith && isOwnerBoundKillerOf(player)
        );
    }

    /** Resolves the owner only for Wraith candidates. / 仅在候选者为冤魂时才解析放置者。 */
    private boolean isOwnerBoundKillerOf(PlayerEntity vendetta) {
        UUID owner = getOwnerUuid();
        PlayerEntity ownerPlayer = owner == null ? null : getWorld().getPlayerByUuid(owner);
        return ownerPlayer != null
                && VendettaInteractionService.isBoundKillerTargetingVendetta(ownerPlayer, vendetta);
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

    /**
     * 获取当前真正承托捕兽夹的方块。
     *
     * supportPos 始终保留最初放置时的基础方块，不能直接改成地毯等覆盖物；
     * 否则覆盖物被拆除后，捕兽夹会丢失原始支撑位置并被错误删除。
     *
     * 基础方块上方如果出现地毯、薄雪、压力板、底半砖等高度小于一格的
     * 薄碰撞体，就把它视为新的承托面。这样可以处理“先放夹子、后铺地毯”
     * 的情况，同时不会把上方完整方块误判为新的支撑面。
     */
    @Nullable
    private BlockPos getEffectiveSupportPos() {
        if (supportPos == null || getCachedSupportShape(supportPos).isEmpty()) {
            return null;
        }

        BlockPos effectiveSupportPos = supportPos;
        // 限制扫描层数，避免异常世界数据造成无界循环；正常情况下地毯等覆盖层不会很多。
        for (int layer = 0; layer < 8; layer++) {
            BlockPos abovePos = effectiveSupportPos.up();
            VoxelShape aboveShape = getWorld()
                    .getBlockState(abovePos)
                    .getCollisionShape(getWorld(), abovePos);
            if (!isThinSupportShape(aboveShape)) {
                break;
            }
            effectiveSupportPos = abovePos;
        }
        return effectiveSupportPos;
    }

    /**
     * 判断方块碰撞形状是否属于可以覆盖在捕兽夹上方的薄支撑层。
     *
     * 只判断碰撞形状的 Y 范围，不依赖具体方块类，兼容原版和其他模组的
     * 地毯、薄雪、压力板、按钮底座等薄型方块；完整方块的高度为 1.0，
     * 会被排除，不会让捕兽夹穿过完整方块跑到它的顶部。
     */
    private static boolean isThinSupportShape(VoxelShape shape) {
        return !shape.isEmpty()
                && shape.getMin(Direction.Axis.Y) <= 1.0E-6D
                && shape.getMax(Direction.Axis.Y) < 1.0D - 1.0E-6D;
    }

    private VoxelShape getCachedSupportShape(BlockPos supportPosition) {
        if (supportPosition == null) {
            return net.minecraft.util.shape.VoxelShapes.empty();
        }
        BlockState state = getWorld().getBlockState(supportPosition);
        if (cachedSupportShape == null
                || !supportPosition.equals(cachedSupportPos)
                || state != cachedSupportState) {
            cachedSupportPos = supportPosition.toImmutable();
            cachedSupportState = state;
            cachedSupportShape = state.getCollisionShape(getWorld(), supportPosition);
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
