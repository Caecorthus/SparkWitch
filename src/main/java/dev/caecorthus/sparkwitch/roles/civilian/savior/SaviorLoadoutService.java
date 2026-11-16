package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.caecorthus.sparkwitch.util.ServerDisconnectCleanup;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.Scheduler;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Savior kit and its lifecycle. Server only. Layout: the game-given room key and letter in hotbar slots 0/1,
 * SparkStrength's police tablet in 2, then the pistol (3) and the lightsaber (4), so the gun stays left of the sword.
 * A Wathe revolver is planted in main-inventory slot 9: during a round SparkFactionAPI's limited inventory draws only
 * the hotbar and the second row (27–35), and rows 9–26 are not drawn at all, so the revolver is invisible and out of
 * reach; it exists for Wathe's death drop (and the innocent-kill punishment). Both visible weapons start on cooldown.
 * Every kit item is placed with {@code setStack}: inserting a gun into a Savior's inventory is refused by a mixin.
 * One grant per player per round ({@link #GRANTED}): at the round start in an after-default
 * {@code ON_FINISH_INITIALIZE} phase (after SparkStrength's default-phase tablet grant, on final roles), or on a
 * {@code RoleAssigned} to the Savior while the round is ACTIVE (an admin role change, a Schrödinger's Cat
 * transformation; the USEC pattern). Any holder may use the weapons (owner, 2026-11-09, the AXMC precedent), and they
 * vanish with whoever holds them on a terminal death (not one SparkTraits intercepted) and on a live-round
 * disconnect; Wathe's own death drop still drops the hidden revolver. A role change away strips nothing.
 * 救世主装备及其生命周期。仅服务端。布局：游戏发放的房间钥匙与信件放在快捷栏 0/1，SparkStrength 警用平板放在 2，然后是
 * 手枪（3）与光剑（4），保证枪在剑左边。主背包 9 槽塞一把 Wathe 左轮：对局中 SparkFactionAPI 的受限物品栏只绘制快捷栏与
 * 第二行（27–35），9–26 行完全不绘制，因此该左轮不可见也碰不到；它留作 Wathe 死亡掉落（以及误杀惩罚）。两把可见武器都带开局
 * 冷却。所有装备都用 {@code setStack} 放置：向救世主物品栏插入枪械会被 mixin 拒绝。每名玩家每回合只发放一次
 * （{@link #GRANTED}）：开局时在默认阶段之后的 {@code ON_FINISH_INITIALIZE} 阶段（晚于 SparkStrength 默认阶段的平板发放，
 * 按最终身份）发放，或在 ACTIVE 对局中通过 {@code RoleAssigned} 成为救世主时发放（管理员换职业、薛定谔之猫转化；USEC 模式）。
 * 武器任何持有者都能使用（所有者 2026-11-09，与 AXMC 先例一致），持有者最终死亡（不含被 SparkTraits 拦截的死亡）以及在
 * 进行中的对局里断线时随之消失；Wathe 自身的死亡掉落仍会掉出隐藏的左轮。转出救世主不收走任何东西。
 */
public final class SaviorLoadoutService {
    /**
     * Wathe's ON_FINISH_INITIALIZE phase for the Savior, ordered after the default phase (the USEC/Seeker pattern):
     * roles are final and SparkStrength's default-phase tablet grant has run, so the tablet moves at once. Still STARTING.
     * 救世主在 Wathe ON_FINISH_INITIALIZE 上使用的阶段，排在默认阶段之后（USEC/搜寻者模式）：身份已是最终身份，SparkStrength
     * 默认阶段的平板发放也已执行，因此平板可立即移动。对局仍处于 STARTING。
     */
    static final Identifier FINISH_INITIALIZE_PHASE = SparkWitch.id("savior_finish_initialize");
    static final int KEY_SLOT = 0;
    static final int LETTER_SLOT = 1;
    static final int TABLET_SLOT = 2;
    static final int PISTOL_SLOT = 3;
    static final int LIGHTSABER_SLOT = 4;
    /** Top-left hidden main-inventory slot (PlayerScreenHandler slot 9). / 隐藏主背包左上角（PlayerScreenHandler 槽位 9）。 */
    static final int HIDDEN_REVOLVER_SLOT = 9;
    /** First slot of the second visible row (SparkFactionAPI limited inventory). / 第二可见行的首槽（SparkFactionAPI 受限物品栏）。 */
    static final int SECOND_ROW_START = 27;
    /** A mid-round tablet comes from SparkStrength's 20-tick reconciliation. / 局中平板来自 SparkStrength 每 20 刻的对账。 */
    private static final int TABLET_RETRY_ATTEMPTS = 40;

    /**
     * Players already granted this round; a repeated {@code RoleAssigned} to the Savior never grants again. Server
     * thread only; cleared per player on death, reset and disconnect, and wholesale at round start, finalize and server
     * stop. A role change away keeps the record.
     * 本回合已发放过的玩家；重复的救世主 {@code RoleAssigned} 不会再次发放。仅服务端线程；死亡、重置与断线时逐个清除，开局、
     * 收尾与服务器停止时整体清空。转出救世主时保留记录。
     */
    private static final Set<UUID> GRANTED = new HashSet<>();
    private static boolean registered;

    private SaviorLoadoutService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GameEvents.ON_FINISH_INITIALIZE.addPhaseOrdering(Event.DEFAULT_PHASE, FINISH_INITIALIZE_PHASE);
        GameEvents.ON_FINISH_INITIALIZE.register(FINISH_INITIALIZE_PHASE, (world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                grantRoundStartLoadouts(serverWorld, game);
            }
        });
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && SaviorRules.isSavior(role)) {
                grantLateLoadout(serverPlayer);
            }
        });
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            if (victim != null) {
                afterKill(victim);
            }
        });
        // Wathe's reset already cleared the inventory: only state is forgotten. / Wathe 重置已清空物品栏：只忘记状态。
        ResetPlayer.EVENT.register(SaviorLoadoutService::forget);
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> forgetRound());
        ServerDisconnectCleanup.register((player, server) ->
                onDisconnect(player, GameFunctions.isPlayerPlayingAndAlive(player)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> forgetRound());
    }

    /** Every final, living Savior gets exactly one grant. / 每名最终存活的救世主恰好获得一次发放。 */
    static void grantRoundStartLoadouts(ServerWorld world, GameWorldComponent game) {
        GRANTED.clear();
        for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
            if (receivesLoadout(game.hasAnyRole(player), game.isPlayerDead(player.getUuid()), game.getRole(player))) {
                grant(player);
            }
        }
    }

    /** Final roles only, still STARTING (no running-state helper). / 仅按最终身份，仍处于 STARTING（不使用运行态判定）。 */
    static boolean receivesLoadout(boolean hasRole, boolean dead, @Nullable Role finalRole) {
        return hasRole && !dead && SaviorRules.isSavior(finalRole);
    }

    /**
     * Mid-round entry, from {@code RoleAssigned} to the Savior and from {@code SchrodingersCatLoadouts} (a backstop for a
     * failed earlier listener; {@link #GRANTED} keeps it idempotent): grants once while the round is ACTIVE to a player
     * who is playing and alive and was not granted this round. Round-start assignments (STARTING) wait for the
     * final-role phase.
     * 中途入口，来自成为救世主的 {@code RoleAssigned} 与 {@code SchrodingersCatLoadouts}（防止前序监听器失败的兜底；
     * {@link #GRANTED} 保证幂等）：对局 ACTIVE 时，对存活在局且本回合尚未发放过的玩家发放一次。开局分配（STARTING）等待最终
     * 身份阶段。
     */
    public static void grantLateLoadout(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (receivesMidRoundGrant(game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player), isGranted(player))) {
            grant(player);
        }
    }

    /** Pure mid-round rule. / 纯中途发放规则。 */
    static boolean receivesMidRoundGrant(boolean roundActive, boolean playingAndAlive, boolean alreadyGranted) {
        return roundActive && playingAndAlive && !alreadyGranted;
    }

    static boolean isGranted(ServerPlayerEntity player) {
        return GRANTED.contains(player.getUuid());
    }

    private static void grant(ServerPlayerEntity player) {
        GRANTED.add(player.getUuid());
        PlayerInventory inventory = player.getInventory();
        // The game already hands out the room key and letter with their room-number/text components. Reuse those
        // exact stacks (do not create blank items), then order key, letter, tablet, pistol, sword.
        // 房间钥匙和信件由游戏按义警同款流程发放，自带房间号/信件内容；复用原物品堆（不要新建白板物品），
        // 再按钥匙、信件、平板、手枪、光剑排序。
        moveExistingToHotbar(player, KEY_SLOT, WatheItems.KEY);
        moveExistingToHotbar(player, LETTER_SLOT, WatheItems.LETTER);
        // At the round start the tablet is already there; mid-round SparkStrength reconciles it later, so retry briefly.
        // 开局时平板已在身上；局中由 SparkStrength 稍后对账发放，因此短暂重试。
        Item tablet = tabletItem();
        if (tablet != null && !moveExistingToHotbar(player, TABLET_SLOT, tablet)) {
            scheduleTabletMove(player, 0);
        }
        putSlot(player, PISTOL_SLOT, new ItemStack(SparkWitchItems.saviorPistol()), false);
        putSlot(player, LIGHTSABER_SLOT, new ItemStack(SparkWitchItems.saviorLightsaber()), false);
        // A player who already carries a Wathe revolver (an admin turned a Vigilante into a Savior) keeps that one as
        // the death-drop revolver: a second plant would add a gun to the round.
        // 已携带 Wathe 左轮的玩家（管理员把义警改成救世主）沿用那一把作为死亡掉落左轮：再放一把会让本局多出一把枪。
        if (!inventory.contains(stack -> stack.isOf(WatheItems.REVOLVER))) {
            putSlot(player, HIDDEN_REVOLVER_SLOT, new ItemStack(WatheItems.REVOLVER), true);
        }

        // Creative players get the weapons without the round-start cooldowns. / 创造模式玩家拿到武器但不进入开局冷却。
        if (!player.isCreative()) {
            SaviorCooldowns.writeCooldown(player, SparkWitchItems.saviorPistol(), SaviorRules.PISTOL_START_COOLDOWN_TICKS);
            SaviorCooldowns.writeCooldown(player, SparkWitchItems.saviorLightsaber(), SaviorRules.LIGHTSABER_START_COOLDOWN_TICKS);
        }
        // The Savior may not pick guns back up for the whole round (Wathe tag); our mixin covers SparkWitch guns.
        // 整局禁止救世主捡枪（Wathe 标签）；SparkWitch 自有枪械由 mixin 拦截。
        GameWorldComponent.KEY.get(player.getServerWorld()).addToPreventGunPickup(player);
        inventory.markDirty();
        player.currentScreenHandler.sendContentUpdates();
    }

    /**
     * Moves the game-given key/letter stack (components preserved) into the requested hotbar slot by swapping with
     * whatever currently sits there. Does nothing when the game did not hand that item out.
     * 把游戏发放的钥匙/信件（保留组件）通过交换移到指定快捷栏槽；游戏没发该物品时不做任何事。
     */
    private static boolean moveExistingToHotbar(ServerPlayerEntity player, int slot, Item item) {
        PlayerInventory inventory = player.getInventory();
        int from = -1;
        for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
            if (inventory.getStack(i).isOf(item)) {
                from = i;
                break;
            }
        }
        if (from < 0) {
            return false;
        }
        if (from != slot) {
            ItemStack target = inventory.getStack(slot);
            inventory.setStack(slot, inventory.getStack(from));
            inventory.setStack(from, target);
        }
        return true;
    }

    private static @Nullable Item tabletItem() {
        Item item = Registries.ITEM.get(Identifier.of("sparkstrength", "tablet"));
        return item == Items.AIR ? null : item;
    }

    /**
     * Retries the tablet move each tick for a short while; stops once the player left, died or was forgotten.
     * 每刻短暂重试平板移动；玩家离开、死亡或被忘记后停止。
     */
    private static void scheduleTabletMove(ServerPlayerEntity player, int attempt) {
        if (attempt > TABLET_RETRY_ATTEMPTS) {
            return;
        }
        Scheduler.schedule(() -> {
            Item tablet = tabletItem();
            if (tablet == null || player.isRemoved() || !isGranted(player)) {
                return;
            }
            if (!moveExistingToHotbar(player, TABLET_SLOT, tablet)) {
                scheduleTabletMove(player, attempt + 1);
            } else {
                player.currentScreenHandler.sendContentUpdates();
            }
        }, 1);
    }

    /**
     * Puts a kit stack into {@code slot}. Whatever sat there moves to the first empty slot of {@link #displacedOrder}
     * (dropped only when the inventory is full), so it is never parked out of sight by accident.
     * 把装备放进 {@code slot}。原物品移到 {@link #displacedOrder} 中第一个空槽（物品栏全满时才掉落），不会意外藏进不可见区域。
     */
    private static void putSlot(ServerPlayerEntity player, int slot, ItemStack stack, boolean fromHiddenRow) {
        PlayerInventory inventory = player.getInventory();
        ItemStack existing = inventory.getStack(slot);
        if (!existing.isEmpty()) {
            int free = firstEmpty(displacedOrder(fromHiddenRow), index -> inventory.getStack(index).isEmpty());
            if (free >= 0) {
                inventory.setStack(free, existing);
            } else {
                player.dropItem(existing, false);
            }
        }
        inventory.setStack(slot, stack);
    }

    /**
     * Pure: where a displaced stack goes. From a hotbar kit slot: the rows the player can use first (SparkFactionAPI's
     * limited-inventory order: the free hotbar slots 5–8, then the second row 27–35), the hidden rows 10–26 last. From the
     * hidden slot 9: the rest of the hidden rows first, since another add-on parked it there out of sight. Never a kit
     * slot (0–4, 9).
     * 纯函数：被挤出的物品去向。来自快捷栏装备槽：先放玩家能用的行（SparkFactionAPI 受限物品栏顺序：空闲快捷栏 5–8，再到第二行
     * 27–35），最后才放隐藏行 10–26。来自隐藏槽 9：先放其余隐藏行，因为那是其他附属模组刻意藏起来的物品。绝不放进装备槽（0–4、9）。
     */
    static int[] displacedOrder(boolean fromHiddenRow) {
        IntStream hotbar = IntStream.range(LIGHTSABER_SLOT + 1, PlayerInventory.getHotbarSize());
        IntStream secondRow = IntStream.range(SECOND_ROW_START, PlayerInventory.MAIN_SIZE);
        IntStream hidden = IntStream.range(HIDDEN_REVOLVER_SLOT + 1, SECOND_ROW_START);
        return (fromHiddenRow
                ? IntStream.concat(IntStream.concat(hidden, secondRow), hotbar)
                : IntStream.concat(IntStream.concat(hotbar, secondRow), hidden)).toArray();
    }

    static int firstEmpty(int[] order, IntPredicate empty) {
        for (int index : order) {
            if (empty.test(index)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * {@code KillPlayer.AFTER} for every victim: a death SparkTraits intercepted (Last Stand; fail-closed check, the
     * USEC/Dominator precedent) keeps everything. Otherwise every Savior pistol and lightsaber is stripped from the
     * victim, whoever holds them, and the state is forgotten. Wathe's drop loop already ran and dropped the revolvers;
     * revolvers are never touched here.
     * 对每名受害者执行的 {@code KillPlayer.AFTER}：被 SparkTraits 拦截的死亡（背水一战；保守失败判定，USEC/支配者先例）保留一切。
     * 否则从受害者身上（无论是谁持有）清除所有救世主手枪与光剑并忘记状态。Wathe 的掉落循环已经执行并掉出了左轮；这里从不触碰左轮。
     */
    static void afterKill(ServerPlayerEntity victim) {
        if (WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
            return;
        }
        cleanUp(victim);
    }

    /**
     * A disconnect from a live round is a death: Wathe schedules the ESCAPED kill only after the player has left and
     * been saved, so the weapons are stripped here, before the save (USEC precedent). Any other disconnect only forgets.
     * 在进行中的对局里断线即死亡：Wathe 安排的 ESCAPED 击杀发生在玩家离开并保存之后，因此武器在此处、保存之前清除（USEC 先例）。
     * 其他断线只忘记状态。
     *
     * @param livingParticipant Wathe {@code isPlayerPlayingAndAlive}, the same test as Wathe's own ESCAPED kill
     */
    static void onDisconnect(ServerPlayerEntity player, boolean livingParticipant) {
        if (livingParticipant) {
            cleanUp(player);
            return;
        }
        forget(player);
    }

    private static void cleanUp(ServerPlayerEntity player) {
        stripWeapons(player);
        forget(player);
    }

    /** Forgets one player without touching items. / 忘记单名玩家而不触碰其物品。 */
    static void forget(ServerPlayerEntity player) {
        GRANTED.remove(player.getUuid());
        SaviorCooldowns.clear(player);
    }

    /** Round finalize and server stop. / 回合收尾与服务器停止。 */
    static void forgetRound() {
        GRANTED.clear();
        SaviorCooldowns.forgetAll();
    }

    /**
     * Removes every Savior pistol and lightsaber the player owns: inventory 0..40 (offhand and armor included), the
     * cursor and the player's own screen (the 2x2 crafting grid). Explicit slot writes, never item entities.
     * 移除玩家拥有的所有救世主手枪与光剑：背包 0..40（含副手与盔甲栏）、光标以及玩家自己的界面（2x2 合成格）。显式栏位写入，
     * 从不生成物品实体。
     */
    static void stripWeapons(ServerPlayerEntity player) {
        boolean changed = false;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (isSaviorWeapon(inventory.getStack(slot))) {
                inventory.setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        ScreenHandler handler = player.currentScreenHandler;
        if (isSaviorWeapon(handler.getCursorStack())) {
            handler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        ScreenHandler own = player.playerScreenHandler;
        for (Slot slot : own.slots) {
            if (isSaviorWeapon(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            inventory.markDirty();
            handler.sendContentUpdates();
            if (own != handler) {
                own.sendContentUpdates();
            }
        }
    }

    private static boolean isSaviorWeapon(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.isOf(SparkWitchItems.saviorPistol()) || stack.isOf(SparkWitchItems.saviorLightsaber()));
    }
}
