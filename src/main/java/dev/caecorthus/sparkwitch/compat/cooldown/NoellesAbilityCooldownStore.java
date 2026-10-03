package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkfactionapi.api.cooldown.RoleSkillCooldownStore;
import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.AbilityPlayerComponent;
import org.agmas.noellesroles.pathogen.PathogenPlayerComponent;
import org.agmas.noellesroles.spiritualist.SpiritPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.OptionalInt;

/**
 * NoellesRoles' shared {@code AbilityPlayerComponent.cooldown} (pinned 1.7.6). Every player has the component, so
 * {@code appliesTo} lists only the roles whose ability packet reads or writes it on the server (the Swapper only
 * writes it; its gate is client-side, which the synced value still drives). Writes always go through
 * {@code setCooldown}, the only path that syncs. The nominal is the full post-use value the holder's current role
 * (raw, or a disguised Black Raven's acting role) writes: the Pathogen's public {@code getBaseCooldownTicks}, else the
 * mirrored literal in {@link NoellesAbilityNominals}; the Morphling has none. A projecting Spirit Walker is never
 * forced (its projection end writes a flat cooldown over it).
 * NoellesRoles 共享的 AbilityPlayerComponent.cooldown（锁定 1.7.6）。每个玩家都有此组件，因此 appliesTo 只列出其技能数据包
 * 在服务端读写它的职业（交换者只写入，门控在客户端，同步值仍会生效）。写入一律经由唯一会同步的 setCooldown。标准冷却是
 * 持有者当前职业（真实职业，或伪装中黑羽鸦的扮演职业）使用后写入的完整值：病原体取公开的 getBaseCooldownTicks，其余取
 * NoellesAbilityNominals 中镜像的字面量；变形者没有标准冷却。出窍中的灵行者绝不强制（出窍结束时会写入固定冷却覆盖它）。
 */
final class NoellesAbilityCooldownStore implements RoleSkillCooldownStore {
    static final Identifier ID = Identifier.of(NoellesRoleIds.NAMESPACE, "ability");
    static final Identifier PATHOGEN = role("pathogen");
    // The Spirit Walker's role id is "spiritualist". / 灵行者的职业 id 为 "spiritualist"。
    static final Identifier SPIRITUALIST = role("spiritualist");

    /** Roles whose ability uses this counter, verified against the pinned jar. / 经锁定 jar 核实的使用此计数的职业。 */
    static final List<Identifier> ROLES = List.of(
            role("voodoo"),
            role("morphling"),
            role("vulture"),
            role("swapper"),
            role("recaller"),
            role("phantom"),
            PATHOGEN,
            role("noisemaker"),
            role("reporter"),
            role("detective"),
            role("silencer"),
            role("party_animal"),
            SPIRITUALIST
    );

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public boolean appliesTo(ServerPlayerEntity player) {
        return roleOf(player) != null;
    }

    @Override
    public int remainingTicks(ServerPlayerEntity player) {
        return AbilityPlayerComponent.KEY.get(player).getCooldown();
    }

    @Override
    public OptionalInt nominalTicks(ServerPlayerEntity player) {
        Identifier role = roleOf(player);
        if (PATHOGEN.equals(role)) {
            int base = PathogenPlayerComponent.KEY.get(player).getBaseCooldownTicks();
            return base > 0 ? OptionalInt.of(base) : OptionalInt.empty();
        }
        return role == null ? OptionalInt.empty() : NoellesAbilityNominals.fixedTicks(role);
    }

    @Override
    public void setRemainingTicks(ServerPlayerEntity player, int ticks) {
        AbilityPlayerComponent.KEY.get(player).setCooldown(Math.max(0, ticks));
    }

    /**
     * Other roles stay forceable; the projection flag is read only for the Spirit Walker (raw or acting).
     * 其他职业照常可强制；仅对灵行者（真实或扮演）读取出窍标记。
     */
    @Override
    public boolean mayForce(ServerPlayerEntity player) {
        boolean spiritualist = SPIRITUALIST.equals(roleOf(player));
        return ForcedCooldownMath.mayForceNoellesAbility(
                spiritualist,
                spiritualist && SpiritPlayerComponent.KEY.get(player).isProjecting()
        );
    }

    private static @Nullable Identifier roleOf(ServerPlayerEntity player) {
        return ForcedCooldownRoles.selfRoleIn(player, ROLES);
    }

    private static Identifier role(String path) {
        return Identifier.of(NoellesRoleIds.NAMESPACE, path);
    }
}
