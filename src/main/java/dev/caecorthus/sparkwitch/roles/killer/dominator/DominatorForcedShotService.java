package dev.caecorthus.sparkwitch.roles.killer.dominator;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownEntryAccessor;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.GunShootPayload;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.network.packet.s2c.play.CooldownUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Server orchestration of the forced shot: begin the swap sequence, fire once through Wathe's own gun receiver inside
 * {@link DominatorForcedShotScope}, and pay the Dominator when the shot's victim died for good.
 * <p>Cooldown contract: the forced shot neither depends on nor changes the shooter's own revolver cooldown. The
 * receiver's cooldown gate and final cooldown write are bypassed inside the scope ({@code DominatorForcedShotGunMixin});
 * any other write to that entry during the shot (NoellesRoles' Corrupt Cop RETURN rewrite, gun-cycle listeners such as
 * Emma's, a Depression fake-kill discount) is reverted to the exact pre-shot entry, without passing through
 * {@code set}/{@code remove} again, so SparkFactionAPI forced locks and SparkWitch timer releases are untouched.</p>
 * 强制开枪的服务端编排：开始换枪序列，在 {@link DominatorForcedShotScope} 内通过 Wathe 自身的枪械接收器开一枪，并在这一枪的
 * 受害者确定死亡时向支配者发放奖金。冷却契约：强制开枪既不依赖也不改变射手自己的左轮冷却。作用域内绕过接收器的冷却检查与
 * 最终冷却写入；开枪期间对该条目的其他写入（NoellesRoles 黑警的 RETURN 重写、艾玛等枪械循环监听者、抑郁假死折扣）都被精确
 * 还原为开枪前的条目，且不再经过 {@code set}/{@code remove}，因此 SparkFactionAPI 强制锁与 SparkWitch 计时释放均不受影响。
 */
public final class DominatorForcedShotService {
    private DominatorForcedShotService() {
    }

    public static boolean begin(ServerPlayerEntity marked) {
        return DominatorForcedShotComponent.KEY.get(marked).begin();
    }

    public static void fireNow(ServerPlayerEntity shooter, ServerPlayerEntity dominator) {
        DominatorForcedShotAim.Shot shot = DominatorForcedShotAim.resolve(shooter);
        ServerPlayerEntity victim = shot.victim();
        boolean victimWasAlive = victim != null && GameFunctions.isPlayerPlayingAndAlive(victim);
        if (refusedByFaction(shooter, shot.targetId())) {
            // SparkFactionAPI's own HEAD guard skips this check while the shooter's revolver cools down (it expects
            // Wathe to refuse); the forced shot ignores that cooldown, so the guard is asked here and, like it,
            // cancels the whole shot. / SparkFactionAPI 自身的 HEAD 拦截在射手左轮冷却中时跳过此检查（它预期 Wathe 会拒绝）；
            // 强制开枪无视该冷却，因此在此询问该拦截，并与其一致地取消整次射击。
            return;
        }
        CooldownSnapshot cooldown = CooldownSnapshot.take(shooter, WatheItems.REVOLVER);
        try {
            DominatorForcedShotScope.fire(shooter, () -> new GunShootPayload.Receiver().receive(
                    new GunShootPayload(shot.targetId()),
                    new ForcedGunContext(shooter.getServer(), shooter, ServerPlayNetworking.getSender(shooter))
            ));
        } finally {
            cooldown.restore(shooter);
        }
        // The receiver resolves kills synchronously: a victim alive before the shot and dead for good after it was
        // killed by this shot (a Last Stand interception is not a death). The +100 is the Dominator's even when the
        // victim is a killer teammate (owner decision); it is paid outside the scope, so it is never frozen.
        // 接收器同步处理击杀：开枪前存活、开枪后确定死亡（背水一战拦截不算死亡）的目标即为这一枪所杀。即使受害者是杀手队友，
        // +100 也归支配者（所有者决定）；在作用域外发放，因此不会被冻结。
        if (victimWasAlive
                && victim != dominator
                && !GameFunctions.isPlayerPlayingAndAlive(victim)
                && !WitchFactorTraitsBridge.isDeathIntercepted(victim)
                && GameFunctions.isPlayerPlayingAndAlive(dominator)) {
            PlayerShopComponent.KEY.get(dominator).addToBalance(DominatorRules.FORCED_SHOT_KILL_REWARD);
        }
    }

    /** Server, {@code GameFunctionsForcedShotDeathMixin}: a committed death. / 死亡已确定。 */
    public static void beforeDeathDrops(ServerPlayerEntity victim) {
        if (DominatorForcedShotComponent.isSequenceActive(victim)
                && !WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
            DominatorForcedShotComponent.KEY.get(victim).settleBeforeDeathDrops();
        }
    }

    /** Disconnect, before Wathe's disconnect kill: gun out, stash back. / 断线，先于 Wathe 的断线击杀。 */
    public static void release(ServerPlayerEntity player) {
        DominatorForcedShotComponent.KEY.maybeGet(player).ifPresent(DominatorForcedShotComponent::settle);
    }

    /**
     * {@code RoleAssigned}: a mid-round role change (Bewitched promotion, Schrödinger's cat) keeps the player's own
     * stack; a role deal outside a running round discards it. / 回合中途换职业保留玩家自己的物品；回合外发职业则丢弃。
     */
    public static void onRoleAssigned(ServerPlayerEntity player) {
        if (GameFunctions.isPlayerPlayingAndAlive(player)) {
            release(player);
        } else {
            discard(player);
        }
    }

    /** {@code ResetPlayer} / round finalize: Wathe has cleared the inventory. / Wathe 已清空物品栏。 */
    public static void discard(ServerPlayerEntity player) {
        DominatorForcedShotComponent.KEY.maybeGet(player).ifPresent(DominatorForcedShotComponent::discard);
    }

    private static boolean refusedByFaction(ServerPlayerEntity shooter, int targetId) {
        Entity entity = targetId < 0 ? null : shooter.getServerWorld().getEntityById(targetId);
        if (!(entity instanceof ServerPlayerEntity target) || target.isSpectator() || target.distanceTo(shooter) >= 65.0F) {
            return false;
        }
        return !SparkFactionApi.canAffectPlayer(
                shooter,
                target,
                GameConstants.DeathReasons.GUN,
                GameWorldComponent.KEY.get(shooter.getWorld())
        );
    }

    /**
     * The exact cooldown entry of one item before the shot. Restored by writing the entry map directly and sending
     * the vanilla update packet, so no cooldown modifier, lock floor or removal hook runs again.
     * 开枪前某物品的精确冷却条目。还原时直接写回条目表并发送原版更新包，因此任何冷却修正、锁定下限或移除钩子都不会再次运行。
     */
    private record CooldownSnapshot(Item item, @Nullable Object entry) {
        static CooldownSnapshot take(ServerPlayerEntity player, Item item) {
            return new CooldownSnapshot(item, entries(player.getItemCooldownManager()).get(item));
        }

        void restore(ServerPlayerEntity player) {
            ItemCooldownManager manager = player.getItemCooldownManager();
            Map<Item, Object> entries = entries(manager);
            if (entries.get(item) == entry) {
                return;
            }
            int remaining = 0;
            if (entry == null) {
                entries.remove(item);
            } else {
                entries.put(item, entry);
                remaining = Math.max(0, ((ItemCooldownEntryAccessor) entry)
                        .sparkwitch$getEndTick() - ((ItemCooldownManagerAccessor) manager).sparkwitch$getTick());
            }
            player.networkHandler.sendPacket(new CooldownUpdateS2CPacket(item, remaining));
        }

        @SuppressWarnings("unchecked")
        private static Map<Item, Object> entries(ItemCooldownManager manager) {
            return (Map<Item, Object>) ((ItemCooldownManagerAccessor) manager).sparkwitch$getEntries();
        }
    }

    private record ForcedGunContext(
            MinecraftServer server,
            ServerPlayerEntity player,
            PacketSender responseSender
    ) implements ServerPlayNetworking.Context {
    }
}
