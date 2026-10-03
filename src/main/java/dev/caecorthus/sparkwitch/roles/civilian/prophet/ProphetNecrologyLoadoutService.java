package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative lifecycle of the bound Necrology: granted on Prophet assignment, kept at exactly one copy while
 * its owner is a living, playing Prophet of the current match, and deleted on death, role loss, player reset, round
 * finish and stale-match cleanup. The book never drops and cannot be handed to item frames, armor stands, allays or
 * decorated pots, so deletion is the only way it leaves a player.
 * 亡者名录的服务端权威生命周期：分配先知时发放；持有者是本局存活且参与对局的先知时始终恰好保留一本；死亡、失去职业、
 * 玩家重置、对局结束与过期对局清理时删除。书本永不掉落，也无法交给物品展示框、盔甲架、悦灵或饰纹陶罐，
 * 因此删除是它离开玩家的唯一途径。
 */
public final class ProphetNecrologyLoadoutService {
    /**
     * ON_FINISH_INITIALIZE phase ordered after the default phase, so every default listener has settled final roles
     * (and Wathe has started the match record) before the kit check, whatever the mod initializer order.
     * 排在默认阶段之后的 ON_FINISH_INITIALIZE 阶段：无论模组初始化顺序如何，检查装备前所有默认监听器都已确定最终身份，
     * Wathe 也已开始对局记录。
     */
    static final Identifier FINISH_INITIALIZE_PHASE = SparkWitch.id("prophet_necrology_finish_initialize");

    private static boolean registered;

    private ProphetNecrologyLoadoutService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                assignForRole(serverPlayer, role);
            }
        });
        KillPlayer.AFTER.register((victim, killer, deathReason) -> removeNecrology(victim));
        // The book cannot be handed to item frames, armor stands or allays. Both sides: the client stops before
        // sending, the server refuses a forged packet. Decorated pots are handled by
        // mixin/prophet/DecoratedPotBlockProphetNecrologyMixin instead, so the Necrology still opens there.
        // 书本无法交给物品展示框、盔甲架或悦灵。双端生效：客户端在发包前拦截，服务端拒绝伪造的数据包。
        // 饰纹陶罐改由 DecoratedPotBlockProphetNecrologyMixin 处理，因此对着陶罐时亡者名录仍可打开。
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                ProphetNecrologyRules.blocksEntityUse(player.getStackInHand(hand), entity)
                        ? ActionResult.FAIL : ActionResult.PASS);
        ResetPlayer.EVENT.register(ProphetNecrologyLoadoutService::removeNecrology);
        GameEvents.ON_FINISH_INITIALIZE.addPhaseOrdering(Event.DEFAULT_PHASE, FINISH_INITIALIZE_PHASE);
        GameEvents.ON_FINISH_INITIALIZE.register(FINISH_INITIALIZE_PHASE, (world, game) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                if (ProphetRules.isProphet(game.getRole(player))) {
                    // Wathe is still STARTING here (ACTIVE is set right after this event), so the per-tick
                    // playing-and-alive gate would always skip; check role, life and match instead.
                    // 此时 Wathe 仍处于 STARTING（本事件之后才设为 ACTIVE），逐刻的“参与且存活”检查总会跳过；改为检查职业、存活与对局。
                    restoreAtRoundStart(player, game);
                } else {
                    removeNecrology(player);
                }
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    removeNecrology(player);
                }
            }
        });
    }

    /** A fresh Prophet assignment re-grants one clean book; any other role loses it. / 新分配先知时重新发放一本；其他职业失去它。 */
    public static void assignForRole(ServerPlayerEntity player, @Nullable Role role) {
        removeNecrology(player);
        if (ProphetRules.isProphet(role)) {
            player.giveItemStack(new ItemStack(SparkWitchItems.prophetNecrology()));
            player.currentScreenHandler.sendContentUpdates();
        }
    }

    /**
     * Keeps exactly one book for a living, playing Prophet whose state is not bound to an older match: restores a
     * missing copy and deletes extras. Called every server tick from {@link ProphetRuntime#tick}.
     * 为存活且参与对局、状态未绑定旧对局的先知始终保留恰好一本：缺失时补发，多余时删除。由 {@link ProphetRuntime#tick} 每刻调用。
     */
    public static void restoreIfNeeded(ServerPlayerEntity player) {
        UUID currentMatch = currentMatchId();
        if (currentMatch == null
                || ProphetPlayerState.isStale(ProphetPlayerComponent.KEY.get(player).matchId(), currentMatch)
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || !ProphetRules.isProphet(GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player))) {
            return;
        }
        keepExactlyOneAndSync(player);
    }

    /**
     * Round-start variant for the late ON_FINISH_INITIALIZE phase, where the game is not yet running: requires the
     * Prophet role, a living participant (not in Wathe's dead set) and the just-started, non-stale match.
     * 供较晚的 ON_FINISH_INITIALIZE 阶段使用的开局版本，此时对局尚未运行：要求先知职业、存活的参与者（不在 Wathe 死亡集合中），
     * 以及刚开始且未过期的对局。
     */
    static void restoreAtRoundStart(ServerPlayerEntity player, GameWorldComponent game) {
        UUID currentMatch = currentMatchId();
        if (currentMatch == null
                || ProphetPlayerState.isStale(ProphetPlayerComponent.KEY.get(player).matchId(), currentMatch)
                || !ProphetRules.isProphet(game.getRole(player))
                || game.isPlayerDead(player.getUuid())
                || GameFunctions.isPlayerSpectatingOrCreative(player)) {
            return;
        }
        keepExactlyOneAndSync(player);
    }

    private static void keepExactlyOneAndSync(ServerPlayerEntity player) {
        if (keepExactlyOne(player)) {
            player.getInventory().markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }

    /** Deletes every copy from the inventory, the cursor and any open handler slot. / 从背包、光标与当前界面栏位中删除所有副本。 */
    public static void removeNecrology(ServerPlayerEntity player) {
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (ProphetNecrologyRules.isNecrology(player.getInventory().getStack(slot))) {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (ProphetNecrologyRules.isNecrology(player.currentScreenHandler.getCursorStack())) {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        for (Slot slot : player.currentScreenHandler.slots) {
            if (ProphetNecrologyRules.isNecrology(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }

    /** Keeps the first copy (inventory, then cursor), deletes extras, and grants one if missing. / 保留首本、删除多余、缺失时补发。 */
    private static boolean keepExactlyOne(ServerPlayerEntity player) {
        boolean found = false;
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (!ProphetNecrologyRules.isNecrology(player.getInventory().getStack(slot))) {
                continue;
            }
            if (!found) {
                found = true;
            } else {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (ProphetNecrologyRules.isNecrology(player.currentScreenHandler.getCursorStack())) {
            if (found) {
                player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
                changed = true;
            } else {
                found = true;
            }
        }
        if (!found) {
            player.giveItemStack(new ItemStack(SparkWitchItems.prophetNecrology()));
            changed = true;
        }
        return changed;
    }

    private static @Nullable UUID currentMatchId() {
        GameRecordManager.MatchRecord match = GameRecordManager.getCurrentMatch();
        return GameRecordManager.hasActiveMatch() && match != null ? match.getMatchId() : null;
    }
}
