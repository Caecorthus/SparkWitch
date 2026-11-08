package dev.caecorthus.sparkwitch.roles.civilian.bear.launcher;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.registry.SparkWitchDataComponents;
import dev.caecorthus.sparkwitch.roles.civilian.bear.BearItems;
import dev.caecorthus.sparkwitch.roles.civilian.bear.BearRules;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * M32A1 cursor loading (owner G4, 2026-11-05; the USEC cursor-loading model): in any inventory screen, a right-click
 * on the launcher with smoke rounds on the cursor loads exactly ONE round ({@link M32Cylinder#load()}, D8: the next
 * empty chamber in rotation); a right-click with an empty cursor unloads one round onto the cursor
 * ({@link M32Cylinder#unload()}, the most recently loaded). Left-clicks, other cursor stacks and an empty-cursor click
 * on an empty launcher stay vanilla. {@code Item#onClicked} runs wherever the click is resolved and every such side
 * claims the click, so a claimed right-click never turns into a vanilla swap or half pick-up. Exactly one side writes
 * each click ({@link #writer}, the USEC {@code UsecAttachmentCursorLoading} Writer rule):
 * <ul>
 *   <li>Survival, adventure and container clicks (a creative player's too) resolve on both sides; only the server
 *   writes, and its inventory sync corrects the client's slot and cursor.</li>
 *   <li>The creative inventory screen resolves its clicks on the client only and syncs whole stacks through creative
 *   packets (a creative server accepts any stack anyway); the server never runs this hook for them, so there the client
 *   applies the same pure rule and the creative stack sync carries the result. The client installs the probe for that
 *   screen ({@code client.bear.launcher.M32LauncherClient}). This closes the anti-tank launcher's creative gap, whose
 *   loading writes only on the server and is lost in the creative screen.</li>
 * </ul>
 * The writer re-checks the player gate (the USEC attachment gate: not a dead participant, alive, not a spectator, not
 * stunned, not in a Seeker session; never the role) and that the clicked slot is the player's own main inventory
 * (0-35) or off-hand slot.
 * M32A1 光标装填（所有者 G4，2026-11-05；沿用 USEC 光标装填模型）：在任何背包界面中，光标拿着烟雾榴弹右键发射器恰好装入一发
 * （{@link M32Cylinder#load()}，D8：按转动顺序的下一个空膛）；空光标右键则把一发退到光标上（{@link M32Cylinder#unload()}，
 * 即最近装入的一发）。左键、光标上的其他物品，以及空光标点击空发射器都保持原版行为。{@code Item#onClicked} 在结算该点击的一端
 * 执行，每一端都认领这次点击，因此被认领的右键永远不会变成原版交换或拿起一半。每次点击恰好由一端写入（{@link #writer}，即 USEC
 * {@code UsecAttachmentCursorLoading} 的写入方规则）：生存、冒险与容器中的点击（包括创造模式玩家的）在两端都会结算，只有服务端
 * 写入，由服务端的背包同步纠正客户端的栏位与光标；创造模式物品栏只在客户端结算点击，并通过创造模式数据包同步整个物品堆（创造
 * 模式服务端本就接受任意物品堆），服务端不会为这些点击执行本钩子，因此由客户端应用同一纯规则，再由创造模式物品堆同步带走结果。
 * 该界面的探针由客户端安装（{@code client.bear.launcher.M32LauncherClient}）。这补上了反坦克炮筒的创造模式缺口（其装填只在服务端
 * 写入，在创造模式物品栏中会丢失）。写入方复核玩家准入（即 USEC 配件准入：不是已死亡的参与者、存活、非旁观、未被眩晕、不在
 * 搜寻者会话中；从不检查职业），并确认被点击的栏位是玩家自己的主背包（0-35）或副手栏位。
 */
public final class M32CursorLoading {
    static final String FULL_KEY = "message.sparkwitch.bear.m32.full";
    /** Player inventory indices a load may target: 0-35 and the off-hand 40. / 可装填的背包下标：0-35 与副手 40。 */
    static final int MAIN_SLOTS = 36;
    static final int OFFHAND_SLOT = 40;
    /** Until the client installs its probe (and always on a dedicated server), no click is a creative-screen click. */
    private static volatile BooleanSupplier creativeScreenProbe = () -> false;

    private M32CursorLoading() {
    }

    /** What a click on the launcher does. / 点击发射器的结果。 */
    public enum Action {
        /** Left to vanilla. / 交给原版。 */
        PASS,
        /** One round from the cursor into the cylinder. / 从光标装入一发。 */
        LOAD,
        /** Rounds on the cursor but all six chambers are loaded: claimed, nothing changes. / 已满：认领但不变。 */
        FULL,
        /** One round from the cylinder onto the empty cursor. / 退一发到空光标上。 */
        UNLOAD;

        public boolean consumesClick() {
            return this != PASS;
        }
    }

    /** Which side writes a claimed click. / 已认领的点击由哪一端写入。 */
    enum Writer {
        /** The server (every server-side click). / 服务端（所有服务端点击）。 */
        SERVER,
        /** The client of a creative player in the creative inventory screen. / 创造模式物品栏中创造模式玩家的客户端。 */
        CREATIVE_CLIENT,
        /** Claim only; the server writes (survival and container clicks on the client). / 只认领，由服务端写入。 */
        NONE
    }

    /** Player gate in check order; only {@link #ALLOWED} writes. / 玩家准入（按检查顺序）；仅 ALLOWED 写入。 */
    public enum Gate {
        DEAD_PARTICIPANT,
        NOT_ALIVE,
        SPECTATOR,
        STUNNED,
        SESSION_LOCKED,
        ALLOWED
    }

    /**
     * Client only, called once at client init: answers whether the click being resolved belongs to the vanilla creative
     * inventory screen. / 仅客户端，在客户端初始化时调用一次：回答当前结算的点击是否来自原版创造模式物品栏。
     */
    public static void installCreativeScreenProbe(BooleanSupplier probe) {
        creativeScreenProbe = probe == null ? () -> false : probe;
    }

    /** Pure click decision. / 纯点击判定。 */
    public static Action decide(boolean rightClick, boolean cursorIsRound, boolean cursorEmpty, M32Cylinder cylinder) {
        if (!rightClick || cylinder == null) {
            return Action.PASS;
        }
        if (cursorIsRound) {
            return cylinder.isFull() ? Action.FULL : Action.LOAD;
        }
        if (cursorEmpty && !cylinder.isEmpty()) {
            return Action.UNLOAD;
        }
        return Action.PASS;
    }

    /** Pure writer decision (USEC rule). / 纯写入方判定（USEC 规则）。 */
    static Writer writer(boolean clientSide, boolean creative, boolean creativeScreen) {
        if (!clientSide) {
            return Writer.SERVER;
        }
        return creative && creativeScreen ? Writer.CREATIVE_CLIENT : Writer.NONE;
    }

    /** Pure player gate. / 纯玩家准入。 */
    public static Gate gate(@Nullable OffMatchUse.Mode mode, boolean alive, boolean spectator, boolean stunned,
                            boolean sessionLocked) {
        if (mode == null || mode == OffMatchUse.Mode.REFUSED) {
            return Gate.DEAD_PARTICIPANT;
        }
        if (!alive) {
            return Gate.NOT_ALIVE;
        }
        if (spectator) {
            return Gate.SPECTATOR;
        }
        if (stunned) {
            return Gate.STUNNED;
        }
        if (sessionLocked) {
            return Gate.SESSION_LOCKED;
        }
        return Gate.ALLOWED;
    }

    /** Own main inventory (0-35) or the off-hand. / 自己的主背包（0-35）或副手。 */
    static boolean isLoadSlot(boolean ownInventory, int index) {
        return ownInventory && (index >= 0 && index < MAIN_SLOTS || index == OFFHAND_SLOT);
    }

    /** {@code M32A1Item#onClicked}. Returns whether the click is claimed. / 返回是否认领该点击。 */
    public static boolean onLauncherClicked(ItemStack launcher, ItemStack cursor, Slot slot, ClickType clickType,
                                            PlayerEntity player, StackReference cursorReference) {
        boolean cursorIsRound = cursor != null && !cursor.isEmpty() && cursor.getItem() instanceof SmokeRoundItem;
        M32Cylinder cylinder = M32A1Item.cylinder(launcher);
        Action action = decide(clickType == ClickType.RIGHT, cursorIsRound, cursor == null || cursor.isEmpty(),
                cylinder);
        if (action.consumesClick() && writes(player) && mayWrite(player, slot, launcher)) {
            apply(action, launcher, cylinder, cursor, player, cursorReference);
        }
        return action.consumesClick();
    }

    private static void apply(Action action, ItemStack launcher, M32Cylinder cylinder, ItemStack cursor,
                              PlayerEntity player, StackReference cursorReference) {
        switch (action) {
            case LOAD -> {
                launcher.set(SparkWitchDataComponents.M32_CYLINDER, cylinder.load());
                cursor.decrement(1);
                cursorReference.set(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
                cue(player, SparkWitchSounds.BEAR_M32_LOAD);
            }
            case UNLOAD -> {
                launcher.set(SparkWitchDataComponents.M32_CYLINDER, cylinder.unload());
                cursorReference.set(new ItemStack(BearItems.SMOKE_ROUND));
                cue(player, SparkWitchSounds.BEAR_M32_UNLOAD);
            }
            case FULL -> {
                if (player instanceof ServerPlayerEntity server) {
                    server.sendMessage(Text.translatable(FULL_KEY).withColor(BearRules.COLOR), true);
                }
            }
            default -> {
            }
        }
    }

    private static boolean writes(PlayerEntity player) {
        if (!player.getWorld().isClient) {
            return player instanceof ServerPlayerEntity;
        }
        return writer(true, player.isCreative(), creativeScreenProbe.getAsBoolean()) == Writer.CREATIVE_CLIENT;
    }

    private static boolean mayWrite(PlayerEntity player, Slot slot, ItemStack launcher) {
        return slot != null && isLoadSlot(slot.inventory == player.getInventory(), slot.getIndex())
                && launcher.getCount() == 1
                && gate(OffMatchUse.mode(player), player.isAlive() && !player.isRemoved(), player.isSpectator(),
                ControlExpertStun.isStunned(player), SeekerRemoteSessionService.isLocked(player)) == Gate.ALLOWED;
    }

    /**
     * Server: a public cue at the player (the sound's own attenuation keeps it short-ranged). Creative client: local
     * only, since the server never sees this click.
     * 服务端：在玩家处播放公开音效（音效自身的衰减使其范围很小）。创造模式客户端：只在本地播放，因为服务端看不到这次点击。
     */
    private static void cue(PlayerEntity player, @Nullable SoundEvent sound) {
        if (sound == null) {
            return;
        }
        if (player instanceof ServerPlayerEntity server) {
            server.getServerWorld().playSound(null, server.getX(), server.getEyeY(), server.getZ(), sound,
                    SoundCategory.PLAYERS, 1.0F, 1.0F);
        } else {
            player.playSound(sound, 1.0F, 1.0F);
        }
    }
}
