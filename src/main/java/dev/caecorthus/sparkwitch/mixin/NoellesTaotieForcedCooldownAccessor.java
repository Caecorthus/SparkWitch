package dev.caecorthus.sparkwitch.mixin;

import org.agmas.noellesroles.taotie.TaotiePlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * External seam (pinned NoellesRoles 1.7.6): read-only access to the private per-round swallow cooldown
 * {@code calculatedSwallowCooldown}, the value every swallow writes, as the forced-cooldown nominal. A NoellesRoles
 * member, hence {@code remap = false}. Only {@code compat.cooldown.NoellesTaotieSwallowCooldownStore} casts to this
 * interface; the Seeker keeps its own accessor. Server only.
 * 外部接缝（锁定 NoellesRoles 1.7.6）：只读访问私有的按回合吞噬冷却 calculatedSwallowCooldown（每次吞噬写入的值），
 * 作为强制冷却的标准冷却。该字段属于 NoellesRoles，因此使用 remap = false。只有
 * compat.cooldown.NoellesTaotieSwallowCooldownStore 会转换为此接口；搜寻者保留自己的访问器。仅服务端。
 */
@Mixin(value = TaotiePlayerComponent.class, remap = false)
public interface NoellesTaotieForcedCooldownAccessor {
    @Accessor("calculatedSwallowCooldown")
    int sparkwitch$getForcedCooldownSwallowNominal();
}
