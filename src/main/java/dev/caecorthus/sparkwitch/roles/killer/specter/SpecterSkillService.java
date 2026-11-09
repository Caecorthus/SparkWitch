package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Specter skill one, server-authoritative: first press marks the crosshair target within 3 blocks; the next press makes
 * the marked player shriek (no distance limit). The mark expires after 10 s without a cooldown; a shriek costs 45 s.
 * 幽魂技能一，服务端权威：第一次按标记 3 格内准星目标；第二次按让被标记者尖啸（无距离限制）。
 * 标记 10 秒自然过期不进冷却；尖啸后 45 秒冷却。
 */
public final class SpecterSkillService {
    private static boolean registered;

    private SpecterSkillService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerSkillFormatter(SpecterRules.MARK_SKILL_ID, SpecterSkillService::formatMark);
        ReplayRegistry.registerSkillFormatter(SpecterRules.SHRIEK_SKILL_ID, SpecterSkillService::formatShriek);
        ReplayRegistry.registerSkillFormatter(SpecterRules.OTHERWORLD_SKILL_ID, SpecterSkillService::formatOtherworld);
    }

    /**
     * Server entry of the skill key (Dominator order): the shared caster gate {@link SpecterRules#mayCast} (refused
     * silently), the cooldown, then the Grand Witch Fear re-check, which tells the caster why. A refused press changes
     * nothing.
     * 技能键的服务端入口（与支配者顺序相同）：共用施放者门槛 {@link SpecterRules#mayCast}（静默拒绝）、冷却，最后再检查大魔女恐惧
     * （会提示原因）。被拒绝的按键不改变任何东西。
     */
    public static void use(ServerPlayerEntity caster) {
        if (caster == null || !SpecterRules.mayCast(caster)) {
            return;
        }
        SpecterPlayerComponent component = SpecterPlayerComponent.KEY.get(caster);
        if (component.isOnCooldown()) {
            return;
        }
        if (GrandWitchFearService.denyRoleSkillIfFeared(caster)) {
            return;
        }
        if (component.hasActiveMark()) {
            shriek(caster, component);
            return;
        }

        PlayerEntity target = SpecterRules.findMarkTarget(caster);
        if (!(target instanceof ServerPlayerEntity serverTarget)
                || !canAffect(caster, serverTarget, SpecterRules.MARK_SKILL_ID)) {
            // The HUD's target is kept as is: a policy-refused player is not swapped for the one behind it.
            // HUD 显示的目标保持不变：被策略拒绝的玩家不会换成其身后的玩家。
            return;
        }
        component.mark(serverTarget.getUuid());
        GameRecordManager.recordSkillUse(caster, SpecterRules.MARK_SKILL_ID, serverTarget, null);
    }

    /**
     * Marks the victim of the owner's ghost trigger when skill one is ready (no cooldown, no mark) and the owner could
     * cast it now ({@link SpecterRules#mayCast}; Fear is not re-checked because nothing is pressed, and a Feared Specter
     * cannot shriek anyway). The victim must pass the same target gate as a shriek ({@link #isValidMarkedTarget}).
     * 当技能一就绪（无冷却、无标记）且放置者此刻能施放时（{@link SpecterRules#mayCast}；没有按键，因此不再检查恐惧，
     * 被恐惧的幽魂本来也无法尖啸），标记踩中其鬼魂触发器的玩家。被踩者须通过与尖啸相同的目标门槛（{@link #isValidMarkedTarget}）。
     */
    static void markFromTrigger(ServerPlayerEntity owner, ServerPlayerEntity victim) {
        if (owner == victim || !SpecterRules.mayCast(owner)) {
            return;
        }
        SpecterPlayerComponent component = SpecterPlayerComponent.KEY.get(owner);
        if (component.isOnCooldown()
                || component.hasActiveMark()
                || !isValidMarkedTarget(owner, victim, SpecterRules.MARK_SKILL_ID)) {
            return;
        }
        component.mark(victim.getUuid());
        GameRecordManager.recordSkillUse(owner, SpecterRules.MARK_SKILL_ID, victim, null);
    }

    private static void shriek(ServerPlayerEntity caster, SpecterPlayerComponent component) {
        ServerPlayerEntity target = markedPlayer(caster, component);
        if (target == null) {
            // An invalid mark just clears; the skill stays available. / 标记失效只清除，技能保持可用。
            component.clearMark();
            return;
        }

        component.consumeMarkForShriek();
        // Owner-specified sound: the sculk shrieker's shriek. / 所有者指定音效：幽匿尖啸体的尖啸声。
        target.getWorld().playSound(
                null,
                target.getX(),
                target.getY(),
                target.getZ(),
                SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK,
                SoundCategory.PLAYERS,
                1.0F,
                1.0F
        );
        // White action-bar line for 3 s (the vanilla overlay-message lifetime). / 白色动作栏小字，3 秒。
        target.networkHandler.sendPacket(new OverlayMessageS2CPacket(
                Text.translatable("message.sparkwitch.specter.shriek").formatted(Formatting.WHITE)
        ));
        GameRecordManager.recordSkillUse(caster, SpecterRules.SHRIEK_SKILL_ID, target, null);
        applyFear(caster, target);
    }

    /**
     * The marked player if the shriek may still land on them (no distance limit): online, in the caster's own world
     * (Dominator {@code markedPlayer} pattern), still passing {@link #isValidMarkedTarget}. Otherwise null, which
     * clears the mark without a cooldown.
     * 若尖啸仍可作用于被标记者则返回之（无距离限制）：在线、与施放者处于同一世界（支配者 {@code markedPlayer} 模式），且仍通过
     * {@link #isValidMarkedTarget}。否则返回 null，标记随之清除且不进入冷却。
     */
    private static @Nullable ServerPlayerEntity markedPlayer(ServerPlayerEntity caster, SpecterPlayerComponent component) {
        UUID uuid = component.getMarkedTarget();
        if (uuid == null || caster.getServer() == null) {
            return null;
        }
        ServerPlayerEntity target = caster.getServer().getPlayerManager().getPlayer(uuid);
        return target != null && isValidMarkedTarget(caster, target, SpecterRules.SHRIEK_SKILL_ID) ? target : null;
    }

    /**
     * Server target gate for a mark that is not aimed (a ghost-trigger mark, a shriek): same world as the caster, the
     * side-neutral {@link SpecterRules#isMarkable} filter (alive participant, not spectating, invisible, swallowed, an
     * active Wraith or instinct-hidden), then SparkFactionAPI {@code canAffectPlayer} with {@code actionId} last.
     * 不经瞄准的标记（鬼魂触发器标记、尖啸）的服务端目标门槛：与施放者同一世界、两端通用的 {@link SpecterRules#isMarkable}
     * 过滤（存活参与者，非旁观、隐身、被吞下、激活冤魂或被本能隐藏），最后以 {@code actionId} 询问 SparkFactionAPI
     * {@code canAffectPlayer}。
     */
    static boolean isValidMarkedTarget(ServerPlayerEntity caster, ServerPlayerEntity target, Identifier actionId) {
        return target.getServerWorld() == caster.getServerWorld()
                && SpecterRules.isMarkable(caster, target)
                && canAffect(caster, target, actionId);
    }

    /** SparkFactionAPI's structural veto (Wraith, Vendetta, Rift occupant, Knight duel). / SFA 结构性否决。 */
    private static boolean canAffect(ServerPlayerEntity caster, ServerPlayerEntity target, Identifier actionId) {
        return SparkFactionApi.canAffectPlayer(
                caster, target, actionId, GameWorldComponent.KEY.get(caster.getServerWorld()));
    }

    /**
     * Living, non-spectator, non-creative participants within 5 blocks of the marked target that have an effective real
     * sanity bar ({@link SpecterRules#hasRealSanity}: Conscience killers count, Impostors and active Wraiths never) and
     * that SparkFactionAPI lets the caster affect ({@link SpecterRules#FEAR_ACTION_ID}) gain one level; the marked
     * target gains one extra.
     * 被标记者 5 格内、存活且非旁观/创造的参与者，若拥有有效真实理智条（{@link SpecterRules#hasRealSanity}：善良杀手计入，
     * 内鬼与激活冤魂永不计入）且 SparkFactionAPI 允许施放者影响（{@link SpecterRules#FEAR_ACTION_ID}），则 +1 层；
     * 被标记者额外 +1 层。
     */
    private static void applyFear(ServerPlayerEntity caster, ServerPlayerEntity markedTarget) {
        ServerWorld world = markedTarget.getServerWorld();
        double radiusSquared = SpecterRules.FEAR_SHRIEK_RADIUS * SpecterRules.FEAR_SHRIEK_RADIUS;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()
                    || GameFunctions.isPlayerSpectatingOrCreative(player)
                    || !GameFunctions.isPlayerPlayingAndAlive(player)
                    || player.squaredDistanceTo(markedTarget) > radiusSquared
                    || !SpecterRules.hasRealSanity(player)
                    || !canAffect(caster, player, SpecterRules.FEAR_ACTION_ID)) {
                continue;
            }
            int levels = player.getUuid().equals(markedTarget.getUuid()) ? 2 : 1;
            SpecterFearComponent.KEY.get(player).addLevel(levels);
        }
    }

    private static @Nullable Text formatMark(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        return formatTarget(event, match, "replay.skill.sparkwitch.specter_mark.target");
    }

    private static @Nullable Text formatShriek(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        return formatTarget(event, match, "replay.skill.sparkwitch.specter_shriek.target");
    }

    private static @Nullable Text formatOtherworld(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        return Text.translatable(
                "replay.skill.sparkwitch.specter_otherworld_skill",
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), ReplayGenerator.getPlayerInfoCache(match))
        );
    }

    private static @Nullable Text formatTarget(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            String translationKey
    ) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor") || !data.containsUuid("target")) {
            return null;
        }
        var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
        return Text.translatable(
                translationKey,
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), playerInfo),
                ReplayGenerator.formatPlayerName(data.getUuid("target"), playerInfo)
        );
    }
}
