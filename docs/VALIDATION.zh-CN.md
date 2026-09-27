# 初始化验证记录

核对日期：2026-09-27。本记录只证明 Forge 1.20.1 工程骨架及其依赖配置可构建；不代表 Technomancy 玩法已迁移或游戏内兼容性已经验证。

## 输入与来源

| 输入 | 固定值或检查 |
|---|---|
| Forge MDK | 官方 Minecraft 1.20.1 / Forge 47.4.23 MDK；下载地址、SHA-1、SHA-256 与原始文件清单见 [MDK_PROVENANCE.json](MDK_PROVENANCE.json) |
| 原版 Technomancy | 相邻 `../Technomancy`，提交 `37bf9a56fe1f713258f298d7ef392b104ccae88f`；作为玩法基线，没有直接复制到新工程 |
| Technomancy-2 | 相邻 `../Technomancy-2`，提交 `223160a924e6f2c3c2170f2b9fa8b291a07cd31b`；作为经审查的参考来源，没有执行机械合并 |
| TC4R | 本地 Maven 版本 `0.1.0-20721`，运行时版本 `4.2.3.5-1.20.1-port.0.1.0-20721`；四个文件的 SHA-256 见 [DEPENDENCIES.json](../local-repo/DEPENDENCIES.json) |
| 工具链 | JDK 17、项目自带 Gradle Wrapper 8.8、ForgeGradle 6.0.54 |

TC4R JAR/POM 由 `tools/bootstrap-local-deps.ps1` 从已有本地仓库复制，脚本在复制前后核对 SHA-256；重复执行时发现四个文件均相同，未重复复制。`verifyLocalDependencies` 在构建时再次验证四个文件。JAR/POM、Gradle 缓存、构建产物和运行日志都不纳入 Git。

## 已执行

在项目根目录，以 JDK 17 作为 `JAVA_HOME` 执行：

```powershell
.\tools\bootstrap-local-deps.ps1
.\gradlew.bat build --offline --console=plain --no-daemon
.\gradlew.bat -PwithGtceu=true build --offline --console=plain --no-daemon
```

普通构建与启用 GTCEu 开发运行时的构建均成功。两次构建的 `verifyLocalDependencies` 都通过；`compileJava`、`processResources`、`jar`、`reobfJar`、`assemble` 和 `check` 完成。`test` 显示 `NO-SOURCE`，因为此骨架尚无测试代码。编译有 Forge MDK 风格入口调用的弃用提示；Gradle 也提示有与 Gradle 9.0 不兼容的弃用特性，这些提示没有阻止 Gradle 8.8 构建。

输出为 `build/libs/technom-1.20.1-0.1.0-dev.jar`，大小 7,218 字节，SHA-256：

```text
6e3a00db103c4130ec94564e12d77ab87d3c3f12f05be93971cc2e2183943bc5
```

已检查 JAR 包含模组入口、展开后的 `META-INF/mods.toml`、`pack.mcmeta`、`META-INF/LICENSE` 和 `META-INF/NOTICE`；模组 ID 是 `technom`，TC4R 必需依赖的运行时版本精确绑定到 20721。JAR 中没有模板的 `examplemod`、未展开的 `${...}` 占位符或打包进去的 TC4R/GTCEu/CoFH 类与依赖 JAR。启用 `withGtceu` 后成品哈希不变，说明该参数只调整开发运行时依赖，没有生成第二个发布变体。

## 尚未验证

- 尚未执行 `runClient`、`runServer`、`runGameTestServer` 或多人联机；仅构建成功不能证明模组加载顺序、Mixin、实际运行时依赖或 UI 正确。
- 尚未实现游戏注册、机器、配方、研究、数据生成器、FE/EU 能量逻辑或 GTCEu 适配器；因此没有对应功能测试。
- `-PwithGtceu=true build` 检查的是同一工程的构建，并不等同于已经启动含 GTCEu 的游戏实例。
- 旧版存档迁移、资源授权核对和第三方模组组合要按 [总工程指导](ENGINEERING_GUIDE.zh-CN.md) 的阶段逐项处理。

下一阶段开始后，每批功能都应更新此记录，写明实际运行命令、观察结果和未覆盖的环境；不要用 `test NO-SOURCE` 代替功能验收。
