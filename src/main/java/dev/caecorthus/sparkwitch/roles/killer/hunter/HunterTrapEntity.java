package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.SparkStrengthPartygoerCompat;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import dev.doctor4t.wathe.game.GameConstants;
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
    /**
     * Achievement record contract W16 (2026-10-14), read by SparkAssist: on {@link #EVENT_TRIGGERED}, true when an
     * active Bone Setting absorbed this trap's fracture. Every earlier field keeps its name and meaning.
     * 成就记录契约 W16（2026-10-14），由 SparkAssist 读取：写在 {@link #EVENT_TRIGGERED} 上，生效中的正骨吸收了这次捕兽夹
     * 骨折时为 true。原有字段的名称与含义均不变。
     */
    static final String BONE_SETTING_ABSORBED_KEY = "bone_setting_absorbed";
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

    /**
     * Server-side poisoner credit, which the entity never syncs; null on a poisoned trap credits the placer.
     * 服务端的下毒者归属，实体从不同步它；带毒捕兽夹上为 null 时记在放置者名下。
     */
    @Nullable
    public UUID getPoisonerUuid() {
        return poisonerUuid;
    }

    public void setPoisoned(boolean poisoned) {
        dataTracker.set(POISONED, poisoned);
        if (!poisoned) {
            poisonerUuid = null;
        }
    }

    public void setSupportPos(BlockPos supportPos) {
        this.supportPos = supportPos.toImmutable();
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

        // The trap never moves itself (no move() call), so its velocity stays zero unless something pushed it (fluid
        // flow, explosion); only then is it zeroed and the zero sent, instead of a velocity packet every tick.
        // 捕兽夹从不自行移动（不调用 move()），速度保持为零，除非被推动（流体、爆炸）；只在那时清零并同步零速度，
        // 而不是每刻都发送速度包。
        if (!getVelocity().equals(Vec3d.ZERO)) {
            setVelocity(Vec3d.ZERO);
            velocityModified = true;
        }
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
        if (!isRemoved() && isPoisoned()) {
            for (Entity partygoer : getWorld().getOtherEntities(this, triggerBox,
                    SparkStrengthPartygoerCompat::isKillable)) {
                triggerOnPartygoer(partygoer);
                break;
            }
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
        HunterPlayerComponent injuryComponent = HunterPlayerComponent.KEY.get(player);
        HunterInjuryState.FractureApplication fracture = injuryComponent.applyTrapInjury();
        // Recorded after the injury so the record can tell whether Bone Setting absorbed it (W16); the injury writes no
        // record, and the trigger record still precedes the trap poison's, as before.
        // 在伤害之后记录，以便写明正骨是否吸收了骨折（W16）；伤害本身不写记录，触发记录仍像以前一样早于捕兽夹中毒记录。
        recordTrigger(player, fracture);

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

        playSnap();
        discardTrap();
    }

    /**
     * SparkStrength Partygoer seam (owner rule 2026-10-21: whatever can kill a player kills a Partygoer in one hit). Only
     * the trap's poison can kill a player, so only a poisoned trap catches a Partygoer, after the players in its box had
     * their chance this tick. It dies at once ({@code wathe:poison}, the reason the trap poison kills a player with),
     * credited to whom that poison would credit (the poisoner, else the placer) while online; the trap then snaps and is
     * spent as on a player. No trigger record: SparkStrength records the kill.
     * SparkStrength 派对客接缝（所有者规则 2026-10-21：能杀死玩家的东西一击打死派对客）。捕兽夹只有其毒能杀死玩家，因此只有带毒的
     * 捕兽夹会夹住派对客，且排在本 tick 夹框内的玩家之后。它立即死亡（{@code wathe:poison}，即捕兽夹毒杀玩家所用的死因），在线时
     * 记在该毒所记之人头上（下毒者，否则放置者）；随后捕兽夹与夹住玩家时一样合拢并用掉。不写触发记录：由 SparkStrength 记录击杀。
     */
    private void triggerOnPartygoer(Entity partygoer) {
        UUID placerUuid = getOwnerUuid();
        UUID actualPoisonerUuid = poisonerUuid != null ? poisonerUuid : placerUuid;
        PlayerEntity poisoner = actualPoisonerUuid == null ? null : getWorld().getPlayerByUuid(actualPoisonerUuid);
        if (!SparkStrengthPartygoerCompat.killByWeapon(
                poisoner instanceof ServerPlayerEntity serverPoisoner ? serverPoisoner : null,
                partygoer,
                GameConstants.DeathReasons.POISON
        )) {
            return;
        }
        playSnap();
        discardTrap();
    }

    private void playSnap() {
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

    private void recordTrigger(PlayerEntity player, HunterInjuryState.FractureApplication fracture) {
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
        data.putBoolean(BONE_SETTING_ABSORBED_KEY, fracture.absorbedByBoneSetting());
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
