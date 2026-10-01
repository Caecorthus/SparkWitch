package dev.caecorthus.sparkwitch.roles.witch.abysslistener;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkfactionapi.api.cooldown.ForcedCooldowns;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.caecorthus.sparkwitch.compat.SparkTraitsAbyssListenerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Role-private suppression toolkit shared by the Warden's Shriek, the Shriek Gun and the Deep Dark Zone: target
 * eligibility, ally detection, sanity drain, forced cooldowns and status effects. Server-only; every caller runs on
 * the server thread. Effects, mood and cooldowns are not hooked by SparkFactionAPI, so callers ask {@link #canAffect}
 * once per bundle and skip the whole bundle when it is false.
 * 监守之啸、啸音铳与深暗领域共用的职业私有压制工具：目标资格、队友判定、理智扣除、强制冷却与状态效果。仅服务端，
 * 所有调用都在服务端线程。SparkFactionAPI 不拦截状态效果、理智与冷却，因此调用方每组效果先询问一次 {@link #canAffect}，
 * 为 false 时整组跳过。
 */
public final class AbyssSuppression {
    private AbyssSuppression() {
    }

    /**
     * Living participant of a running game: playing and alive, not spectating (which also covers Taotie-swallowed
     * players and fake deaths), not creative, not an active Wraith, has a role, not SparkTraits Last Stand pending and
     * not in SparkTraits Last Escape (N16). Absent SparkTraits adds no exclusion.
     * 进行中对局的存活参与者：参与且存活、非旁观（同时排除被饕餮吞下与假死的玩家）、非创造、非激活冤魂、拥有职业、
     * 不处于 SparkTraits 背水一战待定且不处于绝处逢生（N16）。未安装 SparkTraits 时不附加排除。
     */
    public static boolean isParticipantTarget(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        return participant(
                GameFunctions.isPlayerPlayingAndAlive(player),
                player.isSpectator(),
                player.isCreative(),
                () -> WraithStateService.isActive(player),
                () -> GameWorldComponent.KEY.get(player.getWorld()).getRole(player) != null,
                () -> SparkTraitsAbyssListenerBridge.isLastStandPending(player),
                () -> SparkTraitsKillerBridge.isLastEscapeActive(player));
    }

    /**
     * Ally of the Abyss Listener: SparkFactionAPI effective faction is {@code sparkwitch:witch} (N6: Grand Witch,
     * Accomplice, special accomplices, Curser; not the Apprentice or the Murderous Witch). Wraith-aware via SFA.
     * 聆渊者的队友：SparkFactionAPI 有效阵营为 {@code sparkwitch:witch}（N6：大魔女、共犯、各特殊共犯、诅咒者；
     * 不含预备魔女与杀意魔女）。冤魂状态由 SFA 解析。
     */
    public static boolean isAlly(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return isWitchFaction(SparkFactionApi.resolveEffectiveFaction(player, game));
    }

    /**
     * Full eligibility plus SparkFactionAPI's structural veto for {@code actionId} when an actor is known. Allies and
     * the actor itself are NOT excluded here (the gun pushes allies, the zone speeds up its own thrower); callers
     * exclude them where their spec says so.
     * 完整资格判定；已知施加者时再加上 SparkFactionAPI 对 {@code actionId} 的结构性否决。这里不排除队友与施加者本人
     * （枪会推动队友，领域会加速投掷者本人），由调用方按各自规格排除。
     */
    public static boolean canAffect(@Nullable ServerPlayerEntity actor, ServerPlayerEntity target, Identifier actionId) {
        if (target == null) {
            return false;
        }
        return affects(
                () -> isParticipantTarget(target),
                actor != null,
                () -> SparkFactionApi.canAffectPlayer(
                        actor, target, actionId, GameWorldComponent.KEY.get(target.getWorld())));
    }

    /**
     * Whether the target's sanity is effectively REAL, mirroring SparkTraits' effective mood type: Conscience makes it
     * REAL, Impostor makes it FAKE, otherwise the base role decides; an active Wraith never has real sanity. Absent
     * SparkTraits: the base role's mood type. An unknown trait answer fails closed (no real sanity).
     * 目标理智是否实际为真实理智，与 SparkTraits 的有效情绪类型一致：良知使其为真实，内鬼使其为伪装，否则由基础职业决定；
     * 激活冤魂永远没有真实理智。未安装 SparkTraits：取基础职业情绪类型。词条查询结果未知时失败关闭（视为无真实理智）。
     */
    public static boolean hasRealSanity(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return effectiveRealSanity(
                role != null && role.getMoodType() == Role.MoodType.REAL,
                SparkTraitsAbyssListenerBridge.isConscienceActive(player),
                SparkTraitsAbyssListenerBridge.isImpostorActive(player),
                WraithStateService.isActive(player));
    }

    /**
     * Lowers Wathe mood by {@code amount} (1 point = 0.01). Wathe (and the SparkTraits redirect) ignores non-REAL
     * targets; a breakdown at −1 is allowed and unattributed (owner D6).
     * 降低 Wathe 理智 {@code amount}（1 点 = 0.01）。Wathe（及 SparkTraits 重定向）会忽略非真实理智目标；
     * 降至 −1 的精神崩溃允许发生且不记功（所有者 D6）。
     */
    public static void drainSanity(ServerPlayerEntity player, float amount) {
        if (player == null || !(amount > 0.0F)) {
            return;
        }
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        mood.setMood(mood.getMood() - amount);
    }

    /**
     * Floors every role-skill and carried-item cooldown at {@code ticks} through SparkFactionAPI's exact, monotonic
     * {@code ForcedCooldowns.raiseAll}; never writes a cooldown store directly.
     * 通过 SparkFactionAPI 精确且单调的 {@code ForcedCooldowns.raiseAll} 把所有职业技能与携带物品冷却抬高到至少
     * {@code ticks}；从不直接写任何冷却存储。
     */
    public static void forceCooldowns(ServerPlayerEntity player, int ticks) {
        if (player == null || ticks <= 0) {
            return;
        }
        ForcedCooldowns.raiseAll(player, ticks);
    }

    /**
     * Adds a house-style effect: not ambient, no particles, icon shown. {@code source} should be the Abyss Listener so
     * the Wraith status-effect rule can filter it.
     * 以本模组惯例施加效果：非环境、不显示粒子、显示图标。{@code source} 应为聆渊者，使冤魂状态效果规则可以过滤。
     */
    public static void addEffect(
            ServerPlayerEntity player,
            RegistryEntry<StatusEffect> effect,
            int ticks,
            int amplifier,
            @Nullable Entity source
    ) {
        if (player == null || effect == null || ticks <= 0) {
            return;
        }
        player.addStatusEffect(new StatusEffectInstance(effect, ticks, amplifier, false, false, true), source);
    }

    /** Pure gate; later seams are consulted only after every earlier one passed. / 纯判定；前序条件全部通过后才查询后续接缝。 */
    static boolean participant(boolean playingAndAlive, boolean spectator, boolean creative,
                               BooleanSupplier activeWraith, BooleanSupplier hasRole,
                               BooleanSupplier lastStandPending, BooleanSupplier lastEscape) {
        return playingAndAlive
                && !spectator
                && !creative
                && !activeWraith.getAsBoolean()
                && hasRole.getAsBoolean()
                && !lastStandPending.getAsBoolean()
                && !lastEscape.getAsBoolean();
    }

    /** Pure gate: eligibility first, then the SFA veto only when an actor is known. / 纯判定：先资格，有施加者时再查 SFA 否决。 */
    static boolean affects(BooleanSupplier participant, boolean hasActor, BooleanSupplier factionAllows) {
        return participant.getAsBoolean() && (!hasActor || factionAllows.getAsBoolean());
    }

    /** Unknown factions are not allies. / 未知阵营不是队友。 */
    static boolean isWitchFaction(@Nullable Identifier effectiveFaction) {
        return SparkWitchFactions.WITCH.equals(effectiveFaction);
    }

    /**
     * Pure effective-REAL rule: an active Wraith never; Conscience (TRUE) always; otherwise a REAL base role that is
     * proven not Impostor (FALSE). {@code null} means the SparkTraits answer is unknown and never grants real sanity.
     * 纯判定：激活冤魂永不；良知（TRUE）总是；否则需基础职业为真实理智且确认非内鬼（FALSE）。{@code null} 表示 SparkTraits
     * 结果未知，从不据此认定真实理智。
     */
    static boolean effectiveRealSanity(boolean baseReal, @Nullable Boolean conscience, @Nullable Boolean impostor,
                                       boolean activeWraith) {
        if (activeWraith) {
            return false;
        }
        if (Boolean.TRUE.equals(conscience)) {
            return true;
        }
        return baseReal && Boolean.FALSE.equals(impostor);
    }
}
