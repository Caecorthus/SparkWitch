package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.caecorthus.sparkwitch.compat.NoellesHiddenBodiesBridge;
import dev.caecorthus.sparkwitch.compat.SparkStrengthPartygoerCompat;
import dev.caecorthus.sparkwitch.compat.SparkTraitsBodyDragBridge;
import dev.caecorthus.sparkwitch.roles.killer.hiro.returning.HiroFakeBodies;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianDecoyBodies;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanFeignService;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendFakeBodies;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssSuppression;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheEntities;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.agmas.noellesroles.ModEffects;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 幽灵的加护 Ghost's Blessing (the Medium's SparkWitch skill-key skill), server-authoritative. The empty
 * {@link UseMediumBlessingC2SPacket} is the paid act (there is no input box): it re-checks every gate, charges 通灵's
 * live price + 100, starts both cooldowns and opens the world vote window in {@link MediumWindowKind#BLESSING} mode.
 * The dead then pick one of the four {@link MediumBlessing}s for 20 s with the same weights and tie rule as 通灵; when
 * nobody voted a random available blessing is granted instead (owner rule). The blessing lands only on a caster who is
 * still online, still the Medium and alive.
 * 幽灵的加护（通灵师的 SparkWitch 技能键技能），服务端权威。空的 {@link UseMediumBlessingC2SPacket} 即付费动作（没有输入框）：
 * 重新复核所有门槛、扣除通灵实时价格 +100、同时进入两个冷却，并以 {@link MediumWindowKind#BLESSING} 模式开启世界级投票窗口。
 * 随后死者在 20 秒内从四种 {@link MediumBlessing} 中选一种，票权与平票规则与通灵相同；无人投票时改为随机给予一种可用加护
 * （所有者规则）。只有仍在线、仍是通灵师且存活的施放者才会获得加护。
 */
public final class MediumBlessingService {
    /** Replay data of the grant entry; absent on the cast entry. / 获得加护那条回放记录的数据；施放那条没有。 */
    private static final String REPLAY_BLESSING = "blessing";
    private static final String REPLAY_RANDOM = "random";

    private static boolean registered;

    private MediumBlessingService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerSkillFormatter(MediumRules.BLESSING_SKILL_ID, MediumBlessingService::formatBlessing);
    }

    /**
     * Server entry of the skill key and the paid act at once: every gate first (the shared caster set, no live window
     * of either kind, the blessing cooldown, the price), Grand Witch Fear last, then the charge.
     * 技能键的服务端入口，同时也是付费动作：先过所有门槛（共用施放者条件、没有任何种类的进行中窗口、加护冷却、价格），
     * 最后检查大魔女恐惧，然后扣费。
     */
    public static void use(ServerPlayerEntity caster) {
        if (caster == null || !MediumSkillService.mayUse(caster)) {
            return;
        }
        MediumSeanceComponent seance = MediumSeanceComponent.KEY.get(caster.getWorld());
        if (seance.isActive()) {
            return;
        }
        MediumPlayerComponent component = MediumPlayerComponent.KEY.get(caster);
        if (component.getBlessingCooldownTicks() > 0) {
            return;
        }
        int tier = MediumRules.currentTier(GameWorldComponent.KEY.get(caster.getWorld()));
        int price = MediumRules.blessingPrice(tier);
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(caster);
        if (shop.getBalance() < price || MediumSkillService.deniedByFear(caster)) {
            return;
        }
        shop.addToBalance(-price);
        // A paid 加护 starts the 通灵 cooldown too (and the other way round). / 付费加护同时开始通灵冷却（反之亦然）。
        component.startCooldowns(tier);
        seance.startBlessing(caster.getUuid(), disabledMask(caster), component.getBoundTargets());
        GameRecordManager.recordSkillUse(caster, MediumRules.BLESSING_SKILL_ID, null, null);
    }

    /**
     * Decided once at window start and frozen in the window. Owner rule: 回满 san is greyed out for a Medium without
     * real sanity, i.e. a SparkTraits Impostor (FAKE mood); the shared rule fails closed on an unknown trait answer.
     * 在窗口开启时一次性决定并冻结在窗口中。所有者规则：通灵师没有真实理智（即 SparkTraits 内鬼，伪装理智）时「回满 san」
     * 置灰；共用规则在词条查询结果未知时失败关闭。
     */
    static int disabledMask(ServerPlayerEntity caster) {
        return AbyssSuppression.hasRealSanity(caster) ? 0 : MediumBlessing.SANITY.mask();
    }

    /**
     * End of a blessing window, called from {@link MediumSkillService#onSeanceEnded} (which clears the window
     * afterwards). {@code bound} already holds the frozen bindings plus those bought during the window. A caster who
     * is no longer the Medium or no longer alive gets nothing and is told nothing.
     * 加护窗口结束，由 {@link MediumSkillService#onSeanceEnded} 调用（随后由它清空窗口）。{@code bound} 已包含冻结的绑定与
     * 窗口期间购买的绑定。不再是通灵师或已不存活的施放者什么也得不到，也不会收到任何消息。
     */
    static void onWindowEnded(ServerWorld world, MediumSeanceComponent seance, ServerPlayerEntity caster,
                              Set<UUID> bound) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (!MediumRules.isMedium(game.getRole(caster)) || !GameFunctions.isPlayerPlayingAndAlive(caster)) {
            return;
        }
        Map<UUID, Integer> votes = seance.getVotes();
        // Disabled choices never enter the map (recordVote drops them); a voter no longer in the dead set is dropped
        // here, as for 通灵.
        // 已禁用的选项不会进入映射（recordVote 会丢弃）；已不在死亡集合中的投票者在此被剔除，与通灵相同。
        MediumTally tally = MediumTally.count(votes, game::isPlayerDead, bound::contains, MediumBlessing.count());
        MediumBlessing voted = tally.hasWinner() ? MediumBlessing.byIndex(tally.winner()) : null;
        boolean random = voted == null;
        List<UUID> corpses = realCorpses(world, game);
        MediumBlessing blessing = random ? randomBlessing(caster, seance, !corpses.isEmpty()) : voted;
        if (blessing != null) {
            grant(caster, blessing, corpses);
            NbtCompound extra = new NbtCompound();
            extra.putInt(REPLAY_BLESSING, blessing.index());
            extra.putBoolean(REPLAY_RANDOM, random);
            GameRecordManager.recordSkillUse(caster, MediumRules.BLESSING_SKILL_ID, null, extra);
        }
        if (!ServerPlayNetworking.canSend(caster, MediumSeanceResultS2CPacket.ID)) {
            return;
        }
        // Which blessing landed (and that it was drawn at random) always goes out. The tie note and the bound targets'
        // own picks are extra knowledge, sent only once deaths are over half, as for 通灵.
        // 获得了哪种加护（以及是否随机抽取）始终发送。平票提示与绑定目标各自的选择属于额外情报，与通灵一样只有死者过半时才发送。
        boolean detail = !random && MediumRules.deathsOverHalf(game);
        ServerPlayNetworking.send(caster, MediumSeanceResultS2CPacket.ofBlessing(
                blessing,
                random,
                detail && tally.tie(),
                detail ? MediumSkillService.boundAnswers(game, bound, votes) : List.of()));
    }

    /**
     * Owner rule: with no valid vote a random available blessing is granted. Available means not disabled in this
     * window; 尸体透视 is left out when there is no real corpse to show. Null when nothing is left.
     * 所有者规则：没有任何有效票时随机给予一种可用加护。可用指本窗口未禁用；没有真实尸体可显示时不抽尸体透视。
     * 一种都不剩时返回 null。
     */
    private static @Nullable MediumBlessing randomBlessing(ServerPlayerEntity caster, MediumSeanceComponent seance,
                                                         boolean anyCorpse) {
        List<MediumBlessing> pool = new ArrayList<>(MediumBlessing.count());
        for (MediumBlessing blessing : MediumBlessing.values()) {
            if (!seance.isDisabled(blessing) && (blessing != MediumBlessing.CORPSE_SIGHT || anyCorpse)) {
                pool.add(blessing);
            }
        }
        return pool.isEmpty() ? null : pool.get(caster.getRandom().nextInt(pool.size()));
    }

    private static void grant(ServerPlayerEntity caster, MediumBlessing blessing, List<UUID> corpses) {
        switch (blessing) {
            // Ambient off, particles off, icon on (the Noa / Fisher precedent). / 非环境、无粒子、显示图标（诺亚、钓鱼佬先例）。
            case SPEED -> caster.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SPEED, MediumRules.BLESSING_SPEED_TICKS, 0, false, false, true));
            case SHIELD -> addRoundLongShieldLayer(caster);
            case CORPSE_SIGHT -> MediumPlayerComponent.KEY.get(caster).addCorpseSightBodies(corpses);
            // The setter clamps to the true maximum (1.0, or 1.25 under SparkTraits Steady).
            // setter 会钳制到真正的最大值（1.0，SparkTraits 沉稳下为 1.25）。
            case SANITY -> PlayerMoodComponent.KEY.get(caster).setMood(MediumRules.BLESSING_SANITY_REQUEST);
        }
    }

    /**
     * One NoellesRoles whiskey shield layer that lasts the whole round (owner rule). NoellesRoles'
     * {@code WhiskeyShieldEffect.addShieldLayer} keeps an existing stack's remaining time, so a blessing layer added
     * on top of a 20 s whiskey would expire with it; the stack is rebuilt by hand instead with one more layer
     * (amplifier + 1) and an infinite duration, in NoellesRoles' own flags. The whole stack shares one duration, so
     * layers already there (and any whiskey drunk later) become round-long too. Wathe clears status effects at the
     * round edges.
     * 一层持续整局的 NoellesRoles 威士忌护盾（所有者规则）。NoellesRoles 的 {@code WhiskeyShieldEffect.addShieldLayer}
     * 会沿用已有层数的剩余时间，叠在 20 秒威士忌之上的加护层会随之过期；因此改为手动重建：层数 +1（等级 +1）、无限时长，
     * 沿用 NoellesRoles 自身的标志位。整叠护盾共用一个时长，因此已有的层（以及之后喝下的威士忌）同样变为整局有效。
     * Wathe 在回合节点会清除状态效果。
     */
    private static void addRoundLongShieldLayer(ServerPlayerEntity caster) {
        StatusEffectInstance existing = caster.getStatusEffect(ModEffects.WHISKEY_SHIELD);
        int amplifier = existing == null ? 0 : existing.getAmplifier() + 1;
        if (existing != null) {
            caster.removeStatusEffect(ModEffects.WHISKEY_SHIELD);
        }
        caster.addStatusEffect(new StatusEffectInstance(
                ModEffects.WHISKEY_SHIELD, StatusEffectInstance.INFINITE, amplifier, false, false, true));
    }

    /**
     * Entity UUIDs of the REAL corpses in the caster's world right now: the Prophet's Death Sense filter (hidden
     * bodies, SparkTraits fake-death bodies, Fiend and Hiro fake corpses, Partygoer corpses, Magician decoys) plus the
     * Charon's feign corpses, and the owner must be in Wathe's dead set, which also drops any stand-in corpse of a
     * living player.
     * 施放者所在世界此刻的真实尸体的实体 UUID：沿用先知死亡感知的过滤（被隐藏的尸体、SparkTraits 假死尸体、魔人与希罗的
     * 假尸体、派对客尸体、魔术师诱饵），再排除卡戎的假死尸体，并要求尸体主人在 Wathe 的死亡集合中，这同时排除了活人的任何
     * 替身尸体。
     */
    static List<UUID> realCorpses(ServerWorld world, GameWorldComponent game) {
        List<? extends PlayerBodyEntity> bodies = world.getEntitiesByType(
                WatheEntities.PLAYER_BODY,
                body -> !body.isRemoved()
                        && body.getPlayerUuid() != null
                        && game.isPlayerDead(body.getPlayerUuid())
                        && !NoellesHiddenBodiesBridge.isHidden(world, body.getPlayerUuid())
                        && !SparkTraitsBodyDragBridge.isConfirmedFakeDeathBody(body)
                        && !FiendFakeBodies.isFake(body)
                        && !HiroFakeBodies.isFake(body)
                        && !FerrymanFeignService.isFakeBody(body)
                        && !SparkStrengthPartygoerCompat.isPartygoerCorpse(body)
                        && !MagicianDecoyBodies.isDecoy(body)
        );
        List<UUID> uuids = new ArrayList<>(bodies.size());
        for (PlayerBodyEntity body : bodies) {
            uuids.add(body.getUuid());
        }
        return uuids;
    }

    /**
     * Two lines under one skill id: the cast (no {@code blessing} data) and the grant (with it).
     * 同一技能 id 下的两种回放行：施放（没有 {@code blessing} 数据）与获得加护（带有该数据）。
     */
    private static @Nullable Text formatBlessing(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid("actor"), ReplayGenerator.getPlayerInfoCache(match));
        if (!data.contains(REPLAY_BLESSING, NbtElement.NUMBER_TYPE)) {
            return Text.translatable("replay.skill.sparkwitch.medium_blessing_skill", actor);
        }
        MediumBlessing blessing = MediumBlessing.byIndex(data.getInt(REPLAY_BLESSING));
        if (blessing == null) {
            return null;
        }
        return Text.translatable(
                "replay.skill.sparkwitch.medium_blessing_skill.granted",
                actor,
                Text.translatable(blessing.translationKey())
        );
    }
}
