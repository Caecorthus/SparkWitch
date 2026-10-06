package dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment;

import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetDeathCauseGroup;
import dev.caecorthus.sparkwitch.roles.special.wraith.conversion.WraithBodyRoleAccess;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.index.WatheEntities;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * The fake corpse a recruit leaves where they stood (owner request 2026-10-06). It is an ordinary Wathe body placed the
 * way Wathe places a real one, showing the recruit's former role, with a death reason drawn uniformly from every known
 * reason, so a Coroner may notice that the cause does not fit. Its entity UUID is recorded in the round ledger so the
 * Prophet's Death Sense skips it like a Depression fake-death body.
 * 被招募者在原地留下的假尸体（所有者 2026-10-06 要求）。它是一具普通的 Wathe 尸体，摆放方式与 Wathe 真实死亡相同，显示被招募者
 * 原来的身份，死因从所有已知死因中均匀抽取，因此验尸官可能看出死因不对劲。其实体 UUID 记入本局账本，先知的死亡感知会像对待
 * 抑郁假死尸体一样跳过它。
 */
public final class RecruitmentDecoyBody {
    private RecruitmentDecoyBody() { }

    /**
     * Where and how the recruit stood, captured before the conversion touches them.
     * 被招募者原本的位置与朝向，在转换触及其之前捕获。
     */
    public record Origin(Vec3d position, float headYaw, Vec3d facing) {
        public static Origin capture(ServerPlayerEntity player) {
            return new Origin(player.getPos(), player.getHeadYaw(), player.getRotationVector());
        }
    }

    /**
     * Known reasons whose providing mod is loaded (SparkTraits is optional), so a drawn reason always has its
     * translation. Keeps the input order.
     * 提供方模组已加载的已知死因（SparkTraits 为可选），保证抽到的死因总有译名；保持输入顺序。
     */
    static List<Identifier> drawableReasons(List<Identifier> known, Predicate<String> modLoaded) {
        return known.stream().filter(reason -> modLoaded.test(reason.getNamespace())).toList();
    }

    static Identifier drawReason(List<Identifier> drawable, RandomGenerator random) {
        return drawable.get(random.nextInt(drawable.size()));
    }

    /** Spawns the corpse and records it; null when Wathe cannot create the entity. / 生成并登记假尸体；Wathe 无法创建实体时返回 null。 */
    public static @Nullable PlayerBodyEntity spawn(
            ServerWorld world,
            ServerPlayerEntity recruit,
            Origin origin,
            Identifier formerRoleId,
            RandomGenerator random
    ) {
        List<Identifier> drawable = drawableReasons(ProphetDeathCauseGroup.knownReasons(),
                FabricLoader.getInstance()::isModLoaded);
        PlayerBodyEntity body = WatheEntities.PLAYER_BODY.create(world);
        if (body == null || drawable.isEmpty()) {
            return null;
        }
        body.setPlayerUuid(recruit.getUuid());
        // The live role is already the accomplice role; the corpse must show the role the recruit "died" as.
        // 当前身份已是共犯；尸体必须显示被招募者"死去"时的身份。
        ((WraithBodyRoleAccess) body).sparkwitch$setDeathRole(formerRoleId);
        body.setDeathReason(drawReason(drawable, random));
        body.setDeathGameTime(world.getTime());
        // Same placement as Wathe's killPlayer: one block along the look vector, at the player's feet height.
        // 与 Wathe killPlayer 相同的摆放：沿视线方向一格，高度为玩家脚下。
        Vec3d spawnPos = origin.position().add(origin.facing().normalize());
        body.refreshPositionAndAngles(spawnPos.getX(), origin.position().getY(), spawnPos.getZ(), origin.headYaw(), 0.0F);
        body.setYaw(origin.headYaw());
        body.setHeadYaw(origin.headYaw());
        world.spawnEntity(body);
        GrandWitchRecruitmentRoundComponent.KEY.get(world).recordDecoyBody(body.getUuid());
        return body;
    }

    /** True for a corpse a recruit left behind this round. / 本局被招募者留下的假尸体返回 true。 */
    public static boolean isDecoy(ServerWorld world, PlayerBodyEntity body) {
        return GrandWitchRecruitmentRoundComponent.KEY.get(world).isDecoyBody(body.getUuid());
    }
}
