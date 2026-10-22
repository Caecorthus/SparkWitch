package dev.caecorthus.sparkwitch.util;

import dev.caecorthus.sparkwitch.compat.SparkTraitsAbyssListenerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * The one "living round participant" predicate (owner-approved consolidation, Phase 5), as named {@link Profile}s. Every
 * profile starts from Wathe {@code isPlayerPlayingAndAlive} (running game, a role in the player's own world, not marked
 * dead) and is null-safe (a null player never passes). A profile is an ordered list of {@link Gate}s evaluated in
 * exactly the declared order and stopping at the first failure, so optional SparkTraits bridges are only consulted
 * after every earlier gate passed. Each profile reproduces one existing site's rule verbatim, divergences included
 * (Rift entry has no role gate, Crow landing allows creative players and Wraiths, the Swordfish needs status ACTIVE
 * rather than merely running, the Mini Wither checks Wraiths only on the server and Last Escape before the role).
 * The bound-item scope ({@link OffMatchUse#isMatchParticipant}, dead participants count) and the Seeker's bound-match
 * owner rule ({@code SeekerTargeting.isActiveParticipant}) are deliberately not profiles.
 * {@link Profile#test(PlayerEntity)} reads the player's own world's game component;
 * {@link Profile#test(PlayerEntity, GameWorldComponent)} reads the caller's component for {@link Gate#HAS_ROLE} and
 * {@link Gate#STATUS_ACTIVE} (Wathe's own gate always reads the player's world). Both sides: a client may evaluate a
 * profile without SparkTraits gates.
 * 唯一的“存活对局参与者”判定（所有者批准的第 5 阶段合并），以具名 {@link Profile} 表示。每个档案都以 Wathe
 * {@code isPlayerPlayingAndAlive}（对局进行中、在玩家自身世界拥有职业、未被标记死亡）为基础，并且空值安全（null 玩家永不
 * 通过）。档案是有序的 {@link Gate} 列表，严格按声明顺序求值、遇到第一个失败即停止，因此可选的 SparkTraits 桥只在前序条件
 * 全部通过后才被查询。每个档案逐字复现一个既有调用点的规则，包括差异（裂隙进门不检查职业；化鸦落地允许创造模式与冤魂；
 * 剑鱼要求状态恰为 ACTIVE 而非仅进行中；小凋灵只在服务端检查冤魂，且先查最后逃脱再查职业）。绑定物品范围
 * （{@link OffMatchUse}）与搜寻者的绑定对局拥有者规则刻意不做成档案。{@link Profile#test(PlayerEntity)} 读取玩家自身世界的
 * 对局组件；{@link Profile#test(PlayerEntity, GameWorldComponent)} 在 {@link Gate#HAS_ROLE} 与 {@link Gate#STATUS_ACTIVE}
 * 上读取调用方提供的组件（Wathe 自身的判定始终读取玩家所在世界）。双端可用：客户端可以求值不含 SparkTraits 条件的档案。
 */
public final class RoundParticipants {
    /**
     * One condition. Each passes when the player satisfies the named state.
     * 单个条件。玩家满足所述状态时通过。
     */
    public enum Gate {
        /** A server-side player; a client-side player fails. / 服务端玩家；客户端玩家不通过。 */
        SERVER_PLAYER,
        /** Not a disconnected server player; a client-side player passes. / 不是已断线的服务端玩家；客户端玩家通过。 */
        CONNECTED,
        /** Wathe {@code isPlayerPlayingAndAlive}; required in every profile. / Wathe {@code isPlayerPlayingAndAlive}；每个档案必含。 */
        PLAYING_AND_ALIVE,
        /** Vanilla {@code isAlive}. / 原版 {@code isAlive}。 */
        ENTITY_ALIVE,
        /** Not in spectator mode. / 非旁观模式。 */
        NOT_SPECTATOR,
        /** Not in creative mode. / 非创造模式。 */
        NOT_CREATIVE,
        /** Game status exactly {@code ACTIVE} (STOPPING fails). / 对局状态恰为 {@code ACTIVE}（STOPPING 不通过）。 */
        STATUS_ACTIVE,
        /** Not an active Wraith, on either side. / 非激活冤魂（双端）。 */
        NOT_ACTIVE_WRAITH,
        /** Not an active Wraith, checked for server players only. / 非激活冤魂，只对服务端玩家检查。 */
        NOT_ACTIVE_WRAITH_SERVER_ONLY,
        /** Raw {@code getRole} is non-null. / 原始 {@code getRole} 非空。 */
        HAS_ROLE,
        /** Not SparkTraits Last Stand pending (Abyss bridge; absent → passes). / 非背水一战待定（聆渊桥；未安装则通过）。 */
        NOT_LAST_STAND_PENDING,
        /** Not in SparkTraits Last Escape (killer bridge; absent → passes). / 非绝处逢生（杀手桥；未安装则通过）。 */
        NOT_LAST_ESCAPE
    }

    /** Pure seam: answers one gate. / 纯接缝：回答单个条件。 */
    @FunctionalInterface
    public interface GateProbe {
        boolean passes(Gate gate);
    }

    /**
     * Control Expert, Bell Ringer Echo and Time Stealer Clock targets.
     * 控场专家、敲钟人回响与窃时者时钟的目标。
     */
    public static final Profile ROLE_HOLDER = Profile.of(
            Gate.PLAYING_AND_ALIVE, Gate.NOT_SPECTATOR, Gate.NOT_CREATIVE, Gate.NOT_ACTIVE_WRAITH, Gate.HAS_ROLE);

    /**
     * {@link #ROLE_HOLDER} for an online server player: Witches' Sabbath targets and the Seeker device-break gate.
     * 在线服务端玩家的 {@link #ROLE_HOLDER}：魔女集会目标与搜寻者设备损坏门槛。
     */
    public static final Profile CONNECTED_ROLE_HOLDER = Profile.of(
            Gate.SERVER_PLAYER, Gate.CONNECTED, Gate.PLAYING_AND_ALIVE, Gate.NOT_SPECTATOR, Gate.NOT_CREATIVE,
            Gate.NOT_ACTIVE_WRAITH, Gate.HAS_ROLE);

    /**
     * Alive, connected, playing, survival or adventure; no Wraith or role gate: Fisher items and the Blind's money.
     * 存活、在线、在局、生存或冒险模式；不检查冤魂与职业：钓鱼佬物品与盲人金币。
     */
    public static final Profile LIVING_SURVIVOR = Profile.of(
            Gate.ENTITY_ALIVE, Gate.CONNECTED, Gate.PLAYING_AND_ALIVE, Gate.NOT_SPECTATOR, Gate.NOT_CREATIVE);

    /** {@link #LIVING_SURVIVOR} in an ACTIVE round, not an active Wraith. / ACTIVE 对局中且非激活冤魂的 {@link #LIVING_SURVIVOR}。 */
    public static final Profile SWORDFISH_TARGET = LIVING_SURVIVOR.then(Gate.STATUS_ACTIVE, Gate.NOT_ACTIVE_WRAITH);

    /**
     * {@link #ROLE_HOLDER}, then SparkTraits Last Stand pending, then Last Escape (Abyss Listener suppression).
     * {@link #ROLE_HOLDER}，随后依次为 SparkTraits 背水一战待定与绝处逢生（聆渊者压制）。
     */
    public static final Profile ABYSS_TARGET = ROLE_HOLDER.then(Gate.NOT_LAST_STAND_PENDING, Gate.NOT_LAST_ESCAPE);

    /** Rift Gate entry: alive, survival, not an active Wraith; no role gate. / 裂隙门进门：存活、生存模式、非激活冤魂；不检查职业。 */
    public static final Profile RIFT_ENTRANT = Profile.of(
            Gate.PLAYING_AND_ALIVE, Gate.ENTITY_ALIVE, Gate.NOT_SPECTATOR, Gate.NOT_CREATIVE, Gate.NOT_ACTIVE_WRAITH);

    /** The Judge: playing, survival or adventure. / 大法官：在局、生存或冒险模式。 */
    public static final Profile JUDGE_ACTIVE = Profile.of(
            Gate.PLAYING_AND_ALIVE, Gate.NOT_SPECTATOR, Gate.NOT_CREATIVE);

    /** Raven Form landing: alive, not spectating; creative players and Wraiths pass. / 化鸦落地：存活、非旁观；创造模式与冤魂通过。 */
    public static final Profile CROW_LANDING = Profile.of(
            Gate.ENTITY_ALIVE, Gate.NOT_SPECTATOR, Gate.PLAYING_AND_ALIVE);

    /**
     * Mini Wither targets: survival, playing, server-side Wraith check, Last Escape before the role.
     * 小凋灵目标：生存模式、在局、仅服务端检查冤魂，先查最后逃脱再查职业。
     */
    public static final Profile MINI_WITHER_TARGET = Profile.of(
            Gate.NOT_SPECTATOR, Gate.NOT_CREATIVE, Gate.PLAYING_AND_ALIVE, Gate.NOT_ACTIVE_WRAITH_SERVER_ONLY,
            Gate.NOT_LAST_ESCAPE, Gate.HAS_ROLE);

    private RoundParticipants() {
    }

    /**
     * An immutable ordered gate list. Gates are evaluated in declaration order and evaluation stops at the first failing
     * gate, so later (costlier or optional) gates are never consulted for a rejected player.
     * 不可变的有序条件列表。按声明顺序求值，遇到第一个失败即停止，被拒绝的玩家永远不会查询后续（更昂贵或可选的）条件。
     */
    public static final class Profile {
        private final Gate[] gates;

        private Profile(Gate[] gates) {
            if (gates.length == 0) {
                throw new IllegalArgumentException("a profile needs at least one gate");
            }
            EnumSet<Gate> seen = EnumSet.noneOf(Gate.class);
            for (Gate gate : gates) {
                if (gate == null) {
                    throw new IllegalArgumentException("null gate");
                }
                if (!seen.add(gate)) {
                    throw new IllegalArgumentException("duplicate gate " + gate);
                }
            }
            if (!seen.contains(Gate.PLAYING_AND_ALIVE)) {
                throw new IllegalArgumentException("every profile starts from Wathe isPlayerPlayingAndAlive");
            }
            this.gates = gates;
        }

        /** Profile of these gates, in this order. / 由这些条件按此顺序组成的档案。 */
        public static Profile of(Gate... gates) {
            return new Profile(gates.clone());
        }

        /** A new profile: these gates, then {@code more}. / 新档案：本档案的条件，随后是 {@code more}。 */
        public Profile then(Gate... more) {
            Gate[] joined = Arrays.copyOf(gates, gates.length + more.length);
            System.arraycopy(more, 0, joined, gates.length, more.length);
            return new Profile(joined);
        }

        /** The gates in evaluation order. / 按求值顺序排列的条件。 */
        public List<Gate> gates() {
            return List.of(gates);
        }

        /** Pure evaluation in order, stopping at the first failure. / 按顺序的纯求值，遇到第一个失败即停止。 */
        public boolean evaluate(GateProbe probe) {
            for (Gate gate : gates) {
                if (!probe.passes(gate)) {
                    return false;
                }
            }
            return true;
        }

        /** Live test against the player's own world's game component. / 依据玩家自身世界对局组件的实时判定。 */
        public boolean test(@Nullable PlayerEntity player) {
            return player != null && run(player, null, true);
        }

        /**
         * Live test reading {@code game} for the role and status gates (the caller's game, as the site always did).
         * 实时判定，职业与状态条件读取 {@code game}（即调用方的对局，与调用点一贯做法一致）。
         */
        public boolean test(@Nullable PlayerEntity player, GameWorldComponent game) {
            return player != null && run(player, game, false);
        }

        private boolean run(PlayerEntity player, @Nullable GameWorldComponent callerGame, boolean ownWorld) {
            for (Gate gate : gates) {
                if (!passes(gate, player, callerGame, ownWorld)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static boolean passes(Gate gate, PlayerEntity player, @Nullable GameWorldComponent callerGame,
                                  boolean ownWorld) {
        return switch (gate) {
            case SERVER_PLAYER -> player instanceof ServerPlayerEntity;
            case CONNECTED -> !(player instanceof ServerPlayerEntity serverPlayer && serverPlayer.isDisconnected());
            case PLAYING_AND_ALIVE -> GameFunctions.isPlayerPlayingAndAlive(player);
            case ENTITY_ALIVE -> player.isAlive();
            case NOT_SPECTATOR -> !player.isSpectator();
            case NOT_CREATIVE -> !player.isCreative();
            case STATUS_ACTIVE -> game(player, callerGame, ownWorld).getGameStatus()
                    == GameWorldComponent.GameStatus.ACTIVE;
            case NOT_ACTIVE_WRAITH -> !WraithStateService.isActive(player);
            case NOT_ACTIVE_WRAITH_SERVER_ONLY -> !(player instanceof ServerPlayerEntity serverPlayer
                    && WraithStateService.isActive(serverPlayer));
            case HAS_ROLE -> game(player, callerGame, ownWorld).getRole(player) != null;
            case NOT_LAST_STAND_PENDING -> !SparkTraitsAbyssListenerBridge.isLastStandPending(player);
            case NOT_LAST_ESCAPE -> !SparkTraitsKillerBridge.isLastEscapeActive(player);
        };
    }

    // A caller-supplied component is used as given (a null one fails at the gate that reads it, as the sites did).
    // 调用方提供的组件按原样使用（为 null 时在读取它的条件处失败，与原调用点一致）。
    private static GameWorldComponent game(PlayerEntity player, @Nullable GameWorldComponent callerGame,
                                           boolean ownWorld) {
        return ownWorld ? GameWorldComponent.KEY.get(player.getWorld()) : callerGame;
    }
}
