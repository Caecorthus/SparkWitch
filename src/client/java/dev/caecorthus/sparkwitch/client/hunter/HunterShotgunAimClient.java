package dev.caecorthus.sparkwitch.client.hunter;

import dev.caecorthus.sparkwitch.roles.killer.hunter.DoubleBarrelShotgunItem;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterShotgunAim;
import dev.caecorthus.sparkwitch.roles.killer.hunter.net.DoubleBarrelAimC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;

/**
 * Client half of the shotgun aim claim: what this screen shows on the ray, i.e. other players' current client boxes
 * (the delayed view the shooter aimed at). Sent from {@code interactItem} HEAD ({@code DoubleBarrelAimClaimMixin}), so
 * every shotgun use packet is preceded by its claim, including the held-trigger repeat that vanilla sends without
 * calling {@code use} while this client still predicts the 4-tick follow-up cooldown. The server decides eligibility
 * and confirms the claim.
 * 猎枪瞄准声明的客户端一半：本画面在射线上显示的内容，即其他玩家当前的客户端箱体（射手所瞄准的延迟画面）。
 * 从 {@code interactItem} 开头（{@code DoubleBarrelAimClaimMixin}）发送，因此每个猎枪使用数据包之前都有其声明，
 * 包括按住右键的重复使用：当本客户端仍预判 4 刻的连发冷却时，原版不调用 {@code use} 却照样发送使用数据包。
 * 资格判定与确认由服务端负责。
 */
public final class HunterShotgunAimClient {
    private HunterShotgunAimClient() {
    }

    public static void beforeUsePacket(PlayerEntity shooter, Hand hand) {
        if (!(shooter instanceof ClientPlayerEntity player) || player != MinecraftClient.getInstance().player
                || !(player.getStackInHand(hand).getItem() instanceof DoubleBarrelShotgunItem)
                || !ClientPlayNetworking.canSend(DoubleBarrelAimC2SPacket.ID)) {
            return;
        }
        DoubleBarrelShotgunItem.Aim aim = DoubleBarrelShotgunItem.aim(player);
        List<HunterShotgunAim.Candidate> candidates = new ArrayList<>();
        for (AbstractClientPlayerEntity other : player.clientWorld.getPlayers()) {
            if (other != player) {
                candidates.add(new HunterShotgunAim.Candidate(other.getId(), other.getBoundingBox()));
            }
        }
        ClientPlayNetworking.send(new DoubleBarrelAimC2SPacket(
                HunterShotgunAim.claimOrder(aim.eye(), aim.end(), candidates)));
    }
}
