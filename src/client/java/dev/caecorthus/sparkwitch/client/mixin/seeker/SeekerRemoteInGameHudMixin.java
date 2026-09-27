package dev.caecorthus.sparkwitch.client.mixin.seeker;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;

/**
 * CCTV crosshair and camera-player handling while viewing; hotbar kept.
 * TODO(WP-10b): stub until the owning work package adds its injectors. / 待 WP-10b 添加注入器。
 * 观看期间的 CCTV 准星与相机玩家处理；保留快捷栏。
 */
@Mixin(InGameHud.class)
public abstract class SeekerRemoteInGameHudMixin {
}
