package dev.caecorthus.sparkwitch.mixin.seeker;

import org.agmas.noellesroles.taotie.TaotiePlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * External seam (pinned NoellesRoles 1.7.6): read-only access to the private dynamic swallow cooldown
 * {@code calculatedSwallowCooldown} ({@code clamp(60 - players, 20..50)} seconds, set by {@code initializeForGame}), so
 * a car swallow costs exactly what a player swallow costs without duplicating NoellesRoles' formula. The field is a
 * NoellesRoles member, hence {@code remap = false}; the field name and {@code I} descriptor are pinned by
 * {@code SeekerNoellesTaotieContractTest}. Only {@code NoellesTaotieSeekerBridge} casts to this interface. Server only.
 * 外部接缝（固定 NoellesRoles 1.7.6）：只读访问私有的动态吞噬冷却 {@code calculatedSwallowCooldown}
 * （{@code initializeForGame} 设置，{@code clamp(60 - 人数, 20..50)} 秒），使吞车的代价与吞人完全一致，而无需复制
 * NoellesRoles 的公式。该字段属于 NoellesRoles，因此使用 {@code remap = false}；字段名与 {@code I} 描述符由
 * {@code SeekerNoellesTaotieContractTest} 固定。只有 {@code NoellesTaotieSeekerBridge} 会转换为此接口。仅服务端。
 */
@Mixin(value = TaotiePlayerComponent.class, remap = false)
public interface SeekerTaotieCooldownAccessor {
    @Accessor("calculatedSwallowCooldown")
    int sparkwitch$getCalculatedSwallowCooldown();
}
