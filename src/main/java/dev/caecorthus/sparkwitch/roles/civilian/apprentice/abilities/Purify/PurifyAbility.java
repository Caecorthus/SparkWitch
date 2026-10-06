package dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Purify;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.ApprenticePlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.ApprenticeRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchTargeting;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Purify (净化), the graduated Apprentice's fixed second skill on the secondary key (owner 2026-10-06 D3). It is not
 * in {@code WitchSkillRegistry}, so the random draw can never hand it out; it keeps its own cooldown in
 * {@link ApprenticePlayerComponent}. The aimed player within 6 blocks, or herself when she aims at no one and holds a
 * factor, loses their Witch Factor; she gains 30 mana. A target without a factor still costs mana and cooldown: the
 * "no factor" answer is information.
 * 净化：出师后预备魔女固定的第二技能，使用副技能键（所有者 2026-10-06 D3）。它不在 {@code WitchSkillRegistry} 中，
 * 因此随机抽取永远不会给出它；冷却独立保存在 {@link ApprenticePlayerComponent}。准心 6 格内的玩家（未瞄准任何人且自己持有
 * 因子时为她自己）失去魔女因子，她获得 30 魔力。目标没有因子时仍消耗魔力与冷却：“未感知到因子”本身就是情报。
 */
public final class PurifyAbility {
    public static final Identifier ID = SparkWitch.id("purify");
    public static final int COLOR = 0xD9C2FF;
    public static final int MANA_COST = 30;
    public static final int COOLDOWN_TICKS = GameConstants.getInTicks(0, 20);
    public static final double RANGE_BLOCKS = 6.0;
    public static final int MANA_REWARD = 30;
    private static final String REMOVED_KEY = "removed";

    private PurifyAbility() {
    }

    public static void registerReplayFormatter() {
        ReplayRegistry.registerSkillFormatter(ID, PurifyAbility::formatSkillUse);
    }

    /** Handles the empty Purify request; the server re-checks everything. / 处理空的净化请求；服务端重新校验全部条件。 */
    public static void use(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        if (!game.isRunning() || !GameFunctions.isPlayerPlayingAndAlive(player) || player.isSpectator()
                || game.getRole(player) != SparkWitchRoles.apprenticeWitch()) {
            return;
        }
        // Same silent refusals as the other dedicated role skills (Blind Attune, Warden's Shriek).
        // 与其他自有流程职业技能（盲人凝神、监守之啸）相同的静默拒绝。
        if (SparkTraitsKillerBridge.isRoleSkillBlocked(player) || NoellesTaotieSeekerBridge.isSwallowed(player)) {
            return;
        }
        ApprenticePlayerComponent apprentice = ApprenticePlayerComponent.KEY.get(player);
        if (!apprentice.isGraduated()) {
            send(player, Text.translatable("message.sparkwitch.skill.purify.locked",
                    apprentice.getCompletedTasks(), ApprenticeRules.GRADUATION_TASKS));
            return;
        }
        if (GrandWitchFearService.denyRoleSkillIfFeared(player)) {
            return;
        }
        if (apprentice.getPurifyCooldownTicks() > 0) {
            send(player, Text.translatable("message.sparkwitch.skill.cooldown",
                    (apprentice.getPurifyCooldownTicks() + 19) / 20));
            return;
        }

        ServerPlayerEntity target = GrandWitchTargeting.findTarget(player, null, RANGE_BLOCKS);
        if (target == null) {
            if (!WitchFactorService.isFactorHolder(player)) {
                send(player, Text.translatable("message.sparkwitch.skill.purify.no_target"));
                return;
            }
            target = player;
        }
        WitchPlayerComponent mana = WitchPlayerComponent.KEY.get(player);
        if (!mana.spendMana(MANA_COST)) {
            send(player, Text.translatable("message.sparkwitch.skill.not_enough_mana"));
            return;
        }
        apprentice.raisePurifyCooldown(COOLDOWN_TICKS);

        boolean removed = WitchFactorService.purify(target);
        boolean self = target == player;
        if (removed) {
            mana.addMana(MANA_REWARD);
            send(player, self
                    ? Text.translatable("message.sparkwitch.skill.purify.removed_self")
                    : Text.translatable("message.sparkwitch.skill.purify.removed", target.getDisplayName()));
            if (!self) {
                send(target, Text.translatable("message.sparkwitch.skill.purify.purified"));
                target.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.2f);
            }
            player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.2f);
        } else {
            send(player, Text.translatable("message.sparkwitch.skill.purify.none", target.getDisplayName()));
            player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_STEP, SoundCategory.PLAYERS, 1.0f, 0.8f);
        }
        NbtCompound extra = new NbtCompound();
        extra.putBoolean(REMOVED_KEY, removed);
        GameRecordManager.recordSkillUse(player, ID, target, extra);
    }

    private static void send(ServerPlayerEntity player, Text text) {
        player.sendMessage(text, true);
    }

    private static @Nullable Text formatSkillUse(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor") || !data.containsUuid("target")) {
            return null;
        }
        // recordSkillUse copies the extra compound into the event data itself. / recordSkillUse 会把额外数据直接并入事件数据。
        boolean removed = data.getBoolean(REMOVED_KEY);
        var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
        return Text.translatable(
                removed ? "replay.skill.sparkwitch.purify.removed" : "replay.skill.sparkwitch.purify.none",
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), playerInfo),
                ReplayGenerator.formatPlayerName(data.getUuid("target"), playerInfo)
        );
    }
}
