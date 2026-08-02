package dev.caecorthus.sparkwitch.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleStartupSubtitleResourcesTest {
    private static final Map<String, String> ENGLISH = Map.ofEntries(
            Map.entry("black_raven", "Mark your prey, reveal identities, and eliminate the passengers."),
            Map.entry("witch_maiden", "Track your prey, poison platters, and eliminate the passengers."),
            Map.entry("prophet", "Follow death's traces and help the passengers."),
            Map.entry("orthopedist", "Mend fractures and keep the passengers moving."),
            Map.entry("ninja", "Strike from the darkness and eliminate the passengers."),
            Map.entry("hunter", "Trap your prey, break their stride, and finish the hunt."),
            Map.entry("perfumer", "Mark suspects, follow bloody scents, and aid the passengers."),
            Map.entry("tarot_reader", "Complete tasks and divine the truth of the round."),
            Map.entry("wraith", "Complete three tasks and earn a new identity."),
            Map.entry("wind_spirit", "Stay swift, complete tasks, and survive the journey."),
            Map.entry("guardian_angel", "Protect your companions."),
            Map.entry("vendetta", "Find the killer bound to your death and take revenge."),
            Map.entry("saboteur", "Black out the train and aid the killers."),
            Map.entry("curser", "Curse the living before time runs out.")
    );
    private static final Map<String, String> CHINESE = Map.ofEntries(
            Map.entry("black_raven", "标记猎物、洞察身份并消灭好人阵营。"),
            Map.entry("witch_maiden", "追踪猎物、毒害餐盘并消灭好人阵营。"),
            Map.entry("prophet", "追寻死亡痕迹，帮助好人阵营。"),
            Map.entry("orthopedist", "治疗骨折，让好人继续前进。"),
            Map.entry("ninja", "潜入黑暗，使用忍具消灭好人阵营。"),
            Map.entry("hunter", "困住猎物、打断脚步并完成狩猎。"),
            Map.entry("perfumer", "标记嫌疑人、追踪血腥气味并帮助好人。"),
            Map.entry("tarot_reader", "完成任务，通过占卜洞察本局真相。"),
            Map.entry("wraith", "完成三项任务，获得新的身份。"),
            Map.entry("wind_spirit", "保持迅捷、完成任务并活到旅程结束。"),
            Map.entry("guardian_angel", "守护你的同伴。"),
            Map.entry("vendetta", "找到与你死亡绑定的凶手并完成复仇。"),
            Map.entry("saboteur", "熄灭列车灯光，协助杀手消灭好人阵营。"),
            Map.entry("curser", "诅咒附近玩家，并在时间耗尽前消灭平民。")
    );

    @Test
    void requestedRolesUseOneConciseStartupSentenceInBothTargetForms() throws Exception {
        assertSubtitles(language("en_us"), ENGLISH);
        assertSubtitles(language("zh_cn"), CHINESE);
    }

    private static void assertSubtitles(JsonObject language, Map<String, String> expected) {
        assertEquals(14, expected.size());
        expected.forEach((role, subtitle) -> {
            assertEquals(subtitle, value(language, "announcement.goal." + role));
            assertEquals(subtitle, value(language, "announcement.goals." + role));
            assertTrue(subtitle.length() <= 80, role + " startup subtitle is too long");
            String alias = "announcement.goal.sparkwitch." + role;
            if (language.has(alias)) {
                assertEquals(subtitle, value(language, alias));
            }
        });
    }

    private static JsonObject language(String locale) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/sparkwitch/lang/" + locale + ".json"
        ))).getAsJsonObject();
    }

    private static String value(JsonObject language, String key) {
        return language.get(key).getAsString();
    }
}
