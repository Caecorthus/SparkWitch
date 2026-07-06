# Building SparkWitch

## 中文

请从 SparkWitch 的源码根目录构建。这个目录里应该能看到：

```text
build.gradle
settings.gradle
gradle.properties
gradlew
gradlew.bat
src/
libs/
  noellesroles-*.jar
  sparkfactionapi-*.jar
  wathe-*.jar
```

Windows:

```bat
gradlew.bat clean build
```

macOS / Linux:

```sh
./gradlew clean build
```

SparkWitch 会优先使用旁边的 `../SparkFactionAPI` 源码项目，方便联调；如果没有这个兄弟目录，就使用本仓库 `libs/` 里的 `sparkfactionapi-*.jar`。NoellesRoles 和 Wathe 会优先从 `libs/` 读取，缺失时再使用相邻源码仓库里的 jar。因此单独发布 SparkWitch 源码包时必须保留 `libs/` 目录。

不要从缺少这些文件的外层目录直接运行 `gradle build`。如果外层目录有自己的 `settings.gradle`，那外层必须真的包含它声明的子项目目录。

## English

Build from the SparkWitch source root. Use the checked-in Gradle Wrapper:

```sh
./gradlew clean build
```

On Windows:

```bat
gradlew.bat clean build
```

SparkWitch prefers a sibling `../SparkFactionAPI` source checkout for local development. If that checkout is absent, it falls back to bundled SparkFactionAPI jars under `libs/`. NoellesRoles and Wathe prefer `libs/` first and then sibling source jars, so standalone source packages must keep `libs/noellesroles-*.jar`, `libs/sparkfactionapi-*.jar`, and `libs/wathe-*.jar`.
