package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkfactionapi.api.cooldown.RoleSkillCooldownStore;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.ObjIntConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * A forced-cooldown store for a role-skill counter that needs no {@code mayForce}, {@code raiseTo} or
 * {@code extendBy} override: SparkFactionAPI's default raise and extend both end in {@link #setRemainingTicks}. Every
 * store states its round gate ({@link #anyGameStatus} or {@link #whileActive}) and its {@link Write} kind; neither has
 * a default, because the stores differ on both. The nominal is read on every call, so building a store initializes no
 * role or component class.
 * 用于职业技能计数的强制冷却存储，适用于无需覆盖 mayForce、raiseTo 或 extendBy 的计数：SparkFactionAPI 默认的抬高与延长
 * 都落到 setRemainingTicks。每个存储都声明自己的回合门控（anyGameStatus 或 whileActive）与写入方式；二者都没有默认值，
 * 因为各存储在这两点上互不相同。标准冷却在每次调用时读取，因此构建存储不会初始化任何职业或组件类。
 */
final class SimpleRoleSkillCooldownStore implements RoleSkillCooldownStore {
    private final Identifier id;
    private final Gate gate;
    private final ToIntFunction<ServerPlayerEntity> remaining;
    private final IntSupplier nominal;
    private final Write write;
    private final ObjIntConsumer<ServerPlayerEntity> writer;

    private SimpleRoleSkillCooldownStore(
            Identifier id,
            Gate gate,
            ToIntFunction<ServerPlayerEntity> remaining,
            IntSupplier nominal,
            Write write,
            ObjIntConsumer<ServerPlayerEntity> writer
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.remaining = Objects.requireNonNull(remaining, "remaining");
        this.nominal = Objects.requireNonNull(nominal, "nominal");
        this.write = write;
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    /** The counter's setter receives {@code ticks} as given. / 计数 setter 原样接收 ticks。 */
    static SimpleRoleSkillCooldownStore exact(
            Identifier id,
            Gate gate,
            ToIntFunction<ServerPlayerEntity> remaining,
            IntSupplier nominal,
            ObjIntConsumer<ServerPlayerEntity> setter
    ) {
        return new SimpleRoleSkillCooldownStore(id, gate, remaining, nominal, Write.EXACT, setter);
    }

    /** The counter's setter receives {@code max(0, ticks)}. / 计数 setter 接收 max(0, ticks)。 */
    static SimpleRoleSkillCooldownStore clampedAtZero(
            Identifier id,
            Gate gate,
            ToIntFunction<ServerPlayerEntity> remaining,
            IntSupplier nominal,
            ObjIntConsumer<ServerPlayerEntity> setter
    ) {
        Objects.requireNonNull(setter, "setter");
        return new SimpleRoleSkillCooldownStore(id, gate, remaining, nominal, Write.CLAMPED_AT_ZERO,
                (player, ticks) -> setter.accept(player, Math.max(0, ticks)));
    }

    /**
     * The counter is an absolute ready tick on {@code state}: a write reads it once, then moves it to
     * {@code now + ticks} (server world time) only when that is later ({@link ForcedCooldownMath#raiseReadyTick}),
     * else writes nothing, so it never shortens.
     * 计数是 {@code state} 上的绝对就绪刻：写入时读取一次状态，仅当 {@code now + ticks}（服务端世界时间）更晚时才移动到该值
     * （ForcedCooldownMath.raiseReadyTick），否则不写入，因此绝不缩短。
     */
    static <S> SimpleRoleSkillCooldownStore raisedReadyTick(
            Identifier id,
            Gate gate,
            ToIntFunction<ServerPlayerEntity> remaining,
            IntSupplier nominal,
            Function<ServerPlayerEntity, S> state,
            ToLongFunction<S> readyTick,
            ObjLongConsumer<S> setReadyTick
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(readyTick, "readyTick");
        Objects.requireNonNull(setReadyTick, "setReadyTick");
        return new SimpleRoleSkillCooldownStore(id, gate, remaining, nominal, Write.RAISED_READY_TICK,
                (player, ticks) -> {
                    S current = state.apply(player);
                    ForcedCooldownMath.raiseReadyTick(readyTick.applyAsLong(current),
                                    player.getServerWorld().getTime(), ticks)
                            .ifPresent(ready -> setReadyTick.accept(current, ready));
                });
    }

    /** Applies in every game status, exactly when {@code holder} does. / 在任何游戏状态下生效，完全由 holder 决定。 */
    static Gate anyGameStatus(Predicate<ServerPlayerEntity> holder) {
        return new Gate(false, Objects.requireNonNull(holder, "holder"));
    }

    /**
     * Applies only in an ACTIVE round of the player's world, and then exactly when {@code holder} does. The world's game
     * component is looked up once, the round is tested first, and {@code holder} runs only for an ACTIVE round.
     * 只在玩家所在世界处于 ACTIVE 回合时生效，此时完全由 holder 决定。世界的游戏组件只查询一次，先判断回合，
     * 只有 ACTIVE 回合才运行 holder。
     */
    static Gate whileActive(BiPredicate<GameWorldComponent, ServerPlayerEntity> holder) {
        Objects.requireNonNull(holder, "holder");
        return new Gate(true, player -> {
            GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
            return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE && holder.test(game, player);
        });
    }

    @Override
    public Identifier id() {
        return id;
    }

    @Override
    public boolean appliesTo(ServerPlayerEntity player) {
        return gate.test(player);
    }

    @Override
    public int remainingTicks(ServerPlayerEntity player) {
        return remaining.applyAsInt(player);
    }

    @Override
    public OptionalInt nominalTicks(ServerPlayerEntity player) {
        return OptionalInt.of(nominal.getAsInt());
    }

    @Override
    public void setRemainingTicks(ServerPlayerEntity player, int ticks) {
        writer.accept(player, ticks);
    }

    Gate gate() {
        return gate;
    }

    Write write() {
        return write;
    }

    /** How {@link #setRemainingTicks} reaches the counter. / setRemainingTicks 如何写入计数。 */
    enum Write {
        EXACT,
        CLAMPED_AT_ZERO,
        RAISED_READY_TICK
    }

    /**
     * Who a store applies to; {@link #activeRoundOnly()} is true exactly for {@link #whileActive} gates.
     * 存储作用于谁；仅 whileActive 门控的 activeRoundOnly() 为 true。
     */
    static final class Gate {
        private final boolean activeRoundOnly;
        private final Predicate<ServerPlayerEntity> test;

        private Gate(boolean activeRoundOnly, Predicate<ServerPlayerEntity> test) {
            this.activeRoundOnly = activeRoundOnly;
            this.test = test;
        }

        boolean activeRoundOnly() {
            return activeRoundOnly;
        }

        boolean test(ServerPlayerEntity player) {
            return test.test(player);
        }
    }
}
