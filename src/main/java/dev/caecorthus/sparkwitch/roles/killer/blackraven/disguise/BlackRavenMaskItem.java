package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.net.OpenBlackRavenDisguiseS2CPacket;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Bound transform item: a server-validated use opens one selection session and the Absent Good Roles tab.
 * It never carries a vanilla item cooldown (that would refuse use); the disguise component is the authority.
 * Opening is allowed while the transform is still locked or cooling down: the screen shows the countdown and the
 * server re-validates on select.
 * 绑定的变身物品：经服务端校验的使用会开启一次选择会话并打开“未登场的善良职业”页。从不设置原版物品冷却（否则会拒绝使用），
 * 伪装组件才是权威。变身尚未解锁或冷却中也允许打开：界面显示倒计时，服务端在选择时重新校验。
 */
public final class BlackRavenMaskItem extends Item {
    public BlackRavenMaskItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity player)
                || !stack.isOf(SparkWitchItems.blackRavenMask())
                || !GameWorldComponent.KEY.get(world).isRunning()
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || !BlackRavenRules.isBlackRaven(GameWorldComponent.KEY.get(world).getRole(player))
                || !BlackRavenDisguiseService.isBoundToCurrentMatch(player)
                || !ServerPlayNetworking.canSend(player, OpenBlackRavenDisguiseS2CPacket.ID)) {
            return TypedActionResult.fail(stack);
        }
        if (GrandWitchFearService.denyRoleSkillIfFeared(player)) {
            return TypedActionResult.fail(stack);
        }
        if (SparkTraitsKillerBridge.isRoleSkillBlocked(player)) {
            player.sendMessage(Text.translatable(BlackRavenDisguiseRules.messageKey("blocked")), true);
            return TypedActionResult.fail(stack);
        }
        int session = BlackRavenDisguiseSessions.open(player.getUuid(), player.getServerWorld().getTime());
        ServerPlayNetworking.send(player, new OpenBlackRavenDisguiseS2CPacket(session));
        return TypedActionResult.success(stack);
    }
}
