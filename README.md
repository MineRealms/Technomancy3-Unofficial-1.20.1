# Technomancy Unofficial — Forge 1.20.1

基于官方 Forge MDK 初始化的现代移植工程。当前是开发骨架：已有模组入口、构建和依赖配置、来源记录与工程指导；机器、研究、仪式及 FE/EU 能量适配尚未实现。

目标是恢复 Technomancy 的 TC4 玩法，使用 TC4R 20721，移除 Thermal Expansion 和 CoFH RF 依赖，提供 Forge Energy 与 GTCEu EU 兼容。Botania 和 Blood Magic 作为后续可选模块。

## 开始开发

需要 JDK 17。使用仓库自带 Gradle Wrapper，不需要另装 Gradle。

```powershell
Set-Location 'H:\MinecraftMods\Technomancy-1.20.1'
.\tools\bootstrap-local-deps.ps1
.\gradlew.bat build --console=plain --no-daemon
```

本地 TC4R 获取方式见 [local-repo/README.md](local-repo/README.md)。其他依赖由 Gradle 下载。成品路径：`build/libs/technom-1.20.1-0.1.0-dev.jar`，当前仅为骨架 JAR。

```powershell
# TC4R 基础开发环境
.\gradlew.bat runClient
.\gradlew.bat runServer

# 同一工程加入 GTCEu 开发运行时；不改变编译 API 和发布功能集
.\gradlew.bat runClient -PwithGtceu=true
.\gradlew.bat runServer -PwithGtceu=true

# IDE / 数据生成
.\gradlew.bat genIntellijRuns
.\gradlew.bat runData
```

`runGameTestServer` 现有 3 个 `technom_smoke` 冒烟测试（TC4R 要素 API 已链接、GTCEu 存在性与类隔离），在有/无 `-PwithGtceu=true` 两种开发运行时均 3/3 通过，结果见[验证记录](docs/VALIDATION.zh-CN.md)；它们只覆盖加载与隔离，不是玩法验收。`runData` 目前仍没有内容提供器。首次手动启动服务端时按 Minecraft 的提示处理开发目录中的 EULA。

## 工程文档

- [总工程指导](docs/ENGINEERING_GUIDE.zh-CN.md)：架构、FE/EU 规则、TC4 API、数据持久化、里程碑与验收要求。
- [功能迁移矩阵](docs/FEATURE_MATRIX.zh-CN.md)：实际功能范围、依赖组、迁移状态和验证条件。
- [源码基线与取舍](docs/SOURCE_BASELINE.zh-CN.md)：原版/1.12 分支关系、可吸收改动和旧代码问题。
- [初始化验证记录](docs/VALIDATION.zh-CN.md)：本次实际执行的检查与尚未验证的边界。
- [官方 MDK 来源与校验](docs/MDK_PROVENANCE.json)。

## 固定基线

| 项目 | 版本 |
|---|---|
| Minecraft / Java | 1.20.1 / 17 |
| Forge / ForgeGradle / Gradle | 47.4.23 / 6.0.54 / 8.8 |
| 模组 ID / Java 包 | `technom` / `theflogat.technomancy` |
| TC4R | `0.1.0-20721`，必需 |
| GTCEu | `7.5.3`，可选运行时；编译 API 始终存在 |
| Curios / TerraBlender | `5.14.1+1.20.1` / `1.20.1-3.0.1.10` |

版本事实以 `gradle.properties` 为准。运行时元数据严格限定当前 TC4R/GTCEu 核对版本，不宣称自动支持其他版本。

代理配置放在本机的 Gradle 用户配置中。项目不保存本机 JDK 路径或代理端口；网络环境确实需要镜像时可以传 `-PmavenCentralMirror=https://maven.aliyun.com/repository/public`，正常情况下使用默认仓库。

新代码和文档采用 [Apache-2.0](LICENSE)；MDK 原始许可保存在 `LICENSES/`。旧代码、美术与第三方依赖的来源分别记录，详见 [NOTICE](NOTICE) 和源码基线文档。
