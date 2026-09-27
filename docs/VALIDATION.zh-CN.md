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

## GTCEu 原生 EU 集成验证（2026-09-28）

本节只覆盖 `theflogat.technomancy.compat.gtceu` 的可选 GT EU 适配及其 GameTest，取代上文“尚未实现 … GTCEu 适配器”一条。它不代表任何游戏内机器、方块或配方已迁移：适配层挂在能源组件上，本工程目前仍没有自己的方块实体。

以 JDK 17 作为 `JAVA_HOME`，在本工程执行：

```powershell
.\gradlew.bat test --console=plain --no-daemon --offline
.\gradlew.bat clean; .\gradlew.bat build --console=plain --no-daemon --offline
.\gradlew.bat clean; .\gradlew.bat build -PwithGtceu=true --console=plain --no-daemon --offline
.\gradlew.bat runGameTestServer --console=plain --no-daemon --offline
.\gradlew.bat runGameTestServer -PwithGtceu=true --console=plain --no-daemon --offline
```

- 单元测试 55 项通过、0 失败（新增 `EuTierTest` 3、`EuPortTest` 11、`EuPushTest` 8；原有 `EnergyLedgerTest` 28、`FeEnergyViewTest` 5）。GT API 是 `compileOnly`，不在测试类路径上，因此这些测试只覆盖不含 GT 类型的那一层。
- 两次 `build` 的 `build/libs/technom-1.20.1-0.1.0-dev.jar` 逐字节相同，SHA-256 `ce8a89635fb485ca1d289a12b43e31c398d1547209263616ec71424e625be9ae`；JAR 内 `com/gregtechceu/**` 条目为 0，解包后只有 `GtceuEnergyProtocol`、`GtEnergyContainerView`、`GtceuRateCheck` 三个类引用 GT。
- `runGameTestServer`（无 GT）：6 个 GameTest 全部通过；三个新增 EU 测试按存在性检查跳过并在日志写明原因。
- `runGameTestServer -PwithGtceu=true`：6 个 GameTest 全部通过。日志实测 15 个电压等级与 `GTValues.V`/`VN` 一致、15 个电压探针与 `GTUtil.getTierByVoltage` 一致；能力视图的面权限、整包上限、过压拒绝与 null 面只读规则在真实 `IEnergyContainer` 接口上成立，并被 GT 自己的 `EnergyContainerList` 驱动通过；真实 LV 电炉从我们的主动输出接收 1 A × 32 EU（128 Q），未被接受的第 2 安培已退还，且“邻居拒绝”返回 0 与“邻居不说 EU”返回 `NOT_APPLICABLE` 保持可区分；类扫描仍为 20 个发行类、0 个越界引用。
- 倍率一致性：`fePerEu = 4` 时记录 `Technomancy and GTCEu agree on 4 FE per EU (nativeEUToFE=true, FE converters=true)`；把开发运行时配置改成 `fePerEu = 8` 重跑，记录到预期的 WARN（含存档经济后果与修复建议），6 个 GameTest 仍全部通过，EU 包变为 256 Q，说明测试按 `EnergyUnits.qPerEu()` 计算而不是写死倍率。配置已改回 4。

### 本节尚未验证

- GT 侧主动把 EU 推进我们的方块：需要本工程注册自己的 BlockEntity。GameTest 目前用普通木桶充当能源组件宿主，木桶不对外暴露我们的能力，所以只验证了由我们发起的推送方向和由 GT 代码驱动的接收调用。
- GT 电缆网络（`EnergyNetHandler`）没有接入，同样因为缺少我们自己的方块实体，电缆无法连接。
- `EUToFEProvider.GTEnergyWrapper` 的退让分支（只有 FE 的邻居返回 `NOT_APPLICABLE` 以便走原生 FE）没有在游戏内触发；当前可用方块里没有“暴露 FE 但不暴露原生 EU”的对象，该分支只有代码审查依据。
- 没有执行 `runClient`、联机或专用服务器长期运行；也没有验证存档重载后 EU 读数与 Q 余额的一致性。
