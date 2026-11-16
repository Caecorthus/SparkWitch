package dev.caecorthus.sparkwitch.compat;

import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.shadowjester.ShadowJesterPlayerComponent;

import java.util.List;
import java.util.function.Predicate;

/**
 * Witch-owned boundary for the NoellesRoles Shadow Jester state read by the Witch win conditions. NoellesRoles is a
 * required, exactly pinned dependency, so its component is called directly; a provider failure still fails closed
 * exactly as the former reflective calls did.
 * 魔女胜利条件读取 NoellesRoles 双影小丑状态的 SparkWitch 自有边界。NoellesRoles 是必需且精确固定版本的依赖，
 * 因此直接调用其组件；提供方出错时仍与此前的反射调用一样失败即关闭。
 */
public final class ShadowJesterShowdownBridge {
    private static final Identifier SHADOW_SHOWDOWN_START = Identifier.of(NoellesRoleIds.NAMESPACE, "shadow_showdown_start");

    private ShadowJesterShowdownBridge() {
    }

    public static boolean isAllied(PlayerEntity player) {
        return componentBoolean(player, ShadowJesterPlayerComponent::isAllied);
    }

    public static boolean isShowdownActive(PlayerEntity player) {
        return componentBoolean(player, ShadowJesterPlayerComponent::isShowdownActive);
    }

    public static void activateShowdown(ServerWorld world, List<ServerPlayerEntity> boundShadowJesters) {
        if (world == null || boundShadowJesters == null || boundShadowJesters.isEmpty()) {
            return;
        }
        try {
            ShadowJesterPlayerComponent.activateShowdown(world, boundShadowJesters);
        } catch (RuntimeException | Error providerFailure) {
            // Method.invoke used to wrap anything the provider threw, and a failing showdown start records nothing, as
            // before; so every unchecked provider failure is still swallowed here.
            // 此前 Method.invoke 会包装提供方抛出的任何异常，且谢幕启动失败时不记录；因此这里仍吞掉提供方的所有非受检失败。
            return;
        }
        GameRecordManager.recordGlobalEvent(world, SHADOW_SHOWDOWN_START, null, null);
    }

    private static boolean componentBoolean(PlayerEntity player, Predicate<ShadowJesterPlayerComponent> query) {
        if (player == null) {
            return false;
        }
        // The component lookup stays outside the catch, as the former key lookup was.
        // 组件查找与此前的键查找一样位于 catch 之外。
        ShadowJesterPlayerComponent component = ShadowJesterPlayerComponent.KEY.get(player);
        try {
            return query.test(component);
        } catch (RuntimeException | Error providerFailure) {
            // Method.invoke used to wrap anything the provider threw, which read as false; keep that fail-closed answer.
            // 此前 Method.invoke 会包装提供方抛出的任何异常并视为 false；保持这一失败即关闭的结果。
            return false;
        }
    }
}
