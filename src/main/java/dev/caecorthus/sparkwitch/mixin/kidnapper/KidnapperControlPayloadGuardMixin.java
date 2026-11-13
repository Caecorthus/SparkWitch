package dev.caecorthus.sparkwitch.mixin.kidnapper;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaTargetingPacketGuard;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlGuards;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * External seam (Fabric networking internals, like the stun, hypnosis, Seeker, Riftwalker and crow-body payload
 * guards): wraps the single main-thread hand-off in {@code receive} so a direct weapon payload from a player under
 * Knockout Drug control is dropped on the server thread, atomically with the handler it would have run. Wathe's gun
 * and knife receivers check neither the Fabric use callbacks nor the control state, and the victim is teleported onto
 * the Kidnapper every tick, so without this a modified client shoots or stabs its captor at point-blank range.
 * The payload set is reused, not copied: {@link VendettaTargetingPacketGuard#isTargetPayload} (Wathe knife stab and
 * gun shot, the Savior pistol, SparkStrength's Serial Killer pistol and Bandit revolver), the weapon payloads an
 * unmodified client sends only from the attack / use keys the control already locks. Role-skill and shop payloads are
 * deliberately not dropped here: the client lock does not stop them either (owner decision pending). On Netty only the
 * payload's class and id are checked; the control state is read inside the scheduled Runnable. Every other payload,
 * and every sender who is not controlled, is scheduled unchanged. Chains with the other
 * {@code MinecraftServer#execute} wraps.
 * 外部接缝（Fabric 网络内部实现，与眩晕、催眠、搜寻者、隙行者、化鸦本体的数据包拦截相同）：包装 {@code receive} 中唯一一次
 * 移交主线程的调用，使处于迷药控制下的玩家发出的直接武器数据包在服务端主线程上、与其原本要执行的处理器原子地被丢弃。
 * Wathe 的枪与刀接收器既不经过 Fabric 使用回调，也不检查控制状态，而受害者每刻都被传送到绑架者身上，因此若无此拦截，修改过的
 * 客户端可以贴脸射杀或刺杀绑架者。数据包集合是复用而非复制：{@link VendettaTargetingPacketGuard#isTargetPayload}（Wathe 刀刺
 * 与开枪、救世主手枪、SparkStrength 连环杀手手枪与强盗左轮），即未修改的客户端只会由已被控制锁定的攻击/使用键发出的武器
 * 数据包。职业技能与商店数据包有意不在此丢弃：客户端锁同样不拦截它们（待所有者决定）。Netty 线程上只判断数据包的类与 id；
 * 控制状态在被调度的 Runnable 内读取。其他数据包以及未被控制的发送者一律原样调度。与其他 {@code MinecraftServer#execute}
 * 包装链式叠加。
 */
@Mixin(value = ServerPlayNetworkAddon.class, remap = false)
public abstract class KidnapperControlPayloadGuardMixin {
    @Shadow
    @Final
    private ServerPlayNetworking.Context context;

    @WrapOperation(
            method = "receive(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$PlayPayloadHandler;"
                    + "Lnet/minecraft/network/packet/CustomPayload;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V")
    )
    private void sparkwitch$dropControlledWeaponPayload(
            MinecraftServer server,
            Runnable handler,
            Operation<Void> original,
            @Local(argsOnly = true) CustomPayload payload
    ) {
        if (!VendettaTargetingPacketGuard.isTargetPayload(payload)) {
            original.call(server, handler);
            return;
        }
        original.call(server, (Runnable) () -> {
            if (!KidnapperControlGuards.refusesPayload(context.player())) {
                handler.run();
            }
        });
    }
}
