package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStatusComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * External seam: the Seeker's only read of Control Expert state. If the Control Expert module changes, only this
 * file changes; a missing component reads as "not stunned".
 * 外部接缝：搜寻者对控场专家状态的唯一读取点。控场专家模块变更时只需修改此文件；组件缺失时视为“未眩晕”。
 */
public final class SeekerControlExpertBridge {
    private SeekerControlExpertBridge() {
    }

    public static boolean isStunned(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        return ControlExpertStatusComponent.KEY.maybeGet(player)
                .map(ControlExpertStatusComponent::isStunned)
                .orElse(false);
    }
}
