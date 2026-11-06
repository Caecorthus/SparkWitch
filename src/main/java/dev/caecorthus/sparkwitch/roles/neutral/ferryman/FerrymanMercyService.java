package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A ferried body (怜悯) that is destroyed pays its Ferryman one more share and marks whoever did it with 瞥视: the bag
 * that carries the body away and the Vulture that eats it both count as destroying it (owner spec). The mark is
 * consumed with the body, so a second destroyer can never be paid twice.
 * 被破坏的已摆渡遗体（怜悯）会再付给摆渡人一份报酬，并给动手的人挂上「瞥视」：收走遗体的裹尸袋与吃掉遗体的秃鹫都算
 * 破坏（所有者设定）。标记随遗体一起消耗，因此第二个破坏者不会重复领赏。
 */
public final class FerrymanMercyService {
    private static final String BROKEN_KEY = "message.sparkwitch.ferryman.mercy.broken";

    private FerrymanMercyService() {
    }

    /**
     * Server only. Called after the body is already gone (the bag consumed it, the Vulture ate it); a null destroyer
     * still pays, because the body is destroyed either way.
     * 仅服务端。在遗体已经消失之后调用（袋子收走或秃鹫吃掉）；破坏者为 null 时仍发放报酬，因为遗体确实没了。
     */
    public static void onBodyDestroyed(@Nullable PlayerBodyEntity body, @Nullable ServerPlayerEntity destroyer) {
        if (body == null) {
            return;
        }
        UUID bodyOwner = body.getPlayerUuid();
        MinecraftServer server = body.getServer();
        if (bodyOwner == null || server == null) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!FerrymanParticipation.isFerryman(player)) {
                continue;
            }
            FerrymanPlayerComponent component = FerrymanPlayerComponent.KEY.get(player);
            if (!component.hasFerried(bodyOwner)) {
                continue;
            }
            component.removeFerried(bodyOwner);
            PlayerShopComponent.KEY.get(player).addToBalance(FerrymanRules.MERCY_BREAK_GOLD);
            if (destroyer != null) {
                component.addGaze(destroyer.getUuid());
            }
            player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_SOUL_SOIL_BREAK, SoundCategory.PLAYERS, 0.6F, 0.7F);
            player.sendMessage(Text.translatable(BROKEN_KEY, FerrymanRules.MERCY_BREAK_GOLD), true);
        }
    }
}
