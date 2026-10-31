package dev.caecorthus.sparkwitch.roles.civilian.knight;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.net.CrossSwordStabC2SPayload;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import dev.caecorthus.sparkwitch.util.RoundParticipants;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server authority for the cross sword stab ({@code sparkwitch:cross_sword_stab}). The client only names the entity it
 * aimed at; this class requires a fresh server-side release of a real raise, then re-checks the round, the Knight, the
 * held sword, the duel target and Wathe's knife distance tolerance before the kill. Payload handlers and the release
 * record run on the server thread only.
 * 十字剑刺击（{@code sparkwitch:cross_sword_stab}）的服务端权威。客户端只报告瞄准的实体；本类要求一次新鲜的、服务端
 * 亲眼见到的举剑松手，再重新校验对局、骑士、手持十字剑、决斗目标与 Wathe 小刀的距离容差，然后才击杀。数据包处理与松手
 * 记录只在服务端线程运行。
 */
public final class CrossSwordStabService {
    /**
     * Same 3-block reach as the client pick ({@link CrossSwordItem#STAB_RANGE}) and Wathe's knife receiver; the server
     * position may lag the client's ray.
     * 与客户端选靶（{@link CrossSwordItem#STAB_RANGE}）及 Wathe 小刀接收端相同的 3 格距离；服务端位置可能落后于客户端射线。
     */
    public static final double STAB_TOLERANCE = CrossSwordItem.STAB_RANGE;
    /**
     * The client holds {@link CrossSwordItem#MIN_HOLD_TICKS}; the server's use and release packets may straddle two
     * fewer tick boundaries (same slack as the Swordfish).
     * 客户端需举满 {@link CrossSwordItem#MIN_HOLD_TICKS}；服务端收到的使用与松手包可能少跨过两次 tick 边界（与剑鱼相同的余量）。
     */
    public static final int SERVER_MIN_HOLD_TICKS = CrossSwordItem.MIN_HOLD_TICKS - 2;
    /** The stab payload must follow the release within this many ticks. / 刺击包须在松手后这么多 tick 内到达。 */
    public static final long RELEASE_VALID_TICKS = 5L;

    private static final Map<UUID, Release> RELEASES = new HashMap<>();
    private static boolean registered;

    private CrossSwordStabService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(CrossSwordStabC2SPayload.ID, CrossSwordStabC2SPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CrossSwordStabC2SPayload.ID,
                (payload, context) -> stab(context.player(), payload.targetEntityId()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RELEASES.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RELEASES.clear());
    }

    /**
     * Called from the server-side {@link CrossSwordItem#onStoppedUsing}; vanilla processes the release action before
     * the client's stab payload on the same connection. Only a main-hand raise that the server itself saw counts.
     * 由服务端的 {@link CrossSwordItem#onStoppedUsing} 调用；同一连接上原版先处理松手动作，再处理客户端的刺击包。
     * 只有服务端亲眼见到的主手举剑才算数。
     */
    static void recordRelease(ServerPlayerEntity player, ItemStack stack, int heldTicks) {
        RELEASES.remove(player.getUuid());
        if (player.getActiveHand() != Hand.MAIN_HAND || player.getActiveItem() != stack
                || player.getMainHandStack() != stack || heldTicks < SERVER_MIN_HOLD_TICKS
                || heldTicks > CrossSwordItem.MAX_USE_TICKS) {
            return;
        }
        RELEASES.put(player.getUuid(), new Release(player.getServerWorld(), player.getServerWorld().getTime(), stack));
    }

    /** Handles {@link CrossSwordStabC2SPayload}; the payload is untrusted. / 处理刺击包；包内容不可信。 */
    static void stab(ServerPlayerEntity player, int targetEntityId) {
        if (player == null) {
            return;
        }
        // Consumed before any check, so a rejected or replayed payload cannot reuse this release.
        // 在任何校验之前消耗，被拒绝或重放的数据包都无法复用这次松手。
        Release release = RELEASES.remove(player.getUuid());
        ServerWorld world = player.getServerWorld();
        ItemStack stack = player.getMainHandStack();
        if (release == null || release.world() != world || release.stack() != stack
                || !stack.isOf(SparkWitchItems.crossSword())) {
            return;
        }
        long age = world.getTime() - release.releasedAt();
        if (age < 0 || age > RELEASE_VALID_TICKS) {
            return;
        }
        // Running round, alive, connected, not spectator or creative (Wathe playing-and-alive first). A stunned sender
        // is refused here, like the Swordfish stab, because the payload is not on the stun deny-list.
        // 对局进行中、存活、在线、非旁观且非创造（先判 Wathe 在局存活）。与剑鱼刺击相同，被眩晕的发送者在此拒绝，
        // 因为该数据包不在眩晕拦截名单中。
        if (!RoundParticipants.LIVING_SURVIVOR.test(player)
                || player.getItemCooldownManager().isCoolingDown(stack.getItem())
                || ControlExpertStun.isStunned(player)) {
            return;
        }
        KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(world);
        // The cross sword only works for the Knight of the active duel. / 十字剑只在当前决斗的骑士手里有效。
        if (!duel.isKnight(player.getUuid())) {
            return;
        }
        if (!(world.getEntityById(targetEntityId) instanceof ServerPlayerEntity victim) || victim == player
                // Only the duelled player can be stabbed. / 只能刺中被决斗者。
                || !duel.isTarget(victim.getUuid())
                || !RoundParticipants.LIVING_SURVIVOR.test(victim)
                || victim.distanceTo(player) > STAB_TOLERANCE) {
            return;
        }
        // Same veto as the Swordfish stab, asked with the death reason it kills with. Rift occupants are alive
        // spectators, so LIVING_SURVIVOR above refuses them as sender (the payload, like the Swordfish's, is not on the
        // Rift deny-list) and the SFA Rift policy refuses them as target.
        // 与剑鱼刺击相同的否决，以实际击杀死因询问。裂隙门内玩家是活着的旁观者，上方 LIVING_SURVIVOR 已拒绝其作为发送者
        // （该数据包与剑鱼的一样不在裂隙拦截名单中），SFA 的裂隙策略拒绝其作为目标。
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (!SparkFactionApi.canAffectPlayer(player, victim, GameConstants.DeathReasons.KNIFE, game)
                || SparkTraitsKillerBridge.isLastEscapeActive(victim)) {
            return;
        }
        world.playSound(null, victim.getX(), victim.getY(), victim.getZ(), WatheSounds.ITEM_KNIFE_STAB,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
        GameFunctions.killPlayer(victim, true, player, GameConstants.DeathReasons.KNIFE);
    }

    private record Release(ServerWorld world, long releasedAt, ItemStack stack) {
    }
}
