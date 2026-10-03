package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown Deep Dark Spore Flask (Shock Device template). Server-authoritative: only the server resolves the landing
 * and opens the Deep Dark Zone; the client merely predicts the flight. It breaks on any block or on any living
 * participant player but its thrower (every non-player entity is transparent), never outlives the round and is never
 * saved with the world.
 * 投出的深暗孢瓶（以电击装置为模板）。由服务端权威决定：只有服务端结算落点并展开深暗领域，客户端只预测飞行轨迹。
 * 它碰到任何方块或投掷者以外的存活参与者玩家就会碎裂（所有非玩家实体都对其透明），永不跨出本局，也从不随世界保存。
 */
public final class DeepDarkSporeFlaskEntity extends ThrownItemEntity {
    private static final float TRAIL_CHANCE = 0.3F;
    /** Server only: the zone owner, kept even after the thrower leaves (plan default N19). / 仅服务端：领域主人。 */
    private @Nullable UUID throwerUuid;

    public DeepDarkSporeFlaskEntity(EntityType<? extends DeepDarkSporeFlaskEntity> type, World world) {
        super(type, world);
    }

    public DeepDarkSporeFlaskEntity(World world, LivingEntity owner) {
        super(AbyssListenerEntities.deepDarkSporeFlask(), owner, world);
        this.throwerUuid = owner.getUuid();
    }

    @Override
    public void tick() {
        // A flask still flying when the round stops is dropped at once; the finalize sweep covers unticked ones.
        // 对局停止时仍在飞行的孢瓶立即移除；未被 tick 的孢瓶由收尾清理处理。
        if (!getWorld().isClient() && !GameWorldComponent.KEY.get(getWorld()).isRunning()) {
            discard();
            return;
        }
        super.tick();
        if (getWorld().isClient() && random.nextFloat() < TRAIL_CHANCE) {
            getWorld().addParticle(ParticleTypes.SCULK_CHARGE_POP, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
        }
    }

    /**
     * Its own thrower never stops the flask. On the server only living participant players do: an active Wraith (an
     * ADVENTURE-mode dead player) or a creative player is transparent, so a landing never reveals them, and every
     * non-player entity (e.g. Wathe {@code PlayerBodyEntity} corpses) is transparent, so blocks alone stop it otherwise.
     * The client's flight prediction keeps vanilla hits and the server's removal settles the landing.
     * 投掷者本人永远不会挡下孢瓶。服务端只有存活的参与者玩家会挡下它：激活的冤魂（冒险模式的死亡玩家）或创造模式玩家是透明的，
     * 因此落点永远不会暴露他们；所有非玩家实体（例如 Wathe 的 {@code PlayerBodyEntity} 尸体）同样透明，除此之外只有方块能挡下它。
     * 客户端的飞行预测保留原版命中，落点以服务端移除实体为准。
     */
    @Override
    protected boolean canHit(Entity entity) {
        if (!super.canHit(entity) || isOwner(entity)) {
            return false;
        }
        return getWorld().isClient() || entity instanceof PlayerEntity player && stopsFlask(player);
    }

    private static boolean stopsFlask(PlayerEntity player) {
        return GameFunctions.isPlayerPlayingAndAlive(player)
                && GameFunctions.isPlayerAliveAndSurvival(player)
                && !WraithStateService.isActive(player);
    }

    /**
     * Landing (server): the cell in front of a struck block face, or the cell at an entity hit point, opens the zone.
     * The zone opens even if the thrower died or left mid-flight (plan default N19).
     * 落地（服务端）：被击中方块面前方的格子，或实体命中点所在的格子，展开领域。即使投掷者在飞行途中死亡或离开，
     * 领域也照常展开（N19）。
     */
    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!(getWorld() instanceof ServerWorld world) || isRemoved()) {
            return;
        }
        DeepDarkZoneService.open(world, throwerUuid, DeepDarkZoneShape.landingCell(hitResult));
        discard();
    }

    /**
     * Round-end sweep, called at Wathe's finalize (the game-stop event carries no world).
     * 回合结束清理，在 Wathe 收尾时调用（游戏停止事件不携带世界）。
     */
    public static void discardAll(ServerWorld world) {
        for (DeepDarkSporeFlaskEntity flask
                : world.getEntitiesByType(AbyssListenerEntities.deepDarkSporeFlask(), EntityPredicates.VALID_ENTITY)) {
            flask.discard();
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
