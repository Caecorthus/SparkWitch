package dev.caecorthus.sparkwitch.entity;

import dev.caecorthus.sparkwitch.SparkWitchEntities;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun.SphinxHaoFistMotion;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Visual-only spring fist of the Sphinx Hao "手"枪 ({@code sparkwitch:sphinx_hao_fist}). The shot itself is a
 * server hitscan resolved before this entity exists; the entity only carries the presentation: it is spawned at the
 * shooter's eyes with the shot's end point (the hit player, the first block, or the ray's full range) as tracked data
 * and the shooter's id in the spawn packet, lives {@link SphinxHaoFistMotion#END_AGE} ticks (out, hold, back) and is
 * discarded. A plain {@link Entity}, never a {@code ProjectileEntity}: projectile hooks (Rift Gate deflection, Magician
 * hits) never see it, it cannot be hit, pushed, saved or summoned, and it never moves.
 * 狮身豪“手”枪的纯表现弹簧拳头（{@code sparkwitch:sphinx_hao_fist}）。射击本身是在本实体生成之前就已结算的服务端即时射线；
 * 本实体只负责表现：生成于射手眼睛处，以追踪数据携带射击终点（命中的玩家、第一个方块或射线满射程处），并在生成包中携带射手 id，
 * 存活 {@link SphinxHaoFistMotion#END_AGE} 刻（伸出、停留、收回）后移除。普通 {@link Entity}，而非 {@code ProjectileEntity}：
 * 投射物钩子（裂隙门偏转、魔术师命中）不会处理它；它不能被命中、推动、保存或召唤，也从不移动。
 */
public final class SphinxHaoFistEntity extends Entity {
    /**
     * Shot end point as an offset from the spawn position (floats stay exact near the entity on far-out maps).
     * 射击终点，以相对生成位置的偏移存储（即使地图坐标很远，浮点数在实体附近也保持精确）。
     */
    private static final TrackedData<Vector3f> TARGET =
            DataTracker.registerData(SphinxHaoFistEntity.class, TrackedDataHandlerRegistry.VECTOR3F);
    /** Drawn up to 64 blocks away, like the grappling hook. / 与钩爪一样在 64 格内绘制。 */
    private static final double RENDER_DISTANCE_SQUARED = 64.0 * 64.0;
    private static final float HIT_VOLUME = 1.0F;
    private static final float RETRACT_VOLUME = 0.6F;
    private static final float RETRACT_PITCH = 1.6F;

    private @Nullable UUID ownerUuid;
    private int ownerEntityId;
    /** Server only: the fist met a player, so its arrival thumps. / 仅服务端：拳头打中了玩家，到达时发出击打声。 */
    private boolean hitPlayer;

    public SphinxHaoFistEntity(EntityType<? extends SphinxHaoFistEntity> type, World world) {
        super(type, world);
        noClip = true;
    }

    /**
     * Server: a fist from {@code owner}'s eyes to {@code end}. {@code hitPlayer} only chooses the arrival sound.
     * 服务端：从 {@code owner} 眼睛到 {@code end} 的拳头。{@code hitPlayer} 只决定到达时的音效。
     */
    public SphinxHaoFistEntity(World world, PlayerEntity owner, Vec3d end, boolean hitPlayer) {
        this(SparkWitchEntities.sphinxHaoFist(), world);
        ownerUuid = owner.getUuid();
        ownerEntityId = owner.getId();
        this.hitPlayer = hitPlayer;
        Vec3d eye = owner.getEyePos();
        refreshPositionAndAngles(eye.x, eye.y, eye.z, owner.getYaw(), owner.getPitch());
        dataTracker.set(TARGET, end.subtract(eye).toVector3f());
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(TARGET, new Vector3f());
    }

    /** The shot's end point in world space. / 射击终点（世界坐标）。 */
    public Vec3d getTarget() {
        return getPos().add(new Vec3d(dataTracker.get(TARGET)));
    }

    public @Nullable UUID getOwnerUuid() {
        return ownerUuid;
    }

    /**
     * The shooter on both sides: the server resolves the UUID, a client the entity id from the spawn packet.
     * 双端都可取得射手：服务端按 UUID 解析，客户端按生成包中的实体 id 解析。
     */
    public @Nullable PlayerEntity getOwnerPlayer() {
        if (!getWorld().isClient()) {
            return ownerUuid == null ? null : getWorld().getPlayerByUuid(ownerUuid);
        }
        return getWorld().getEntityById(ownerEntityId) instanceof PlayerEntity player ? player : null;
    }

    /** Extension in [0, 1] at this frame. / 本帧的伸展量 [0, 1]。 */
    public float extension(float tickDelta) {
        return SphinxHaoFistMotion.extension(age + tickDelta);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient() || isRemoved()) {
            return;
        }
        if (SphinxHaoFistMotion.expired(age) || !(getOwnerPlayer() instanceof ServerPlayerEntity owner)
                || owner.getWorld() != getWorld()) {
            discard();
            return;
        }
        if (age == SphinxHaoFistMotion.ARRIVE_AGE && hitPlayer) {
            Vec3d target = getTarget();
            getWorld().playSound(null, target.x, target.y, target.z, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK,
                    SoundCategory.PLAYERS, HIT_VOLUME, 1.0F);
            getWorld().playSound(null, target.x, target.y, target.z, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
                    SoundCategory.PLAYERS, HIT_VOLUME, 1.0F);
        } else if (age == SphinxHaoFistMotion.RETRACT_AGE) {
            getWorld().playSound(null, owner.getX(), owner.getEyeY(), owner.getZ(), SoundEvents.BLOCK_PISTON_CONTRACT,
                    SoundCategory.PLAYERS, RETRACT_VOLUME, RETRACT_PITCH);
        }
    }

    /** Owner id in the spawn packet, as vanilla projectiles do. / 与原版投射物一样在生成包中携带射手 id。 */
    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entry) {
        PlayerEntity owner = getOwnerPlayer();
        return new EntitySpawnS2CPacket(this, entry, owner == null ? 0 : owner.getId());
    }

    @Override
    public void onSpawnPacket(EntitySpawnS2CPacket packet) {
        super.onSpawnPacket(packet);
        ownerEntityId = packet.getEntityData();
    }

    /** Never a crosshair, attack or projectile target. / 永远不会成为准星、攻击或投射物的目标。 */
    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    @Override
    public boolean canUsePortals(boolean allowVehicles) {
        return false;
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < RENDER_DISTANCE_SQUARED;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }
}
