package dev.caecorthus.sparkwitch.roles.civilian.seeker.hit;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCallbackPhases;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * AttackEntityCallback in phase {@code seeker_device}, registered once from common code and therefore firing on both
 * sides: the client answers SUCCESS for a foreign device so Fabric sends the attack packet itself (later listeners, such
 * as the shuriken's "FAIL on any entity" guard, would otherwise swallow it) and FAIL for its own device; the server
 * breaks through {@code SeekerDeviceHits.onMelee} (it fires at {@code ServerPlayerEntity#attack} HEAD, before Wathe's
 * player-only attack wrapper and before vanilla's spectator branch). Spectators and every non-device target PASS.
 * The client never learns ownership beyond its own synced device ids; the server decides every break.
 * 在 {@code seeker_device} 阶段注册的 AttackEntityCallback；由通用代码注册一次，因此两端都会触发：客户端对他人设备返回
 * SUCCESS，让 Fabric 自行发送攻击包（否则后续监听器如手里剑“对任何实体返回 FAIL”会吞掉它），对自己的设备返回 FAIL；
 * 服务端经 {@code SeekerDeviceHits.onMelee} 打坏设备（它在 {@code ServerPlayerEntity#attack} HEAD 触发，早于 Wathe
 * 只认玩家的攻击包装和原版旁观分支）。旁观者及所有非设备目标返回 PASS。客户端除自己同步到的设备 id 外无从得知归属，
 * 每次损坏都由服务端决定。
 */
public final class SeekerDeviceAttackHandlers {
    private static boolean registered;

    private SeekerDeviceAttackHandlers() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SeekerCallbackPhases.ensureOrdered();
        AttackEntityCallback.EVENT.register(SeekerCallbackPhases.DEVICE, SeekerDeviceAttackHandlers::onAttack);
    }

    static ActionResult onAttack(PlayerEntity player, World world, Hand hand, Entity entity,
                                 @Nullable EntityHitResult hitResult) {
        if (!(entity instanceof SeekerDeviceEntity device) || player == null || player.isSpectator()) {
            return ActionResult.PASS;
        }
        if (world.isClient()) {
            return SeekerDeviceRaycast.isOwnDevice(player, device) ? ActionResult.FAIL : ActionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayerEntity attacker)) {
            return ActionResult.FAIL;
        }
        return SeekerDeviceHits.onMelee(attacker, device);
    }
}
