package dev.caecorthus.sparkwitch.roles.civilian.hanna;

import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.util.ShopEntry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 汉娜 runtime: the float window, the interruption penalty, income and the two shop rows.
 * 汉娜运行期：浮游窗口、被打断的惩罚、收入与两行商店。
 */
public final class HannaService {

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> FLOAT_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> PENALTY_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> UMASKERCHIEF_UNTIL = new HashMap<>();

    private HannaService() {
    }

    public static void register() {
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity p && role != null
                    && HannaRules.ROLE_ID.equals(role.identifier())) {
                COOLDOWN_UNTIL.put(p.getUuid(), p.getServer().getTicks() + (long) HannaRules.INITIAL_COOLDOWN_TICKS);
                // The float spends mana, so the mana system (and its HUD row) must be switched on.
                // 浮游要花魔力，必须打开魔力系统（以及它的 HUD 行）。
                var mana = dev.caecorthus.sparkwitch.component.WitchPlayerComponent.KEY.get(p);
                mana.initializeMana();
                // Her pool is a fixed 40 and never refills. 她的魔力是固定 40 点，永不回复。
                mana.addMana(HannaRules.INITIAL_MANA);
                // Point the panel at her skill so the mana row is rendered.
                // 让面板指向她的技能，魔力行才会渲染。
                mana.setActiveSkill(HannaRules.SKILL_ID);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(HannaService::tick);
        TaskComplete.EVENT.register((player, taskType) -> {
            if (player instanceof ServerPlayerEntity p && isHanna(p)) {
                PlayerShopComponent shop = PlayerShopComponent.KEY.get(p);
                shop.setBalance(shop.getBalance() + HannaRules.TASK_COINS);
            }
        });
        // Any hostile touch cancels the float and starts the penalty.
        // 任何外来交互都会打断浮游并开始惩罚。
        KillPlayer.BEFORE.register((victim, killer, deathReason) -> {
            if (victim instanceof ServerPlayerEntity p && isFloating(p)) {
                interrupt(p, killer);
            }
            return null;
        });
        BuildShopEntries.EVENT.register(HannaService::buildShop);
        // Non-lethal touches also break the float: knife hits and right-click interactions.
        // 非致命交互同样会打断浮游：刀的左键攻击与右键交互。
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (entity instanceof ServerPlayerEntity target && isFloating(target)) {
                interrupt(target, player instanceof ServerPlayerEntity p ? p : null);
            }
            return net.minecraft.util.ActionResult.PASS;
        });
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (entity instanceof ServerPlayerEntity target && isFloating(target)
                    && player instanceof ServerPlayerEntity p && p != target) {
                interrupt(target, p);
            }
            return net.minecraft.util.ActionResult.PASS;
        });
    }

    public static boolean isHanna(net.minecraft.entity.player.PlayerEntity player) {
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && HannaRules.ROLE_ID.equals(role.identifier());
    }

    public static boolean isFloating(ServerPlayerEntity player) {
        Long until = FLOAT_UNTIL.get(player.getUuid());
        return until != null && player.getServer().getTicks() < until;
    }

    /** Ability key: spend 40 mana for a 10 s float. 技能键：消耗 40 魔力换 10 秒浮游。 */
    public static void floatUp(ServerPlayerEntity player) {
        if (!isHanna(player) || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        // Cooldown is owned by the skill panel (WitchSkillUseService), not here: checking it again would always fail
        // because the service has already started the cooldown by the time the handler runs.
        // 冷却交给技能面板（WitchSkillUseService），这里不再检查：因为处理器运行时冷却已经开始计时，
        // 再查一次必然失败——这就是按下技能键没反应的原因。
        long now = player.getServer().getTicks();
        var witch = dev.caecorthus.sparkwitch.component.WitchPlayerComponent.KEY.get(player);
        if (witch.hasManaSystem() && !witch.spendMana(HannaRules.FLOAT_MANA)) {
            player.sendMessage(Text.translatable("message.sparkwitch.hanna.no_mana")
                    .formatted(Formatting.RED), true);
            return;
        }
        FLOAT_UNTIL.put(player.getUuid(), now + (long) HannaRules.FLOAT_TICKS);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, HannaRules.FLOAT_TICKS, 1, false, false, false));
        NbtCompound replay = new NbtCompound();
        replay.putString("action", "float");
        dev.doctor4t.wathe.record.GameRecordManager.recordSkillUse(player, HannaRules.ROLE_ID, null, replay);
    }

    /** Ends the float and starts the 10 s sanity drain. 结束浮游并开始 10 秒理智流失。 */
    public static void interrupt(ServerPlayerEntity player, ServerPlayerEntity interrupter) {
        FLOAT_UNTIL.remove(player.getUuid());
        PENALTY_UNTIL.put(player.getUuid(), player.getServer().getTicks() + (long) HannaRules.PENALTY_TICKS);
        player.sendMessage(Text.translatable("message.sparkwitch.hanna.interrupted")
                .formatted(Formatting.RED), true);
        NbtCompound replay = new NbtCompound();
        replay.putString("action", "interrupted");
        if (interrupter != null) {
            replay.putUuid("by", interrupter.getUuid());
        }
        dev.doctor4t.wathe.record.GameRecordManager.recordSkillUse(player, HannaRules.ROLE_ID, null, replay);
    }

    /** Handkerchief: 5 s of +6% sanity per second, and it clears the penalty. 手帕：5 秒每秒 +6% 理智，并解除惩罚。 */
    public static void useHandkerchief(ServerPlayerEntity player) {
        boolean cleared = PENALTY_UNTIL.remove(player.getUuid()) != null;
        UMASKERCHIEF_UNTIL.put(player.getUuid(), player.getServer().getTicks() + (long) HannaRules.HANDKERCHIEF_TICKS);
        NbtCompound replay = new NbtCompound();
        replay.putString("action", cleared ? "handkerchief_clear" : "handkerchief");
        dev.doctor4t.wathe.record.GameRecordManager.recordSkillUse(player, HannaRules.ROLE_ID, null, replay);
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTicks();
        for (ServerWorld world : server.getWorlds()) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (!isHanna(player)) {
                    COOLDOWN_UNTIL.remove(player.getUuid());
                    FLOAT_UNTIL.remove(player.getUuid());
                    PENALTY_UNTIL.remove(player.getUuid());
                    continue;
                }
                // Natural income every 30 s. 每 30 秒自然收入。
                if (now % HannaRules.PASSIVE_INTERVAL_TICKS == 0L && now > 0L) {
                    PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
                    shop.setBalance(shop.getBalance() + HannaRules.PASSIVE_COINS);
                }
                // Penalty drains 5% per second. 惩罚每秒 -5% 理智。
                Long penaltyUntil = PENALTY_UNTIL.get(player.getUuid());
                if (penaltyUntil != null) {
                    if (now >= penaltyUntil) {
                        PENALTY_UNTIL.remove(player.getUuid());
                    } else if (now % 20L == 0L) {
                        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
                        mood.setMood(Math.max(-1.0F, mood.getMood() - HannaRules.PENALTY_SANITY_PER_SECOND));
                    }
                }
                // Handkerchief regeneration. 手帕回理智。
                Long hk = UMASKERCHIEF_UNTIL.get(player.getUuid());
                if (hk != null) {
                    if (now >= hk) {
                        UMASKERCHIEF_UNTIL.remove(player.getUuid());
                    } else if (now % 20L == 0L) {
                        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
                        mood.setMood(Math.min(1.0F, mood.getMood() + HannaRules.HANDKERCHIEF_SANITY_PER_SECOND));
                    }
                }
                // HUD numbers. 同步给客户端显示。
                Long cd = COOLDOWN_UNTIL.get(player.getUuid());
                Long fl = FLOAT_UNTIL.get(player.getUuid());
                int cdSec = cd == null ? 0 : (int) Math.max(0, Math.ceil((cd - now) / 20.0));
                int flSec = fl == null ? 0 : (int) Math.max(0, Math.ceil((fl - now) / 20.0));
                int penSec = penaltyUntil == null ? 0 : (int) Math.max(0, Math.ceil((penaltyUntil - now) / 20.0));
                HannaComponent.KEY.get(player).set(cdSec, flSec, penSec);
                if (penSec > 0 && now % 20L == 0L) {
                    player.sendMessage(Text.translatable("message.sparkwitch.hanna.penalty", penSec)
                            .formatted(Formatting.RED), true);
                }
            }
        }
    }

    /** Umbrella 150, handkerchief 50. 洋伞 150，手帕 50。 */
    private static void buildShop(net.minecraft.entity.player.PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!isHanna(player)) {
            return;
        }
        ItemStack umbrella = new ItemStack(dev.caecorthus.sparkwitch.SparkWitchItems.hannaUmbrella());
        umbrella.set(DataComponentTypes.CUSTOM_NAME, Text.translatable("item.sparkwitch.hanna_umbrella"));
        context.addEntry(new ShopEntry.Builder("sparkwitch:hanna_umbrella", umbrella,
                HannaRules.UMBRELLA_PRICE, ShopEntry.Type.TOOL).cooldown(0).build());
        ItemStack hk = new ItemStack(dev.caecorthus.sparkwitch.SparkWitchItems.hannaHandkerchief());
        hk.set(DataComponentTypes.CUSTOM_NAME, Text.translatable("item.sparkwitch.hanna_handkerchief"));
        context.addEntry(new ShopEntry.Builder("sparkwitch:hanna_handkerchief", hk,
                HannaRules.HANDKERCHIEF_PRICE, ShopEntry.Type.TOOL).cooldown(0).build());
    }
}
