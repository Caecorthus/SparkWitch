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
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative lifecycle of the bound Necrology: granted on Prophet assignment, kept at exactly one copy while
 * its owner is a living, playing Prophet of the current match, and deleted on death, role loss, player reset, round
 * finish and stale-match cleanup. The book never drops, so deletion is the only way it leaves a player.
 * 亡者名录的服务端权威生命周期：分配先知时发放；持有者是本局存活且参与对局的先知时始终恰好保留一本；死亡、失去职业、
 * 玩家重置、对局结束与过期对局清理时删除。书本永不掉落，因此删除是它离开玩家的唯一途径。
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
        ResetPlayer.EVENT.register(ProphetNecrologyLoadoutService::removeNecrology);
        GameEvents.ON_FINISH_INITIALIZE.addPhaseOrdering(Event.DEFAULT_PHASE, FINISH_INITIALIZE_PHASE);
        GameEvents.ON_FINISH_INITIALIZE.register(FINISH_INITIALIZE_PHASE, (world, game) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                if (ProphetRules.isProphet(game.getRole(player))) {
                    restoreIfNeeded(player);
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
