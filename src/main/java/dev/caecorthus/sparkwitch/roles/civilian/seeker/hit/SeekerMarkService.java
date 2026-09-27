package dev.caecorthus.sparkwitch.roles.civilian.seeker.hit;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.compat.SparkStrengthTabletCompat;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerDeviceKind;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerTargeting;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Frozen contract: breaker mark (10 s, owner-only, newest replaces oldest) when the owner has a tablet anywhere.
 * Called only by {@code SeekerDeviceService.breakDevice} for attributable sources; car and camera breaks both mark
 * (Q8), while Taotie swallows, recalls and battery depletion never reach here. Server only. Conditions (plan §3.14):
 * a breaker other than the owner; the owner is the owner of record and alive in the round; the owner holds the
 * SparkStrength tablet anywhere in the inventory (the car-item console fallback does not count, the spec says
 * "tablet"); SparkFactionAPI lets the owner's {@code MARK_ACTION_ID} affect the breaker. The mark lives in the
 * owner-only {@code sparkwitch:seeker_status} component (decay is WP-02's tick, rendering WP-05's), so no other client
 * ever learns it; the owner is told only that a mark was applied, never the breaker's name.
 * 冻结契约：拥有者背包任意位置持有平板时标记损坏者（10 秒、仅拥有者可见、新标记替换旧标记）。仅由
 * {@code SeekerDeviceService.breakDevice} 在可归属来源下调用；小车与摄像头被打坏都会标记（Q8），饕餮吞车、回收与电量耗尽
 * 永远不会走到这里。仅服务端。条件（计划 §3.14）：损坏者不是拥有者；拥有者是记录中的拥有者且在本局存活；拥有者背包任意位置
 * 持有 SparkStrength 平板（小车物品兜底不算，规格写的是“平板电脑”）；SparkFactionAPI 允许拥有者的 {@code MARK_ACTION_ID}
 * 影响损坏者。标记保存在仅同步给拥有者的组件中（衰减由 WP-02 负责、渲染由 WP-05 负责），因此其他客户端无从得知；
 * 拥有者只会被告知“已标记”，不会得知损坏者的名字。
 */
public final class SeekerMarkService {
    static final String APPLIED_MESSAGE_KEY = "message.sparkwitch.seeker.mark.applied";

    private SeekerMarkService() {
    }

    public static void onDeviceBroken(ServerPlayerEntity owner, @Nullable ServerPlayerEntity breaker,
                                      SeekerDeviceKind kind) {
        if (owner == null || kind == null || owner.getWorld().isClient()) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(owner.getWorld());
        boolean marks = shouldMark(
                breaker != null,
                breaker != null && SeekerDamageRules.isSamePlayer(owner, breaker),
                () -> SeekerTargeting.isOwnerOfRecord(owner),
                () -> GameFunctions.isPlayerPlayingAndAlive(owner),
                () -> SparkStrengthTabletCompat.hasTabletAnywhere(owner),
                () -> SparkFactionApi.canAffectPlayer(owner, breaker, SeekerRules.MARK_ACTION_ID, game));
        if (!marks) {
            return;
        }
        SeekerStatusComponent component = SeekerStatusComponent.KEY.get(owner);
        // Newest replaces oldest: mark() overwrites the target and restarts the 10 s window. / 新标记覆盖旧标记并重新计时。
        component.apply(component.state().mark(breaker.getUuid(), SeekerRules.MARK_TICKS));
        // Chat, not the action bar, so the device-broken action-bar notice that follows does not hide it.
        // 使用聊天栏而非动作栏，避免随后的设备损坏提示将其覆盖。
        owner.sendMessage(Text.translatable(APPLIED_MESSAGE_KEY), false);
    }

    /** Pure gate in evaluation order. / 按求值顺序排列的纯判定。 */
    static boolean shouldMark(boolean breakerPresent, boolean breakerIsOwner, BooleanSupplier ownerOfRecord,
                              BooleanSupplier ownerAlive, BooleanSupplier ownerHasTablet,
                              BooleanSupplier factionAllows) {
        return breakerPresent
                && !breakerIsOwner
                && ownerOfRecord.getAsBoolean()
                && ownerAlive.getAsBoolean()
                && ownerHasTablet.getAsBoolean()
                && factionAllows.getAsBoolean();
    }
}
