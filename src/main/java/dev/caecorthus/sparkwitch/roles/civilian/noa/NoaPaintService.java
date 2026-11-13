package dev.caecorthus.sparkwitch.roles.civilian.noa;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import org.agmas.noellesroles.effect.WhiskeyShieldEffect;

/**
 * Server side of Noa's painting ({@code sparkwitch:use_noa_paint}): gates the request ({@link NoaPaintRules}), restarts
 * the cooldown in {@link NoaComponent}, rolls {@link NoaPaintTable} with her current sanity and this round's painted
 * one-offs, applies the outcome, and records the replay. A refused request changes nothing and costs nothing. Sanity
 * outcomes tell her only the direction, never an amount (owner 2026-11-04: her sanity value is never shown).
 * 诺亚作画（{@code sparkwitch:use_noa_paint}）的服务端部分：先判定请求（{@link NoaPaintRules}），在 {@link NoaComponent} 中
 * 重新开始冷却，按当前理智与本局已画出的一次性结果抽取 {@link NoaPaintTable}，执行结果并记录回放。被拒绝的请求不改变
 * 任何东西，也不消耗冷却。理智类结果只告诉她方向，从不给出数值（所有者 2026-11-04：从不显示她的理智值）。
 */
public final class NoaPaintService {
    private NoaPaintService() {
    }

