package dev.caecorthus.sparkwitch.roles.witch.accomplice.variant;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.world.World;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/**
 * Server-only, never-synced world ledger of the special accomplices used this round. Owned by the Grand Witch
 * recruitment lifecycle: begun at {@code ON_FINISH_INITIALIZE}, cleared at {@code ON_FINISH_FINALIZE}.
 * 仅服务端、从不同步的世界账本，记录本局已使用的特殊共犯；由大魔女招募生命周期管理：
 * {@code ON_FINISH_INITIALIZE} 开始，{@code ON_FINISH_FINALIZE} 清空。
 */
public final class AccompliceVariantRoundComponent extends AccompliceVariantRoundState implements Component {
    public static final ComponentKey<AccompliceVariantRoundComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("accomplice_variant_round"), AccompliceVariantRoundComponent.class);

    public AccompliceVariantRoundComponent(World world) {
    }
}
