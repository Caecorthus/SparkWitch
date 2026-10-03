package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkfactionapi.api.cooldown.RoleSkillCooldownStore;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindComponent;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindParticipants;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import dev.caecorthus.sparkwitch.roles.civilian.blind.kit.BlindLoadoutService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.OptionalInt;

/**
 * The Blind's Attune cooldown (owner decision 2026-10-03), kept as an absolute ready tick in the owner-synced
 * {@code sparkwitch:blind} component. Remaining time includes a running 10 s window, as the HUD shows; the nominal is
 * the 45 s cooldown after it. Every write only moves the ready tick later through {@code setAttune}, which syncs the
 * owner, so the window is never touched and nothing is shortened.
 * 盲人凝神冷却（所有者 2026-10-03 决定），以绝对就绪刻保存在仅同步本人的 {@code sparkwitch:blind} 组件中。剩余时间包含
 * 进行中的 10 秒窗口（与 HUD 显示一致）；标准冷却为窗口之后的 45 秒。每次写入都只经 {@code setAttune} 把就绪刻推后
 * （并同步本人），因此不改动窗口，也绝不缩短。
 */
final class BlindAttuneCooldownStore implements RoleSkillCooldownStore {
    static final Identifier ID = SparkWitch.id("blind_attune");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public boolean appliesTo(ServerPlayerEntity player) {
        // Same gate as BlindAttuneService: an ACTIVE round, the real role (the Blind is never a disguise target) and a
        // granted kit.
        // 与 BlindAttuneService 相同的门控：ACTIVE 回合、真实职业（盲人不能被伪装）且道具已发放。
        return GameWorldComponent.KEY.get(player.getWorld()).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && BlindParticipants.isActiveBlind(player) && BlindLoadoutService.isGranted(player);
    }

    @Override
    public int remainingTicks(ServerPlayerEntity player) {
        return BlindComponent.KEY.get(player).attuneCooldownRemaining(now(player));
    }

    @Override
    public OptionalInt nominalTicks(ServerPlayerEntity player) {
        return OptionalInt.of(BlindRules.ATTUNE_COOLDOWN_TICKS);
    }

    @Override
    public void setRemainingTicks(ServerPlayerEntity player, int ticks) {
        BlindComponent state = BlindComponent.KEY.get(player);
        ForcedCooldownMath.raiseReadyTick(state.attuneReadyTick(), now(player), ticks)
                .ifPresent(ready -> state.setAttune(state.attuneActiveUntilTick(), ready));
    }

    private static long now(ServerPlayerEntity player) {
        return player.getServerWorld().getTime();
    }
}
