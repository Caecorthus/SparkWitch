package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Control Expert lifecycle owner: shop, economy, the round-start loadout, the stun interaction guards, replay lines,
 * stun cleanup and the round-end Shock Device sweep.
 * 控场专家生命周期的归属模块：商店、经济、开局装备、眩晕交互拦截、回放文本、眩晕清理与回合结束时的电击装置清理。
 */
public final class ControlExpertFeatureService {
    private static boolean registered;

    private ControlExpertFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ControlExpertShopService.register();
        ControlExpertEconomyService.register();
        ControlExpertStunGuards.register();
        ControlExpertReplayFormatters.register();
        // Final roles only: fires after every RoleAssigned (including SparkTraits Conscience compensation) and
        // Wathe's per-Item revolver start cooldown, and before the round becomes ACTIVE.
        // 仅按最终身份发放：在所有 RoleAssigned（含 SparkTraits 良心补偿）与 Wathe 按物品写入的左轮开局冷却之后、
        // 对局进入 ACTIVE 之前触发。
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                grantRoundStartLoadouts(serverWorld, game);
            }
        });
        registerCleanup();
    }

    /**
     * Releases the stun lock and the effects it still owns whenever a player leaves play, and discards Shock Devices
     * still in flight at finalize. Control Expert items are never removed here.
     * 玩家离开对局时解除眩晕锁并移除其仍拥有的效果，并在收尾时移除仍在飞行的电击装置。此处从不移除控场专家道具。
     */
    private static void registerCleanup() {
        KillPlayer.AFTER.register((victim, killer, reason) -> {
            // SparkTraits Last Stand keeps the victim in play; the component's participant check covers that state.
            // SparkTraits 背水一战会让受害者继续留在对局中；该状态由组件自身的参与者检查处理。
            if (WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
                return;
            }
            ControlExpertStun.release(victim);
        });
        ResetPlayer.EVENT.register(ControlExpertStun::release);
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    ControlExpertStun.release(player);
                }
                ShockDeviceEntity.discardAll(serverWorld);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ControlExpertStun.release(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ControlExpertStun.forgetAll());
    }

    static void grantRoundStartLoadouts(ServerWorld world, GameWorldComponent game) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            // The game is still STARTING here, so the running-state helper would reject every player.
            // 此时对局仍处于 STARTING，运行态判定会拒绝所有玩家。
            if (receivesRoundStartLoadout(game.hasAnyRole(player), game.isPlayerDead(player.getUuid()),
                    game.getRole(player))) {
                grantRoundStartLoadout(player);
            }
        }
    }

    static boolean receivesRoundStartLoadout(boolean hasRole, boolean dead, @Nullable Role finalRole) {
        return hasRole && !dead && ControlExpertRules.isControlExpert(finalRole);
    }

    private static void grantRoundStartLoadout(ServerPlayerEntity player) {
        // The revolver shares Wathe's per-Item start cooldown written earlier in initializeGame. SparkTraits refuses
        // free guns to an Impostor at this point; that trait rule is deliberately not bypassed.
        // 左轮沿用 initializeGame 先前按物品写入的开局冷却。SparkTraits 此时会拒绝向内鬼发放免费枪械；刻意不绕过该词条规则。
        player.getInventory().insertStack(new ItemStack(WatheItems.REVOLVER));
        for (Item item : List.of(SparkWitchItems.disruptor(), SparkWitchItems.taser(), SparkWitchItems.shockDevice())) {
            applyExactCooldown(player, item, ControlExpertRules.INITIAL_COOLDOWN);
        }
        ControlExpertEconomyService.initialize(player);
        player.getInventory().markDirty();
        player.currentScreenHandler.sendContentUpdates();
    }

    /**
     * Exact anti-abuse floor: SparkTraits' exact write performs the vanilla set itself after Fast Hands, so the plain
     * vanilla write runs only when SparkTraits is absent or older (never both, which would send two packets).
     * 精确的防滥用冷却下限：SparkTraits 的精确写入会在“快手”之后自行完成原版写入，因此仅在其缺失或过旧时
     * 回退到原版写入（二者不会同时执行，以免发送两次数据包）。
     */
    static void applyExactCooldown(ServerPlayerEntity player, Item item, int ticks) {
        if (!SparkTraitsKillerBridge.setExactItemCooldownRemaining(player, item, ticks)) {
            player.getItemCooldownManager().set(item, ticks);
        }
    }
}
