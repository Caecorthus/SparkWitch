package dev.caecorthus.sparkwitch.roles.neutral.koko;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.neutral.koko.net.KokoCorpseStreamC2SPayload;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.CheckWinCondition;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 泽渡可可 runtime: live window, traffic, corpse stream, shop, win check. 泽渡可可运行期。 */
public final class KokoService {
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> BUFF_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> LIGHTSTICK_UNTIL = new HashMap<>();
    private static final Map<UUID, Boolean> WAS_STREAMING = new HashMap<>();
    private static final java.util.Set<UUID> PENDING_BODIES = new java.util.HashSet<>();
    private static boolean registered;

    private KokoService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(KokoCorpseStreamC2SPayload.ID, KokoCorpseStreamC2SPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(KokoCorpseStreamC2SPayload.ID,
                (payload, context) -> streamCorpse(context.player()));
        KokoReplay.register();
        WitchSkillRegistry.register(new WitchSkillDefinition(
                KokoRules.STREAM_SKILL_ID, KokoRules.COLOR, 1, KokoRules.INITIAL_COOLDOWN_TICKS,
                KokoRules.COOLDOWN_TICKS, KokoRules.STREAM_MANA,
                context -> isKoko(context.role()), context -> startStream(context.player())));
        RoleAssigned.EVENT.register((player, role) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            KokoComponent.KEY.get(serverPlayer).clear();
            COOLDOWN_UNTIL.remove(serverPlayer.getUuid());
            WAS_STREAMING.remove(serverPlayer.getUuid());
            if (isKoko(role)) {
                COOLDOWN_UNTIL.put(serverPlayer.getUuid(),
                        serverPlayer.getWorld().getTime() + KokoRules.INITIAL_COOLDOWN_TICKS);
                WitchPlayerComponent mana = WitchPlayerComponent.KEY.get(serverPlayer);
                mana.initializeMana();
                mana.setActiveSkill(KokoRules.STREAM_SKILL_ID);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(KokoService::tick);
        TaskComplete.EVENT.register((player, taskType) -> {
            if (player instanceof ServerPlayerEntity p && isKoko(GameWorldComponent.KEY.get(p.getWorld()).getRole(p))) {
                WitchPlayerComponent taskMana = WitchPlayerComponent.KEY.get(p);
                if (taskMana.getMana() < KokoRules.MAX_MANA) {
                    taskMana.addMana(Math.min(KokoRules.FAKE_TASK_MANA, KokoRules.MAX_MANA - taskMana.getMana()));
                }
                PlayerShopComponent shop = PlayerShopComponent.KEY.get(p);
                shop.setBalance(shop.getBalance() + KokoRules.FAKE_TASK_COINS);
            }
        });
        // Both sides build the shop from this event (see SpecterShopService): gating on ServerPlayerEntity
        // left the client with an empty list, which is why Koko could not buy or sell anything.
        // 商店由双端通过该事件共同构建（参见 SpecterShopService）：只放行 ServerPlayerEntity 会让客户端拿到空列表，
        // 这正是可可无法买卖任何物品的原因。
        BuildShopEntries.EVENT.register((player, context) -> {
            if (!isKoko(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
                return;
            }
            // Koko sells exactly these three (owner spec); Wathe's neutral default list would otherwise leak in.
            // 可可只卖这三样（所有者规格）；否则 Wathe 的中立默认条目会混进来。
            context.clearEntries();
            // Owner 2026-11-13: sell Wathe's own knife and cap every item at one purchase.
            // 所有者 2026-11-13：刀改用 Wathe 原生刀，三样每样限购 1。
            context.addEntry(new ShopEntry.Builder("knife", new ItemStack(WatheItems.KNIFE),
                    KokoRules.KNIFE_PRICE, ShopEntry.Type.WEAPON)
                    .cooldown(0).stock(1).build());
            context.addEntry(new ShopEntry.Builder("sparkwitch:koko_lightstick",
                    new ItemStack(SparkWitchItems.kokoLightstick()), KokoRules.LIGHTSTICK_PRICE, ShopEntry.Type.TOOL)
                    .cooldown(0).stock(1).build());
            context.addEntry(new ShopEntry.Builder("lockpick", new ItemStack(WatheItems.LOCKPICK),
                    KokoRules.LOCKPICK_PRICE, ShopEntry.Type.TOOL)
                    .cooldown(0).stock(1).build());
        });
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            if (killer == null
                    || !isKoko(GameWorldComponent.KEY.get(killer.getWorld()).getRole(killer))) {
                return;
            }
            KokoComponent.KEY.get(killer).addTraffic(KokoRules.KILL_TRAFFIC);
            // The body spawns after this event, so remember who died and highlight the body on the next tick.
            // 尸体在本事件之后才生成，先记下死者，下一 tick 再高亮尸体。
            PENDING_BODIES.add(victim.getUuid());
        });
        CheckWinCondition.EVENT.register((world, gameComponent, currentStatus) -> {
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (isKoko(gameComponent.getRole(p))) {
                    KokoComponent comp = KokoComponent.KEY.get(p);
                    if (comp.isStreaming() && comp.traffic() >= KokoRules.WIN_TRAFFIC) {
                        return CheckWinCondition.WinResult.neutralWin(p, List.of(p));
                    }
                }
            }
            return null;
        });
    }

