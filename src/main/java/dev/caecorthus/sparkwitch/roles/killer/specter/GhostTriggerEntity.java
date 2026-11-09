package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
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
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Invisible placed ghost trigger. It cannot be damaged, pushed or reclaimed, and is spent as soon as an eligible player
 * ({@link #canTrigger}) walks into its box; {@link SpecterTriggerService} applies the effect.
 * <ul>
 *   <li>Round binding (server only, never synced): the trigger stores the Wathe replay match id it was placed in.
 *       Each server tick discards it unless the round is exactly ACTIVE (STOPPING ends it too) and that id is the
 *       current match, so a trigger saved in an unloaded chunk, including one saved without an id by an older build,
 *       never fires in a later round with a stale owner. {@link #discardAll} is the round-edge sweep for loaded
 *       triggers.</li>
 *   <li>Victims mirror the Hunter trap: whoever is alive in survival/adventure with a role, the owner and killer
 *       teammates included; active Wraiths only when the owner is their bound killer (Vendetta isolation).</li>
 * </ul>
 * 放置后隐形的鬼魂触发器。不可破坏、不可推动或回收，符合条件的玩家（{@link #canTrigger}）走进判定框即消耗，效果由
 * {@link SpecterTriggerService} 施加。对局绑定（仅服务端，从不同步）：触发器保存放置时的 Wathe 回放对局 id；每个服务端 tick
 * 除非对局恰为 ACTIVE（STOPPING 也算结束）且该 id 为当前对局，否则移除自身，因此存在未加载区块中的触发器（包括旧版本保存的
 * 无 id 触发器）绝不会在之后的回合带着过期的放置者触发。{@link #discardAll} 是已加载触发器的回合节点清扫。
 * 受害者与猎人捕兽夹一致：任何拥有职业且处于生存/冒险模式的存活者，包括放置者本人与杀手队友；激活冤魂只有在放置者是其绑定凶手时
 * 才会触发（仇杀客隔离）。
 */
public final class GhostTriggerEntity extends Entity {
    public static final Identifier EVENT_TRIGGERED = SparkWitch.id("ghost_trigger_triggered");
    /** Same arm delay as the Hunter trap: 0.5 s. / 与猎人夹子相同的 0.5 秒布防延迟。 */
    private static final int ARM_TICKS = 10;
    private static final double TRIGGER_EXPAND_XZ = 0.35D;
    private static final double TRIGGER_EXPAND_Y = 0.15D;

    private static final String MATCH_ID = "MatchId";

    private int armTicks = ARM_TICKS;
    private @Nullable UUID ownerUuid;
    private @Nullable UUID matchId;

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

    /** Server only: binds the trigger to the current match; call once before spawning. / 仅服务端：绑定当前对局，生成前调用一次。 */
    public void bindToCurrentMatch() {
        matchId = currentMatchId(getWorld());
    }

    /**
     * Wathe's replay match id while a match is recorded (ON_FINISH_INITIALIZE to finalize), else null; server only.
     * 对局记录期间（ON_FINISH_INITIALIZE 至收尾）的 Wathe 回放对局 id，否则为 null；仅服务端。
     */
    static @Nullable UUID currentMatchId(World world) {
        if (world == null || world.isClient() || !GameRecordManager.hasActiveMatch()) {
            return null;
        }
        GameRecordManager.MatchRecord match = GameRecordManager.getCurrentMatch();
        return match == null ? null : match.getMatchId();
    }

    /** Round-edge sweep of every loaded ghost trigger in the world. / 回合节点清扫该世界所有已加载的鬼魂触发器。 */
    public static void discardAll(ServerWorld world) {
        for (GhostTriggerEntity trigger : world.getEntitiesByType(
                TypeFilter.equals(GhostTriggerEntity.class),
                trigger -> true
        )) {
            trigger.discard();
        }
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
        if (!belongsToCurrentRound()) {
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
                this::canTrigger
        )) {
            trigger(player);
            break;
        }
    }

    /** Exactly ACTIVE and placed in the current match (a missing id never matches). / 恰为 ACTIVE 且放置于当前对局（缺失 id 永不匹配）。 */
    private boolean belongsToCurrentRound() {
        return GameWorldComponent.KEY.get(getWorld()).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && matchId != null
                && Objects.equals(matchId, currentMatchId(getWorld()));
    }

    /**
     * Hunter trap rule ({@code HunterTrapEntity.canCatch}) plus a role: alive in survival/adventure with a role; an
     * active Wraith only when the owner is its bound killer, any other player only while alive in Wathe. The owner and
     * killer teammates are caught like anyone else, as by the Hunter's trap.
     * 猎人捕兽夹规则（{@code HunterTrapEntity.canCatch}）加上职业要求：处于生存/冒险模式、存活且拥有职业；激活冤魂只有在放置者是其
     * 绑定凶手时才会触发，其他玩家须在 Wathe 中存活。放置者本人与杀手队友同样会触发，与猎人捕兽夹一致。
     */
    private boolean canTrigger(PlayerEntity player) {
        if (!GameFunctions.isPlayerAliveAndSurvival(player)
                || GameWorldComponent.KEY.get(getWorld()).getRole(player) == null) {
            return false;
        }
        if (WraithStateService.isActive(player)) {
            return isOwnerBoundKillerOf(player);
        }
        return GameFunctions.isPlayerPlayingAndAlive(player);
    }

    /** Resolves the owner only for Wraith candidates. / 仅在候选者为冤魂时才解析放置者。 */
    private boolean isOwnerBoundKillerOf(PlayerEntity vendetta) {
        PlayerEntity owner = ownerUuid == null ? null : getWorld().getPlayerByUuid(ownerUuid);
        return owner != null && VendettaInteractionService.isBoundKillerTargetingVendetta(owner, vendetta);
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

    /** A missing {@code MatchId} (older save) loads as null and is discarded on the first tick. / 缺失 MatchId（旧存档）读为 null，首个 tick 即被移除。 */
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        ownerUuid = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        matchId = nbt.containsUuid(MATCH_ID) ? nbt.getUuid(MATCH_ID) : null;
        armTicks = nbt.getInt("ArmTicks");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerUuid != null) {
            nbt.putUuid("Owner", ownerUuid);
        }
        if (matchId != null) {
            nbt.putUuid(MATCH_ID, matchId);
        }
        nbt.putInt("ArmTicks", armTicks);
    }
}
