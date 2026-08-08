package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.doctor4t.wathe.cca.PlayerStaminaComponent;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Central stamina repair for active Wraith identities.
 * 活跃冤魂身份的统一体力修复入口。
 */
public final class WraithStaminaService {
    public static final int INFINITE_MAX_SPRINT_TIME = -1;
    public static final float INFINITE_SPRINTING_TICKS = Integer.MAX_VALUE;

    private WraithStaminaService() {
    }

    public static void ensureInfiniteStamina(PlayerEntity player) {
        if (player == null) {
            return;
        }
        PlayerStaminaComponent stamina = PlayerStaminaComponent.KEY.get(player);
        boolean changed = false;

        // 冤魂可能由好人或其他有限体力职业死亡转化而来，这里主动覆盖旧职业残留的体力上限。
        if (stamina.getMaxSprintTime() != INFINITE_MAX_SPRINT_TIME) {
            stamina.setMaxSprintTime(INFINITE_MAX_SPRINT_TIME);
            changed = true;
        }

        // Wathe 也会把 sprintingTicks>=Integer.MAX_VALUE 视为无限体力；
        // 同时写入该值可以让客户端按键和服务端跳跃体力检查在同一 tick 内立即放行。
        if (stamina.getSprintingTicks() < INFINITE_SPRINTING_TICKS) {
            stamina.setSprintingTicks(INFINITE_SPRINTING_TICKS);
            changed = true;
        }

        // 转化前如果已经因为有限体力进入疲惫状态，冤魂阶段必须立即清掉。
        if (stamina.isExhausted()) {
            stamina.setExhausted(false);
            changed = true;
        }

        if (changed && !player.getWorld().isClient) {
            stamina.sync();
        }
    }
}
