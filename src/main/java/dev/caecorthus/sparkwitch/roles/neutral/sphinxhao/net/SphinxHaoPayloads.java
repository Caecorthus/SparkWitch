package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * The single registration point of the Sphinx Hao's two C2S payload TYPES ({@code sparkwitch:use_sphinx_hao_infect},
 * {@code sparkwitch:use_sphinx_hao_roar}). Common initializer, so both sides know the types: the client needs them to
 * send, the server to decode. Idempotent: Fabric throws on a second registration of one id, so every caller (the
 * common event hub and the server feature service) goes through here. The server receivers live with the skill
 * services, not here.
 * 狮身豪两个 C2S 数据包类型（{@code sparkwitch:use_sphinx_hao_infect}、{@code sparkwitch:use_sphinx_hao_roar}）的唯一注册点。
 * 在通用初始化器中调用，两端都知道这些类型：客户端需要它们才能发送，服务端需要它们才能解码。幂等：Fabric 对同一 id 的第二次
 * 注册会抛异常，因此所有调用方（通用事件注册中心与服务端功能服务）都经由此处。服务端接收器与技能服务放在一起，不在这里。
 */
public final class SphinxHaoPayloads {
    private static boolean typesRegistered;

    private SphinxHaoPayloads() {
    }

    public static synchronized void registerTypes() {
        if (typesRegistered) {
            return;
        }
        typesRegistered = true;
        PayloadTypeRegistry.playC2S().register(UseSphinxHaoInfectC2SPayload.ID, UseSphinxHaoInfectC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UseSphinxHaoRoarC2SPayload.ID, UseSphinxHaoRoarC2SPayload.CODEC);
    }
}
