package dev.caecorthus.sparkwitch.client.witchmaiden.skin;

/**
 * The bilingual README written once into the skins directory ({@link MiniWitherSkinFolder#ensureReadme}). Keep it in
 * step with the folder rules in {@link MiniWitherSkinFolder}.
 * 只写入一次到皮肤目录的双语 README（{@link MiniWitherSkinFolder#ensureReadme}）。内容须与 {@link MiniWitherSkinFolder}
 * 的文件夹规则保持一致。
 */
final class MiniWitherSkinReadme {
    static final String TEXT = """
            SparkWitch - Mini Wither skins
            ==============================

            Put each skin in its own folder here. The folder name is the skin id: use only a-z, 0-9, _ . -
            Pick a skin in Options > Skin Customization > Mini Wither Appearance, and press Reload there after you
            change any file. A skin only changes how every Mini Wither looks and sounds on YOUR game; nobody else sees
            or hears it. Any model is scaled automatically to the vanilla Mini Wither's size.

            Layout of one skin folder (every part is optional):

              mini_wither/cirno/
                pink_dye.json            the model (Bedrock geometry, e.g. a Blockbench "Bedrock Model" or YSM export)
                pink_dye.png             its texture
                idle.animation.json      Bedrock animations (any *.animation.json here)
                skin.json                optional settings, see below
                skull/                   the normal skull projectile, same rules (skull.geo.json + skull.png ...)
                  blessed.png            optional: lets the blue (Conscience) skull use this model too
                  crimson.png            optional: lets the red (crimson) skull use this model too
                sounds/                  replacement sounds (.ogg)

            Without skin.json:
              - the model is the only *.json in the folder that holds Bedrock geometry (two or more is an error);
              - the texture is the only *.png in the folder;
              - every *.animation.json is loaded, and an animation plays for the state its name ends with:
                .idle   .move / .walk / .fly   .summon / .spawn / .appear   .shoot / .attack   .crimson
              - the head bone (turned toward the last shot) is Head, head, AllHead or bone_head;
              - bones whose name starts with "ysmGlow" glow, but only in the settings preview; in the world they follow
                the world's light like the rest of the model, so a skin never makes a Mini Wither easier to spot in the
                dark.
            A folder with sounds only keeps the vanilla Wither model.

            Skull kinds stay unmistakable: only the normal skull uses the skin's skull model. The blue (Conscience) and
            red (crimson) skulls use it only when that kind has its own texture (skull/blessed.png, skull/crimson.png,
            or the "blessedTexture" / "crimsonTexture" keys); otherwise they are drawn as the vanilla skull of that
            kind.

            Sounds: sounds/<name>.ogg, where <name> is one of
              idle, shoot, summon, dismiss, unlock, crimson_shoot, crimson_hit, burst
            Add variants as idle1.ogg, idle2.ogg or idle_1.ogg ...; one is picked at random each time. Stereo files
            are mixed down to mono so you can still hear where they come from.

            skin.json (every key is optional; paths are relative to the skin folder and must stay inside it):
              {
                "name": "Cirno",
                "model": "pink_dye.json", "geometry": "geometry.npc.alex", "texture": "pink_dye.png",
                "animations": ["cirno.animation.json"],
                "states": {"idle": ["animation.cirno.idle"], "move": "animation.cirno.walk",
                           "summon": "...", "shoot": "...", "crimson": "..."},
                "head": "Head", "glowPrefix": "ysmGlow", "translucent": false,
                "skull": {"model": "skull/skull.geo.json", "texture": "skull/skull.png",
                          "blessedTexture": "skull/blessed.png", "crimsonTexture": "skull/crimson.png",
                          "animations": [], "states": {"idle": "..."}, "translucent": false},
                "sounds": {"idle": ["sounds/a.ogg", "sounds/b.ogg"]}
              }

            Limits:
              - .json up to 16 MiB; .png up to 4096x4096 px and 16 MiB; .ogg up to 8 MiB each, 32 sound files at most;
              - all files of one skin together up to 48 MiB, and at most 32 .json files searched for the models (folder
                and skull/ together; set "model" in skin.json to skip the search). Going over is an error.
              - Animations, per model: 8 .animation.json files, 64 animations and 8 animations per state at most. Extra
                ones are ignored (the first ones in file order are kept) and noted in the game log.

            Example (Cirno): create the folder mini_wither/cirno, copy pink_dye.json and pink_dye.png into it, then
            press Reload in the Mini Wither Appearance screen and choose "cirno".


            SparkWitch - 小凋灵皮肤
            =======================

            每个皮肤放在这里的一个单独文件夹中。文件夹名就是皮肤 id：只能使用 a-z、0-9、_ . -
            在 选项 > 皮肤自定义 > 小凋灵外观 中选择皮肤；修改任何文件后，请在那里点击“重新加载”。皮肤只改变你自己游戏中
            所有小凋灵的外观与声音，其他玩家看不到也听不到。任何模型都会自动缩放到原版小凋灵的大小。

            单个皮肤文件夹的结构（每一部分都是可选的）：

              mini_wither/cirno/
                pink_dye.json            模型（Bedrock 几何，例如 Blockbench 的“基岩版模型”或 YSM 导出）
                pink_dye.png             模型贴图
                idle.animation.json      Bedrock 动画（此处任何 *.animation.json）
                skin.json                可选设置，见下文
                skull/                   普通头颅弹体，规则相同（skull.geo.json + skull.png ……）
                  blessed.png            可选：让蓝色（善良）头颅也使用此模型
                  crimson.png            可选：让红色（赤首）头颅也使用此模型
                sounds/                  替换声音（.ogg）

            没有 skin.json 时：
              - 模型为文件夹中唯一包含 Bedrock 几何的 *.json（两个及以上会报错）；
              - 贴图为文件夹中唯一的 *.png；
              - 加载所有 *.animation.json，动画按名字结尾播放在对应状态：
                .idle（待机）  .move / .walk / .fly（移动）  .summon / .spawn / .appear（召唤）
                .shoot / .attack（射击）  .crimson（赤首）
              - 头部骨骼（朝上一次射击方向转动）为 Head、head、AllHead 或 bone_head；
              - 名字以 “ysmGlow” 开头的骨骼只在设置界面的预览中发光；在世界中它们和模型其余部分一样随环境光照变化，
                因此皮肤绝不会让小凋灵在黑暗中更容易被发现。
            只有声音的文件夹会保留原版凋灵模型。

            头颅种类始终一眼可辨：只有普通头颅使用皮肤的头颅模型。蓝色（善良）与红色（赤首）头颅只有在该种类有自己的贴图
            （skull/blessed.png、skull/crimson.png，或 "blessedTexture" / "crimsonTexture" 键）时才使用它，否则按该种类的
            原版头颅绘制。

            声音：sounds/<名字>.ogg，<名字> 为以下之一
              idle（环境）、shoot（射击）、summon（召唤）、dismiss（收回）、unlock（解锁）、
              crimson_shoot（发射赤首）、crimson_hit（赤首命中）、burst（爆裂）
            变体命名为 idle1.ogg、idle2.ogg 或 idle_1.ogg ……，每次随机选一个。立体声文件会混成单声道，以保留方位感。

            skin.json（所有键都是可选的；路径相对于皮肤文件夹，且必须留在其中）：格式见上方英文部分的示例。

            限制：
              - .json 最大 16 MiB；.png 最大 4096x4096 像素且 16 MiB；.ogg 每个最大 8 MiB，声音文件最多 32 个；
              - 一个皮肤的所有文件合计最大 48 MiB，查找模型时最多检查 32 个 .json 文件（文件夹与 skull/ 合计；在 skin.json
                中设置 "model" 可跳过查找）。超出即报错。
              - 动画（每个模型）：最多 8 个 .animation.json 文件、64 个动画，每种状态最多 8 个动画。多出的会被忽略（按文件顺序
                保留前面的），并记录在游戏日志中。

            示例（琪露诺）：新建文件夹 mini_wither/cirno，把 pink_dye.json 和 pink_dye.png 复制进去，然后在小凋灵外观
            界面点击“重新加载”并选择 “cirno”。
            """;

    private MiniWitherSkinReadme() {
    }
}
