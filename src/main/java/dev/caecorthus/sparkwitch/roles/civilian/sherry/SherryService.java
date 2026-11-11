package dev.caecorthus.sparkwitch.roles.civilian.sherry;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.registry.SparkWitchRoleRegistry;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.api.event.GameEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.LightType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 橘雪莉 runtime: mana and coin income, the dim-light sanity reprieve, the detective-badge shop and gift, and the
 * report that arrives when a badge holder dies.
 * 橘雪莉运行期：魔力与金币收入、暗处理智补偿、侦探徽章商店与赠送、以及徽章持有者死亡时送来的报告。
 */
public final class SherryService {

    /** Last observed mood per player, used to halve the drain in dim light. 每位玩家上一刻的理智，用于在暗处减半。 */
    private static final Map<UUID, Float> LAST_MOOD = new HashMap<>();
    private static final Map<UUID, Long> NEXT_INCOME = new HashMap<>();

    private SherryService() {
    }

    public static void register() {
        RoleAssigned.EVENT.register((player, role) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            SherryComponent.KEY.get(serverPlayer).reset();
            LAST_MOOD.remove(serverPlayer.getUuid());
            NEXT_INCOME.remove(serverPlayer.getUuid());
            if (role != null && SherryRules.ROLE_ID.equals(role.identifier())) {
                // The role pays mana, so its mana system must be switched on at deal time.
                // 该身份消耗魔力，因此分配身份时就要打开魔力系统。
                WitchPlayerComponent mana = WitchPlayerComponent.KEY.get(serverPlayer);
                mana.initializeMana();
                // The inventory panel lists whatever skill the component points at.
                // 背包面板列出的是组件指向的那个技能。
                mana.setActiveSkill(SherryMightyForce.SKILL_ID);
            }
        });
        TaskComplete.EVENT.register(SherryService::onTaskComplete);
        ServerTickEvents.END_SERVER_TICK.register(SherryService::tick);
        BuildShopEntries.EVENT.register(SherryService::buildShop);
        UseEntityCallback.EVENT.register(SherryService::onUseEntity);
        KillPlayer.AFTER.register(SherryService::afterKill);
        GameEvents.ON_FINISH_INITIALIZE.register((world, gameComponent) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            for (UUID uuid : gameComponent.getAllWithRole(SparkWitchRoleRegistry.sherry())) {
                ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(uuid);
                if (player != null) {
                    SherryDeductionService.applyInitialCooldown(player);
                    SherryMightyForce.applyInitialCooldown(player);
                }
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, gameComponent) -> {
            LAST_MOOD.clear();
            NEXT_INCOME.clear();
            SherryMightyForce.clear();
        });
    }

    /**
     * Role check that works on both sides: the shop screen builds its entries on the client, and the role map is
     * synced there, so requiring a {@code ServerWorld} here silently dropped the badge from the list.
     * 两端都可用的身份判定：商店界面是在客户端构建条目的，而角色表在客户端也有同步；
     * 这里若要求 {@code ServerWorld}，徽章就会从列表里静默消失。
     */
    public static boolean isSherry(PlayerEntity player) {
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && SherryRules.ROLE_ID.equals(role.identifier());
    }

    /** 50 coins and 10 mana per finished task. 每完成一个任务：50 金币、10 魔力。 */
    private static void onTaskComplete(ServerPlayerEntity player, PlayerMoodComponent.Task taskType) {
        if (!isSherry(player)) {
            return;
        }
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        shop.setBalance(shop.getBalance() + SherryRules.TASK_COINS);
        WitchPlayerComponent.KEY.get(player).addMana(SherryRules.TASK_MANA);
    }

    private static void tick(MinecraftServer server) {
        SherryDeductionService.tickVision(server);
        long now = server.getTicks();
        for (ServerWorld world : server.getWorlds()) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (!isSherry(player)) {
                    LAST_MOOD.remove(player.getUuid());
                    continue;
                }
                applyPassiveIncome(server, player, now);
                applyDimLightReprieve(player);
                SherryMightyForce.tickHud(player);
                refreshDeductionCooldown(player);
                if (!WitchPlayerComponent.KEY.get(player).hasManaSystem()) {
                    // Covers a round that started before this role existed in the save.
                    // 覆盖“存档里角色先于本实现存在”的情况。
                    WitchPlayerComponent.KEY.get(player).initializeMana();
                }
            }
        }
    }

    /** Keeps the HUD's deduction seconds ticking down even when the key is never pressed. 不按键也让 HUD 的推演冷却走字。 */
    private static void refreshDeductionCooldown(ServerPlayerEntity player) {
        SherryDeductionService.refreshHud(player);
    }

    /** Natural coins and mana, every 10 s. 每 10 秒自然获得金币与魔力。 */
    private static void applyPassiveIncome(MinecraftServer server, ServerPlayerEntity player, long now) {
        long next = NEXT_INCOME.getOrDefault(player.getUuid(), 0L);
        if (next == 0L) {
            NEXT_INCOME.put(player.getUuid(), now + SherryRules.PASSIVE_INTERVAL_TICKS);
            return;
        }
        if (now < next) {
            return;
        }
        NEXT_INCOME.put(player.getUuid(), now + SherryRules.PASSIVE_INTERVAL_TICKS);
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        shop.setBalance(shop.getBalance() + SherryRules.PASSIVE_COINS);
        WitchPlayerComponent.KEY.get(player).addMana(SherryRules.PASSIVE_MANA);
    }

    /**
     * Below light level 10 the sanity drain is halved, by giving back half of whatever this tick took.
     * <p>Compensating around Wathe's own drain keeps its mood rules (tasks, gains, clamps) untouched.
     * 亮度低于 10 时理智下降减半：把本 tick 掉的那部分补回一半。
     * <p>在 Wathe 自身的扣减之外做补偿，不动它的任务、回复与上下限规则。
     */
    private static void applyDimLightReprieve(ServerPlayerEntity player) {
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        float current = mood.getMood();
        Float previous = LAST_MOOD.put(player.getUuid(), current);
        if (previous == null) {
            return;
        }
        float drained = previous - current;
        if (drained <= 0.0F) {
            return;
        }
        if (player.getWorld().getLightLevel(LightType.BLOCK, player.getBlockPos()) >= SherryRules.DIM_LIGHT_LEVEL) {
            return;
        }
        mood.setMood(current + drained * 0.5F);
        LAST_MOOD.put(player.getUuid(), mood.getMood());
    }

    /** 侦探徽章 50 coins, two per round. 侦探徽章 50 金币，每局两枚。 */
    private static void buildShop(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!isSherry(player)) {
            return;
        }
        ItemStack badge = new ItemStack(dev.caecorthus.sparkwitch.SparkWitchItems.sherryBadge());
        if (badge.isEmpty()) {
            return;
        }
        context.addEntry(new dev.doctor4t.wathe.util.ShopEntry.Builder(
                "sparkwitch:sherry_badge", badge, SherryRules.BADGE_PRICE,
                dev.doctor4t.wathe.util.ShopEntry.Type.TOOL)
                .cooldown(0)
                .stock(SherryRules.BADGE_STOCK)
                .build());
    }

    /** Hand a badge to another player by right-clicking them with it. 手持徽章右键他人即可赠送。 */
    private static net.minecraft.util.ActionResult onUseEntity(PlayerEntity player, net.minecraft.world.World world,
                                                               Hand hand, net.minecraft.entity.Entity entity,
                                                               net.minecraft.util.hit.EntityHitResult hit) {
        if (world.isClient || !(player instanceof ServerPlayerEntity sherry)
                || !(entity instanceof ServerPlayerEntity target) || target == sherry) {
            return net.minecraft.util.ActionResult.PASS;
        }
        ItemStack held = sherry.getStackInHand(hand);
        if (!held.isOf(dev.caecorthus.sparkwitch.SparkWitchItems.sherryBadge()) || !isSherry(sherry)) {
            return net.minecraft.util.ActionResult.PASS;
        }
        SherryComponent component = SherryComponent.KEY.get(sherry);
        if (!component.addBadgeHolder(target.getUuid())) {
            sherry.sendMessage(Text.translatable("message.sparkwitch.sherry.badge.already",
                    target.getGameProfile().getName()).formatted(Formatting.GRAY), true);
            return net.minecraft.util.ActionResult.FAIL;
        }
        held.decrement(1);
        sherry.sendMessage(Text.translatable("message.sparkwitch.sherry.badge.handed",
                target.getGameProfile().getName()).formatted(Formatting.GREEN), true);
        NbtCompound extra = new NbtCompound();
        extra.putString("action", "badge");
        extra.putUuid("target", target.getUuid());
        dev.doctor4t.wathe.record.GameRecordManager.recordSkillUse(
                sherry, SherryRules.ROLE_ID, target, extra);
        return net.minecraft.util.ActionResult.SUCCESS;
    }

    /** A badge holder died: the detective learns the killer's name and role, and gets a note. 徽章持有者死亡：得知凶手与职业，并获得纸条。 */
    private static void afterKill(ServerPlayerEntity victim, ServerPlayerEntity killer,
                                  net.minecraft.util.Identifier deathReason) {
        if (!(victim.getWorld() instanceof ServerWorld world)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        for (UUID uuid : game.getAllWithRole(SparkWitchRoleRegistry.sherry())) {
            ServerPlayerEntity sherry = world.getServer().getPlayerManager().getPlayer(uuid);
            if (sherry == null) {
                continue;
            }
            SherryComponent component = SherryComponent.KEY.get(sherry);
            if (!component.isBadgeHolder(victim.getUuid())) {
                continue;
            }
            component.removeBadgeHolder(victim.getUuid());
            String killerName = killer != null ? killer.getGameProfile().getName() : "?";
            String killerRole = "?";
            if (killer != null) {
                var role = game.getRole(killer);
                if (role != null) {
                    killerRole = Text.translatable("announcement.role." + role.identifier().getPath()).getString();
                }
            }
            ItemStack note = new ItemStack(dev.caecorthus.sparkwitch.SparkWitchItems.sherryNote());
            NbtCompound data = new NbtCompound();
            data.putString("killer_name", killerName);
            data.putString("killer_role", killerRole);
            note.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
            sherry.giveItemStack(note);
            sherry.sendMessage(Text.translatable("message.sparkwitch.sherry.note.received")
                    .formatted(Formatting.GOLD), true);
            NbtCompound extra = new NbtCompound();
            extra.putString("action", "note");
            extra.putUuid("victim", victim.getUuid());
            dev.doctor4t.wathe.record.GameRecordManager.recordSkillUse(
                    sherry, SherryRules.ROLE_ID, victim, extra);
        }
    }
}
