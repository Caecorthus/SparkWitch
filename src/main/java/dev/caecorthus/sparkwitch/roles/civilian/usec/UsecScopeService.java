package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.roles.civilian.usec.net.UsecScopeC2SPacket;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server authority for {@code UsecPlayerComponent.scoped}, the all-player flag behind the scope glint (S1). The
 * {@code sparkwitch:usec_scope} receiver stores {@code scoped = true} only for a qualifying shooter: any holder who may
 * use the rifle (owner, 2026-11-05: the role is never read; {@code OffMatchUse} not REFUSED, so a dead participant of an
 * ACTIVE round never qualifies while an off-match holder does), alive, non-spectating, non-Wraith, holding the rifle in
 * the main hand and using it (the held use that {@code UsecRifleItem.use} starts); anything else stores false. The glint
 * and the scope-in sound follow the flag for every holder; only the instinct cloak, a USEC passive, also checks the
 * target's role (client {@code UsecInstinctCloak}). Every server tick the scoped players are re-checked and cleared
 * once they stop qualifying (released use, swapped item, death, round change), so a lost "false" packet can never
 * leave a stale glint. The payload is harmless presentation and is deliberately on no
 * deny-list. Fabric runs play receivers on the server thread, so no hand-off is needed.
 * {@code UsecPlayerComponent.scoped}（镜头反光 S1 背后的全员同步标记）的服务端权威。{@code sparkwitch:usec_scope} 接收器
 * 只为符合条件的射手写入 {@code scoped = true}：任何可以使用步枪的持有者（所有者 2026-11-05：从不读取职业；
 * {@code OffMatchUse} 不为 REFUSED，因此 ACTIVE 对局中已死亡的参与者永不符合，对局外的持有者则符合）、存活、非旁观、非冤魂、
 * 主手持狙击步枪并正在使用（即 {@code UsecRifleItem.use} 开始的按住使用）；其他情况一律写入 false。镜头反光与开镜声对每名
 * 持有者都跟随该标记；只有作为 USEC 被动的本能隐蔽还会检查目标的职业（客户端 {@code UsecInstinctCloak}）。每个服务端刻都会
 * 复查已开镜的玩家，一旦不再符合条件（松开使用、换物品、死亡、对局变化）即清除，因此丢失的 "false" 数据包永远不会留下过期
 * 反光。该数据包是
 * 无害的表现信息，刻意不进入任何拒绝列表。Fabric 在服务端线程执行游戏数据包接收器，因此无需切换线程。
 * <p>
 * Scope-in sound (owner, 2026-10-07): when the flag turns on, a subtle {@code item.usec_rifle.scope} plays through the
 * public {@code ServerWorld#playSound} at the shooter (fixed {@link UsecRules#SCOPE_SOUND_RANGE}-block broadcast, so the
 * shooter and nearby players hear it and the Blind perceives it as an ordinary sound), at most once per
 * {@link UsecRules#SCOPE_SOUND_MIN_INTERVAL_TICKS} ticks per player.
 * 开镜声（所有者 2026-10-07）：标记由关变开时，经公开的 {@code ServerWorld#playSound} 在射手处播放细微的
 * {@code item.usec_rifle.scope}（固定 {@link UsecRules#SCOPE_SOUND_RANGE} 格广播，射手与附近玩家都能听到，盲人按普通声音感知），
 * 每名玩家最多每 {@link UsecRules#SCOPE_SOUND_MIN_INTERVAL_TICKS} 刻一次。
 */
public final class UsecScopeService {
    private static boolean registered;
    /** Server-thread only: last world tick a scope-in sound played, per player. / 仅服务端线程：每名玩家上次开镜声的世界刻。 */
    private static final Map<UUID, Long> LAST_SCOPE_SOUND = new HashMap<>();

    private UsecScopeService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerPlayNetworking.registerGlobalReceiver(UsecScopeC2SPacket.ID,
                (payload, context) -> receive(context.player(), payload.scoped()));
        ServerTickEvents.END_SERVER_TICK.register(UsecScopeService::sweep);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                LAST_SCOPE_SOUND.remove(handler.getPlayer().getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_SCOPE_SOUND.clear());
    }

    static void receive(ServerPlayerEntity player, boolean scoped) {
        UsecPlayerComponent component = component(player);
        if (component == null) {
            return;
        }
        boolean wasScoped = component.isScoped();
        boolean nowScoped = scoped && qualifies(player);
        component.setScoped(nowScoped);
        long now = player.getWorld().getTime();
        Long last = LAST_SCOPE_SOUND.get(player.getUuid());
        if (shouldPlayScopeSound(wasScoped, nowScoped, last == null ? Long.MIN_VALUE : last, now)) {
            LAST_SCOPE_SOUND.put(player.getUuid(), now);
            player.getWorld().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    SparkWitchSounds.USEC_RIFLE_SCOPE, SoundCategory.PLAYERS, UsecRules.SCOPE_SOUND_VOLUME,
                    0.95F + player.getRandom().nextFloat() * 0.1F);
        }
    }

    /**
     * Pure rule: the scope-in sound plays only on an off-to-on change, and not again within the minimum interval.
     * 纯规则：开镜声只在由关变开时播放，且最小间隔内不重复。
     */
    static boolean shouldPlayScopeSound(boolean wasScoped, boolean nowScoped, long lastTick, long now) {
        if (wasScoped || !nowScoped) {
            return false;
        }
        // A clock that went backwards (another world's time) never suppresses it. / 时钟倒退（换到另一世界）时不抑制。
        return lastTick == Long.MIN_VALUE || now < lastTick || now - lastTick >= UsecRules.SCOPE_SOUND_MIN_INTERVAL_TICKS;
    }

    /** Clears every scoped player who no longer qualifies; unscoped players cost one flag read. / 清除不再符合条件者。 */
    static void sweep(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UsecPlayerComponent component = component(player);
            if (component != null && component.isScoped() && !qualifies(player)) {
                component.setScoped(false);
            }
        }
    }

    /** The live server facts behind {@link #qualifies(OffMatchUse.Mode, boolean, boolean, boolean, boolean, boolean)}. */
    public static boolean qualifies(ServerPlayerEntity player) {
        if (player == null || player.isDisconnected()) {
            return false;
        }
        ItemStack mainHand = player.getMainHandStack();
        boolean rifleInMainHand = mainHand.getItem() instanceof UsecRifleItem;
        boolean usingRifle = rifleInMainHand && player.isUsingItem() && player.getActiveHand() == Hand.MAIN_HAND
                && player.getActiveItem().getItem() instanceof UsecRifleItem;
        return qualifies(
                OffMatchUse.mode(player),
                player.isAlive(),
                player.isSpectator(),
                WraithStateService.isActive(player),
                rifleInMainHand,
                usingRifle);
    }

    /**
     * Pure rule: who may show a scope glint. Any holder; the role is never read (owner, 2026-11-05).
     * 纯规则：谁可以显示镜头反光。任何持有者；从不读取职业（所有者 2026-11-05）。
     *
     * @param mode {@link OffMatchUse#mode(net.minecraft.entity.player.PlayerEntity)}; REFUSED or null never qualifies
     */
    public static boolean qualifies(@Nullable OffMatchUse.Mode mode, boolean alive, boolean spectator,
                                    boolean wraithActive, boolean rifleInMainHand, boolean usingRifleInMainHand) {
        return mode != null && mode != OffMatchUse.Mode.REFUSED && alive && !spectator && !wraithActive
                && rifleInMainHand && usingRifleInMainHand;
    }

    private static UsecPlayerComponent component(ServerPlayerEntity player) {
        return player == null ? null : UsecPlayerComponent.KEY.getNullable(player);
    }
}
