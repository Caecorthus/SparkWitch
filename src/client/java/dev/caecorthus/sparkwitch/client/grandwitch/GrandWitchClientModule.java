package dev.caecorthus.sparkwitch.client.grandwitch;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.text.Text;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.net.GrandWitchRecruitC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

/** Recruitment owns only key 2; Witch Factor uses the shared primary dispatch. / 招募只拥有二技能键；魔女因子沿用主技能分发。 */
public final class GrandWitchClientModule {
    private static boolean registered;

    private GrandWitchClientModule() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        SecondaryAbilityRegistry.register(SparkWitchRoles.GRAND_WITCH_ID, new SecondaryAbilityHandler() {
            @Override
            public void onPressed(MinecraftClient client) {
                if (client.player == null
                        || !WitchPlayerComponent.KEY.get(client.player).hasUnlockedGrandWitchCeremonialSword()) {
                    return;
                }
                PlayerEntity target = GrandWitchClientTargeting.findAimedPlayer(client.player);
                // The server resolves and validates the aim again; this UUID is only a hint. / 服务端重新校验准心；UUID 仅是提示。
                ClientPlayNetworking.send(new GrandWitchRecruitC2SPacket(target == null ? null : target.getUuid()));
            }
        });
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.isOf(SparkWitchItems.ceremonialSword())) {
                lines.add(Text.translatable("item.sparkwitch.ceremonial_sword.tooltip.unlock"));
                lines.add(Text.translatable("item.sparkwitch.ceremonial_sword.tooltip.attack"));
                lines.add(Text.translatable("item.sparkwitch.ceremonial_sword.tooltip.dash"));
                lines.add(Text.translatable("item.sparkwitch.ceremonial_sword.tooltip.protection"));
            }
        });
        registered = true;
    }
}
