package dev.caecorthus.sparkwitch.client.seeker;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerDeviceKind;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Pure client outline policy for Seeker devices and the owner's breaker mark (plan §3.14, §3.15, owner decision
 * Q12/Q13). Instinct viewers are killers (Wathe's {@code isKiller()}, which SparkTraits extends to Impostors),
 * spectators with spectator information, and the Grand Witch, every accomplice (plain or special) and the Murderous
 * Witch. The owner always sees their own car dimly; cameras are never outlined. During a car or camera view the owner
 * also sees their own body. The hooks feed plain facts in; nothing here touches the client.
 * 搜寻者设备与拥有者损坏者标记的纯客户端描边规则（计划 §3.14、§3.15，所有者决定 Q12/Q13）。有本能的观察者为
 * 杀手（Wathe 的 {@code isKiller()}，SparkTraits 会把内鬼也算进去）、可见旁观信息的旁观者，以及大魔女、所有共犯
 * （普通或特殊）和杀意魔女。拥有者始终以暗色看到自己的小车；摄像头从不描边。小车或摄像头视角期间，拥有者还会看到
 * 自己的本体描边。钩子只传入事实，这里不接触客户端。
 */
public final class SeekerInstinctRules {
    public static final Identifier GRAND_WITCH_ID = SparkWitch.id("grand_witch");
    public static final Identifier ACCOMPLICE_ID = SparkWitch.id("accomplice");
    /** The Bewitched is accomplice-like (C2). / 魔化使属于共犯类（C2）。 */
    public static final Identifier BEWITCHED_ID = SparkWitch.id("bewitched");
    public static final Identifier MURDEROUS_WITCH_ID = SparkWitch.id("murderous_witch");
    private static final Set<Identifier> WITCH_INSTINCT_ROLES =
            Set.of(GRAND_WITCH_ID, ACCOMPLICE_ID, BEWITCHED_ID, MURDEROUS_WITCH_ID);

    /** Outcome of the device outline decision. / 设备描边判定结果。 */
    public enum DeviceOutline {
        /** Not ours to decide; Wathe's default logic applies. / 不由本规则决定，交给 Wathe 默认逻辑。 */
        NONE,
        /** The owner's own car: always-on dim outline. / 拥有者自己的小车：常亮暗色描边。 */
        OWN,
        /** Instinct viewer: outline only while the instinct key is active. / 有本能者：仅在本能键生效时描边。 */
        INSTINCT,
        /** Explicitly never outlined (cameras). / 明确从不描边（摄像头）。 */
        HIDDEN
    }

    private SeekerInstinctRules() {
    }

    /**
     * Grand Witch, any accomplice (plain, Bewitched or special, the last read from the live registry) or Murderous
     * Witch.
     * 大魔女、任一共犯（普通、魔化使或特殊，后者读取实时注册表）或杀意魔女。
     */
    public static boolean isWitchInstinctRole(@Nullable Identifier roleId) {
        return roleId != null
                && (WITCH_INSTINCT_ROLES.contains(roleId) || WitchFactionRules.isAccompliceVariantId(roleId));
    }

    /**
     * Whether the local viewer has car instinct. Spectator information counts regardless of role; killers and the
     * witch roles count only while playing and alive, matching Wathe's own keyed instinct gate.
     * 本地观察者是否拥有小车本能。旁观信息与职业无关；杀手和魔女职业仅在参与且存活时计入，与 Wathe 自身的按键本能门槛一致。
     */
    public static boolean hasInstinct(boolean playingAndAlive, boolean killer, boolean spectatorInformation,
                                      @Nullable Identifier viewerRoleId) {
        if (spectatorInformation) {
            return true;
        }
        return playingAndAlive && (killer || isWitchInstinctRole(viewerRoleId));
    }

    /**
     * Outline decision for one Seeker device. Ownership wins over instinct so the owner never needs the key.
     * 单个搜寻者设备的描边判定。拥有者优先于本能，因此拥有者无需按键。
     */
    public static DeviceOutline deviceOutline(SeekerDeviceKind kind, boolean ownDevice, boolean hasInstinct) {
        if (kind != SeekerDeviceKind.CAR) {
            return DeviceOutline.HIDDEN;
        }
        if (ownDevice) {
            return DeviceOutline.OWN;
        }
        return hasInstinct ? DeviceOutline.INSTINCT : DeviceOutline.NONE;
    }

    /** The synced car entity id names this entity (-1 means no car). / 同步的小车实体 id 指向此实体（-1 表示没有小车）。 */
    public static boolean isOwnCar(int syncedCarEntityId, int entityId) {
        return syncedCarEntityId >= 0 && syncedCarEntityId == entityId;
    }

    /**
     * Whether the owner's breaker-mark outline shows on this player. Hidden while the target is a spectator or
     * swallowed (a swallowed outline would reveal the Taotie), while the target is invisible to the viewer (vanilla
     * still draws an outline silhouette for invisible entities, so an always-on mark would expose e.g. a Phantom;
     * SparkWitch's other always-on player outlines skip invisible targets too), when SparkTraits hides the target from
     * the viewer, or while the viewer is swallowed.
     * 损坏者标记描边是否显示在该玩家上。目标为旁观者或被吞噬（否则会暴露饕餮位置）、目标对观察者隐身（原版仍会为隐身
     * 实体绘制描边轮廓，常亮标记会暴露如幽灵等隐身者；SparkWitch 其他常亮玩家描边同样跳过隐身目标）、SparkTraits
     * 对观察者隐藏目标，或观察者自己被吞噬时均不显示。
     */
    public static boolean showsMark(@Nullable UUID markTarget, int markRemainingTicks, @Nullable UUID targetUuid,
                                    boolean targetSpectating, boolean targetInvisible, boolean targetSwallowed,
                                    boolean targetInstinctHidden, boolean viewerSwallowed) {
        if (markTarget == null || markRemainingTicks <= 0 || !markTarget.equals(targetUuid)) {
            return false;
        }
        return !targetSpectating && !targetInvisible && !targetSwallowed && !targetInstinctHidden && !viewerSwallowed;
    }

    /**
     * Whether the viewer's own body gets the always-on remote-view outline: only while the local remote view is in the
     * car or camera, only on the viewer itself, and never for a spectator. It never outlines another player, so it
     * reveals nothing to anyone but the owner.
     * 观察者自己的本体是否获得遥控视角常亮描边：仅当本地遥控视角处于小车或摄像头、目标就是观察者本人、且观察者不是旁观者时。
     * 它从不描边其他玩家，因此除拥有者本人外不向任何人透露信息。
     */
    public static boolean outlinesOwnBody(@Nullable SeekerSessionMode remoteMode, boolean targetIsViewer,
                                          boolean viewerSpectator) {
        boolean viewing = remoteMode == SeekerSessionMode.CAR || remoteMode == SeekerSessionMode.CAMERA;
        return viewing && targetIsViewer && !viewerSpectator;
    }
}
