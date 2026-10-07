package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.util.hitscan.PlayerHitboxHistory;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Applies one silent, non-refreshable delayed mark on right click. */
public final class FeatherBladeItem extends Item {
    public FeatherBladeItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (SparkTraitsKillerBridge.blocksWeaponAction(user, stack)) {
            return TypedActionResult.fail(stack);
        }
        if (world.isClient) {
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity serverUser)) {
            return TypedActionResult.fail(stack);
        }
        // The Black Raven aimed at its delayed client view of others, so the server tests their rewound volumes.
        // 黑羽鸦瞄准的是客户端延迟画面中的其他玩家，因此服务端改为检测其回溯后的命中体积。
        BlackRavenTargeting.Aim<ServerPlayerEntity> aim = BlackRavenTargeting.findAimedPlayer(serverUser,
                serverUser.getServerWorld().getPlayers(),
                candidate -> PlayerHitboxHistory.hitVolumes(
                        serverUser, candidate, BlackRavenTargeting.FEATHER_BOX_EXPANSION));
        // Seeker seam: a nearer Seeker device within reach absorbs the blade and breaks; nobody is marked.
        // 搜寻者接缝：射程内更近的搜寻者设备吸收羽刃并被打坏；不标记任何人。
        ServerPlayerEntity target = SeekerDeviceHits.onFeatherBladeFired(serverUser,
                aim == null ? null : aim.player(), BlackRavenRules.FEATHER_REACH);
        UUID matchId = BlackRavenMatch.currentId();
        BlackRavenMarkPlayerComponent mark = target == null ? null : BlackRavenMarkPlayerComponent.KEY.get(target);
        boolean allowed = BlackRavenRules.canMark(
                BlackRavenRules.isBlackRaven(GameWorldComponent.KEY.get(world).getRole(serverUser)),
                GameFunctions.isPlayerPlayingAndAlive(serverUser),
                target != null && VendettaInteractionService.isOrdinaryAliveOrBoundKillerTarget(serverUser, target),
                target == serverUser,
                mark != null && mark.hasMark(),
                // Sight and reach come from the rewound hit, not the current position: the aim is block-clipped.
                // 可见性与距离取自回溯命中处而非当前位置：瞄准射线已被方块截断。
                target != null,
                target == null || aim == null ? Double.POSITIVE_INFINITY : aim.feetDistanceSquared()
        );
        if (!allowed || matchId == null || mark == null
                || !mark.mark(serverUser.getUuid(), world.getTime() + BlackRavenRules.MARK_DURATION_TICKS, matchId)) {
            return TypedActionResult.fail(stack);
        }
        serverUser.getItemCooldownManager().set(this, BlackRavenRules.FEATHER_COOLDOWN_TICKS);
        return TypedActionResult.success(stack);
    }
}
