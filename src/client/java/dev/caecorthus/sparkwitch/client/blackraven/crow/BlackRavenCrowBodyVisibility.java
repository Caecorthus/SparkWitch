package dev.caecorthus.sparkwitch.client.blackraven.crow;

import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenInstinctVeto;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowForm;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Client presentation of the body left behind in Raven Form (plan §2.6, §3.6 "隐身"): vanilla invisibility hides the
 * model and the vanilla name tag but still draws held items, armour and Wathe's {@code RoleNameRenderer} label, so the
 * body is not drawn at all ({@code BlackRavenCrowBodyRenderMixin}) and every name-tag raycast passes through it
 * ({@code WraithNameTagPassThrough}). Only viewers who may see spectator information (spectator or creative and not a
 * living participant: dead spectators, admins) keep vanilla behaviour, exactly the instinct veto's exemption
 * ({@code BlackRavenInstinctVeto.seesSpectatorInformation}); a living participant in spectator mode (a Rift Gate
 * occupant, a feign-dead Fiend) does not see the body. While the local player flies the crow, its own name-tag raycasts
 * (cast from the frozen body) pass through everything, so nobody walking past the body is labelled on its screen. Reads
 * only the synced active bit.
 * 化鸦时被留在原地的本体的客户端展示（计划 §2.6、§3.6"隐身"）：原版隐身只隐藏模型与原版名牌，仍会绘制手持物、护甲与 Wathe 的
 * RoleNameRenderer 标签，因此本体完全不绘制（BlackRavenCrowBodyRenderMixin），且所有名牌射线都穿过它（WraithNameTagPassThrough）。
 * 只有可查看旁观信息的观察者（旁观或创造模式且不是存活参与者：死亡旁观者、管理员）保持原版表现，与本能否决的豁免完全一致
 * （BlackRavenInstinctVeto.seesSpectatorInformation）；处于旁观模式的存活参与者（传送门内的玩家、假死的魔人）看不到本体。
 * 本地玩家操控乌鸦时，其自身的名牌射线（从冻结的本体发出）穿过一切，因此有人经过本体时不会在其屏幕上显示名牌。只读取同步的在场标记。
 */
public final class BlackRavenCrowBodyVisibility {
    private BlackRavenCrowBodyVisibility() {
    }

    /** {@code BlackRavenCrowBodyRenderMixin}: skip rendering this entity for the local viewer. / 对本地观察者跳过渲染该实体。 */
    public static boolean hidesFromLocalViewer(@Nullable Entity entity) {
        if (!(entity instanceof PlayerEntity) || !SparkWitchServerConnection.isConfirmedServer()) {
            return false;
        }
        return hidesBody(MinecraftClient.getInstance().player, entity);
    }

    /** A hidden body, for a viewer without spectator information. / 对不可查看旁观信息的观察者隐藏的本体。 */
    public static boolean hidesBody(@Nullable PlayerEntity viewer, @Nullable Entity entity) {
        return BlackRavenCrowForm.isHiddenBody(entity)
                && hides(BlackRavenInstinctVeto.seesSpectatorInformation(viewer));
    }

    /**
     * Name-tag raycast narrowing for {@code WraithNameTagPassThrough}: a hidden body is never a label target, and a
     * viewer flying the crow labels nobody. / 名牌射线收窄：被隐藏的本体从不是标签目标，操控乌鸦的观察者不给任何人显示名牌。
     */
    public static boolean nameTagPassesThrough(@Nullable PlayerEntity viewer, @Nullable Entity candidate) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return false;
        }
        if (viewer != null && BlackRavenCrowForm.isInCrowForm(viewer)) {
            return true;
        }
        return hidesBody(viewer, candidate);
    }

    /**
     * Pure: viewers with spectator information (dead spectators, admins) see everything.
     * 纯判定：可查看旁观信息的观察者（死亡旁观者、管理员）看到一切。
     */
    static boolean hides(boolean viewerSeesSpectatorInformation) {
        return !viewerSeesSpectatorInformation;
    }
}
