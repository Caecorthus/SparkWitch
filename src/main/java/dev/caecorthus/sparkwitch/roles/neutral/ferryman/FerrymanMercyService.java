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
 * consumed with the body in every case, so a second destroyer can never be paid twice. Only a Charon still playing and
 * alive is paid, and a Charon destroying its own ferried body earns nothing and marks nobody.
 * 被破坏的已摆渡遗体（怜悯）会再付给摆渡人一份报酬，并给动手的人挂上「瞥视」：收走遗体的裹尸袋与吃掉遗体的秃鹫都算
 * 破坏（所有者设定）。无论如何标记都随遗体一起消耗，因此第二个破坏者不会重复领赏。只有仍在局内存活的摆渡人才会获得
 * 报酬；摆渡人自己破坏自己摆渡的遗体既不得钱也不标记任何人。
 */
public final class FerrymanMercyService {
    private static final String BROKEN_KEY = "message.sparkwitch.ferryman.mercy.broken";

    private FerrymanMercyService() {
    }

    /** What a destroyed ferried body does for one Charon that holds its mark. / 被破坏的已摆渡遗体对持有其标记的摆渡人的效果。 */
    enum Outcome {
        /** The mark is spent and nothing else happens. / 只消耗标记，不产生其他效果。 */
        CONSUME_ONLY,
        /** The mark is spent, the Charon is paid and a foreign destroyer is marked. / 消耗标记、支付报酬并标记外人。 */
        PAY
    }

    static Outcome outcome(boolean activeFerryman, boolean selfDestroyer) {
        return activeFerryman && !selfDestroyer ? Outcome.PAY : Outcome.CONSUME_ONLY;
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
            if (!component.removeFerried(bodyOwner)) {
                continue;
            }
            boolean self = destroyer != null && destroyer.getUuid().equals(player.getUuid());
            if (outcome(FerrymanParticipation.isActiveFerryman(player), self) != Outcome.PAY) {
                continue;
            }
            PlayerShopComponent.KEY.get(player).addToBalance(FerrymanRules.MERCY_BREAK_GOLD);
            if (destroyer != null) {
                component.addGaze(destroyer.getUuid());
            }
            // An alive spectator (swallowed, inside a Rift Gate) is paid silently: a world sound at its position would
            // give the hidden spot away.
            // 存活的旁观者（被吞下、在传送门内）静默领取报酬：在其位置播放世界音效会暴露隐藏位置。
            if (!player.isSpectator()) {
                player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BLOCK_SOUL_SOIL_BREAK, SoundCategory.PLAYERS, 0.6F, 0.7F);
            }
            player.sendMessage(Text.translatable(BROKEN_KEY, FerrymanRules.MERCY_BREAK_GOLD), true);
        }
    }
}
