package dev.caecorthus.sparkwitch.mixin.kidnapper;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStunRules;
import dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeHypnosisGuards;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlGuards;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * External seam (Fabric networking internals, like the stun, hypnosis, Seeker, Riftwalker and crow-body payload
 * guards): wraps the single main-thread hand-off in {@code receive} so a weapon, role-skill or shop payload from a
 * player under Knockout Drug control is dropped on the server thread, atomically with the handler it would have run.
 * Wathe's gun and knife receivers check neither the Fabric use callbacks nor the control state, and the victim is
 * teleported onto the Kidnapper every tick, so without this a modified client shoots or stabs its captor at
 * point-blank range.
 * Owner decision 2026-11 ("禁掉"): a controlled player uses no role skill and buys nothing either, although the
 * client lock leaves the ability keys and the inventory open. The payload set is reused, not copied:
 * {@link ControlExpertStunRules#isBlockedPayload}, the stun deny-list (Wathe knife, gun and {@code wathe:storebuy},
 * the only shop purchase path, and every classified role-skill payload of NoellesRoles, SparkWitch and
 * SparkStrength), so a new skill payload is still classified in one place. It is asked through
 * {@link NatsumeHypnosisGuards#blocksPayloadWhileLocked}, the hypnosis lock's predicate, which adds the two item-release
 * stabs the stun list leaves to their own handlers (cross sword, Swordfish): those handlers refuse a stunned sender
 * but know nothing of the Knockout Drug, and a controlled player stabs with nothing either. Dropped silently: the
 * victim's screen is black. On Netty only the payload id is checked; the control state is read inside the scheduled Runnable. Every other
 * payload, and every sender who is not controlled, is scheduled unchanged. Chains with the other
 * {@code MinecraftServer#execute} wraps.
 * 外部接缝（Fabric 网络内部实现，与眩晕、催眠、搜寻者、隙行者、化鸦本体的数据包拦截相同）：包装 {@code receive} 中唯一一次
 * 移交主线程的调用，使处于迷药控制下的玩家发出的武器、职业技能或商店数据包在服务端主线程上、与其原本要执行的处理器原子地
 * 被丢弃。Wathe 的枪与刀接收器既不经过 Fabric 使用回调，也不检查控制状态，而受害者每刻都被传送到绑架者身上，因此若无此拦截，
 * 修改过的客户端可以贴脸射杀或刺杀绑架者。
 * 所有者 2026-11 决定（“禁掉”）：被控制的玩家同样不能使用职业技能、不能购物，尽管客户端锁没有锁住技能键与物品栏。数据包集合
 * 是复用而非复制：{@link ControlExpertStunRules#isBlockedPayload}，即眩晕拦截名单（Wathe 刀、枪与唯一的购物路径
 * {@code wathe:storebuy}，以及 NoellesRoles、SparkWitch 与 SparkStrength 所有已归类的职业技能包），因此新增技能包仍只需在
 * 一处归类。判定经由催眠锁的 {@link NatsumeHypnosisGuards#blocksPayloadWhileLocked}：它在眩晕名单之外补上两种由各自处理器
 * 自行拒绝眩晕者的“松手刺击”（十字剑、剑鱼）——这些处理器并不知道迷药，而被控制的玩家同样什么都不能刺。静默丢弃：受害者本就黑屏。Netty 线程上只判断数据包 id；控制状态在被调度的 Runnable 内读取。其他数据包以及未被
 * 控制的发送者一律原样调度。与其他 {@code MinecraftServer#execute} 包装链式叠加。
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
    private void sparkwitch$dropControlledPayload(
            MinecraftServer server,
            Runnable handler,
            Operation<Void> original,
            @Local(argsOnly = true) CustomPayload payload
    ) {
        Identifier payloadId = payload == null || payload.getId() == null ? null : payload.getId().id();
        if (!NatsumeHypnosisGuards.blocksPayloadWhileLocked(payloadId)) {
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
