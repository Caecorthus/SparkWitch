package dev.caecorthus.sparkwitch.roles.civilian.fisher.swordfish;

import net.minecraft.server.network.ServerPlayerEntity;

/** Server validation of a Swordfish stab, consumption and friendly-fire death. / 剑鱼刺杀校验、消耗与小脑。WP3 stub. */
public final class SwordfishStabService {
    private SwordfishStabService() {
    }

    public static void register() {
    }

    /** Called from the server release of {@link SwordfishItem}. / 由剑鱼的服务端松手调用。 */
    public static void recordServerRelease(ServerPlayerEntity player, int heldTicks) {
    }

    /** Handles {@link SwordfishStabC2SPayload}; the payload is untrusted. / 处理刺杀包；包内容不可信。 */
    public static void use(ServerPlayerEntity player, int targetEntityId) {
    }
}