    /** Skill-panel handler: deduct mana, open the live window. 技能面板处理器：扣魔力、开直播窗口。 */
    private static WitchSkillUseResult startStream(ServerPlayerEntity player) {
        long now = player.getWorld().getTime();
        KokoComponent comp = KokoComponent.KEY.get(player);
        // Mana is deducted here: WitchSkillUseService never charges the handler's role.
        // 魔力在这里扣：WitchSkillUseService 不会替处理器扣自己职业的魔力。
        if (!WitchPlayerComponent.KEY.get(player).spendMana(KokoRules.STREAM_MANA)) {
            return WitchSkillUseResult.fail("message.sparkwitch.koko.not_enough_mana");
        }
        // She glows for 10 s; the through-wall sight of others lasts the full 20 s.
        // 自己高亮 10 秒；对别人的穿墙透视持续整段 20 秒。
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING,
                KokoRules.SELF_HIGHLIGHT_TICKS, 0, false, false, false));
        playCue(player, SoundEvents.BLOCK_BEACON_ACTIVATE, 0.8F, 1.2F);
        comp.setStreamEndTick(now + KokoRules.STREAM_TICKS);
        comp.setCooldownEndTick(now + KokoRules.COOLDOWN_TICKS);
        COOLDOWN_UNTIL.put(player.getUuid(), now + KokoRules.COOLDOWN_TICKS);
        KokoReplay.record(player, "stream_start");
        return WitchSkillUseResult.success(KokoRules.COOLDOWN_TICKS, "message.sparkwitch.koko.stream_start");
    }

    /** Second key: stream the corpse in front. 第二技能键：直播正前方的尸体。 */
    public static void streamCorpse(ServerPlayerEntity player) {
        if (!isKoko(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        long now = player.getWorld().getTime();
        Long until = COOLDOWN_UNTIL.get(player.getUuid());
        if (until != null && now < until) {
            player.sendMessage(Text.translatable("message.sparkwitch.koko.cooldown", (until - now) / 20L), true);
            return;
        }
        HitResult hit = player.raycast(KokoRules.CORPSE_RANGE, 1.0F, false);
        if (!(hit instanceof EntityHitResult entityHit)) {
            player.sendMessage(Text.translatable("message.sparkwitch.koko.no_corpse"), true);
            return;
        }
        Entity target = entityHit.getEntity();
        if (!(target instanceof PlayerBodyEntity)) {
            player.sendMessage(Text.translatable("message.sparkwitch.koko.no_corpse"), true);
            return;
        }
        ((PlayerBodyEntity) target).addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING,
                KokoRules.CORPSE_HIGHLIGHT_TICKS, 0, false, false, false));
        KokoComponent.KEY.get(player).addTraffic(KokoRules.CORPSE_TRAFFIC);
        COOLDOWN_UNTIL.put(player.getUuid(), now + KokoRules.CORPSE_COOLDOWN_TICKS);
        KokoComponent.KEY.get(player).setCooldownEndTick(now + KokoRules.CORPSE_COOLDOWN_TICKS);
        playCue(player, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.6F, 1.4F);
        KokoReplay.record(player, "corpse_stream");
        player.sendMessage(Text.translatable("message.sparkwitch.koko.corpse_stream", KokoRules.CORPSE_TRAFFIC), true);
    }

    /** Lightstick: 10 s Speed II and doubled traffic. 应援棒：10 秒速度 II、流量翻倍。 */
    public static void useLightstick(ServerPlayerEntity player) {
        long now = player.getWorld().getTime();
        Long until = LIGHTSTICK_UNTIL.get(player.getUuid());
        if (until != null && now < until) {
            player.sendMessage(Text.translatable("message.sparkwitch.koko.lightstick_cooldown",
                    (until - now + 19L) / 20L), true);
            return;
        }
        LIGHTSTICK_UNTIL.put(player.getUuid(), now + KokoRules.LIGHTSTICK_COOLDOWN_TICKS);
        playCue(player, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.8F, 1.6F);
        BUFF_UNTIL.put(player.getUuid(), now + KokoRules.LIGHTSTICK_BUFF_TICKS);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,
                KokoRules.LIGHTSTICK_BUFF_TICKS, 1, false, false, false));
        KokoReplay.record(player, "lightstick");
    }

    private static void tick(MinecraftServer server) {
        // Highlight bodies of players Koko killed. 高亮可可爱击杀玩家的尸体。
        if (!PENDING_BODIES.isEmpty()) {
            for (var world : server.getWorlds()) {
                for (var entity : world.iterateEntities()) {
                    if (entity instanceof PlayerBodyEntity body
                            && PENDING_BODIES.remove(body.getPlayerUuid())) {
                        body.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING,
                                KokoRules.KILL_HIGHLIGHT_TICKS, 0, false, false, false));
                    }
                }
            }
            PENDING_BODIES.clear();
        }
        for (var world : server.getWorlds()) {
            long now = world.getTime();
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (!isKoko(GameWorldComponent.KEY.get(world).getRole(player))) {
                    WAS_STREAMING.remove(player.getUuid());
                    continue;
                }
                KokoComponent comp = KokoComponent.KEY.get(player);
                if (now % 20L == 0L) {
                    WitchPlayerComponent mana = WitchPlayerComponent.KEY.get(player);
                    if (mana.getMana() < KokoRules.MAX_MANA) {
                        mana.addMana(Math.min(KokoRules.MANA_PER_SECOND, KokoRules.MAX_MANA - mana.getMana()));
                    }
                    comp.sync();
                }
                // Neutral roles get no task income in every round, so pay a steady wage (Hanna precedent).
                // 中立职业并非每局都有任务收入，因此给固定工资（沿用汉娜的先例）。
                if (now % (30L * 20L) == 0L) {
                    PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
                    shop.setBalance(shop.getBalance() + KokoRules.NATURAL_COINS);
                }
                boolean streaming = comp.isStreaming();
                boolean was = Boolean.TRUE.equals(WAS_STREAMING.put(player.getUuid(), streaming));
                if (was && !streaming) {
                    KokoReplay.record(player, "end_stream");
                }
                if (streaming && now % 20L == 0L) {
                    boolean doubled = BUFF_UNTIL.getOrDefault(player.getUuid(), 0L) > now;
                    comp.addTraffic(KokoRules.STREAM_TRAFFIC_PER_SECOND * (doubled ? 2 : 1));
                }
                if (comp.traffic() >= KokoRules.SPEED_TRAFFIC) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,
                            40, 2, false, false, false));
                }
            }
        }
    }

    /** Owner 2026-11-12: every skill cue is audible to nearby players. / 所有者 2026-11-12：每个技能都有周边可听见的音效。 */
    private static void playCue(ServerPlayerEntity player, net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, SoundCategory.PLAYERS, volume, pitch);
    }

    public static boolean isKoko(dev.doctor4t.wathe.api.Role role) {
        return role != null && KokoRules.ROLE_ID.equals(role.identifier());
    }
}
