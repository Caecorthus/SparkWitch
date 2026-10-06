package dev.caecorthus.sparkwitch.client.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Client mixin config plugin: skips client mixins that cannot coexist with a renderer mod installed alongside.
 * Sodium (mod id {@code sodium}, shipped by the modpack) {@code @Overwrite}s {@code WorldRenderer.setupTerrain}, and
 * Mixin rejects any injector into an overwritten method while applying, even with {@code require} set to 0, so the
 * client would crash on launch. {@code seeker.SeekerRemoteTerrainGridMixin} is therefore not applied when Sodium is
 * loaded; Sodium's renderer already centres terrain on the camera, so far remote views still draw. Every other client
 * mixin applies unchanged.
 * 客户端 mixin 配置插件：跳过无法与同时安装的渲染模组共存的客户端 mixin。Sodium（模组 id {@code sodium}，整合包自带）以
 * {@code @Overwrite} 替换 {@code WorldRenderer.setupTerrain}，Mixin 在应用阶段拒绝向被覆盖的方法注入，即使把 {@code require} 设为 0
 * 也无效，客户端会在启动时崩溃。因此 Sodium 存在时不应用 {@code seeker.SeekerRemoteTerrainGridMixin}；Sodium 的渲染器本就以
 * 镜头为中心渲染地形，远处的遥控画面仍会绘制。其余客户端 mixin 照常应用。
 */
public final class SparkWitchClientMixinPlugin implements IMixinConfigPlugin {
    static final String SODIUM_MOD_ID = "sodium";
    static final String TERRAIN_GRID_MIXIN =
            "dev.caecorthus.sparkwitch.client.mixin.seeker.SeekerRemoteTerrainGridMixin";

    private boolean sodiumLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        sodiumLoaded = FabricLoader.getInstance().isModLoaded(SODIUM_MOD_ID);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return applies(mixinClassName, sodiumLoaded);
    }

    /** Pure decision, unit-tested without a loader. / 纯判定，可脱离加载器做单元测试。 */
    static boolean applies(String mixinClassName, boolean sodiumLoaded) {
        return !(sodiumLoaded && TERRAIN_GRID_MIXIN.equals(mixinClassName));
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
