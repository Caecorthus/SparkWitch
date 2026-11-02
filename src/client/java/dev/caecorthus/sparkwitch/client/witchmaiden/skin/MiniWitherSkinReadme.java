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
                skull/                   the skull projectile, same rules (skull.geo.json + skull.png ...)
                  blessed.png            optional texture for the blue (Conscience) skull
                  crimson.png            optional texture for the red (crimson) skull
                sounds/                  replacement sounds (.ogg)

            Without skin.json:
              - the model is the only *.json in the folder that holds Bedrock geometry (two or more is an error);
              - the texture is the only *.png in the folder;
              - every *.animation.json is loaded, and an animation plays for the state its name ends with:
                .idle   .move / .walk / .fly   .summon / .spawn / .appear   .shoot / .attack   .crimson
              - the head bone (turned toward the last shot) is Head, head, AllHead or bone_head;
              - bones whose name starts with "ysmGlow" are drawn full-bright.
            A folder with sounds only keeps the vanilla Wither model.

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

            Limits: .json up to 16 MiB; .png up to 4096x4096 px and 16 MiB; .ogg up to 8 MiB each, 32 files at most.

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
                skull/                   头颅弹体，规则相同（skull.geo.json + skull.png ……）
                  blessed.png            可选：蓝色（善良）头颅的贴图
                  crimson.png            可选：红色（赤首）头颅的贴图
                sounds/                  替换声音（.ogg）

            没有 skin.json 时：
              - 模型为文件夹中唯一包含 Bedrock 几何的 *.json（两个及以上会报错）；
              - 贴图为文件夹中唯一的 *.png；
              - 加载所有 *.animation.json，动画按名字结尾播放在对应状态：
                .idle（待机）  .move / .walk / .fly（移动）  .summon / .spawn / .appear（召唤）
                .shoot / .attack（射击）  .crimson（赤首）
              - 头部骨骼（朝上一次射击方向转动）为 Head、head、AllHead 或 bone_head；
              - 名字以 “ysmGlow” 开头的骨骼全亮绘制。
            只有声音的文件夹会保留原版凋灵模型。

            声音：sounds/<名字>.ogg，<名字> 为以下之一
              idle（环境）、shoot（射击）、summon（召唤）、dismiss（收回）、unlock（解锁）、
              crimson_shoot（发射赤首）、crimson_hit（赤首命中）、burst（爆裂）
            变体命名为 idle1.ogg、idle2.ogg 或 idle_1.ogg ……，每次随机选一个。立体声文件会混成单声道，以保留方位感。

            skin.json（所有键都是可选的；路径相对于皮肤文件夹，且必须留在其中）：格式见上方英文部分的示例。

            限制：.json 最大 16 MiB；.png 最大 4096x4096 像素且 16 MiB；.ogg 每个最大 8 MiB，最多 32 个。

            示例（琪露诺）：新建文件夹 mini_wither/cirno，把 pink_dye.json 和 pink_dye.png 复制进去，然后在小凋灵外观
            界面点击“重新加载”并选择 “cirno”。
            """;

    private MiniWitherSkinReadme() {
    }
}
