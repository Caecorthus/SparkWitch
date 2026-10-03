package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkfactionapi.api.cooldown.RoleSkillCooldownStore;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.OptionalInt;

/**
 * The Orthopedist's Bone Setting cooldown; its public setter is exact and syncs to the owner.
 * 骨科大夫正骨冷却；其公开 setter 为精确写入并同步给本人。
 */
final class OrthopedistCooldownStore implements RoleSkillCooldownStore {
    static final Identifier ID = SparkWitch.id("orthopedist");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public boolean appliesTo(ServerPlayerEntity player) {
        // Same self gate as OrthopedistSkillService.use, so a disguised Black Raven acting as Orthopedist counts.
        // 与 OrthopedistSkillService.use 相同的自身门控，扮演骨科大夫的黑羽鸦同样计入。
        // Widened by the Black Raven acting overlay; getRole stays raw. / 黑羽鸦扮演覆盖层会放宽此判定；getRole 仍为真实身份。
        return GameWorldComponent.KEY.get(player.getWorld()).isRole(player, SparkWitchRoles.orthopedist());
    }

    @Override
    public int remainingTicks(ServerPlayerEntity player) {
        return OrthopedistPlayerComponent.KEY.get(player).getCooldownTicks();
    }

    @Override
    public OptionalInt nominalTicks(ServerPlayerEntity player) {
        return OptionalInt.of(OrthopedistRules.POST_USE_COOLDOWN_TICKS);
    }

    @Override
    public void setRemainingTicks(ServerPlayerEntity player, int ticks) {
        OrthopedistPlayerComponent.KEY.get(player).setCooldownTicks(ticks);
    }
}