    /**
     * Called on the server thread for every painting request. The stun, Seeker-session, Riftwalker-session, Knight duel
     * and Fear payload guards already drop most refused requests; this re-checks every gate against server state.
     * 每个作画请求都在服务端线程调用。眩晕、搜寻者会话、隙行者会话、骑士决斗与恐惧的数据包拦截已丢弃大多数被拒请求；
     * 此处按服务端状态重新检查每个门槛。
     */
    public static void tryUse(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        long now = world.getTime();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        NoaComponent state = NoaComponent.KEY.get(player);
        NoaPaintRules.Verdict verdict = NoaPaintRules.verdict(
                NoaRules.isNoa(game.getRole(player)),
                game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player) && !player.isSpectator(),
                NoellesTaotieSeekerBridge.isSwallowed(player),
                ControlExpertStun.isStunned(player),
                SparkTraitsKillerBridge.isRoleSkillBlocked(player),
                state.paintCooldownRemaining(now) <= 0,
                GrandWitchFearService.isPlayerFeared(player));
        switch (verdict) {
            case USE -> paint(player, world, state, now);
            case FEARED -> GrandWitchFearService.sendSkillBlocked(player);
            case REFUSE -> {
            }
        }
    }

    private static void paint(ServerPlayerEntity player, ServerWorld world, NoaComponent state, long now) {
        state.setPaintReadyTick(now + NoaRules.COOLDOWN_TICKS);
        float sanity = PlayerMoodComponent.KEY.get(player).getMood();
        NoaPaintTable.Outcome outcome = NoaPaintTable.pick(
                NoaPaintTable.table(sanity, state.paintedOnce()), player.getRandom().nextDouble());
        state.markPainted(outcome);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_HONEY_BLOCK_SLIDE,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
        switch (outcome) {
            case DOG -> giveItem(player, outcome, Registries.ITEM.get(NoaRules.DOG_PORTRAIT_ITEM_ID));
            case KEY -> giveItem(player, outcome, SparkWitchItems.spareKey());
            case SPYGLASS -> giveItem(player, outcome, Items.SPYGLASS);
            case FIRECRACKER -> giveItem(player, outcome, WatheItems.FIRECRACKER);
            case NOTE -> giveItem(player, outcome, WatheItems.NOTE);
            case KNIFE -> giveItem(player, outcome, WatheItems.KNIFE);
            case REVOLVER -> giveItem(player, outcome, WatheItems.REVOLVER);
            case CROWBAR -> giveItem(player, outcome, WatheItems.CROWBAR);
            case SPEED_II -> speed(player, outcome, 1);
            case SPEED_III -> speed(player, outcome, 2);
            case WHISKEY -> {
                // NoellesRoles' own layer (stacks like drinking whiskey), 20 s, without the whiskey's mood bonus.
                // NoellesRoles 自己的护盾层（与喝威士忌一样叠层），20 秒，不带威士忌的理智加成。
                WhiskeyShieldEffect.addShieldLayer(player, NoaRules.WHISKEY_SHIELD_TICKS);
                tell(player, "message.sparkwitch.noa.paint.whiskey");
                NoaReplay.recordEffect(player, outcome);
            }
            case SANITY_LOSS_HIGH -> changeSanity(player, outcome, -percent(player, 10, 50));
            case SANITY_LOSS_LOW -> changeSanity(player, outcome, -percent(player, 10, 30));
            case SANITY_GAIN_LOW -> changeSanity(player, outcome, percent(player, 10, 30));
        }
    }

    private static int percent(ServerPlayerEntity player, int min, int max) {
        return min + player.getRandom().nextInt(max - min + 1);
    }

    private static void giveItem(ServerPlayerEntity player, NoaPaintTable.Outcome outcome, Item item) {
        if (item == Items.AIR) {
            return;
        }
        ItemStack stack = new ItemStack(item);
        Text name = stack.getName();
        placeInHotbar(player, stack);
        player.sendMessage(Text.translatable("message.sparkwitch.noa.paint.item", name)
                .formatted(Formatting.AQUA), true);
        NoaReplay.recordItem(player, outcome, item);
    }

    private static void speed(ServerPlayerEntity player, NoaPaintTable.Outcome outcome, int amplifier) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, NoaRules.SPEED_TICKS, amplifier,
                false, false, true));
        tell(player, amplifier >= 2 ? "message.sparkwitch.noa.paint.speed3" : "message.sparkwitch.noa.paint.speed2");
        NoaReplay.recordEffect(player, outcome);
    }

    /**
     * Ordinary {@code setMood}: Wathe clamps to -1..1 and its breakdown check still applies next tick. The message
     * names only the direction; the replay keeps the amount.
     * 普通 {@code setMood}：Wathe 自行限制在 -1..1，下一刻其精神崩溃判定照常生效。提示只说方向；回放保留数值。
     */
    private static void changeSanity(ServerPlayerEntity player, NoaPaintTable.Outcome outcome, int deltaPercent) {
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        mood.setMood(mood.getMood() + deltaPercent / 100.0F);
        tell(player, deltaPercent > 0 ? "message.sparkwitch.noa.paint.sanity_up"
                : "message.sparkwitch.noa.paint.sanity_down");
        NoaReplay.recordSanity(player, outcome, deltaPercent);
    }

    private static void tell(ServerPlayerEntity player, String key) {
        player.sendMessage(Text.translatable(key).formatted(Formatting.AQUA), true);
    }

    /**
     * Hotbar first, never a hidden row: a free hotbar slot, else the selected slot, whose displaced item moves to a
     * free inventory slot, then an empty off hand, or is dropped at her feet, never deleted (the Recruit knife
     * precedent).
     * 优先快捷栏，绝不放进隐藏行：空的快捷栏格，否则放进当前选中格；被替换的物品移到背包空位，其次是空着的副手，否则掉落在
     * 她脚下，绝不删除（沿用新兵发刀的先例）。
     */
    private static void placeInHotbar(ServerPlayerEntity player, ItemStack stack) {
        PlayerInventory inventory = player.getInventory();
        int empty = inventory.getEmptySlot();
        if (PlayerInventory.isValidHotbarIndex(empty)) {
            inventory.setStack(empty, stack);
        } else {
            ItemStack displaced = inventory.getStack(inventory.selectedSlot);
            inventory.setStack(inventory.selectedSlot, stack);
            if (!displaced.isEmpty() && !player.giveItemStack(displaced)) {
                if (player.getOffHandStack().isEmpty()) {
                    player.setStackInHand(Hand.OFF_HAND, displaced);
                } else {
                    player.dropItem(displaced, false, true);
                }
            }
        }
        inventory.markDirty();
        player.currentScreenHandler.sendContentUpdates();
    }
}
