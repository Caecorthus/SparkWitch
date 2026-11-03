package dev.caecorthus.sparkwitch.roles.civilian.noa;

import dev.caecorthus.sparkwitch.registry.SparkWitchRoleRegistry;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 城崎诺亚 runtime: the painting roll, her frozen sanity, the two shop rows and the wolf-egg watch.
 * 城崎诺亚运行期：作画掷骰、被钉住的理智、两行商店，以及狼蛋的监视。
 */
public final class NoaService {

    /** Cooldown deadline per player. 每名玩家作画的冷却截止 tick。 */
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
    /** Sanity value we keep restoring to. 我们不断还原到的理智值。 */
    private static final Map<UUID, Float> PINNED_SANITY = new HashMap<>();
    /** Who is carrying the wolf egg right now. 当前携带狼蛋的玩家。 */
    private static final Map<UUID, Boolean> WOLF_EGG_CARRIERS = new HashMap<>();

    private static final Random RANDOM = new Random();

    private NoaService() {
    }

    public static void register() {
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                NoaComponent.KEY.get(serverPlayer).setPaintCooldownSeconds(0);
                PINNED_SANITY.remove(serverPlayer.getUuid());
                if (role != null && NoaRules.ROLE_ID.equals(role.identifier())) {
                    COOLDOWN_UNTIL.put(serverPlayer.getUuid(),
                            serverPlayer.getServer().getTicks() + (long) NoaRules.INITIAL_COOLDOWN_TICKS);
                    NoaComponent.KEY.get(serverPlayer)
                            .setPaintCooldownSeconds(NoaRules.INITIAL_COOLDOWN_TICKS / 20);
                }
            }
        });
        // Tasks never restore her sanity. 任务不回她的理智。
        TaskComplete.EVENT.register((player, taskType) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && isNoa(serverPlayer)) {
                // Do NOT re-pin here: Wathe adds the task sanity before this event, so re-pinning would bank it.
                // 这里不要重新钉住：Wathe 在本事件之前已经加了任务理智，重钉等于把这笔理智存下来。
                // Tasks pay coins only; the tick guard pulls the sanity back to the old value.
                // 任务只给金币；每 tick 的守卫会把理智拉回原值。
                if (serverPlayer.getWorld() instanceof ServerWorld world) {
                    PlayerShopComponent.KEY.get(serverPlayer).addToBalance(NoaRules.TASK_COINS);
                    giveCoinsFeedback(serverPlayer, NoaRules.TASK_COINS);
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(NoaService::tick);
        BuildShopEntries.EVENT.register(NoaService::buildShop);
        GameEvents.ON_FINISH_FINALIZE.register((world, gameComponent) -> {
            COOLDOWN_UNTIL.clear();
            PINNED_SANITY.clear();
            WOLF_EGG_CARRIERS.clear();
        });
    }

    public static boolean isNoa(net.minecraft.entity.player.PlayerEntity player) {
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && NoaRules.ROLE_ID.equals(role.identifier());
    }

    /** Keeps the sanity where it was: no passive drain, no task gain. A -100% breakdown still kills her. */
    /** 把理智钉住：不自然掉、任务不回；但 -100% 的精神崩溃照样会杀死她。 */
    private static void pin(ServerPlayerEntity player) {
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        PINNED_SANITY.put(player.getUuid(), mood.getMood());
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTicks();
        for (ServerWorld world : server.getWorlds()) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (!isNoa(player)) {
                    PINNED_SANITY.remove(player.getUuid());
                    WOLF_EGG_CARRIERS.remove(player.getUuid());
                    continue;
                }
                // Restore the pinned sanity every tick (covers both passive drain and task gain).
                // 每 tick 还原被钉住的理智（同时挡住自然掉与任务回）。
                Float pinned = PINNED_SANITY.get(player.getUuid());
                PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
                if (pinned == null) {
                    PINNED_SANITY.put(player.getUuid(), mood.getMood());
                } else if (mood.getMood() != pinned) {
                    mood.setMood(pinned);
                }
                refreshCooldown(server, player, now);
                // Natural income, every 10 s. 每 10 秒自然收入。
                if (now % NoaRules.PASSIVE_INTERVAL_TICKS == 0L && now > 0L) {
                    PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
                    shop.setBalance(shop.getBalance() + NoaRules.PASSIVE_COINS);
                }
                WOLF_EGG_CARRIERS.put(player.getUuid(), carriesWolfEgg(player));
            }
        }
    }

    private static boolean carriesWolfEgg(ServerPlayerEntity player) {
        for (ItemStack stack : player.getInventory().main) {
            if (stack.isOf(Items.WOLF_SPAWN_EGG)) {
                return true;
            }
        }
        return false;
    }

    private static void refreshCooldown(MinecraftServer server, ServerPlayerEntity player, long now) {
        Long until = COOLDOWN_UNTIL.get(player.getUuid());
        if (until == null) {
            NoaComponent.KEY.get(player).setPaintCooldownSeconds(0);
            return;
        }
        long left = until - now;
        NoaComponent.KEY.get(player).setPaintCooldownSeconds(left > 0 ? (int) Math.ceil(left / 20.0) : 0);
        if (left <= 0) {
            COOLDOWN_UNTIL.remove(player.getUuid());
        }
    }

    /** One-off rewards already handed to a player. 每人已获得的一次性奖励。 */
    private static final Map<UUID, java.util.Set<String>> GIVEN_ONCE = new HashMap<>();

    /** A weighted outcome. 一个带权重的结果。 */
    private record Weighted(String key, double weight) {
    }

    /**
     * The painting roll. Weights follow the owner's tables: fixed percentages stay fixed, "dynamic" ones move with the
     * sanity value inside the table's band, and the whole list is normalised before the pick so the shares always add
     * up. One-off rewards are removed when already given.
     * 作画掷骰。权重按所有者给的表：固定项不变，"动态"项随理智在该表区间内移动；
     * 抽取前整体归一化，保证比例总和成立。一次性奖励已获得则移出。
     */
    public static void paint(ServerPlayerEntity player) {
        if (!isNoa(player) || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        long now = player.getServer().getTicks();
        Long until = COOLDOWN_UNTIL.get(player.getUuid());
        if (until != null && now < until) {
            player.sendMessage(Text.translatable("message.sparkwitch.noa.cooldown",
                    (until - now) / 20).formatted(Formatting.GRAY), true);
            return;
        }
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        float sanity = mood.getMood();
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_HONEY_BLOCK_SLIDE,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
        COOLDOWN_UNTIL.put(player.getUuid(), now + (long) NoaRules.COOLDOWN_TICKS);

        java.util.Set<String> given = GIVEN_ONCE.computeIfAbsent(player.getUuid(), k -> new java.util.HashSet<>());
        java.util.List<Weighted> table = new java.util.ArrayList<>();
        if (sanity > NoaRules.LOW_SANITY_THRESHOLD) {
            double high = Math.min(1.0, sanity);
            table.add(new Weighted("dog", Math.max(0.5, 10.0 * (1.0 - high) / 0.5)));
            table.add(new Weighted("speed2", Math.min(25.0, 10.0 * (high - 0.25) / 0.25)));
            table.add(new Weighted("sanity_loss_high", 25.0));
            table.add(new Weighted("master_key", Math.min(25.0, 15.0 * (high - 0.25) / 0.25)));
            table.add(new Weighted("spyglass", Math.max(0.5, 20.0 * (1.0 - high) / 0.5)));
            table.add(new Weighted("firecracker", 10.0));
            table.add(new Weighted("note", 10.0));
        } else {
            table.add(new Weighted("knife", Math.max(0.5, 15.0 * (0.25 - sanity) / 0.25)));
            table.add(new Weighted("revolver", Math.min(25.0, 15.0 * (1.0 + sanity / 0.25))));
            table.add(new Weighted("whiskey", Math.min(25.0, 15.0 * (1.0 + sanity / 0.25))));
            table.add(new Weighted("sanity_loss_low", 10.0));
            table.add(new Weighted("speed3", 20.0));
            table.add(new Weighted("crowbar", Math.max(0.5, 15.0 * (0.25 - sanity) / 0.25)));
            table.add(new Weighted("sanity_gain_low", 10.0));
        }
        // Drop one-off entries already awarded. 移除已获得的一次性条目。
        table.removeIf(w -> isOneOff(w.key()) && given.contains(w.key()));

        double total = 0.0;
        for (Weighted w : table) {
            total += w.weight();
        }
        double roll = RANDOM.nextDouble() * total;
        Weighted chosen = table.get(table.size() - 1);
        for (Weighted w : table) {
            roll -= w.weight();
            if (roll <= 0) {
                chosen = w;
                break;
            }
        }

        String outcome = "item";
        String detail = "";
        switch (chosen.key()) {
            case "dog" -> {
                give(player, new ItemStack(Items.WOLF_SPAWN_EGG));
                detail = itemName("item.minecraft.wolf_spawn_egg");
                note(player, "message.sparkwitch.noa.paint.wolf_egg");
            }
            case "speed2" -> {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10 * 20, 1, false, false, true));
                detail = itemName("effect.minecraft.speed");
                note(player, "message.sparkwitch.noa.paint.speed");
            }
            case "master_key" -> {
                give(player, masterKey());
                detail = itemName("item.sparkwitch.conductor_key");
                note(player, "message.sparkwitch.noa.paint.master_key");
            }
            case "spyglass" -> {
                give(player, new ItemStack(Items.SPYGLASS));
                detail = itemName("item.minecraft.spyglass");
                note(player, "message.sparkwitch.noa.paint.spyglass");
            }
            case "firecracker" -> {
                give(player, watheItem("firecracker"));
                detail = itemName("item.wathe.firecracker");
                note(player, "message.sparkwitch.noa.paint.firecracker");
            }
            case "note" -> {
                give(player, new ItemStack(Items.PAPER));
                detail = itemName("item.minecraft.paper");
                note(player, "message.sparkwitch.noa.paint.note");
            }
            case "knife" -> {
                give(player, new ItemStack(WatheItems.KNIFE));
                detail = itemName("item.wathe.knife");
                note(player, "message.sparkwitch.noa.paint.knife");
            }
            case "revolver" -> {
                give(player, new ItemStack(WatheItems.REVOLVER));
                detail = itemName("item.wathe.revolver");
                note(player, "message.sparkwitch.noa.paint.revolver");
            }
            case "crowbar" -> {
                give(player, watheItem("crowbar"));
                detail = itemName("item.wathe.crowbar");
                note(player, "message.sparkwitch.noa.paint.crowbar");
            }
            case "whiskey" -> {
                applyWhiskeyShield(player);
                outcome = "effect";
                detail = itemName("effect.noellesroles.whiskey_shield");
                note(player, "message.sparkwitch.noa.paint.whiskey");
            }
            case "speed3" -> {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10 * 20, 2, false, false, true));
                outcome = "effect";
                detail = itemName("effect.minecraft.speed");
                note(player, "message.sparkwitch.noa.paint.speed3");
            }
            case "sanity_loss_high" -> {
                int percent = 10 + RANDOM.nextInt(41);
                changeSanity(player, -percent / 100.0F);
                outcome = "sanity";
                detail = String.valueOf(percent);
                note(player, "message.sparkwitch.noa.paint.lost_sanity", percent);
            }
            case "sanity_loss_low" -> {
                int percent = 10 + RANDOM.nextInt(21);
                changeSanity(player, -percent / 100.0F);
                outcome = "sanity";
                detail = String.valueOf(percent);
                note(player, "message.sparkwitch.noa.paint.lost_sanity", percent);
            }
            case "sanity_gain_low" -> {
                int percent = 10 + RANDOM.nextInt(21);
                changeSanity(player, percent / 100.0F);
                outcome = "sanity_gain";
                detail = String.valueOf(percent);
                note(player, "message.sparkwitch.noa.paint.gained_sanity", percent);
            }
            default -> {
                return;
            }
        }
        if (isOneOff(chosen.key())) {
            given.add(chosen.key());
        }
        NbtCompound replay = new NbtCompound();
        replay.putString("action", "paint");
        replay.putBoolean("low", sanity <= NoaRules.LOW_SANITY_THRESHOLD);
        replay.putString("outcome", outcome);
        replay.putString("detail", detail);
        dev.doctor4t.wathe.record.GameRecordManager.recordSkillUse(player, NoaRules.ROLE_ID, null, replay);
    }

    /** Rewards that may only be awarded once per round. 每局只能获得一次的奖励。 */
    private static boolean isOneOff(String key) {
        return switch (key) {
            case "master_key", "spyglass", "knife", "revolver", "crowbar" -> true;
            default -> false;
        };
    }

    private static String itemName(String key) {
        return Text.translatable(key).getString();
    }

    private static ItemStack watheItem(String path) {
        var item = Registries.ITEM.get(Identifier.of("wathe", path));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** The loot table gives the conductor key now, not a master key. 抽奖表现给乘务员钥匙，不再是万能钥匙。 */
    private static ItemStack masterKey() {
        var item = Registries.ITEM.get(dev.caecorthus.sparkwitch.roles.civilian.discoverer
                .DiscovererRules.CONDUCTOR_KEY_ID);
        return item == Items.AIR ? new ItemStack(Items.IRON_INGOT) : new ItemStack(item);
    }

    /** Whiskey shield with an effectively unlimited duration, unless already held. 威士忌护盾，除非已有。 */
    private static void applyWhiskeyShield(ServerPlayerEntity player) {
        var effect = Registries.STATUS_EFFECT.getEntry(Identifier.of("noellesroles", "whiskey_shield"));
        if (effect.isEmpty()) {
            return;
        }
        if (player.hasStatusEffect(effect.get())) {
            return;
        }
        player.addStatusEffect(new StatusEffectInstance(effect.get(), Integer.MAX_VALUE >> 1, 0, false, false, true));
    }

    /** Changes her sanity and re-pins it, so the tick guard does not undo the intended change. 改理智并重新钉住。 */
    private static void changeSanity(ServerPlayerEntity player, float delta) {
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        float next = Math.max(-1.0F, Math.min(1.0F, mood.getMood() + delta));
        mood.setMood(next);
        PINNED_SANITY.put(player.getUuid(), mood.getMood());
    }

    private static void give(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }

    private static ItemStack ironManVial() {
        var item = Registries.ITEM.get(Identifier.of("noellesroles", "iron_man_vial"));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static void giveCoinsFeedback(ServerPlayerEntity player, int coins) {
        player.sendMessage(Text.translatable("message.sparkwitch.noa.task_coins", coins)
                .formatted(Formatting.GOLD), true);
    }

    private static void note(ServerPlayerEntity player, String key, Object... args) {
        player.sendMessage(Text.translatable(key, args).formatted(Formatting.AQUA), true);
    }

    /** Two sanity rows: one restores 25%, one spends 25%, 50 coins each. 两行理智：一行回复 25%，一行扣除 25%，各 50 金币。 */
    private static void buildShop(net.minecraft.entity.player.PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!isNoa(player)) {
            return;
        }
        ItemStack restore = new ItemStack(Items.LIGHT_BLUE_DYE);
        restore.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.translatable("shop.sparkwitch.noa.sanity_up"));
        context.addEntry(new ShopEntry.Builder("sparkwitch:noa_sanity_up", restore,
                NoaRules.SHOP_PRICE, ShopEntry.Type.TOOL)
                .cooldown(0)
                .onBuy(buyer -> {
                    if (!(buyer instanceof ServerPlayerEntity serverPlayer)) {
                        return false;
                    }
                    changeSanity(serverPlayer, NoaRules.SHOP_SANITY_STEP);
                    return true;
                })
                .build());
        ItemStack spend = new ItemStack(Items.RED_DYE);
        spend.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.translatable("shop.sparkwitch.noa.sanity_down"));
        context.addEntry(new ShopEntry.Builder("sparkwitch:noa_sanity_down", spend,
                NoaRules.SHOP_PRICE, ShopEntry.Type.TOOL)
                .cooldown(0)
                .onBuy(buyer -> {
                    if (!(buyer instanceof ServerPlayerEntity serverPlayer)) {
                        return false;
                    }
                    changeSanity(serverPlayer, -NoaRules.SHOP_SANITY_STEP);
                    return true;
                })
                .build());
    }

    /** Whether this player currently carries the wolf egg (used by the wolf watch). 是否携带狼蛋。 */
    public static boolean carriesWolfEggNow(ServerPlayerEntity player) {
        return Boolean.TRUE.equals(WOLF_EGG_CARRIERS.get(player.getUuid()));
    }

    /** Kills near a carrier punish the killer: Slowness IV, Blindness, and a dog bark. 携带者附近杀人会被惩罚。 */
    public static void onKillNearCarrier(ServerPlayerEntity victim, ServerPlayerEntity killer) {
        if (killer == null || !(victim.getWorld() instanceof ServerWorld world)) {
            return;
        }
        for (UUID uuid : GameWorldComponent.KEY.get(world).getAllWithRole(SparkWitchRoleRegistry.noa())) {
            ServerPlayerEntity noa = world.getServer().getPlayerManager().getPlayer(uuid);
            if (noa == null || !carriesWolfEggNow(noa)) {
                continue;
            }
            if (noa.squaredDistanceTo(killer) > NoaRules.WOLF_RADIUS * NoaRules.WOLF_RADIUS) {
                continue;
            }
            killer.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                    NoaRules.WOLF_PUNISH_TICKS, 3, false, false, true));
            killer.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS,
                    NoaRules.WOLF_PUNISH_TICKS, 0, false, false, true));
            world.playSound(null, killer.getBlockPos(), SoundEvents.ENTITY_WOLF_GROWL,
                    SoundCategory.PLAYERS, 1.0F, 1.0F);
            noa.sendMessage(Text.translatable("message.sparkwitch.noa.wolf.locked",
                    killer.getGameProfile().getName()).formatted(Formatting.GOLD), true);
            NbtCompound wolfReplay = new NbtCompound();
            wolfReplay.putString("action", "wolf");
            wolfReplay.putUuid("target", killer.getUuid());
            dev.doctor4t.wathe.record.GameRecordManager.recordSkillUse(
                    noa, NoaRules.ROLE_ID, killer, wolfReplay);
            killer.sendMessage(Text.translatable("message.sparkwitch.noa.wolf.punished")
                    .formatted(Formatting.RED), true);
        }
    }
}
