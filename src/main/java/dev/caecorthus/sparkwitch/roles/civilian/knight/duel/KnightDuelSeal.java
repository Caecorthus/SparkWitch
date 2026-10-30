package dev.caecorthus.sparkwitch.roles.civilian.knight.duel;

import dev.caecorthus.sparkfactionapi.api.cooldown.CooldownKind;
import dev.caecorthus.sparkfactionapi.api.cooldown.CooldownSlot;
import dev.caecorthus.sparkfactionapi.api.cooldown.ForcedCooldowns;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.doctor4t.wathe.block.FoodPlatterBlock;
import dev.doctor4t.wathe.block.TrimmedBedBlock;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.item.CocktailItem;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;

import java.util.Map;
import java.util.Optional;

/**
 * Duel weapon seal (spec: every weapon except the knife and the derringer is sealed until the duel ends). Applies to
 * BOTH duelists, the Knight's revolver included; the cross sword is whitelisted.
 * <ul>
 *   <li>Seal: SparkFactionAPI's exact, monotonic {@code ForcedCooldowns.raise} on every carried non-whitelisted item,
 *   so Fast Hands or NoellesRoles Stimulation cannot shorten it. Re-raised every
 *   {@link KnightDuelRules#REFRESH_INTERVAL_TICKS} to the remaining duel time, which also seals items picked up
 *   mid-duel and re-seals an entry something removed.</li>
 *   <li>Restore: only the items the duel sealed, each set back exactly to its pre-duel remaining time minus the time
 *   the duel held it, unless a penalty raised during the duel still runs past the seal's own floor, which is kept
 *   ({@link KnightDuelRules#restoredRemainingTicks(long, long, int, int)}). Never through
 *   {@code ItemCooldownManager.remove}: its TAIL hook ({@code SparkWitchItemCooldownReleases}) would release a role
 *   timer (Clock, Gift Watch, Ceremonial Sword, White Cane, fish) that was genuinely running before the duel. A zero
 *   result writes a zero-length entry that vanilla expiry drops on the next tick without calling {@code remove}.</li>
 *   <li>Guards: use paths that skip the vanilla cooldown check are refused while a non-whitelisted stack is in the
 *   acting hand: item use (which also covers items SparkFactionAPI exempts from forced cooldowns), {@code useOnEntity}
 *   (the NoellesRoles Catalyst, the timed bomb hand-off), left-click weapons (the poison needle), and block use only on
 *   the blocks whose use applies the held item ({@code appliesHeldItem}), so a sealed revolver still opens doors.
 *   Weapons fired by C2S payload instead of an item are dropped by {@code KnightDuelSkillLock}.</li>
 * </ul>
 * 决斗武器封印（规格：决斗双方除刀与德林加外的武器均被封印，决斗结束后恢复）。作用于决斗双方，包括骑士的左轮；
 * 十字剑在白名单内。
 * <ul>
 *   <li>封印：对每件携带的非白名单物品使用 SparkFactionAPI 精确且单调的 {@code ForcedCooldowns.raise}，快手或
 *   NoellesRoles 兴奋剂无法缩短。每 {@link KnightDuelRules#REFRESH_INTERVAL_TICKS} 刻重新抬高到决斗剩余时间，
 *   因此决斗中捡到的物品也会被封印，被移除的条目也会重新封印。</li>
 *   <li>恢复：只处理决斗封印过的物品，各自精确写回“决斗前剩余时间 − 决斗占用时间”；若决斗期间他人施加的惩罚仍超出封印
 *   自身下限，则保留该惩罚（{@link KnightDuelRules#restoredRemainingTicks(long, long, int, int)}）。绝不经由
 *   {@code ItemCooldownManager.remove}：其 TAIL 钩子（{@code SparkWitchItemCooldownReleases}）会释放决斗前确实在运行的
 *   职业计时（怀表、赠时怀表、仪礼剑、盲杖、鱼）。结果为 0 时写入零时长条目，原版到期逻辑会在下一刻移除它而不调用
 *   {@code remove}。</li>
 *   <li>拦截：手持非白名单物品时，拒绝绕过原版冷却检查的使用途径：物品使用（同时覆盖 SparkFactionAPI 豁免强制冷却的
 *   物品）、{@code useOnEntity}（NoellesRoles 催化剂、定时炸弹转手）、左键武器（毒针），以及仅限
 *   会施用手中物品的方块（{@code appliesHeldItem}）的方块交互，因此被封印的左轮仍可开门。经 C2S 数据包而非物品发射的武器
 *   由 {@code KnightDuelSkillLock} 丢弃。</li>
 * </ul>
 */
