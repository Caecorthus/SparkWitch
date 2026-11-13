package dev.caecorthus.sparkwitch.roles.witch.abysslistener.loadout;

import dev.caecorthus.sparkwitch.item.BoundItemRules;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.gun.ShriekGunItem;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;

/**
 * Binding rules for the Shriek Gun, used by the role-owned guard mixins in {@code mixin/abysslistener/}: the gun never
 * becomes an item entity, never leaves its holder's own inventory slots, and never drops on death. The gun is
 * identified by class, so the checks stay safe before registry lookups. These guards hold for every holder; only the
 * entitlement sweep is limited to match participants, so a free holder keeps its copy ({@link OffMatchUse}). Clicks
 * follow the strict {@link BoundItemRules} policy, as for the Time Stealer. There is no creative exemption.
 * 啸音铳的绑定规则，供 {@code mixin/abysslistener/} 中本职业自有的防护 mixin 使用：枪永远不会变成物品实体、
 * 不会离开持有者自身的背包栏位、死亡时也不会掉落。按物品类识别，因此不依赖注册表获取。这些防护对每个持有者都生效；
 * 只有资格清扫限于对局参与者，因此自由持有者会保留其枪（{@link OffMatchUse}）。
 * 点击与窃时者一样采用 {@link BoundItemRules} 的严格策略。没有创造模式豁免。
 */
public final class AbyssListenerInventoryRules {
    private AbyssListenerInventoryRules() {
    }

    public static boolean isGun(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ShriekGunItem;
    }

    /** Refuses every drop of the gun into an item entity (Q, Ctrl+Q, any server drop path). / 拒绝把枪丢成掉落物。 */
    public static boolean blocksDrop(@Nullable ItemStack stack) {
        return isGun(stack);
    }

    /**
     * Whether a refused drop is handed straight back ({@link AbyssListenerLoadout#keepRefusedDrop}): only to a living
     * free holder (not a match participant, see {@link OffMatchUse#isMatchParticipant}) and only for the cursor stack a
     * closing screen returns, which the caller clears afterwards. No sweep restores a free holder's copy; a
     * participant's copy stays the sweep's job (re-granted to the Abyss Listener, stripped from everyone else).
     * 被拒绝的丢弃是否直接放回（{@link AbyssListenerLoadout#keepRefusedDrop}）：只针对存活的自由持有者（非对局参与者，
     * 见 {@link OffMatchUse#isMatchParticipant}），且只针对关闭界面时退回、随后会被调用方清空的光标物品。没有清扫会补回
     * 自由持有者的枪；参与者的枪仍由清扫处理（补发给聆渊者，从其他人身上收走）。
     */
    public static boolean keepsRefusedDrop(boolean alive, boolean matchParticipant, boolean cursorReturn) {
        return alive && !matchParticipant && cursorReturn;
    }

    /** Excludes the gun from Wathe's death-drop loop. / 将枪排除出 Wathe 死亡掉落流程。 */
    public static boolean blocksDeathDrop(@Nullable ItemStack stack) {
        return isGun(stack);
    }

    /**
     * World-interaction veto for the stack in the used hand ({@link BoundItemRules#takesHeldStack}). Without this the
     * gun would leave the inventory, show in the world although it is hidden in hand, and the 20-tick sweep would mint
     * a fresh one each time.
     * 针对所用手中物品的世界交互否决（{@link BoundItemRules#takesHeldStack}）。否则枪会离开背包、在世界中显露（尽管手持时
     * 对他人隐藏），而 20 tick 清理会每次补发一把新枪。
     */
    public static boolean blocksEntityUse(@Nullable ItemStack held, @Nullable Entity target) {
        return isGun(held) && target != null && BoundItemRules.takesHeldStack(target.getClass());
    }

    /**
     * Block counterpart of {@link #blocksEntityUse}, asked by {@code DecoratedPotBlockAbyssListenerGunMixin}.
     * {@link #blocksEntityUse} 的方块版本，由 {@code DecoratedPotBlockAbyssListenerGunMixin} 调用。
     */
    public static boolean blocksBlockUse(@Nullable ItemStack held, @Nullable BlockState target) {
        return isGun(held) && target != null && BoundItemRules.takesHeldStack(target.getBlock().getClass());
    }

    /**
     * Holder entitlement: playing (round running, has a role), alive, and exactly the Abyss Listener. Every other match
     * participant, including a former Abyss Listener after a role change or Wraith transition, loses the gun; free
     * holders are never swept ({@link AbyssListenerGunSweep#sweeps}).
     * 持有资格：正在对局中（回合进行、有身份）、存活，且身份恰好是聆渊者。其他所有对局参与者（包括换职业或转为亡灵的
     * 前聆渊者）都会失去枪；自由持有者从不被清扫（{@link AbyssListenerGunSweep#sweeps}）。
     */
    public static boolean isEntitled(boolean playingAndAlive, @Nullable Role role) {
        return playingAndAlive && AbyssListenerRules.isAbyssListener(role);
    }

    /**
     * Round-start grant gate. Wathe fires ON_FINISH_INITIALIZE while the round is still STARTING (not yet "running"),
     * so the role map and the dead set are read directly instead of {@link #isEntitled}.
     * 开局发放门槛。Wathe 在回合仍处于 STARTING（尚未“进行中”）时触发 ON_FINISH_INITIALIZE，
     * 因此直接读取身份表与死亡集合，而不使用 {@link #isEntitled}。
     */
    public static boolean receivesRoundStartGun(boolean hasRole, boolean dead, @Nullable Role role) {
        return hasRole && !dead && AbyssListenerRules.isAbyssListener(role);
    }

    /**
     * Server-side slot-click veto for the gun through the strict {@link BoundItemRules} policy (the Time Stealer
     * matrix): throw and clone are refused; shift-click of the gun is always refused (in the holder's own screen it can
     * only push it into the hidden main slots 9-35); drag distribution with the gun on the cursor is refused; any
     * offhand swap or placement involving the gun is refused (the offhand is invisible in Wathe's UI); everything else
     * must stay inside the holder's own inventory slots, so nothing moves into a container or outside the window. Still
     * allowed: PICKUP within the holder's own slots, PICKUP_ALL on an own slot, hotbar number-key SWAP between own
     * slots, and taking the gun out of the offhand.
     * 通过 {@link BoundItemRules} 严格策略（窃时者矩阵）进行的枪的服务端栏位点击否决：拒绝抛出与复制；枪的 Shift 点击一律
     * 拒绝（在自身界面中只会把它推入隐藏的主背包 9-35 格）；光标持枪时拒绝拖拽分配；涉及枪的副手交换或放置一律拒绝
     * （副手在 Wathe 界面中不可见）；其余操作必须留在持有者自身背包栏位内，因此不会移入容器或窗口外。仍然允许：在自身
     * 栏位内 PICKUP、在自身栏位上 PICKUP_ALL、自身栏位之间的快捷栏数字键 SWAP，以及从副手取出枪。
     */
    public static boolean blocksSlotClick(@Nullable PlayerEntity player, int slotIndex, int button,
                                          @Nullable SlotActionType actionType) {
        return BoundItemRules.blocksStrictSlotClick(player, slotIndex, button, actionType,
                AbyssListenerInventoryRules::isGun);
    }
}
