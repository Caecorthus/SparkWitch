package dev.caecorthus.sparkwitch.mixin.seeker;

import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Server slot-click guard for Seeker device items and for every click while viewing.
 * TODO(WP-07): stub until the owning work package adds its injectors. / 待 WP-07 添加注入器。
 * 服务端槽位点击守卫：保护搜寻者设备物品，观看期间禁止一切点击。
 */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerSeekerItemMixin {
}
