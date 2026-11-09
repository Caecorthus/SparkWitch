package dev.caecorthus.sparkwitch.client.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkStrengthPartygoerCompat;
import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.net.SaviorGunDropS2CPayload;
import dev.caecorthus.sparkwitch.net.SaviorMuzzleS2CPayload;
import dev.caecorthus.sparkwitch.net.SaviorPistolShootC2SPayload;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorParticles;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorPistolItem;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorRules;
import dev.doctor4t.wathe.item.RevolverItem;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;

/**
 * Client half of the Savior pistol. The target pick is Wathe's revolver pick taken as an add-on gun
 * ({@link #pickTargetId}); the recoil, local muzzle flash, the muzzle packet for observers and the local gun removal on
 * an innocent-kill punishment all mirror Wathe's revolver.
 * 救世主手枪的客户端部分。选靶按附属枪械方式沿用 Wathe 左轮选靶（{@link #pickTargetId}）；后坐、本地枪口火焰、给观察者的
 * 枪口数据包、误杀惩罚时的本地手枪移除，全部对应 Wathe 左轮。
 */
public final class SaviorClient {
    private SaviorClient() {
    }

    public static void register() {
        ParticleFactoryRegistry.getInstance().register(SaviorParticles.GUNSHOT,
                SaviorFlashParticle.GunshotFactory::new);
        SaviorLightsaberAnimation.register();
        SaviorLightsaberDrawAnimation.register();
        SaviorDomainClient.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> SaviorHandFlash.tick());
        SaviorPistolItem.installClientShot(SaviorClient::shoot);
        ClientPlayNetworking.registerGlobalReceiver(SaviorMuzzleS2CPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> spawnTrackedMuzzle(client, payload.shooterId()));
        });
        ClientPlayNetworking.registerGlobalReceiver(SaviorGunDropS2CPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> removeLocalPistol(client.player));
        });
    }

    /**
     * Connection edges ({@code SparkWitchClient.releaseClosedWorldPlayers}, already on the client thread): drops every
     * Savior client cache keyed by player or held stack (hums, ignition and draw state, muzzle positions, the hand-render
     * mark), so none of them keeps a closed ClientWorld alive or leaks into the next world.
     * 连接边界（{@code SparkWitchClient.releaseClosedWorldPlayers}，已在客户端线程）：丢弃所有按玩家或手持物品堆记录的救世主
     * 客户端缓存（嗡鸣、点火与拔出状态、枪口位置、手部渲染标记），使它们都不会让已关闭的 ClientWorld 继续存活或带入下一个世界。
     */
    public static void releaseClosedWorld() {
        SaviorLightsaberAnimation.reset();
        SaviorLightsaberDrawAnimation.reset();
        SaviorMuzzleTracker.clear();
        SaviorHandRenderScope.reset();
    }

    private static void shoot(PlayerEntity player, ItemStack stack) {
        ClientPlayNetworking.send(new SaviorPistolShootC2SPayload(pickTargetId(player)));
        player.setPitch(player.getPitch() - SaviorRules.PISTOL_RECOIL_PITCH);
        // Wathe: the first-person flash is hand-space, the world flash is only used outside first person.
        // 与 Wathe 相同：第一人称用手的空间火焰，世界粒子只在不处于第一人称时生成。
        SaviorHandFlash.spawn();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.getPerspective() != Perspective.FIRST_PERSON) {
            SaviorMuzzleTracker.spawnLocalFlash(player);
        }
    }

    /**
     * The revolver's own pick, the add-on gun precedent (CONTEXT "Add-on gun world hits", SparkStrength's Serial Killer
     * pistols): {@code RevolverItem.getGunTarget} carries every client hook on the revolver ray (SparkTraits Marksman
     * range, SparkWitch's active-Wraith pass-through, the strictly nearer Magician puppet and Raven Form crow);
     * {@code SparkWitchApi.preferNearerGunWorldTarget} adds the strictly nearer Seeker device that SparkWitch wraps
     * inside {@code RevolverItem#use}; {@code RevolverItem.resolveTargetFromHitResult} turns it into the id a revolver
     * sends (the entity hit, the sleeping player on an aimed bed with the Wraith bed miss, else -1). The id may name a
     * puppet, crow, device or Partygoer ({@code SparkStrengthPartygoerCompat.preferNearer}, the strictly nearer one,
     * as SparkStrength's Serial Killer pistols do); the server re-validates and resolves it. Not covered:
     * SparkStrength's drone pick, which only wraps the call inside {@code RevolverItem#use} and has no public seam.
     * 左轮自身的选靶，沿用附属枪械先例（CONTEXT「Add-on gun world hits」，SparkStrength 连环杀手手枪）：
     * {@code RevolverItem.getGunTarget} 带有左轮射线上的全部客户端钩子（SparkTraits 神射手射程、SparkWitch 激活冤魂穿透、
     * 严格更近的魔术师皮套与化鸦乌鸦）；{@code SparkWitchApi.preferNearerGunWorldTarget} 补上 SparkWitch 包装在
     * {@code RevolverItem#use} 内、严格更近的搜寻者设备；{@code RevolverItem.resolveTargetFromHitResult} 换算成左轮会发送的
     * id（命中的实体、所瞄床上的睡眠玩家并含冤魂床位未命中，否则 -1）。id 可能指向皮套、乌鸦、设备或派对客
     * （{@code SparkStrengthPartygoerCompat.preferNearer}，严格更近者，与 SparkStrength 连环杀手手枪相同），由服务端复核并
     * 解析。不覆盖：SparkStrength 无人机选靶，它只包装 {@code RevolverItem#use} 内的调用且没有公开接缝。
     */
    private static int pickTargetId(PlayerEntity player) {
        HitResult aim = SparkWitchApi.preferNearerGunWorldTarget(player, RevolverItem.getGunTarget(player),
                SaviorRules.PISTOL_CLIENT_RANGE);
        aim = SparkStrengthPartygoerCompat.preferNearer(player, aim, SaviorRules.PISTOL_CLIENT_RANGE);
        return RevolverItem.resolveTargetFromHitResult(player.getWorld(), aim);
    }

    private static void spawnTrackedMuzzle(MinecraftClient client, String shooterId) {
        if (client.world == null) {
            return;
        }
        PlayerEntity shooter;
        try {
            shooter = client.world.getPlayerByUuid(UUID.fromString(shooterId));
        } catch (IllegalArgumentException ignored) {
            return;
        }
        // The local shooter already spawned its flash inside use(). / 本地射手已在 use() 中生成火焰。
        if (shooter == null || shooter == client.player) {
            return;
        }
        SaviorMuzzleTracker.spawnTrackedFlash(shooter);
    }

    private static void removeLocalPistol(ClientPlayerEntity player) {
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(SparkWitchItems.saviorPistol())) {
                inventory.removeStack(slot, 1);
                return;
            }
        }
    }
}
