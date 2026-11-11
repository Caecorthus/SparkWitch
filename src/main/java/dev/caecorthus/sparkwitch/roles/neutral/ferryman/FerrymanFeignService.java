package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkStrengthCoronerCompat;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerInventoryRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowForm;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.special.wraith.conversion.WraithBodyRoleAccess;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.CooldownUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The 10 s feign death a marked attacker falls into: the attacker is moved into spectator, a fake corpse of them is
 * laid at their feet with a civilian cover role and a common death reason (so a body reader cannot identify the marked
 * player or tell it is a feign) and both are restored when the timer runs out. The player stays playing and alive in
 * Wathe; while feigning they cannot speak, use the spectator menu, possess another camera or be swapped, and they wake
 * up in place, facing the way they faced. A Vulture that eats the fake corpse kills them for real.
 * 被标记者陷入的 10 秒假死：攻击者被切成旁观、其脚下铺出具带平民掩护身份与常见死因的假尸体（因此读取尸体的人既认不出
 * 被标记者，也看不出这是假死），计时结束后两者一并复原。玩家在 Wathe 中仍在局内存活；假死期间不能说话、不能使用旁观菜单、
 * 不能附身其他视角、也不能被换位，结束后在原地按原朝向起身。秃鹫吃掉假尸体则使其真正死亡。
 *
 * <p>Standalone by owner decision: this duplicates the shape of the Fiend's cycle without touching it.
 * 按所有者决定独立实现：复刻魔人那套流程的形态，但不改动它。</p>
 */
public final class FerrymanFeignService {
    private static final double GROUND_SNAP_DEPTH = 3.0;
    /** Beyond this drift the camera leaves the corpse and watches from the anchored body. / 超过该漂移则改为以自身视角观看。 */
    private static final double CORPSE_DRIFT_SQUARED = 0.75 * 0.75;
    /** A spectator flies through walls, so even a small drift is pulled back. / 旁观者会穿墙，因此轻微偏移也立即拉回。 */
    private static final double PINNED_DRIFT_SQUARED = 0.01 * 0.01;
    private static boolean registered;

