package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAttachmentRules.Gate;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAttachmentRules.Outcome;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ClickType;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Cursor loading (Q9/D17, after the NoellesRoles Bartender base spirit and the Hunter shotgun): with .338 rounds on the
 * cursor, a right-click on a magazine, or on the rifle, loads exactly one round. {@code Item#onClicked} runs wherever
 * the click is resolved, and every such side claims the click, so a right-click with rounds never turns into a vanilla
 * swap. Exactly one side writes each click ({@link #writer}):
 * <ul>
 *   <li>Survival, adventure and container clicks (creative players' too) are resolved on both sides; only the server
 *   writes (the shotgun pattern: the client never writes rifle or magazine state, and the server's inventory sync
 *   corrects the slot and the cursor).</li>
 *   <li>The creative inventory screen (owner, 2026-10-07) resolves its clicks on the client only and syncs whole stacks
 *   through creative packets, which a creative server accepts for any stack anyway; the server never runs this hook
 *   for them. There the client applies the same pure rule and the creative stack sync carries the result. The probe
 *   for that screen is installed by the client ({@code UsecAttachmentClient.register}).</li>
 * </ul>
 * The writer re-checks the {@link UsecAttachmentRules#gate player gate} and that the clicked slot is in the player's
 * own inventory.
 * 光标装填（Q9/D17，参照 NoellesRoles 酒保基酒与猎人霰弹枪）：光标拿着 .338 子弹时右键弹匣或步枪，恰好装入一发。
 * {@code Item#onClicked} 在结算该点击的一端执行，每一端都认领这次点击，因此拿着子弹右键永远不会变成原版交换。每次点击恰好由
 * 一端写入（{@link #writer}）：生存、冒险与容器中的点击（包括创造模式玩家在容器中的点击）在两端都会结算，只有服务端写入
 * （霰弹枪模式：客户端从不写入步枪或弹匣状态，由服务端的背包同步纠正栏位与光标）。创造模式物品栏（所有者 2026-10-07）只在客户端
 * 结算点击，并通过创造模式数据包同步整个物品堆（创造模式服务端本就接受任意物品堆），服务端不会为这些点击执行本钩子；此时由
 * 客户端应用同一纯规则，再由创造模式物品堆同步带走结果。该界面的探针由客户端安装（{@code UsecAttachmentClient.register}）。
 * 写入方复核 {@link UsecAttachmentRules#gate 玩家准入}，并确认被点击的栏位属于玩家自己的背包。
 */
public final class UsecAttachmentCursorLoading {
    /** Until the client installs its probe (and always on a dedicated server), no click is a creative-screen click. */
    private static volatile BooleanSupplier creativeScreenProbe = () -> false;

    private UsecAttachmentCursorLoading() {
    }

    /** Which side writes a claimed cursor load. / 已认领的光标装填由哪一端写入。 */
    enum Writer {
        /** The server (every server-side click). / 服务端（所有服务端点击）。 */
        SERVER,
        /** The client of a creative player in the creative inventory screen. / 创造模式物品栏中创造模式玩家的客户端。 */
        CREATIVE_CLIENT,
        /** Claim only; the server writes (survival and container clicks on the client). / 只认领，由服务端写入。 */
        NONE
    }

    /**
     * Client only, called once at client init: answers whether the click being resolved belongs to the vanilla creative
     * inventory screen. / 仅客户端，在客户端初始化时调用一次：回答当前结算的点击是否来自原版创造模式物品栏。
     */
    public static void installCreativeScreenProbe(BooleanSupplier probe) {
        creativeScreenProbe = probe == null ? () -> false : probe;
    }

    /**
     * Pure writer decision: the server always writes its own clicks; the client writes only a creative player's
     * creative-screen click, which the server never sees. / 纯判定：服务端总是写入自己的点击；客户端只写入创造模式玩家在创造
     * 模式物品栏中的点击（服务端看不到这类点击）。
     */
    static Writer writer(boolean clientSide, boolean creative, boolean creativeScreen) {
        if (!clientSide) {
            return Writer.SERVER;
        }
        return creative && creativeScreen ? Writer.CREATIVE_CLIENT : Writer.NONE;
    }

    private static boolean writes(PlayerEntity player) {
        if (!player.getWorld().isClient) {
            return player instanceof ServerPlayerEntity;
        }
        return writer(true, player.isCreative(), creativeScreenProbe.getAsBoolean()) == Writer.CREATIVE_CLIENT;
    }

    /**
     * The round type a click would load: a right-click with USEC rounds on the cursor, else null (the click is left to
     * vanilla). / 这次点击将装入的弹种：光标拿着 USEC 子弹右键时返回弹种，否则为 null（交给原版处理）。
     */
    public static @Nullable UsecAmmoType claimedRound(ItemStack cursor, ClickType clickType) {
        if (clickType != ClickType.RIGHT || cursor == null || cursor.isEmpty()
                || !(cursor.getItem() instanceof UsecAmmoItem round)) {
            return null;
        }
        return round.ammoType();
    }

    /** {@code UsecMagazineItem#onClicked}: push one round onto a loose magazine. / 向散装弹匣压入一发。 */
    public static boolean onMagazineClicked(ItemStack magazine, ItemStack cursor, Slot slot, ClickType clickType,
                                            PlayerEntity player, StackReference cursorReference) {
        UsecAmmoType type = claimedRound(cursor, clickType);
        if (type == null) {
            return false;
        }
        if (writes(player) && mayLoad(player, slot, magazine)) {
            UsecMagazineContents next = UsecAttachmentRules.loadLoose(UsecMagazineItem.contents(magazine), type);
            if (next != null) {
                UsecMagazineItem.setContents(magazine, next);
                consumeOne(cursor, cursorReference);
                finish(player, false);
            }
        }
        return true;
    }

    /**
     * {@code UsecRifleItem#onClicked}: into the inserted magazine, or a single round into an empty chamber when there
     * is no magazine; the bolt rule applies. / 装入已装弹匣；未装弹匣时单发压入空弹膛；适用拉栓规则。
     */
    public static boolean onRifleClicked(ItemStack rifle, ItemStack cursor, Slot slot, ClickType clickType,
                                         PlayerEntity player, StackReference cursorReference) {
        UsecAmmoType type = claimedRound(cursor, clickType);
        if (type == null) {
            return false;
        }
        if (writes(player) && mayLoad(player, slot, rifle)) {
            Outcome outcome = UsecAttachmentRules.cursorLoadRifle(UsecRifleState.read(rifle), type);
            if (outcome != null) {
                UsecRifleState.write(rifle, outcome.rifle());
                consumeOne(cursor, cursorReference);
                finish(player, outcome.bolted());
            }
        }
        return true;
    }

    /** Own inventory slot, single target stack, open gate. / 自己的背包栏位、单件目标、准入通过。 */
    private static boolean mayLoad(PlayerEntity player, Slot slot, ItemStack target) {
        return slot != null && slot.inventory == player.getInventory()
                && UsecAttachmentRules.isActionSlot(slot.getIndex()) && target.getCount() == 1
                && UsecAttachmentService.gate(player) == Gate.ALLOWED;
    }

    private static void consumeOne(ItemStack cursor, StackReference cursorReference) {
        cursor.decrement(1);
        cursorReference.set(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
    }

    /**
     * Server: public sounds and the bolt cooldown ({@link UsecAttachmentService#finish}). Creative client: local sounds
     * only; no cooldown is written, since the server never sees this click and owns every rifle cooldown.
     * 服务端：公开声音与拉栓冷却（{@link UsecAttachmentService#finish}）。创造模式客户端：只播放本地声音；不写入冷却，因为服务端
     * 看不到这次点击，且所有步枪冷却都由服务端掌管。
     */
    private static void finish(PlayerEntity player, boolean bolted) {
        if (player instanceof ServerPlayerEntity server) {
            UsecAttachmentService.finish(server, UsecAttachmentService.Cue.ROUND, bolted);
            return;
        }
        playLocal(player, SparkWitchSounds.USEC_RIFLE_LOAD_ROUND);
        if (bolted) {
            playLocal(player, SparkWitchSounds.USEC_RIFLE_BOLT);
        }
    }

    private static void playLocal(PlayerEntity player, @Nullable SoundEvent sound) {
        if (sound != null) {
            player.playSound(sound, 1.0F, 1.0F);
        }
    }
}
