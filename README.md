# Technomancy Unofficial — Forge 1.20.1

基于官方 Forge MDK 初始化的现代移植工程。S0–S2 已落地（S1 精华闭环、S2 机器/线圈/节点/法杖/工具与两台补充机器），S3 起的仪式、Existence 与可选模块尚未实现；逐项证据见[功能矩阵](docs/FEATURE_MATRIX.zh-CN.md)与[验证记录](docs/VALIDATION.zh-CN.md)。

目标是恢复 Technomancy 的 TC4 玩法，使用 TC4R 20721，移除 Thermal Expansion 和 CoFH RF 依赖，提供 Forge Energy 与 GTCEu EU 兼容。Botania 和 Blood Magic 作为后续可选模块。

## 开始开发

需要 JDK 17。使用仓库自带 Gradle Wrapper，不需要另装 Gradle。

```powershell
Set-Location 'H:\MinecraftMods\Technomancy-1.20.1'
.\tools\bootstrap-local-deps.ps1
.\gradlew.bat build --console=plain --no-daemon
```

本地 TC4R 获取方式见 [local-repo/README.md](local-repo/README.md)。其他依赖由 Gradle 下载。成品路径：`build/libs/technom-1.20.1-0.1.0-dev.jar`。

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

`runGameTestServer` 现有 80 个必跑 GameTest（批次见 `src/main/java/theflogat/technomancy/gametest/`），在有/无 `-PwithGtceu=true` 两种开发运行时均 80/80 通过；它们覆盖注册、守恒、方向与安全边界，仍不等于人工实机验收。`runData` 目前仍没有内容提供器。首次手动启动服务端时按 Minecraft 的提示处理开发目录中的 EULA。

## 客户端实机探针

服务端与 GameTest 看不到模型、blockstate 与贴图图集，所以客户端要实机跑一遍。用 RosettaRemoteDebugBridge（`run-client/mods/rosetta_bridge_dev.jar`，桥端口 **48791**）：

```powershell
# 1. 非阻塞地把 dev 客户端拉成独立进程（带 GTCEu，quick-join "New World"）。
#    必须用 Start-Process；`cmd /c start` 与直接调用都会让后台进程占住本命令的管道。
Start-Process -FilePath .\tools\start_client_detached.cmd -ArgumentList '"New World"','"true"' -WorkingDirectory $PWD

# 2. 轮询日志与桥，再对已运行的客户端跑探针
Select-String .\build\client-run.log -Pattern 'Remote bridge listening on'   # 等这一行出现
python .\tools\client_probe.py probes\client --attach                        # PASS/FAIL，退出码=失败数

# 3. 关掉客户端（--attach 不会自动关）：桥 exec Minecraft.stop()，或 taskkill 整个 java 树
```

- `tools/start_client_detached.cmd <世界> [withgtceu]`，默认 `New World` / `true`；Gradle 控制台写到 `build\client-run.log`，游戏日志在 `run-client\logs\`。
- 客户端带 GTCEu 时 Forge 早期窗口会在无真实控制台/union FS 环境下崩（`java.nio.file.FileSystemNotFoundException`），因此 `run-client/config/fml.toml` 设 `earlyWindowControl = false`。
- `tools/client_probe.py <探针> [--attach]`：每个探针是一段 Java 方法体，经桥在客户端线程执行并自带 PASS/FAIL；`--attach` 只跑探针，不开、不关客户端。
- 该流程首次运行即抓到 `technom:node_dynamo` 的模型引用了不存在的 `technom:models/nodedynamo`（粒子图标为 missingno），已改为 `technom:block/nodedynamo`。

## 工程文档

- [总工程指导](docs/ENGINEERING_GUIDE.zh-CN.md)：架构、FE/EU 规则、TC4 API、数据持久化、里程碑与验收要求。
- [功能迁移矩阵](docs/FEATURE_MATRIX.zh-CN.md)：实际功能范围、依赖组、迁移状态和验证条件。
- [源码基线与取舍](docs/SOURCE_BASELINE.zh-CN.md)：原版/1.12 分支关系、可吸收改动和旧代码问题。
- [兼容与联动分析](docs/COMPAT.zh-CN.md)：原版 compat 清单、目标整合包环境、TC4R 20711/20721 API 差异与 Thaumic Energistics 的自动兼容结论。
- [Botania 规格与对齐清单](docs/BOTANIA.zh-CN.md)：四台 Botania 机器的上游精确数值与剩余对齐项（下一步任务）。
- [Claude/agent 交接入口](CLAUDE.md)：当前进度、下一步、读文件顺序与不可改动方向。
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
