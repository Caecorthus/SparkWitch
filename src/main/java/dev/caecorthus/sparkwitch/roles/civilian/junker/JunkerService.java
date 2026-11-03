package dev.caecorthus.sparkwitch.roles.civilian.junker;

import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 拾荒者 runtime: corpse snapshots at death, the search itself, loot marking/destroying, and round reset.
 * <p>Ported from NoellesRoles' Parox build. Loot is a real item taken from the victim's inventory snapshot, marked so
 * it can be consumed on use and so it blocks the next search until the junker spends it.
 * 拾荒者运行期：死亡时拍背包快照、搜刮结算、战利品标记与销毁、回合重置。
 * <p>移植自 NoellesRoles 的 Parox 版本。战利品是从死者背包快照里取出的真实物品，会被打上标记：
 * 用掉即销毁，而且会挡住下一次搜刮，直到拾荒者把它处理掉。
 */
public final class JunkerService {

    private static final Map<UUID, Long> SEARCH_COOLDOWN_UNTIL = new HashMap<>();

    private JunkerService() {
    }

    public static void register() {
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            if (victim.getWorld() instanceof ServerWorld world) {
                JunkerLootWorldComponent.KEY.get(world).recordDeath(victim);
            }
        });
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer
                    && role != null && JunkerRules.ROLE_ID.equals(role.identifier())) {
                JunkerPlayerComponent.KEY.get(serverPlayer).reset();
            }
        });
        GameEvents.ON_FINISH_INITIALIZE.register((world, gameComponent) -> {
            if (world instanceof ServerWorld serverWorld) {
                JunkerLootWorldComponent.KEY.get(serverWorld).reset();
                SEARCH_COOLDOWN_UNTIL.clear();
                // Round-start cooldown for every ragpicker. / 所有拾荒者的回合初始冷却。
                for (UUID uuid : gameComponent.getAllWithRole(
                        dev.caecorthus.sparkwitch.registry.SparkWitchRoleRegistry.ragpicker())) {
                    ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(uuid);
                    if (player != null) {
                        SEARCH_COOLDOWN_UNTIL.put(uuid,
                                serverWorld.getTime() + JunkerRules.INITIAL_COOLDOWN_TICKS);
                        JunkerPlayerComponent.KEY.get(player).setCooldownSeconds(
                                JunkerRules.INITIAL_COOLDOWN_TICKS / 20);
                    }
                }
            }
        });
        // Right-clicking a block with loot in hand spends it too (placing, inserting, turning on).
        // 手持战利品右键方块同样会消耗（放置、插入、开启等）。
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                consumeHeldLoot(serverPlayer, hand);
            }
            return net.minecraft.util.ActionResult.PASS;
        });
        // An opening door spends the loot used to open it, the way the source build does.
        // 开门同样消耗掉用来开门的那件战利品，与原实现一致。
        DoorInteraction.EVENT.register(context -> {
            if (!context.isServerSide() || context.isOpen()) {
                return DoorInteraction.DoorInteractionResult.PASS;
            }
            boolean doorTool = context.getDoorType() == DoorInteraction.DoorType.TRAIN_DOOR
                    || context.requiresKey();
            if (!doorTool || !(context.getPlayer() instanceof ServerPlayerEntity serverPlayer)) {
                return DoorInteraction.DoorInteractionResult.PASS;
            }
            ItemStack held = context.getHandItem();
            if (!isDestroyable(held)) {
                return DoorInteraction.DoorInteractionResult.PASS;
            }
            // Spend the same item one tick later, wherever it sits by then.
            // 下一 tick 扣掉同一种物品，不管它那时在哪只手。
            Item heldItem = held.getItem();
            serverPlayer.getServer().execute(() -> consumeMatching(serverPlayer, heldItem));
            return DoorInteraction.DoorInteractionResult.PASS;
        });
        // Push the remaining cooldown to the owner's HUD once per changed second.
        // 冷却剩余每秒变化时推给本人的 HUD。
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (SEARCH_COOLDOWN_UNTIL.isEmpty()) {
                return;
            }
            long nowTicks = server.getTicks();
            SEARCH_COOLDOWN_UNTIL.entrySet().removeIf(entry -> {
                ServerPlayerEntity owner = server.getPlayerManager().getPlayer(entry.getKey());
                long remaining = entry.getValue() - nowTicks;
                if (owner != null) {
                    JunkerPlayerComponent.KEY.get(owner)
                            .setCooldownSeconds((int) Math.max(0, Math.ceil(remaining / 20.0)));
                }
                return remaining <= 0;
            });
        });
        // Loot is spent the moment it is used, whether that is a right-click use or a left-click swing.
        // 战利品一旦被用掉就销毁——不管是右键使用还是左键挥动。
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                consumeHeldLoot(serverPlayer, hand);
            }
            return net.minecraft.util.ActionResult.PASS;
        });
        // Loot is spent the moment it is used: consume it one tick later so the item's own effect still runs.
        // 战利品用掉即销毁：延后 1 刻再扣，保证物品自身的效果先执行。
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return TypedActionResult.pass(player.getStackInHand(hand));
            }
            if (serverPlayer.isSneaking()) {
                // Sneaking with loot in hand throws it away instead of using it.
                // 手持战利品潜行时是丢弃，而不是使用。
                destroyHeld(serverPlayer);
                return TypedActionResult.fail(player.getStackInHand(hand));
            }
            destroyOnUse(serverPlayer, hand);
            return TypedActionResult.pass(player.getStackInHand(hand));
        });
    }

    public static boolean isJunker(PlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        var role = GameWorldComponent.KEY.get(world).getRole(player);
        return role != null && JunkerRules.ROLE_ID.equals(role.identifier());
    }

    /** Handles the ability-key press: validate, then start the five-second look. 处理技能键：校验后开始五秒注视。 */
    public static void handleSearchRequest(ServerPlayerEntity player, UUID bodyUuid) {
        if (!isJunker(player) || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        long now = player.getServer().getTicks();
        long cooldownUntil = SEARCH_COOLDOWN_UNTIL.getOrDefault(player.getUuid(), 0L);
        if (now < cooldownUntil) {
            // The remaining time lives on the HUD, not in an action-bar toast.
            // 剩余时间显示在 HUD 上，不再占用动作栏。
            return;
        }
        JunkerPlayerComponent component = JunkerPlayerComponent.KEY.get(player);
        if (component.isSearching()) {
            return;
        }
        ItemStack blocking = findBlockingLoot(player);
        if (blocking != null) {
            actionBar(player, Text.translatable("tip.sparkwitch.ragpicker.blocked",
                    blocking.getName().copy().formatted(Formatting.RED)));
            return;
        }
        if (findEmptyHotbarSlot(player) < 0) {
            actionBar(player, Text.translatable("tip.sparkwitch.ragpicker.hotbar_full"));
            return;
        }
        if (!isLookingAtBody(player, bodyUuid)) {
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        JunkerLootWorldComponent loot = JunkerLootWorldComponent.KEY.get(world);
        UUID deadUuid = bodyOwner(world, bodyUuid);
        if (deadUuid == null) {
            return;
        }
        if (loot.wasSearched(deadUuid)) {
            actionBar(player, Text.translatable("tip.sparkwitch.ragpicker.searched"));
            return;
        }
        if (!loot.hasLoot(deadUuid)) {
            loot.markSearched(deadUuid);
            actionBar(player, Text.translatable("tip.sparkwitch.ragpicker.empty"));
            return;
        }
        component.startSearch(bodyUuid);
    }

    /** Pays out one random item from the corpse and starts the cooldown. 从尸体里随机取一件并进入冷却。 */
    public static void completeSearch(ServerPlayerEntity player, UUID bodyUuid) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        UUID deadUuid = bodyOwner(world, bodyUuid);
        if (deadUuid == null) {
            return;
        }
        JunkerLootWorldComponent loot = JunkerLootWorldComponent.KEY.get(world);
        if (loot.wasSearched(deadUuid)) {
            actionBar(player, Text.translatable("tip.sparkwitch.ragpicker.searched"));
            return;
        }
        ItemStack reward = loot.randomLoot(deadUuid, player.getRandom());
        if (reward.isEmpty()) {
            loot.markSearched(deadUuid);
            actionBar(player, Text.translatable("tip.sparkwitch.ragpicker.empty"));
            return;
        }
        markLoot(reward);
        int slot = findEmptyHotbarSlot(player);
        if (slot >= 0) {
            player.getInventory().setStack(slot, reward);
        } else {
            player.giveItemStack(reward);
        }
        loot.markSearched(deadUuid);
        SEARCH_COOLDOWN_UNTIL.put(player.getUuid(),
                player.getServer().getTicks() + (long) JunkerRules.SEARCH_COOLDOWN_TICKS);
        // Two replay lines, as the owner's spec lists them: the search, then what came out of it.
        // 按所有者给的规格记两条回放：先“搜刮了尸体”，再“搜刮获得了什么”。
        NbtCompound searchExtra = new NbtCompound();
        searchExtra.putString("action", "search");
        searchExtra.putUuid("victim", deadUuid);
        dev.doctor4t.wathe.record.GameRecordManager.recordSkillUse(player, JunkerRules.ROLE_ID, null, searchExtra);
        NbtCompound foundExtra = new NbtCompound();
        foundExtra.putString("action", "found");
        foundExtra.putString("item", reward.getName().getString());
        foundExtra.putUuid("victim", deadUuid);
        dev.doctor4t.wathe.record.GameRecordManager.recordSkillUse(player, JunkerRules.ROLE_ID, null, foundExtra);
        actionBar(player, Text.translatable("tip.sparkwitch.ragpicker.found",
                reward.getName().copy().formatted(Formatting.GREEN)));
    }

    /** True while the corpse with this UUID is still within reach and in the player's line of sight. 尸体仍在射程内且被注视。 */
    public static boolean isLookingAtBody(ServerPlayerEntity player, UUID bodyUuid) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        Vec3d eye = player.getEyePos();
        Vec3d reach = player.getRotationVec(1.0F).multiply(JunkerRules.SEARCH_RANGE);
        Box box = player.getBoundingBox().stretch(reach).expand(1.0D);
        for (Entity entity : world.getOtherEntities(player, box)) {
            if (!(entity instanceof PlayerBodyEntity body) || !bodyUuid.equals(body.getUuid())) {
                continue;
            }
            if (body.squaredDistanceTo(player) > JunkerRules.SEARCH_RANGE * JunkerRules.SEARCH_RANGE) {
                continue;
            }
            Optional<Vec3d> hit = body.getBoundingBox().expand(0.35D).raycast(eye, eye.add(reach));
            if (hit.isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static UUID bodyOwner(ServerWorld world, UUID bodyUuid) {
        Entity entity = world.getEntity(bodyUuid);
        return entity instanceof PlayerBodyEntity body ? body.getPlayerUuid() : null;
    }

    private static void actionBar(ServerPlayerEntity player, Text text) {
        player.sendMessage(text, true);
    }

    // === loot marking / destroying === 战利品标记与销毁 ===

    public static void markLoot(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        nbt.putBoolean(JunkerRules.LOOT_MARKER, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    public static boolean isLoot(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                        .copyNbt().getBoolean(JunkerRules.LOOT_MARKER);
    }

    /** Room keys, walkie-talkies and the neutral master key never block a search. 房门钥匙、对讲机、中立万能钥匙不挡搜刮。 */
    public static boolean blocksSearch(ItemStack stack) {
        return isLoot(stack) && !isExemptFromBlocking(stack);
    }

    /**
     * Two different exemptions, deliberately:
     * <p>A room key does not block the next search (it stays useful), but it <b>is</b> spent when it opens a door —
     * owner request 2026-11-02, after a looted key kept working forever. Walkie-talkies and the neutral master key are
     * excluded from both.
     * 两种豁免是分开的，这是有意的：
     * <p>房间钥匙不挡下一次搜刮（它还有用），但**开一次门就会被消耗**——所有者 2026-11-02 反馈，
     * 搜刮来的钥匙原本可以无限使用。对讲机与中立万能钥匙两种豁免都不适用。
     */
    private static boolean isExemptFromBlocking(ItemStack stack) {
        if (stack.isOf(WatheItems.KEY) || stack.isOf(WatheItems.WALKIE_TALKIE)) {
            return true;
        }
        Item masterKey = Registries.ITEM.get(Identifier.of("noellesroles", "neutral_master_key"));
        return masterKey != Items.AIR && stack.isOf(masterKey);
    }

    /** Loot that is spent by using it: everything marked except walkie-talkies and the neutral master key. 使用即消耗的战利品。 */
    private static boolean isDestroyable(ItemStack stack) {
        if (!isLoot(stack)) {
            return false;
        }
        if (stack.isOf(WatheItems.WALKIE_TALKIE)) {
            return false;
        }
        Item masterKey = Registries.ITEM.get(Identifier.of("noellesroles", "neutral_master_key"));
        return masterKey == Items.AIR || !stack.isOf(masterKey);
    }

    public static ItemStack findBlockingLoot(PlayerEntity player) {
        for (ItemStack stack : player.getInventory().main) {
            if (blocksSearch(stack)) {
                return stack;
            }
        }
        return null;
    }

    public static int findEmptyHotbarSlot(PlayerEntity player) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getStack(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static void destroyOnUse(ServerPlayerEntity player, Hand hand) {
        // Read the hand again on the next tick: the item may have been replaced or spent by its own logic.
        // 下一 tick 重取手牌：物品可能已被它自己的逻辑替换或消耗掉。
        Item item = player.getStackInHand(hand).getItem();
        player.getServer().execute(() -> consumeMatching(player, item));
    }

    /** Spends one piece of that exact loot, wherever it is now. 扣掉那件战利品一个，不管它现在在哪。 */
    private static void consumeMatching(ServerPlayerEntity player, Item item) {
        if (item == Items.AIR) {
            return;
        }
        for (Hand hand : Hand.values()) {
            ItemStack stack = player.getStackInHand(hand);
            if (isDestroyable(stack) && stack.isOf(item)) {
                stack.decrement(1);
                return;
            }
        }
    }

    /** Spends one piece of loot from the given hand after the action itself has run. 动作执行后从该手扣掉一个战利品。 */
    private static void consumeHeldLoot(ServerPlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!isDestroyable(stack)) {
            return;
        }
        player.getServer().execute(() -> {
            if (!stack.isEmpty()) {
                stack.decrement(1);
            }
        });
    }

    /** Sneak-right-click with loot in hand destroys it immediately. 手持战利品潜行右键直接销毁。 */
    public static void destroyHeld(ServerPlayerEntity player) {
        for (Hand hand : Hand.values()) {
            ItemStack stack = player.getStackInHand(hand);
            if (isDestroyable(stack)) {
                stack.decrement(1);
                actionBar(player, Text.translatable("tip.sparkwitch.ragpicker.destroyed",
                        stack.getName().copy().formatted(Formatting.GRAY)));
                return;
            }
        }
    }

}
