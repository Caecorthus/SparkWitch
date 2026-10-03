package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Everything a shell effect needs after the blast resolver picked its targets. {@code gunner} is null when the gunner
 * is offline; effects still apply, but nothing is paid out.
 * 爆炸判定选出目标后，炮弹效果所需的全部信息。药炮手离线时 {@code gunner} 为 null；效果照常生效，但不发放任何奖励。
 */
public record PotionBlastContext(
        ServerWorld world,
        Vec3d center,
        PotionShellType type,
        @Nullable UUID gunnerUuid,
        @Nullable ServerPlayerEntity gunner,
        List<PotionBlastHit> hits
) {
}
