package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * Stable CCA contract {@code sparkwitch:sphinx_hao_player} (NEVER_COPY). Server state of one Sphinx Hao: its 豪度值
 * ("hao") and two absolute world ticks, when skill 1 (嘉豪病毒) and skill 2 (豪气冲天) are ready again.
 * <p>Sync: to the owner only (hao plus both ready ticks), which its HUD counts down against the client world clock
 * (the Koko/Noa precedent). NBT keeps everything across a mid-round relog; role assignment and Wathe's
 * {@code ResetPlayer} clear it. The forced-cooldown stores {@code sparkwitch:sphinx_hao_infect} and
 * {@code sparkwitch:sphinx_hao_roar} only ever move the ready ticks.
 * 稳定 CCA 契约 {@code sparkwitch:sphinx_hao_player}（NEVER_COPY）。一名狮身豪的服务端状态：豪度值，以及技能一（嘉豪病毒）
 * 与技能二（豪气冲天）再次就绪的两个绝对世界刻。
 * <p>同步：只发给本人（豪度值与两个就绪刻），其 HUD 依据客户端世界时钟倒数（沿用可可、诺亚的先例）。NBT 使全部状态在局中重新
 * 登录后保留；身份分配与 Wathe 的 {@code ResetPlayer} 会清空。强制冷却存储 {@code sparkwitch:sphinx_hao_infect} 与
 * {@code sparkwitch:sphinx_hao_roar} 只会移动就绪刻。
 */
public final class SphinxHaoPlayerComponent implements AutoSyncedComponent {
    public static final ComponentKey<SphinxHaoPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SphinxHaoRules.ROLE_ID.withSuffixedPath("_player"), SphinxHaoPlayerComponent.class);
    static final String HAO = "Hao";
    static final String INFECT_READY_TICK = "InfectReadyTick";
    static final String ROAR_READY_TICK = "RoarReadyTick";

    private final PlayerEntity player;
    private int hao;
    private long infectReadyTick;
    private long roarReadyTick;

    public SphinxHaoPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public int hao() {
        return hao;
    }

    public long infectReadyTick() {
        return infectReadyTick;
    }

    public long roarReadyTick() {
        return roarReadyTick;
    }

    /** Ticks until skill 1 is ready at world time {@code now}; 0 when ready. / 距技能一就绪的刻数，就绪为 0。 */
    public int infectCooldownRemaining(long now) {
        return (int) Math.clamp(infectReadyTick - now, 0L, Integer.MAX_VALUE);
    }

    /** Ticks until skill 2 is ready at world time {@code now}; 0 when ready. / 距技能二就绪的刻数，就绪为 0。 */
    public int roarCooldownRemaining(long now) {
        return (int) Math.clamp(roarReadyTick - now, 0L, Integer.MAX_VALUE);
    }

    /** Server only: adds hao (never below 0) and tells the owner. / 仅服务端：增加豪度值（不低于 0）并通知本人。 */
    public void addHao(int amount) {
        int next = (int) Math.clamp((long) hao + amount, 0L, Integer.MAX_VALUE);
        if (next != hao) {
            hao = next;
            sync();
        }
    }

    /**
     * Server only: takes {@code amount} hao if the owner holds that much; false and unchanged otherwise.
     * 仅服务端：豪度值足够时扣除 {@code amount}；否则返回 false 且不改变任何东西。
     */
    public boolean spendHao(int amount) {
        if (amount < 0 || hao < amount) {
            return false;
        }
        addHao(-amount);
        return true;
    }

    /** Server only (also the forced-cooldown store's setter). / 仅服务端（亦为强制冷却存储的写入口）。 */
    public void setInfectReadyTick(long tick) {
        long next = Math.max(0L, tick);
        if (next != infectReadyTick) {
            infectReadyTick = next;
            sync();
        }
    }

    /** Server only (also the forced-cooldown store's setter). / 仅服务端（亦为强制冷却存储的写入口）。 */
    public void setRoarReadyTick(long tick) {
        long next = Math.max(0L, tick);
        if (next != roarReadyTick) {
            roarReadyTick = next;
            sync();
        }
    }

    /**
     * Server only: the deal. 0 hao, skill 1 ready 60 s and skill 2 ready 90 s after {@code now}.
     * 仅服务端：发牌。豪度值为 0，技能一在 {@code now} 后 60 秒、技能二在 90 秒后就绪。
     */
    public void assignForRole(long now) {
        hao = 0;
        infectReadyTick = now + SphinxHaoRules.INFECT_INITIAL_COOLDOWN_TICKS;
        roarReadyTick = now + SphinxHaoRules.ROAR_INITIAL_COOLDOWN_TICKS;
        sync();
    }

    /** Server only: nothing of it is set. / 仅服务端：没有任何状态。 */
    public boolean idle() {
        return hao == 0 && infectReadyTick <= 0L && roarReadyTick <= 0L;
    }

    /** Server only: clears everything (role assignment, round reset). / 仅服务端：清空全部状态（身份分配、换局）。 */
    public void clear() {
        boolean hadState = !idle();
        hao = 0;
        infectReadyTick = 0L;
        roarReadyTick = 0L;
        if (hadState) {
            sync();
        }
    }

    private void sync() {
        if (player instanceof ServerPlayerEntity) {
            KEY.sync(player);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // Owner only: hao and timers are nobody else's to read. / 只发给本人：豪度值与计时不给其他人读取。
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(hao);
        buf.writeVarLong(infectReadyTick);
        buf.writeVarLong(roarReadyTick);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        hao = Math.max(0, buf.readVarInt());
        infectReadyTick = Math.max(0L, buf.readVarLong());
        roarReadyTick = Math.max(0L, buf.readVarLong());
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        hao = Math.max(0, tag.getInt(HAO));
        infectReadyTick = Math.max(0L, tag.getLong(INFECT_READY_TICK));
        roarReadyTick = Math.max(0L, tag.getLong(ROAR_READY_TICK));
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (hao > 0) {
            tag.putInt(HAO, hao);
        }
        if (infectReadyTick > 0L) {
            tag.putLong(INFECT_READY_TICK, infectReadyTick);
        }
        if (roarReadyTick > 0L) {
            tag.putLong(ROAR_READY_TICK, roarReadyTick);
        }
    }
}
