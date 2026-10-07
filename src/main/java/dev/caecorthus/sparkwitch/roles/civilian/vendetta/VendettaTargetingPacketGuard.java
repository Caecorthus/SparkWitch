package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import dev.doctor4t.wathe.util.GunShootPayload;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.minecraft.entity.Entity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;

/**
 * Rejects target payloads before they spend items or emit effects for an isolated Vendetta pair: Wathe's knife stab
 * and gun shot, and the SparkStrength Serial Killer pistol shot, treated exactly like the gun shot (owner 2026-10-07).
 * 在目标数据包消耗物品或生成效果前，拒绝不属于仇杀绑定双方的请求：Wathe 刀刺与开枪，以及与开枪完全同等处理的
 * SparkStrength 连环杀手手枪射击（所有者 2026-10-07）。
 */
public final class VendettaTargetingPacketGuard {
    /**
     * SparkStrength {@code SerialPistolShootC2SPayload}: wire layout VAR_INT target entity id, then VAR_INT hand. It is
     * matched by registered id and read through its registered codec, so no SparkStrength class is named here.
     * SparkStrength 连环手枪开火包：线格式为 VAR_INT 目标实体 id，其后为 VAR_INT 手位。按注册 id 匹配并经其注册编解码器读取，
     * 此处不引用任何 SparkStrength 类。
     */
    public static final Identifier SERIAL_PISTOL_SHOOT_ID = Identifier.of("sparkstrength", "serial_pistol_shoot");

    private VendettaTargetingPacketGuard() {
    }

    public static boolean shouldBlock(ServerPlayerEntity actor, CustomPayload payload) {
        OptionalInt targetEntityId = targetEntityId(actor, payload);
        if (targetEntityId.isEmpty()) {
            return false;
        }

        Entity entity = actor.getServerWorld().getEntityById(targetEntityId.getAsInt());
        if (!(entity instanceof ServerPlayerEntity target)) {
            return false;
        }
        boolean vendettaEndpoint = VendettaInteractionService.isActiveVendetta(actor)
                || VendettaInteractionService.isActiveVendetta(target);
        return vendettaEndpoint && !VendettaInteractionService.isExactPair(actor, target);
    }

    private static OptionalInt targetEntityId(ServerPlayerEntity actor, @Nullable CustomPayload payload) {
        if (payload instanceof KnifeStabPayload knife) {
            return OptionalInt.of(knife.target());
        }
        if (payload instanceof GunShootPayload gun) {
            return OptionalInt.of(gun.target());
        }
        if (payload != null && payload.getId() != null && SERIAL_PISTOL_SHOOT_ID.equals(payload.getId().id())) {
            return leadingVarInt(PayloadTypeRegistryImpl.PLAY_C2S.get(SERIAL_PISTOL_SHOOT_ID), payload,
                    actor.getRegistryManager());
        }
        return OptionalInt.empty();
    }

    /**
     * Re-encodes an already decoded payload with its registered C2S codec and reads the leading VAR_INT. Empty when the
     * type is unregistered or its codec rejects the payload; the shot then fires as before and only the kill-time
     * Vendetta veto applies.
     * 用已注册的 C2S 编解码器重新编码已解码的数据包并读取首个 VAR_INT。类型未注册或编解码器拒绝该包时返回空；此时射击照旧发生，
     * 仅由击杀时的仇杀拦截生效。
     */
    static OptionalInt leadingVarInt(
            @Nullable CustomPayload.Type<RegistryByteBuf, ? extends CustomPayload> type,
            CustomPayload payload,
            DynamicRegistryManager registries
    ) {
        if (type == null || payload == null) {
            return OptionalInt.empty();
        }
        @SuppressWarnings("unchecked")
        PacketCodec<RegistryByteBuf, CustomPayload> codec = (PacketCodec<RegistryByteBuf, CustomPayload>) type.codec();
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), registries);
        try {
            codec.encode(buf, payload);
            return OptionalInt.of(buf.readVarInt());
        } catch (RuntimeException ignored) {
            return OptionalInt.empty();
        } finally {
            buf.release();
        }
    }
}
