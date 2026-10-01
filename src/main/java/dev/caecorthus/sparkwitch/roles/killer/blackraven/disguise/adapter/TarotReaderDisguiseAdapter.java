package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter;

import dev.caecorthus.sparkwitch.roles.civilian.tarotreader.TarotReaderSelectionSessionService;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Disguise parity surface of the Tarot Reader: the shop, divination gate and client snapshot read isRole. Round
 * history, the identity answer and the faction count stay raw, so the claimed role reads as absent and the Raven
 * counts as KILLER.
 * 塔罗师的伪装对等面：商店、占卜门控与客户端快照读取 isRole。本局历史、身份结果与阵营计数保持真实，
 * 因此被扮演的职业显示为未登场，黑羽鸦计为杀手。
 */
public final class TarotReaderDisguiseAdapter implements BlackRavenDisguiseAdapter {
    public static final Identifier ROLE_ID = Identifier.of("sparkwitch", "tarot_reader");
    private static final DisguiseShopSpec SHOP = DisguiseShopSpec.of(
            DisguiseShopSpec.Entry.nativeOnly("sparkwitch_tarot_regular"),
            DisguiseShopSpec.Entry.nativeOnly("sparkwitch_tarot_identity"),
            DisguiseShopSpec.Entry.nativeOnly("sparkwitch_tarot_survival")
    );

    @Override
    public Identifier roleId() {
        return ROLE_ID;
    }

    /** A pending selection dies with the identity, as on a real role change. / 待提交的选择随身份失效，与真实换职一致。 */
    @Override
    public void onExit(ServerPlayerEntity player, DisguiseExitReason reason) {
        TarotReaderSelectionSessionService.clear(player.getUuid());
    }

    @Override
    public DisguiseShopSpec shopSpec() {
        return SHOP;
    }

    @Override
    public boolean moneyVisible() {
        return true;
    }

    @Override
    public DisguiseTaskReward taskReward() {
        return DisguiseTaskReward.SPARKWITCH;
    }
}
