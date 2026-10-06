package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownEntryAccessor;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseEconomy;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/** Wires Hunter-only interactions, economy, cleanup, and replay behavior into Wathe events. */
public final class HunterFeatureService {
    private static final int DISMANTLE_COOLDOWN_TICKS = 15 * 20;
    private static boolean registered;

    private HunterFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        HunterShopService.register();
        UseBlockCallback.EVENT.register(HunterFeatureService::interactWithTrap);
        KillPlayer.AFTER.register(HunterFeatureService::afterConfirmedDeath);
        ShouldPunishGunShooter.EVENT.register(HunterFeatureService::gunPunishment);
        ResetPlayer.EVENT.register(player -> HunterPlayerComponent.KEY.get(player).reset());
        RoleAssigned.EVENT.register(HunterFeatureService::assignForRole);
        GameEvents.ON_WIN_DETERMINED.register((world, component, status, neutralWinner) -> cleanupRound(world));
        GameEvents.ON_FINISH_FINALIZE.register((world, component) -> {
            if (world instanceof ServerWorld serverWorld) {
                cleanupRound(serverWorld);
            }
        });
        registerReplayFormatters();
    }

    private static void assignForRole(PlayerEntity player, Role role) {
        HunterPlayerComponent.KEY.get(player).reset();
        // Server-side round-start lock keyed by item type (Ninja knife pattern), so a shotgun bought inside the
        // window stays locked; the vanilla cooldown sync drives the client overlay and crosshair.
        // 服务端按物品类型设置开局锁定（同忍者苦无），窗口内购买的猎枪同样受限；原版冷却同步驱动客户端显示与准星。
        if (player instanceof ServerPlayerEntity serverPlayer
                && role != null
                && HunterRules.ROLE_ID.equals(role.identifier())) {
            serverPlayer.getItemCooldownManager().set(
                    Registries.ITEM.get(DoubleBarrelShotgunItem.ID),
                    HunterRules.SHOTGUN_INITIAL_COOLDOWN_TICKS
            );
        }
    }

    private static ActionResult interactWithTrap(
            PlayerEntity player,
            World world,
            Hand hand,
            net.minecraft.util.hit.BlockHitResult hitResult
    ) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        HunterTrapEntity trap = nearestTrap(world, hitResult);
        if (trap == null) {
            return ActionResult.PASS;
        }

        Role role = GameWorldComponent.KEY.get(world).getRole(player);
        // Owner reclaim needs the placer to still be the real Hunter, so an ex-Hunter whose role changed gets no trap
        // back. 放置者回收要求其当前真实身份仍为猎人，职业已变化的前猎人无法取回捕兽夹。
        if (player.isSneaking() && player.getUuid().equals(trap.getOwnerUuid())
                && role != null && HunterRules.ROLE_ID.equals(role.identifier())) {
            if (!world.isClient) {
                ItemStack returnedTrap = new ItemStack(Registries.ITEM.get(HunterTrapItem.ID));
                if (!player.giveItemStack(returnedTrap)) {
                    player.dropItem(returnedTrap, false);
                }
                recordTrapInteraction(player, trap, "pickup");
                world.playSound(
                        null,
                        trap.getBlockPos(),
                        SoundEvents.BLOCK_CHAIN_FALL,
                        SoundCategory.PLAYERS,
                        0.8F,
                        1.2F
                );
                trap.discardTrap();
            }
            return ActionResult.SUCCESS;
        }

        // Dismantlers have direct-view access only; owner reclaim above intentionally needs no line of sight.
        // 拆除者只能直视操作；上方放置者回收路径有意不要求视线。
        if (player.isSneaking() && role != null
                && HunterRules.canDismantle(role.identifier(), player.canSee(trap))) {
            if (!world.isClient) {
                applyDismantleCooldown(player);
                recordTrapInteraction(player, trap, "dismantle");
                world.playSound(
                        null,
                        trap.getBlockPos(),
                        SoundEvents.BLOCK_CHAIN_FALL,
                        SoundCategory.PLAYERS,
                        0.8F,
                        1.2F
                );
                trap.discardTrap();
            }
            return ActionResult.SUCCESS;
        }

        ItemStack heldStack = player.getStackInHand(hand);
        if (!heldStack.isOf(WatheItems.POISON_VIAL)
                || role == null
                || role.getFaction() != Faction.KILLER
                || trap.isPoisoned()) {
            return ActionResult.PASS;
        }
        if (!world.isClient) {
            trap.poison(player.getUuid());
            recordTrapInteraction(player, trap, "poison");
            world.playSound(
                    null,
                    trap.getBlockPos(),
                    SoundEvents.ITEM_BOTTLE_EMPTY,
                    SoundCategory.PLAYERS,
                    0.8F,
                    1.1F
            );
            heldStack.decrement(1);
        }
        return ActionResult.SUCCESS;
    }

    private static HunterTrapEntity nearestTrap(
            World world,
            net.minecraft.util.hit.BlockHitResult hitResult
    ) {
        Box searchBox = new Box(hitResult.getBlockPos()).expand(1.5D);
        return world.getEntitiesByClass(
                        HunterTrapEntity.class,
                        searchBox,
                        trap -> trap.squaredDistanceTo(hitResult.getPos()) < 2.25D
                ).stream()
                .min(Comparator.comparingDouble((HunterTrapEntity trap) -> trap.squaredDistanceTo(hitResult.getPos()))
                        .thenComparingInt(HunterTrapEntity::getId))
                .orElse(null);
    }

    private static void applyDismantleCooldown(PlayerEntity player) {
        Set<Item> cooldownItems = new HashSet<>();
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty() && isCooldownBearing(player, stack.getItem())) {
                cooldownItems.add(stack.getItem());
            }
        }

        ItemCooldownManager manager = player.getItemCooldownManager();
        ItemCooldownManagerAccessor managerAccessor = (ItemCooldownManagerAccessor) manager;
        Map<Item, ?> entries = managerAccessor.sparkwitch$getEntries();
        int currentTick = managerAccessor.sparkwitch$getTick();
        for (Item item : cooldownItems) {
            int existingTicks = remainingCooldownTicks(entries.get(item), currentTick);
            if (existingTicks < DISMANTLE_COOLDOWN_TICKS) {
                manager.set(item, DISMANTLE_COOLDOWN_TICKS);
            }
        }
    }

    private static boolean isCooldownBearing(PlayerEntity player, Item item) {
        Identifier itemId = Registries.ITEM.getId(item);
        return GameConstants.ITEM_COOLDOWNS.containsKey(item)
                || item instanceof DoubleBarrelShotgunItem
                || HunterRules.isExtraDismantleCooldownItem(itemId)
                || player.getItemCooldownManager().isCoolingDown(item);
    }

    private static int remainingCooldownTicks(Object entry, int currentTick) {
        if (!(entry instanceof ItemCooldownEntryAccessor accessor)) {
            return 0;
        }
        return Math.max(0, accessor.sparkwitch$getEndTick() - currentTick);
    }

    private static void afterConfirmedDeath(
            ServerPlayerEntity victim,
            ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        // Not gated on the victim's current role: a Hunter whose role changed mid-round may keep the loadout,
        // and GameFunctionsHunterDropMixin has already kept every copy out of Wathe's death drops.
        // 不按死者当前身份判断：局中职业已变化的猎人可能仍持有装备，且 GameFunctionsHunterDropMixin
        // 已将所有副本排除在 Wathe 死亡掉落之外。
        removeHunterLoadout(victim);

        HunterPlayerComponent component = HunterPlayerComponent.KEY.get(victim);
        if (GameConstants.DeathReasons.POISON.equals(deathReason)) {
            PlayerPoisonComponent poison = PlayerPoisonComponent.KEY.get(victim);
            if (component.hasConfirmedTrapPoisonDeath(victim.getServerWorld().getTime(), poison.poisoner)) {
                awardTrapPoison(component.getTrapPoisonAttribution(), victim.getServerWorld());
            }
        }
        component.clearPoisonAttribution();
    }

    private static void removeHunterLoadout(ServerPlayerEntity victim) {
        for (int slot = 0; slot < victim.getInventory().size(); slot++) {
            if (HunterInventoryRules.isHunterLoadout(victim.getInventory().getStack(slot))) {
                victim.getInventory().setStack(slot, ItemStack.EMPTY);
            }
        }
        victim.currentScreenHandler.sendContentUpdates();
    }

    private static void awardTrapPoison(
            HunterPoisonAttribution attribution,
            ServerWorld world
    ) {
        if (attribution == null) {
            return;
        }
        UUID poisonerUuid = attribution.effectivePoisonerUuid();
        if (poisonerUuid != null) {
            addKillerReward(world, poisonerUuid, HunterRules.POISONER_REWARD);
        }
        if (attribution.placerUuid() != null) {
            addKillerReward(world, attribution.placerUuid(), HunterRules.PLACER_REWARD);
        }
    }

    /**
     * Poisoner and placer rewards are killer income: a disguised Black Raven receives them in its stashed Raven
     * wallet (amendment W, reported to the SparkStrength purse like any action reward), everyone else in the live
     * wallet.
     * 投毒与布置奖励属于杀手收入：伪装中的黑羽鸦记入其存档中的黑羽鸦钱包（修订 W，与其他行动奖励一样上报
     * SparkStrength 团队资金），其他玩家记入当前钱包。
     */
    private static void addKillerReward(ServerWorld world, UUID playerUuid, int amount) {
        PlayerEntity player = world.getPlayerByUuid(playerUuid);
        if (player == null) {
            return;
        }
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        if (!BlackRavenDisguiseEconomy.creditKillerIncome(shop, amount, true)) {
            shop.addToBalance(amount);
        }
    }

    private static ShouldPunishGunShooter.PunishResult gunPunishment(PlayerEntity shooter, PlayerEntity victim) {
        Role role = GameWorldComponent.KEY.get(shooter.getWorld()).getRole(shooter);
        return role != null && HunterRules.ROLE_ID.equals(role.identifier())
                ? ShouldPunishGunShooter.PunishResult.cancel()
                : null;
    }

    private static void cleanupRound(ServerWorld world) {
        for (HunterTrapEntity trap : world.getEntitiesByType(
                TypeFilter.equals(HunterTrapEntity.class),
                trap -> true
        )) {
            trap.discardTrap();
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            HunterPlayerComponent.KEY.get(player).reset();
        }
    }

    private static void recordTrapInteraction(PlayerEntity player, HunterTrapEntity trap, String action) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        NbtCompound extra = new NbtCompound();
        extra.putString("action", action);
        GameRecordManager.putBlockPos(extra, "pos", trap.getBlockPos());
        GameRecordManager.recordItemUse(serverPlayer, HunterTrapItem.ID, null, extra);
    }

    private static void registerReplayFormatters() {
        ReplayRegistry.registerItemUseFormatter(HunterTrapItem.ID, (event, match, world) -> {
            NbtCompound data = event.data();
            if (!data.containsUuid("actor")) {
                return null;
            }
            Text actor = ReplayGenerator.formatPlayerName(
                    data.getUuid("actor"),
                    ReplayGenerator.getPlayerInfoCache(match)
            );
            String action = data.getString("action");
            String suffix = switch (action) {
                case "dismantle" -> "dismantle";
                case "pickup" -> "pickup";
                case "poison" -> "poison";
                default -> "place";
            };
            return Text.translatable("replay.item_use.sparkwitch.hunter_trap." + suffix, actor);
        });

        ReplayRegistry.registerItemUseFormatter(DoubleBarrelShotgunItem.ID, (event, match, world) -> {
            NbtCompound data = event.data();
            if (!data.containsUuid("actor")) {
                return null;
            }
            Text actor = ReplayGenerator.formatPlayerName(
                    data.getUuid("actor"),
                    ReplayGenerator.getPlayerInfoCache(match)
            );
            if ("reload".equals(data.getString("action"))) {
                return Text.translatable(
                        "replay.item_use.sparkwitch.double_barrel_shotgun.reload",
                        actor,
                        data.getInt("loaded_shells")
                );
            }
            if (data.containsUuid("target")) {
                Text target = ReplayGenerator.formatPlayerName(
                        data.getUuid("target"),
                        ReplayGenerator.getPlayerInfoCache(match)
                );
                return Text.translatable(
                        "replay.item_use.sparkwitch.double_barrel_shotgun.fire_hit",
                        actor,
                        target
                );
            }
            return Text.translatable("replay.item_use.sparkwitch.double_barrel_shotgun.fire", actor);
        });

        ReplayRegistry.registerGlobalEventFormatter(HunterTrapEntity.EVENT_TRIGGERED, (event, match, world) -> {
            NbtCompound data = event.data();
            if (!data.containsUuid("target")) {
                return null;
            }
            var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
            Text target = ReplayGenerator.formatPlayerName(data.getUuid("target"), playerInfo);
            boolean poisoned = data.getBoolean("poisoned");
            if (!data.containsUuid("actor")) {
                return Text.translatable(
                        poisoned
                                ? "replay.global.sparkwitch.hunter_trap_triggered.poisoned_no_owner"
                                : "replay.global.sparkwitch.hunter_trap_triggered.no_owner",
                        target
                );
            }
            Text owner = ReplayGenerator.formatPlayerName(data.getUuid("actor"), playerInfo);
            if (poisoned && data.containsUuid("poisoner")) {
                Text poisoner = ReplayGenerator.formatPlayerName(data.getUuid("poisoner"), playerInfo);
                return Text.translatable(
                        "replay.global.sparkwitch.hunter_trap_triggered.poisoned",
                        owner,
                        poisoner,
                        target
                );
            }
            return Text.translatable(
                    poisoned
                            ? "replay.global.sparkwitch.hunter_trap_triggered.owner_poisoned"
                            : "replay.global.sparkwitch.hunter_trap_triggered",
                    owner,
                    target
            );
        });
    }
}