    private FerrymanFeignService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(FerrymanFeignService::tick);
        KillPlayer.AFTER.register(FerrymanFeignService::afterKill);
        ResetPlayer.EVENT.register(FerrymanFeignService::onReset);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.getPlayer()));
        // SparkTraits reads terminal reasons at kill time, so a Last Stand never turns the devoured death into a
        // revive; registering at server start is early enough and idempotent (the Fiend's FIEND_DEVOURED pattern).
        // SparkTraits 在击杀时读取终结原因，背水一战因此不会把被吞食的死亡变成复活；服务端启动时注册即足够早且幂等
        // （沿用魔人 FIEND_DEVOURED 的做法）。
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                SparkTraitsKillerBridge.registerTerminalDeathReason(SparkWitchDeathReasons.FERRYMAN_FEIGN));
    }

    public static boolean isFeigning(@Nullable ServerPlayerEntity player) {
        return player != null && FerrymanFeignComponent.KEY.get(player).isFeigning();
    }

    /** Server truth by UUID; offline or unknown players are never feigning. / 按 UUID 判断；离线或未知玩家一律视为未假死。 */
    public static boolean isFeigning(@Nullable MinecraftServer server, @Nullable UUID player) {
        return server != null && player != null && isFeigning(server.getPlayerManager().getPlayer(player));
    }

    /**
     * Whether a camera change must be refused: a feigning player may only watch from their own fake corpse or from
     * themselves, never possess another entity's view (vanilla spectator attack) and drift onto it.
     * 是否必须拒绝视角切换：假死的玩家只能从自己的假尸体或自身观看，绝不能附身其他实体的视角（原版旁观攻击）并被带到其位置。
     */
    public static boolean vetoesCamera(ServerPlayerEntity player, @Nullable Entity camera) {
        FerrymanFeignComponent component = FerrymanFeignComponent.KEY.get(player);
        return component.isFeigning() && camera != null && camera != player
                && !camera.getUuid().equals(component.bodyId());
    }

    /** True while {@code body} is the fake corpse of a player currently feigning. / 该遗体是当前假死玩家的假尸体时为 true。 */
    public static boolean isFakeBody(@Nullable PlayerBodyEntity body) {
        ServerPlayerEntity owner = bodyOwner(body);
        if (owner == null) {
            return false;
        }
        FerrymanFeignComponent component = FerrymanFeignComponent.KEY.get(owner);
        return component.isFeigning() && body.getUuid().equals(component.bodyId());
    }

    /**
     * A Vulture ate the fake corpse: the feigning player dies for real, forced, body-less and SparkTraits-terminal
     * (the Fiend's devoured pattern), credited to the Vulture. The eat itself already ran (cooldown, win credit, body
     * discarded), so this only settles the death, and gets the player up if a guard that ignores {@code force} refused
     * it.
     * 秃鹫吃掉了假尸体：假死玩家真正死亡——强制、不留尸体、SparkTraits 终结（沿用魔人被吞食的做法），记为秃鹫所杀。进食本身
     * 已经执行（冷却、胜利进度、尸体移除），这里只结算死亡；若无视 {@code force} 的守卫拒绝了这次死亡，则按普通假死结束起身。
     */
    public static void onBodyDevoured(@Nullable PlayerBodyEntity body, @Nullable ServerPlayerEntity vulture) {
        // A ferried body eaten by the Vulture pays its Ferryman and marks the eater, whether or not it was also fake.
        // 被吞食的已摆渡遗体同样要给摆渡人分成并标记吞食者，无论它是否同时也是假尸体。
        FerrymanMercyService.onBodyDestroyed(body, vulture);
        if (!isFakeBody(body)) {
            return;
        }
        ServerPlayerEntity owner = bodyOwner(body);
        if (owner == null) {
            return;
        }
        FerrymanFeignComponent component = FerrymanFeignComponent.KEY.get(owner);
        owner.setCameraEntity(owner);
        GameFunctions.killPlayer(owner, false, vulture, SparkWitchDeathReasons.FERRYMAN_FEIGN, true);
        if (component.isFeigning()) {
            revive(owner, component);
        }
    }

    /**
     * Body bag refusal, server side ({@code mixin/ferryman/BodyBagItemFerrymanFeignMixin}). Wathe's
     * {@code BodyBagItem#useOnEntity} also runs on the bagger's client, which already discarded the corpse, used a
     * charge and set the cooldown before the server refused; the server truth is sent back: the corpse is spawned again
     * for that client only, the open screen handler is re-synced (the charge) and a cooldown the server never set is
     * cleared. Nothing else changes, so the client never learns more than the refusal itself.
     * 裹尸袋拒绝的服务端处理（{@code mixin/ferryman/BodyBagItemFerrymanFeignMixin}）。Wathe 的
     * {@code BodyBagItem#useOnEntity} 也会在收尸者客户端运行，服务端拒绝之前客户端已经移除了尸体、用掉一次次数并设置了冷却；
     * 这里把服务端的真实状态发回：仅向该客户端重新生成尸体，重新同步当前界面（次数），并清除服务端从未设置的冷却。其他一律
     * 不变，客户端因此只知道“被拒绝”本身。
     */
    public static void resyncRefusedBodyBag(ServerPlayerEntity bagger, PlayerBodyEntity body, ItemStack stack) {
        if (!body.isRemoved() && body.getWorld() instanceof ServerWorld world) {
            // A throwaway tracker entry only builds the spawn packets; the real tracker keeps updating this client.
            // 临时的追踪条目只用于构建生成数据包；真正的追踪器继续向该客户端推送更新。
            EntityTrackerEntry entry = new EntityTrackerEntry(world, body, body.getType().getTrackTickInterval(),
                    body.getType().alwaysUpdateVelocity(), packet -> { });
            List<Packet<? super ClientPlayPacketListener>> packets = new ArrayList<>();
            entry.sendPackets(bagger, packets::add);
            bagger.networkHandler.sendPacket(new BundleS2CPacket(packets));
        }
        bagger.currentScreenHandler.syncState();
        Item item = stack.getItem();
        if (!bagger.getItemCooldownManager().isCoolingDown(item)) {
            bagger.networkHandler.sendPacket(new CooldownUpdateS2CPacket(item, 0));
        }
    }

    private static @Nullable ServerPlayerEntity bodyOwner(@Nullable PlayerBodyEntity body) {
        if (body == null) {
            return null;
        }
        MinecraftServer server = body.getServer();
        return server == null ? null : server.getPlayerManager().getPlayer(body.getPlayerUuid());
    }

    /**
     * The Fiend's start gate, for an arbitrary attacker. / 魔人的开始条件，用于任意攻击者。
     */
    private static boolean canStartNow(ServerPlayerEntity player) {
        return FerrymanRules.canFeign(
                GameWorldComponent.KEY.get(player.getServerWorld()).getGameStatus()
                        == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player),
                GameFunctions.isPlayerAliveAndSurvival(player),
                FerrymanFeignComponent.KEY.get(player).isFeigning(),
                NoellesTaotieSeekerBridge.isSwallowed(player),
                RiftSessionService.isInside(player) || KidnapperControlComponent.KEY.get(player).isControlled()
                        || BlackRavenCrowForm.isInCrowForm(player) || SeekerInventoryRules.isInSession(player)
        );
    }

    /**
     * Server: lays the fake corpse and pins the attacker to it for 10 s. Returns false, changing nothing, when the
     * attacker cannot feign now ({@link FerrymanRules#canFeign}); the caller then falls back to the plain pardon.
     * 服务端：铺出假尸体并把人钉在其上 10 秒。攻击者此刻无法假死（{@link FerrymanRules#canFeign}）时不做任何改动并返回
     * false，调用方随即回到普通宽恕。
     */
    static boolean start(ServerPlayerEntity player) {
        if (!canStartNow(player)) {
            return false;
        }
        FerrymanFeignComponent component = FerrymanFeignComponent.KEY.get(player);
        ServerWorld world = player.getServerWorld();
        player.stopRiding();
        if (player.isSleeping()) {
            player.wakeUp();
        }
        player.clearActiveItem();
        player.closeHandledScreen();
        Vec3d anchor = groundAnchor(world, player);
        PlayerBodyEntity body = spawnCorpse(world, player, anchor);
        component.start(
                world.getTime() + FerrymanRules.FEIGN_TICKS,
                body == null ? null : body.getUuid(),
                anchor.x, anchor.y, anchor.z,
                player.getYaw(), player.getPitch(),
                player.interactionManager.getGameMode(),
                player.isInvulnerable()
        );
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
        player.fallDistance = 0.0F;
        hold(player);
        return true;
    }

    private static void hold(ServerPlayerEntity player) {
        FerrymanFeignComponent component = FerrymanFeignComponent.KEY.get(player);
        if (player.interactionManager.getGameMode() != GameMode.SPECTATOR) {
            player.changeGameMode(GameMode.SPECTATOR);
        }
        Entity body = findBody(player.getServer(), component.bodyId());
        Vec3d anchor = new Vec3d(component.anchorX(), component.anchorY(), component.anchorZ());
        Entity camera = body != null && body.isAlive() && body.squaredDistanceTo(anchor) <= CORPSE_DRIFT_SQUARED
                ? body
                : player;
        if (player.getCameraEntity() != camera) {
            player.setCameraEntity(camera);
        }
        player.setVelocity(Vec3d.ZERO);
        if (camera == player && player.squaredDistanceTo(anchor) > PINNED_DRIFT_SQUARED) {
            teleportToAnchor(player, component);
        }
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            FerrymanInstinctVeilComponent.KEY.get(player).tickServer();
            FerrymanFeignComponent component = FerrymanFeignComponent.KEY.get(player);
            if (!component.isFeigning()) {
                continue;
            }
            if (player.getServerWorld().getTime() >= component.untilTick() || !player.isAlive()) {
                revive(player, component);
                continue;
            }
            hold(player);
        }
    }

    private static void revive(ServerPlayerEntity player, FerrymanFeignComponent component) {
        player.setCameraEntity(player);
        player.changeGameMode(component.previousMode());
        player.setInvulnerable(component.wasInvulnerable());
        teleportToAnchor(player, component);
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
        player.fallDistance = 0.0F;
        discardBody(player.getServer(), component.bodyId());
        component.clear();
        // 5 s where instinct cannot outline the player who just got up (owner spec).
        // 起身后 5 秒内本能描不出他（所有者设定）。
        FerrymanInstinctVeilComponent.KEY.get(player).veil(FerrymanRules.FEIGN_INSTINCT_BLOCK_TICKS);
    }

    /** Back at the anchor, facing the way the player faced when the feign began. / 回到锚点，并恢复假死开始时的朝向。 */
    private static void teleportToAnchor(ServerPlayerEntity player, FerrymanFeignComponent component) {
        player.teleport(player.getServerWorld(), component.anchorX(), component.anchorY(), component.anchorZ(),
                Set.of(), component.anchorYaw(), component.anchorPitch());
    }

    /**
     * A feigning player that really died (Vulture, disconnect, {@code /kill}): drop the corpse and the cycle.
     * 正在假死的玩家真正死亡（秃鹫、断线、{@code /kill}）：移除尸体并结束周期。
     */
    private static void afterKill(ServerPlayerEntity victim, ServerPlayerEntity killer, Identifier deathReason) {
        if (victim == null) {
            return;
        }
        FerrymanFeignComponent component = FerrymanFeignComponent.KEY.get(victim);
        if (!component.isFeigning()) {
            return;
        }
        victim.setCameraEntity(victim);
        victim.setInvulnerable(component.wasInvulnerable());
        discardBody(victim.getServer(), component.bodyId());
        component.clear();
    }

    /**
     * Round boundary: Wathe already reset the game mode and position. The instinct veil never leaks into the next
     * round either.
     * 回合边界：Wathe 已重置游戏模式与位置。本能帷幕同样不会带进下一局。
     */
    private static void onReset(ServerPlayerEntity player) {
        FerrymanInstinctVeilComponent.KEY.get(player).clear();
        FerrymanFeignComponent component = FerrymanFeignComponent.KEY.get(player);
        if (!component.isFeigning()) {
            return;
        }
        player.setCameraEntity(player);
        player.setInvulnerable(component.wasInvulnerable());
        discardBody(player.getServer(), component.bodyId());
        component.clear();
    }

    /**
     * Leaving mid-feign: get up first (the Fiend's rule), so the saved player data never keeps a living player in
     * spectator mode; Wathe's own deferred disconnect handling then decides the death.
     * 假死中途离线：先起身（沿用魔人的规则），确保保存的玩家数据不会让活着的玩家停留在旁观模式；之后由 Wathe 自身延后执行的
     * 断线处理决定死亡。
     */
    private static void onDisconnect(@Nullable ServerPlayerEntity player) {
        if (player == null || !isFeigning(player)) {
            return;
        }
        revive(player, FerrymanFeignComponent.KEY.get(player));
    }

    // ---- Corpse / 尸体 ----

    private static Vec3d groundAnchor(ServerWorld world, ServerPlayerEntity player) {
        Vec3d feet = player.getPos();
        if (player.isOnGround()) {
            return feet;
        }
        BlockHitResult hit = world.raycast(new RaycastContext(feet.add(0.0, 0.1, 0.0),
                feet.subtract(0.0, GROUND_SNAP_DEPTH, 0.0), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? new Vec3d(feet.x, hit.getPos().y, feet.z) : feet;
    }

    private static @Nullable PlayerBodyEntity spawnCorpse(ServerWorld world, ServerPlayerEntity player, Vec3d anchor) {
        PlayerBodyEntity body = WatheEntities.PLAYER_BODY.create(world);
        if (body == null) {
            return null;
        }
        body.setPlayerUuid(player.getUuid());
        // Civilian cover: a body that reads the live role would tell a role reader who the marked player is.
        // 平民掩护：若尸体读出实时职业，读取死者身份的人就能认出被标记者。
        ((WraithBodyRoleAccess) body).sparkwitch$setDeathRole(WatheRoles.CIVILIAN.identifier());
        // A common murder reason: a Charon-specific one would tell every body reader the corpse is a feign.
        // 常见谋杀死因：摆渡人专属的死因会让所有能读取尸体的人看出这是假死。
        body.setDeathReason(FerrymanRules.feignCorpseReason(ThreadLocalRandom.current()));
        body.setDeathGameTime(world.getTime());
        float yaw = player.getHeadYaw();
        body.refreshPositionAndAngles(anchor.x, anchor.y, anchor.z, yaw, 0.0F);
        body.setYaw(yaw);
        body.setHeadYaw(yaw);
        body.bodyYaw = player.bodyYaw;
        body.prevYaw = yaw;
        body.prevHeadYaw = yaw;
        body.prevBodyYaw = player.bodyYaw;
        if (!world.spawnEntity(body)) {
            return null;
        }
        SparkStrengthCoronerCompat.recordBodyRole(body, WatheRoles.CIVILIAN.identifier());
        return body;
    }

    private static @Nullable Entity findBody(@Nullable MinecraftServer server, @Nullable UUID bodyId) {
        if (server == null || bodyId == null) {
            return null;
        }
        for (ServerWorld world : server.getWorlds()) {
            Entity entity = world.getEntity(bodyId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static void discardBody(@Nullable MinecraftServer server, @Nullable UUID bodyId) {
        Entity body = findBody(server, bodyId);
        if (body != null) {
            body.discard();
        }
    }
}
