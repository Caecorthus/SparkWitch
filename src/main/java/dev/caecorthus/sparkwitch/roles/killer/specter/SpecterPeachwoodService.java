package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.index.WatheSounds;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Peachwood sword distribution and its only working attack.
 * <ul>
 *   <li>While a Specter's Otherworld window is active, every alive player at or below 25 sanity — or carrying
 *       cowardice — holds one peachwood sword. The sword is recalled when their sanity rises above 25 or the window
 *       closes (cowardice keeps them eligible).</li>
 *   <li>Only the Specter can be hit. A full-charge hit resolves four knife stabs, one after another: each stab spends
 *       one psycho-armour layer, so fewer than four remaining layers means the extra stabs kill the Specter.</li>
 * </ul>
 * 桃木剑的发放与唯一有效的攻击。
 * 里世界窗口开启期间，所有存活的真实理智不高于 25 或带有怯懦的玩家持有桃木剑；理智回到 25 以上或窗口结束时收回
 * （怯懦者不会因理智回升被收回）。
 * 只有幽魂能被命中。满蓄力的一次攻击连续结算 4 次刀刺，每刀消耗 1 层疯魔护盾；护盾不足 4 层时剩余刀数会击杀幽魂。
 */
public final class SpecterPeachwoodService {
    /** Four knife stabs per full-charge hit. / 每次满蓄力攻击 4 次刀刺。 */
    private static final int STABS_PER_HIT = 4;
    /** Inventory sweep interval; half a second. / 背包检查间隔：半秒。 */
    private static final int MAINTAIN_INTERVAL_TICKS = 10;
    private static boolean registered;

    private SpecterPeachwoodService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(SpecterPeachwoodService::tick);
        AttackEntityCallback.EVENT.register(SpecterPeachwoodService::onAttack);
    }

    /** Immediate pass after a window opens or closes; the periodic sweep covers everything else. / 窗口开关时立即刷新一次。 */
    public static void refresh(ServerWorld world) {
        maintain(world.getPlayers());
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % MAINTAIN_INTERVAL_TICKS != 0) {
            return;
        }
        maintain(server.getPlayerManager().getPlayerList());
    }

    private static void maintain(List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            boolean otherworldActive = SpecterOtherworldComponent.KEY.get(player.getServerWorld()).isActive();
            boolean changed;
            if (otherworldActive && canHoldSword(player)) {
                changed = giveSword(player);
            } else if (GameFunctions.isPlayerSpectatingOrCreative(player) || player.isSpectator()) {
                // Leave creative/spectator inventories alone so a manually given sword can be used for testing.
                // 不碰创造/旁观玩家的背包，方便手动给剑测试。
                changed = false;
            } else {
                changed = removeSwords(player);
            }
            if (changed) {
                player.getInventory().markDirty();
                player.currentScreenHandler.sendContentUpdates();
            }
        }
    }

    /** Alive, non-creative/spectating, and either cowardly or real-mood at or below 25 sanity. / 存活、非创造/旁观，且怯懦或真实理智不高于 25。 */
    private static boolean canHoldSword(ServerPlayerEntity player) {
        if (player.isSpectator()
                || GameFunctions.isPlayerSpectatingOrCreative(player)
                || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }
        if (SpecterCowardiceComponent.KEY.get(player).isCoward()) {
            return true;
        }
        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        if (role == null || role.getMoodType() != Role.MoodType.REAL) {
            return false;
        }
        return PlayerMoodComponent.KEY.get(player).getMood() * 100.0F <= SpecterRules.LOW_SANITY_THRESHOLD;
    }

    private static boolean giveSword(ServerPlayerEntity player) {
        if (hasSword(player)) {
            return false;
        }
        // If the inventory is full, wait for the next sweep instead of dropping a new sword every half second.
        // 背包满时等下一轮检查，避免每半秒往地上丢一把新剑。
        return player.getInventory().insertStack(new ItemStack(SparkWitchItems.peachwoodSword()));
    }

    private static boolean hasSword(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(SparkWitchItems.peachwoodSword())) {
                return true;
            }
        }
        return false;
    }

    private static boolean removeSwords(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        boolean removed = false;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(SparkWitchItems.peachwoodSword())) {
                inventory.removeStack(slot);
                removed = true;
            }
        }
        return removed;
    }

    /**
     * Only a peachwood sword in the main hand against the Specter does anything. Every other target is cancelled so the
     * sword never hurts players or mobs; a non-full charge also does nothing.
     * 只有主手桃木剑攻击幽魂才有效。其他目标一律取消，桃木剑不会伤到玩家或生物；蓄力未满同样无效。
     */
    private static ActionResult onAttack(PlayerEntity player, World world, Hand hand, Entity target,
                                         @Nullable EntityHitResult hitResult) {
        if (world.isClient || hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        if (!(player instanceof ServerPlayerEntity attacker)) {
            return ActionResult.PASS;
        }
        ItemStack stack = attacker.getStackInHand(hand);
        if (!stack.isOf(SparkWitchItems.peachwoodSword())) {
            return ActionResult.PASS;
        }
        if (!(target instanceof ServerPlayerEntity victim) || !isActiveSpecter(victim)) {
            return ActionResult.FAIL;
        }
        // Require the wielder to still be an eligible low-sanity player in an open Otherworld, so a stashed or traded
        // sword cannot be used outside the window. Creative is exempt for in-game testing.
        // 要求持有者仍是里世界内符合条件低理智玩家，避免提前藏起来或转手的剑在窗口外使用。创造模式豁免以便测试。
        if (!attacker.isCreative()
                && (!canHoldSword(attacker)
                || !SpecterOtherworldComponent.KEY.get(attacker.getServerWorld()).isActive())) {
            return ActionResult.FAIL;
        }
        if (attacker.getAttackCooldownProgress(0.5F) < 1.0F) {
            return ActionResult.FAIL;
        }
        GameRecordManager.recordItemUse(attacker, SparkWitchItems.PEACHWOOD_SWORD_ID, victim, null);
        for (int stab = 0; stab < STABS_PER_HIT && GameFunctions.isPlayerPlayingAndAlive(victim); stab++) {
            GameFunctions.killPlayer(victim, true, attacker, GameConstants.DeathReasons.KNIFE);
        }
        ServerWorld serverWorld = attacker.getServerWorld();
        serverWorld.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                WatheSounds.ITEM_KNIFE_STAB, SoundCategory.PLAYERS, 1.0F, 1.0F);
        return ActionResult.SUCCESS;
    }

    private static boolean isActiveSpecter(ServerPlayerEntity player) {
        if (GameFunctions.isPlayerSpectatingOrCreative(player)
                || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        return SpecterRules.isSpecter(game.getRole(player));
    }
}