public final class KnightDuelSeal {
    /**
     * Ordered before {@link Event#DEFAULT_PHASE} so the seal decides before listeners that act inside the callback;
     * non-duelists and whitelisted stacks always get PASS, so other listeners keep their relative order.
     * 排在 {@link Event#DEFAULT_PHASE} 之前，使封印先于在回调内直接生效的监听器做出决定；非决斗者与白名单物品始终得到
     * PASS，其他监听器之间的相对顺序不变。
     */
    public static final Identifier SEAL_PHASE = SparkWitch.id("knight_duel_seal");
    private static boolean registered;

    private KnightDuelSeal() {
    }

    /**
     * Both sides: the component is synced, so the client refuses before sending a packet and the server refuses
     * forged ones. / 双端生效：组件已同步，客户端在发包前拒绝，服务端拒绝伪造的数据包。
     */
    static synchronized void registerGuards() {
        if (registered) {
            return;
        }
        registered = true;
        UseItemCallback.EVENT.addPhaseOrdering(SEAL_PHASE, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(SEAL_PHASE, (player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            return blocks(player, stack) ? TypedActionResult.fail(stack) : TypedActionResult.pass(stack);
        });
        UseBlockCallback.EVENT.addPhaseOrdering(SEAL_PHASE, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(SEAL_PHASE, (player, world, hand, hit) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!blocks(player, stack)) {
                return ActionResult.PASS;
            }
            // Elsewhere a sealed stack only falls through to useOnBlock, which vanilla gates on the cooldown; one the
            // seal has not reached yet (picked up since the last refresh) is refused until it has.
            // 其他方块上被封印的物品只会落到 useOnBlock，原版以冷却拦截；封印尚未覆盖的物品（上次刷新后才捡到）在覆盖前拒绝。
            boolean unsealed = !player.getItemCooldownManager().isCoolingDown(stack.getItem());
            return appliesHeldItem(world.getBlockState(hit.getBlockPos()).getBlock()) || unsealed
                    ? ActionResult.FAIL : ActionResult.PASS;
        });
        UseEntityCallback.EVENT.addPhaseOrdering(SEAL_PHASE, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.register(SEAL_PHASE, (player, world, hand, entity, hit) -> {
            ItemStack stack = player.getStackInHand(hand);
            // The marked knife never goes into a frame, stand or allay, where end-of-duel confiscation cannot reach
            // it; the knife's own use (raising it) still follows. / 标记小刀不能放进展示框、盔甲架或悦灵（决斗结束时
            // 无法收回）；之后小刀自身的使用（举刀）照常进行。
            if (KnightDuelKnives.isDuelKnife(stack) && !(entity instanceof PlayerEntity)) {
                return ActionResult.FAIL;
            }
            return verdict(player, stack);
        });
        // Attacks always come from the main hand. / 攻击始终来自主手。
        AttackEntityCallback.EVENT.addPhaseOrdering(SEAL_PHASE, Event.DEFAULT_PHASE);
        AttackEntityCallback.EVENT.register(SEAL_PHASE, (player, world, hand, entity, hit) ->
                verdict(player, player.getMainHandStack()));
    }

    private static ActionResult verdict(PlayerEntity player, ItemStack stack) {
        return blocks(player, stack) ? ActionResult.FAIL : ActionResult.PASS;
    }

    /**
     * Blocks whose use reads the main-hand stack and applies it as a weapon without the vanilla cooldown check, so a
     * sealed stack is refused on them even while it is cooling down: {@link FoodPlatterBlock} (and its
     * {@code DrinkTrayBlock} subclass: Wathe poison vial, SparkWitch Witch Maiden poison apple, SparkStrength Bomber
     * timed bomb and Toxicologist blue vitriol) and {@link TrimmedBedBlock} (Wathe scorpion, SparkStrength timed bomb
     * and blue vitriol). Doors, buttons, vents and SparkStrength junction boxes only read whitelisted keys, lockpicks,
     * crowbars, master keys, the tablet or non-weapon tools, and stay usable. A new weapon-applying block goes here.
     * 交互时读取主手物品并将其作为武器施用、且不经原版冷却检查的方块，因此即使物品正在冷却也会被拒绝：
     * {@link FoodPlatterBlock}（及其子类 {@code DrinkTrayBlock}：Wathe 毒药瓶、SparkWitch 巫女毒苹果、SparkStrength 炸弹客
     * 定时炸弹与毒理学家蓝矾）与 {@link TrimmedBedBlock}（Wathe 蝎子、SparkStrength 定时炸弹与蓝矾）。门、按钮、通风口与
     * SparkStrength 接线盒只读取白名单内的钥匙、撬锁器、撬棍、万能钥匙、平板或非武器工具，仍可使用。新增施用武器的方块须加在此处。
     */
    static boolean appliesHeldItem(Block block) {
        return block instanceof FoodPlatterBlock || block instanceof TrimmedBedBlock;
    }

    /** Whether a duelist acting with this stack is refused. / 决斗者用该物品行动时是否被拒绝。 */
    public static boolean blocks(PlayerEntity player, ItemStack stack) {
        return player != null && stack != null && !stack.isEmpty() && !whitelisted(stack.getItem())
                && KnightDuelWorldComponent.KEY.get(player.getWorld()).isDuelist(player.getUuid());
    }

    /**
     * Items a duelist keeps: letters, keys, lockpick, crowbar, the Wathe knife, the derringer, the cross sword,
     * cocktails, NoellesRoles master keys, the SparkStrength tablet and vanilla non-weapons. Everything else is sealed.
     * 决斗者保留的物品：信件、钥匙、撬锁器、撬棍、Wathe 小刀、德林加、十字剑、鸡尾酒、NoellesRoles 万能钥匙、SparkStrength
     * 平板与原版非武器。其余全部封印。
     */
    public static boolean whitelisted(Item item) {
        if (item == WatheItems.LETTER || item == WatheItems.KEY || item == WatheItems.LOCKPICK
                || item == WatheItems.CROWBAR || item == WatheItems.KNIFE || item == WatheItems.DERRINGER
                || item == SparkWitchItems.crossSword() || item instanceof CocktailItem) {
            return true;
        }
        Identifier id = Registries.ITEM.getId(item);
        if (id.equals(KnightDuelRules.MASTER_KEY_ID) || id.equals(KnightDuelRules.NEUTRAL_MASTER_KEY_ID)
                || id.equals(KnightDuelRules.TABLET_ID)) {
            return true;
        }
        if (!id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
            return false;
        }
        RegistryEntry<Item> entry = Registries.ITEM.getEntry(item);
        boolean weaponOrTool = entry.isIn(ItemTags.SWORDS) || entry.isIn(ItemTags.AXES)
                || entry.isIn(ItemTags.PICKAXES) || entry.isIn(ItemTags.SHOVELS) || entry.isIn(ItemTags.HOES)
                || item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT || item == Items.MACE;
        return !weaponOrTool;
    }

    /**
     * Seals every carried non-whitelisted item for at least {@code ticks}. The first time an item is seen its pre-duel
     * end tick is recorded, so a re-raise never overwrites what restore needs. Server thread only.
     * 将每件携带的非白名单物品封印至少 {@code ticks}。首次见到某物品时记录其决斗前结束刻，重复抬高不会覆盖恢复所需的数据。
     * 仅限服务端线程。
     */
    static void seal(ServerPlayerEntity player, Map<Item, Long> sealed, int ticks, long now) {
        if (ticks <= 0) {
            return;
        }
        for (CooldownSlot slot : ForcedCooldowns.slots(player, CooldownKind.ITEM)) {
            Optional<Item> item = Registries.ITEM.getOrEmpty(slot.id());
            if (item.isEmpty() || whitelisted(item.get())) {
                continue;
            }
            sealed.putIfAbsent(item.get(), now + slot.remainingTicks());
            ForcedCooldowns.raise(player, slot, ticks);
        }
    }

    /**
     * Puts every item the duel sealed back to {@code max(0, preDuelRemaining - elapsed)}, carried or not, keeping a
     * cooldown someone else raised past {@code sealFloorTicks} (the duel time still left when it ends) if that is
     * longer. Items the duel never sealed are untouched. Server thread only.
     * 将决斗封印过的每件物品（无论是否仍携带）恢复为 {@code max(0, 决斗前剩余 - 已过时间)}；若他人施加的冷却超出
     * {@code sealFloorTicks}（决斗结束时尚余的决斗时间）且更长，则保留该冷却。决斗从未封印的物品不受影响。仅限服务端线程。
     */
    static void restore(ServerPlayerEntity player, Map<Item, Long> sealed, long now, int sealFloorTicks) {
        for (Map.Entry<Item, Long> entry : sealed.entrySet()) {
            Item item = entry.getKey();
            int current = ForcedCooldowns.itemRemainingTicks(player, item);
            int ticks = KnightDuelRules.restoredRemainingTicks(entry.getValue(), now, current, sealFloorTicks);
            if (current != ticks) {
                restoreExact(player, item, ticks);
            }
        }
    }

    /**
     * The one deliberate shortening write, so it cannot be a monotonic {@code ForcedCooldowns.raise}. SparkTraits'
     * exact write performs the vanilla set itself after Fast Hands (and keeps its forced melee floor); the plain set
     * runs only when SparkTraits is absent or older, never both. A zero write is exact whatever the modifier.
     * 唯一有意缩短的写入，因此不能使用单调的 {@code ForcedCooldowns.raise}。SparkTraits 的精确写入会在快手之后自行完成
     * 原版写入（并保留其强制近战下限）；仅当 SparkTraits 缺失或过旧时才回退到原版写入，二者不会同时执行。写入 0 时无论
     * 何种倍率都是精确的。
     */
    private static void restoreExact(ServerPlayerEntity player, Item item, int ticks) {
        if (!SparkTraitsKillerBridge.setExactItemCooldownRemaining(player, item, ticks)) {
            player.getItemCooldownManager().set(item, ticks);
        }
    }
}
