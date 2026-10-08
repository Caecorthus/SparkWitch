package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.compat.SparkFactionSecondRowCompat;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAttachmentRules.Gate;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAttachmentRules.LooseOutcome;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAttachmentRules.Magazine;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAttachmentRules.Outcome;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAttachmentRules.Release;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAttachmentRules.Round;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAttachmentRules.Suppressor;
import dev.caecorthus.sparkwitch.roles.civilian.usec.net.UsecAttachmentAction;
import dev.caecorthus.sparkwitch.roles.civilian.usec.net.UsecAttachmentC2SPacket;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Server authority for {@code sparkwitch:usec_attachment} (the attachment screen) and the shared apply helpers of
 * cursor loading. The client only names an action and two player-inventory slots; this service re-checks the slot
 * shape, the player gate ({@link UsecAttachmentRules#gate}: not a dead participant, alive, not a spectator, not
 * stunned, not in a Seeker session; Fear does not block), the item types, and, before anything changes, that the
 * inventory has room for what leaves the rifle. State is written in place with {@link UsecRifleState#write} and
 * {@link UsecMagazineItem#setContents}, so the held item only changes components and never replays its equip
 * animation. Released items go only to shown slots ({@link UsecAttachmentRules#releaseSlot}, A7): a shown same-item
 * stack, then an empty shown slot, then the empty offhand; otherwise the action is refused as "no room".
 * Sounds are public ({@code ServerWorld#playSound} at the player); a bolt also writes {@link UsecCooldowns#bolt}.
 * Fabric runs play-payload receivers on the server thread, so no extra hand-off is needed.
 * {@code sparkwitch:usec_attachment}（配件界面）的服务端权威，以及光标装填共用的应用辅助方法。客户端只给出动作与两个
 * 玩家背包栏位；本服务复核栏位形状、玩家准入（{@link UsecAttachmentRules#gate}：非已死亡参与者、存活、非旁观、未被
 * 眩晕、不在搜寻者会话；恐惧不阻止）、物品类型，并在任何改动之前确认背包能放下离开步枪的物品。状态以
 * {@link UsecRifleState#write} 与 {@link UsecMagazineItem#setContents} 原地写入，手持物品只改组件，不重播换手动画。
 * 释放的物品只进入显示中的栏位（{@link UsecAttachmentRules#releaseSlot}，A7）：先补进显示中的同种物品堆，再放入空的显示栏位，
 * 最后放入空的副手；都不行时该动作以“没有空位”拒绝。声音公开播放（在玩家位置调用
 * {@code ServerWorld#playSound}）；拉栓时同时写入 {@link UsecCooldowns#bolt}。Fabric 在服务端线程执行游戏数据包接收器，
 * 无需额外切换线程。
 */
public final class UsecAttachmentService {
    static final String NO_ROOM_KEY = "message.sparkwitch.usec_attachment.no_room";
    /** Suppressor fit/remove reuses the magazine click, pitched up. / 消音器装卸复用弹匣声，并提高音调。 */
    static final float SUPPRESSOR_PITCH = 1.35F;

    private static boolean registered;

    private UsecAttachmentService() {
    }

    /** Sound cue of one accepted change. / 一次已接受改动的声音提示。 */
    enum Cue {
        MAGAZINE,
        SUPPRESSOR,
        ROUND
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerPlayNetworking.registerGlobalReceiver(UsecAttachmentC2SPacket.ID,
                (payload, context) -> handle(context.player(), payload));
    }

    /**
     * Applies one attachment action; returns whether anything changed. Every refusal is silent except a full
     * inventory, which tells the player in the action bar.
     * 应用一个配件动作；返回是否有改动。除背包已满会在动作栏提示外，所有拒绝均为静默。
     */
    public static boolean handle(@Nullable ServerPlayerEntity player, @Nullable UsecAttachmentC2SPacket payload) {
        if (player == null || payload == null) {
            return false;
        }
        UsecAttachmentAction action = payload.resolvedAction();
        if (action == null || !UsecAttachmentRules.slotsValid(action, payload.rifleSlot(), payload.itemSlot())
                || gate(player) != Gate.ALLOWED) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack primary = inventory.getStack(payload.rifleSlot());
        int itemSlot = payload.itemSlot();
        ItemStack item = UsecAttachmentRules.usesItemSlot(action) ? inventory.getStack(itemSlot) : ItemStack.EMPTY;
        if (action.targetsLooseMagazine()) {
            if (!isSingle(primary) || !(primary.getItem() instanceof UsecMagazineItem)) {
                return false;
            }
            return action == UsecAttachmentAction.LOAD_ROUND_LOOSE
                    ? loadLoose(player, primary, item)
                    : unloadLoose(player, primary);
        }
        if (!isSingle(primary) || !(primary.getItem() instanceof UsecRifleItem)) {
            return false;
        }
        UsecRifleState state = UsecRifleState.read(primary);
        return switch (action) {
            case INSERT_MAGAZINE -> insertMagazine(player, primary, state, itemSlot, item);
            case REMOVE_MAGAZINE -> release(player, primary, UsecAttachmentRules.removeMagazine(state), Cue.MAGAZINE);
            case ATTACH_SUPPRESSOR -> attachSuppressor(player, primary, state, itemSlot, item);
            case DETACH_SUPPRESSOR ->
                    release(player, primary, UsecAttachmentRules.detachSuppressor(state), Cue.SUPPRESSOR);
            case CHAMBER_ROUND -> consumeRound(player, primary, item,
                    type -> UsecAttachmentRules.chamberRound(state, type));
            case UNLOAD_CHAMBER -> release(player, primary, UsecAttachmentRules.unloadChamber(state), Cue.ROUND);
            case LOAD_ROUND_ATTACHED -> consumeRound(player, primary, item,
                    type -> UsecAttachmentRules.loadAttached(state, type));
            case UNLOAD_ROUND_ATTACHED ->
                    release(player, primary, UsecAttachmentRules.unloadAttached(state), Cue.ROUND);
            case LOAD_ROUND_LOOSE, UNLOAD_ROUND_LOOSE -> false;
        };
    }

    /**
     * The player gate from live state; shared with cursor loading. / 基于实时状态的玩家准入；与光标装填共用。
     */
    static Gate gate(PlayerEntity player) {
        return UsecAttachmentRules.gate(OffMatchUse.mode(player), player.isAlive() && !player.isRemoved(),
                player.isSpectator(), ControlExpertStun.isStunned(player), SeekerRemoteSessionService.isLocked(player));
    }

    // ---- actions / 动作 ----

    /** Swap: the previously inserted magazine takes the vacated slot (the same stack, rewritten). / 交换：原弹匣占据空出的栏位。 */
    private static boolean insertMagazine(ServerPlayerEntity player, ItemStack rifle, UsecRifleState state,
                                          int magazineSlot, ItemStack magazine) {
        if (!isSingle(magazine) || !(magazine.getItem() instanceof UsecMagazineItem)) {
            return false;
        }
        Outcome outcome = UsecAttachmentRules.insertMagazine(state, UsecMagazineItem.contents(magazine));
        UsecRifleState.write(rifle, outcome.rifle());
        if (outcome.release() instanceof Magazine released) {
            UsecMagazineItem.setContents(magazine, released.contents());
        } else {
            player.getInventory().setStack(magazineSlot, ItemStack.EMPTY);
        }
        finish(player, Cue.MAGAZINE, outcome.bolted());
        return true;
    }

    private static boolean attachSuppressor(ServerPlayerEntity player, ItemStack rifle, UsecRifleState state,
                                            int suppressorSlot, ItemStack suppressor) {
        if (!isSingle(suppressor) || !(suppressor.getItem() instanceof UsecSuppressorItem)) {
            return false;
        }
        Outcome outcome = UsecAttachmentRules.attachSuppressor(state);
        if (outcome == null) {
            return false;
        }
        UsecRifleState.write(rifle, outcome.rifle());
        player.getInventory().setStack(suppressorSlot, ItemStack.EMPTY);
        finish(player, Cue.SUPPRESSOR, outcome.bolted());
        return true;
    }

    /** One round from {@code ammo} into the rifle (chamber or inserted magazine). / 从 ammo 取一发装入步枪。 */
    private static boolean consumeRound(ServerPlayerEntity player, ItemStack rifle, ItemStack ammo,
                                        Function<UsecAmmoType, Outcome> transition) {
        if (ammo.isEmpty() || !(ammo.getItem() instanceof UsecAmmoItem round)) {
            return false;
        }
        Outcome outcome = transition.apply(round.ammoType());
        if (outcome == null) {
            return false;
        }
        UsecRifleState.write(rifle, outcome.rifle());
        ammo.decrement(1);
        finish(player, Cue.ROUND, outcome.bolted());
        return true;
    }

    /** Something leaves the rifle for the inventory; refused without room. / 物品离开步枪进入背包；没有空位则拒绝。 */
    private static boolean release(ServerPlayerEntity player, ItemStack rifle, @Nullable Outcome outcome, Cue cue) {
        if (outcome == null) {
            return false;
        }
        ItemStack released = releasedStack(outcome.release());
        if (!released.isEmpty() && !hasRoom(player, released)) {
            tellNoRoom(player);
            return false;
        }
        UsecRifleState.write(rifle, outcome.rifle());
        if (!released.isEmpty()) {
            insertReleased(player, released);
        }
        finish(player, cue, outcome.bolted());
        return true;
    }

    private static boolean loadLoose(ServerPlayerEntity player, ItemStack magazine, ItemStack ammo) {
        if (ammo.isEmpty() || !(ammo.getItem() instanceof UsecAmmoItem round)) {
            return false;
        }
        UsecMagazineContents next = UsecAttachmentRules.loadLoose(UsecMagazineItem.contents(magazine),
                round.ammoType());
        if (next == null) {
            return false;
        }
        UsecMagazineItem.setContents(magazine, next);
        ammo.decrement(1);
        finish(player, Cue.ROUND, false);
        return true;
    }

    private static boolean unloadLoose(ServerPlayerEntity player, ItemStack magazine) {
        LooseOutcome outcome = UsecAttachmentRules.unloadLoose(UsecMagazineItem.contents(magazine));
        if (outcome == null) {
            return false;
        }
        ItemStack released = new ItemStack(outcome.released().item());
        if (!hasRoom(player, released)) {
            tellNoRoom(player);
            return false;
        }
        UsecMagazineItem.setContents(magazine, outcome.contents());
        insertReleased(player, released);
        finish(player, Cue.ROUND, false);
        return true;
    }

    // ---- shared helpers / 共用辅助 ----

    /** A fresh stack for what leaves the rifle; empty for {@link UsecAttachmentRules.None}. / 离开步枪的物品的新堆。 */
    static ItemStack releasedStack(Release release) {
        return switch (release) {
            case Magazine magazine -> {
                ItemStack stack = new ItemStack(SparkWitchItems.usecMagazine());
                UsecMagazineItem.setContents(stack, magazine.contents());
                yield stack;
            }
            case Round round -> new ItemStack(round.type().item());
            case Suppressor ignored -> new ItemStack(SparkWitchItems.usecSuppressor());
            case UsecAttachmentRules.None ignored -> ItemStack.EMPTY;
        };
    }

    /**
     * Room check before any change (A7): only a shown slot counts ({@link UsecAttachmentRules#releaseSlot}), so a
     * release never lands in hidden storage the in-round inventory cannot show, and a refused release never destroys
     * an item. / 在任何改动之前检查空位（A7）：只算显示中的栏位（{@link UsecAttachmentRules#releaseSlot}），因此释放的物品绝不会
     * 落进局内背包无法显示的隐藏栏位，被拒绝的释放也绝不会销毁物品。
     */
    static boolean hasRoom(PlayerEntity player, ItemStack stack) {
        return releaseSlot(player, stack) != UsecAttachmentRules.NO_ROOM;
    }

    /** {@link UsecAttachmentRules#releaseSlot} over live slots. / 基于实时栏位的 {@link UsecAttachmentRules#releaseSlot}。 */
    static int releaseSlot(PlayerEntity player, ItemStack stack) {
        PlayerInventory inventory = player.getInventory();
        return UsecAttachmentRules.releaseSlot(
                slot -> UsecShopPurchase.fitsWhole(inventory, inventory.getStack(slot), stack),
                slot -> inventory.getStack(slot).isEmpty(),
                SparkFactionSecondRowCompat.isShown());
    }

    /**
     * Puts a released stack where {@link #releaseSlot} says, by explicit slot writes (never {@code insertStack}, whose
     * order reaches hidden slots); callers checked {@link #hasRoom} first. Returns whether it was placed.
     * 按 {@link #releaseSlot} 指定的位置以显式栏位写入放置释放的物品（从不使用会落到隐藏栏位的 {@code insertStack}）；调用方已先检查
     * {@link #hasRoom}。返回是否已放置。
     */
    static boolean insertReleased(PlayerEntity player, ItemStack stack) {
        int slot = releaseSlot(player, stack);
        if (slot == UsecAttachmentRules.NO_ROOM) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack target = inventory.getStack(slot);
        if (target.isEmpty()) {
            inventory.setStack(slot, stack);
        } else {
            target.increment(stack.getCount());
        }
        inventory.markDirty();
        return true;
    }

    /** The only refusal with feedback: an action-bar line to the player. / 唯一有反馈的拒绝：给玩家的动作栏提示。 */
    private static void tellNoRoom(ServerPlayerEntity player) {
        player.sendMessage(Text.translatable(NO_ROOM_KEY).withColor(UsecRules.COLOR), true);
    }

    /** Public cue at the player, then the bolt (sound + exact cooldown) when it cycled. / 公开提示音，拉栓时再写冷却。 */
    static void finish(ServerPlayerEntity player, Cue cue, boolean bolted) {
        switch (cue) {
            case MAGAZINE -> play(player, SparkWitchSounds.USEC_RIFLE_MAGAZINE, 1.0F);
            case SUPPRESSOR -> play(player, SparkWitchSounds.USEC_RIFLE_MAGAZINE, SUPPRESSOR_PITCH);
            case ROUND -> play(player, SparkWitchSounds.USEC_RIFLE_LOAD_ROUND, 1.0F);
        }
        if (bolted) {
            UsecCooldowns.bolt(player);
            play(player, SparkWitchSounds.USEC_RIFLE_BOLT, 1.0F);
        }
    }

    private static void play(ServerPlayerEntity player, @Nullable SoundEvent sound, float pitch) {
        if (sound == null) {
            return;
        }
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), sound,
                SoundCategory.PLAYERS, 1.0F, pitch);
    }

    /** Magazines, rifles and suppressors are single items; a forged stack is refused. / 弹匣、步枪与消音器均为单件。 */
    private static boolean isSingle(ItemStack stack) {
        return !stack.isEmpty() && stack.getCount() == 1;
    }
}
