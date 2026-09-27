# 验证记录

本记录按核对日期分节累加，每节只声明当次实际执行的检查。2026-09-27 一节只证明 Forge 1.20.1 工程骨架及其依赖配置可构建；2026-09-28 一节补充了专用服务器与 GameTest 的运行时冒烟验证，并取代该节中“尚未执行服务端/GameTest”的部分结论。任何一节都不代表 Technomancy 玩法已迁移或游戏内兼容性已经验证。

## 初始化构建验证（核对日期 2026-09-27）

### 输入与来源

| 输入 | 固定值或检查 |
|---|---|
| Forge MDK | 官方 Minecraft 1.20.1 / Forge 47.4.23 MDK；下载地址、SHA-1、SHA-256 与原始文件清单见 [MDK_PROVENANCE.json](MDK_PROVENANCE.json) |
| 原版 Technomancy | 相邻 `../Technomancy`，提交 `37bf9a56fe1f713258f298d7ef392b104ccae88f`；作为玩法基线，没有直接复制到新工程 |
| Technomancy-2 | 相邻 `../Technomancy-2`，提交 `223160a924e6f2c3c2170f2b9fa8b291a07cd31b`；作为经审查的参考来源，没有执行机械合并 |
| TC4R | 本地 Maven 版本 `0.1.0-20721`，运行时版本 `4.2.3.5-1.20.1-port.0.1.0-20721`；四个文件的 SHA-256 见 [DEPENDENCIES.json](../local-repo/DEPENDENCIES.json) |
| 工具链 | JDK 17、项目自带 Gradle Wrapper 8.8、ForgeGradle 6.0.54 |

TC4R JAR/POM 由 `tools/bootstrap-local-deps.ps1` 从已有本地仓库复制，脚本在复制前后核对 SHA-256；重复执行时发现四个文件均相同，未重复复制。`verifyLocalDependencies` 在构建时再次验证四个文件。JAR/POM、Gradle 缓存、构建产物和运行日志都不纳入 Git。

### 已执行

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

### 尚未验证

- 尚未执行 `runClient`、`runServer`、`runGameTestServer` 或多人联机；仅构建成功不能证明模组加载顺序、Mixin、实际运行时依赖或 UI 正确。**（2026-09-28 已补做 `runServer` 与 `runGameTestServer` 两种运行时，见下节；`runClient` 与多人联机仍未执行。）**
- 尚未实现游戏注册、机器、配方、研究、数据生成器、FE/EU 能量逻辑或 GTCEu 适配器；因此没有对应功能测试。
- `-PwithGtceu=true build` 检查的是同一工程的构建，并不等同于已经启动含 GTCEu 的游戏实例。**（2026-09-28 已启动含 GTCEu 的专用服务器实例，见下节。）**
- 旧版存档迁移、资源授权核对和第三方模组组合要按 [总工程指导](ENGINEERING_GUIDE.zh-CN.md) 的阶段逐项处理。

## S0 运行时验证（核对日期 2026-09-28）

本节完成 [总工程指导](ENGINEERING_GUIDE.zh-CN.md) S0 验收中“TC 基础客户端和专用服务器可加载、GT 开发依赖能够解析”的服务端一半，并首次执行了 3 条加载期冒烟 GameTest（1 条 TC4R 要素 API、2 条 GTCEu 隔离）。**本节全部结论都是启动与隔离层面的冒烟结果；此时工程仍没有任何方块、物品、BlockEntity、配方或研究，因此不存在任何玩法验证。**

### 环境

| 项目 | 实测值与取值来源 |
|---|---|
| JDK | `17.0.18 (Eclipse Adoptium 17.0.18+8)`，由 `gradlew --version` 的 `JVM:` 行与运行日志 `ModLauncher ... starting: java version 17.0.18 by Eclipse Adoptium` 共同确认；本机默认 `JAVA_HOME` 指向 JDK 21，每条命令都显式改用 JDK 17 |
| Gradle / ForgeGradle | `Gradle 8.8`（wrapper，`gradlew --version`）/ `6.0.54`（`build.gradle` 插件版本） |
| Minecraft / Forge | `1.20.1` / `47.4.23`，映射 `official 1.20.1`；`ModLauncher 10.0.9+10.0.9+main.dcd20f30`、`SpongePowered MIXIN 0.8.5` |
| TC4R | Maven `dev.tc4port:thaumcraft-forge:0.1.0-20721`，运行时 `4.2.3.5-1.20.1-port.0.1.0-20721`；每次编译由 `verifyLocalDependencies` 校验 4 个文件 |
| GTCEu 组 | `gtceu 7.5.3`，随带 `ldlib 1.0.40.b`、`configuration 2.2.0`（后两者以 JarInJar 形式进入运行时） |
| 其他运行时依赖 | `curios 5.14.1+1.20.1`、`terrablender 3.0.1.10` |
| 操作系统 | Windows 11（`OS: Windows 11 10.0 amd64`），无显示设备，全程 headless |
| Python | `3.10.11`，仅用于 `tools/gen_gametest_structures.py --check` |

### 执行的命令

```powershell
# 1 基线构建（含 test 与 check）
.\gradlew.bat build --console=plain --no-daemon

# 2 无 GTCEu 专用服务器；run-server/server.properties 设 server-port=25565、rcon.port=25575、level-name=world-plain
.\gradlew.bat runServer --console=plain --no-daemon

# 3 含 GTCEu 专用服务器；改为 server-port=25566、rcon.port=25576、level-name=world-gtceu
.\gradlew.bat runServer -PwithGtceu=true --console=plain --no-daemon

# 4 GameTest 两种运行时
.\gradlew.bat runGameTestServer --console=plain --no-daemon
.\gradlew.bat runGameTestServer -PwithGtceu=true --console=plain --no-daemon

# 5 发行 JAR 同一性：两次都先删掉 build/libs、build/classes、build/resources、build/tmp 再构建
.\gradlew.bat build --console=plain --no-daemon
.\gradlew.bat build -PwithGtceu=true --console=plain --no-daemon

# 6 GameTest 模板与生成器一致
python tools\gen_gametest_structures.py --check
```

两个服务器都通过 RCON 发送 `stop` 结束（`run-server/server.properties` 临时打开 `enable-rcon`），没有使用 `taskkill`。每次结束后用 `Get-Process java` 确认本工作树没有遗留 Java 进程；期间出现的 Java 进程经 `Win32_Process.CommandLine` 核对属于另一工作树的并发构建，未做处理。RCON 口令、端口与 `run-server/`、`run-gametest/` 目录都不纳入 Git。

### 入口点弃用修复

基线构建成功（`BUILD SUCCESSFUL`），但强制重编译 `compileJava` 报出且只报出两条 `[removal]` 警告，位于 `Technomancy.java:21` 的 `FMLJavaModLoadingContext.get()` 与 `:22` 的 `ModLoadingContext.get()`。反汇编 Forge 47.4.23 自身产物确认了替代形式确实存在，而不是照抄新版本写法：

- `fmlcore-1.20.1-47.4.23.jar` 与 `javafmllanguage-1.20.1-47.4.23.jar` 中两个 `get()` 都带 `@Deprecated(forRemoval=true, since="1.21.1")`。
- `FMLModContainer.constructMod()` 先取 `modClass.getDeclaredConstructor(<FMLJavaModLoadingContext>)`，失败才回退无参构造器，即本版本已支持构造器注入。
- `ModLoadingContext.registerConfig` 调用虚方法 `getContainer()`，而 `FMLJavaModLoadingContext` 覆盖了它并返回自己的 `FMLModContainer`，所以在注入实例上注册配置绑定的所有者正确，不依赖 ThreadLocal。

据此把入口改为 `public Technomancy(FMLJavaModLoadingContext context)`。改后 `compileJava` 无警告，两次服务器启动都出现模组自身的 `FMLCommonSetupEvent` 日志，说明注入的事件总线与配置注册都生效。

### 无 GTCEu 专用服务器

```text
[main/INFO] [cp.mo.mo.Launcher/MODLAUNCHER]: ModLauncher 10.0.9+10.0.9+main.dcd20f30 starting: java version 17.0.18 by Eclipse Adoptium; OS Windows 11 arch amd64 version 10.0
[modloading-worker-0/INFO] [de.tc.th.Thaumcraft/]: Initializing Thaumcraft 4R
[modloading-worker-0/INFO] [th.te.Technomancy/]: Technomancy energy rate fixed at 4 FE per EU for this session
[Server thread/INFO] [minecraft/DedicatedServer]: Done (42.297s)! For help, type "help"
[Server thread/INFO] [minecraft/MinecraftServer]: [Rcon: Stopping the server]
BUILD SUCCESSFUL in 2m 53s
```

RCON `forge mods` 返回的完整模组集合为 6 项，没有 `gtceu`：

```text
Mod List:
• minecraft forge-1.20.1-47.4.23_mapped_official_1.20.1.jar : minecraft (1.20.1) - 1
• userdev_classpath TerraBlender-forge-1.20.1-3.0.1.10_mapped_official_1.20.1.jar : terrablender (3.0.1.10) - 1
• minecraft  : forge (47.4.23) - 1
• userdev_classpath curios-forge-5.14.1+1.20.1_mapped_official_1.20.1.jar : curios (5.14.1+1.20.1) - 1
• userdev_classpath thaumcraft-forge-0.1.0-20721_mapped_official_1.20.1.jar : thaumcraft (4.2.3.5-1.20.1-port.0.1.0-20721) - 1
• minecraft main : technom (0.1.0-dev) - 1
```

`run-server/logs/debug.log` 里 `gtceu`/`gregtech` 的匹配数为 0；模组发现日志同时给出 `Found valid mod file main with {technom} mods - versions {0.1.0-dev}` 与 `Found valid mod file thaumcraft-forge-0.1.0-20721_mapped_official_1.20.1.jar with {thaumcraft} mods - versions {4.2.3.5-1.20.1-port.0.1.0-20721}`。

### 含 GTCEu 专用服务器

```text
[main/INFO] [GregTechCEu/]: GTCEu common proxy init!
[main/INFO] [GregTechCEu/]: Registering GTCEu Materials
[modloading-worker-0/INFO] [th.te.Technomancy/]: Technomancy energy rate fixed at 4 FE per EU for this session
[Server thread/INFO] [minecraft/DedicatedServer]: Done (48.399s)! For help, type "help"
[Server thread/INFO] [minecraft/MinecraftServer]: [Rcon: Stopping the server]
BUILD SUCCESSFUL in 3m 7s
```

RCON `forge mods` 返回 9 项，比上一次多出 `gtceu (7.5.3)`、`ldlib (1.0.40.b)` 和 `configuration (2.2.0)`，`technom (0.1.0-dev)` 与 `thaumcraft (4.2.3.5-1.20.1-port.0.1.0-20721)` 仍在其中。启动过程中本模组没有任何 WARN/ERROR。

**同时观察到的第三方问题（不是本模组产生，但必须记录）：** GTCEu 在配方阶段报出 4 条

```text
[main/ERROR] [GregTechCEu/]: Output item 0 of recipe thaumcraft:compat/native_copper_cluster_smelting is empty
```

（`native_copper` / `native_tin` / `native_lead` / `native_silver`）。这是 TC4R 20721 的 GT 兼容配方在 GTCEu 7.5.3 下产物解析为空，属于这两个依赖的组合问题；服务器仍然启动完成。本轮没有排查或修复，留作 S1 之前对该依赖组合的跟进项。

### GameTest（两种运行时都通过）

测试位于 `src/main/java/theflogat/technomancy/gametest/`，只在开发运行时存在，批次 `technom_smoke`，模板 `technom:gametest/empty_5x5x5`：

| 测试方法 | 断言内容 |
|---|---|
| `Tc4rApiGameTests.primalIgnisResolvesThroughAspectApi` | `AspectId.parse("ignis")` 解析到 `thaumcraft:ignis`；`AspectApi.get` 返回定义且 `primal()`；其 `visChannel()` 为 `IGNIS`；`AspectApi.primals()` 等于六个 `VisChannel` 的 aspect 集合；`aer + ignis` 组合出 `lux`；`AspectApi.registry().generation() >= 1` |
| `GtceuIsolationGameTests.presenceMatchesModListAndClasspath` | `GtceuPresence.isLoaded()`、`ModList.getModFileById("gtceu")` 与 `com.gregtechceu.gtceu.GTCEu` 的实际可见性三者一致，并与 `build.gradle` 由 `-PwithGtceu` 写入的 `technom.gametest.expectGtceu` 系统属性一致 |
| `GtceuIsolationGameTests.onlyIsolatedAdapterClassesLinkGtceu` | 遍历本模组文件内全部随发布的 class（跳过 gametest 包），逐个按字节检查是否出现 GT 内部包名；只允许 `compat/gtceu/` 下的适配类命中，`GtceuPresence` 自身不允许命中 |

```text
[main/INFO] [ne.mi.ga.ForgeGameTestHooks/]: Enabled Gametest Namespaces: [technom]
[Server thread/INFO] [minecraft/GameTestBatchRunner]: Running test batch 'technom_smoke:1' (3 tests)...
[Server thread/INFO] [th.te.Technomancy/]: GameTest GTCEu isolation: scanned 12 shipped classes, 0 link GTCEu outside theflogat/technomancy/compat/gtceu/
[Server thread/INFO] [th.te.Technomancy/]: GameTest GTCEu presence: loaded=false, expected=false
[Server thread/INFO] [minecraft/GameTestServer]: [+++]
[Server thread/INFO] [minecraft/GameTestServer]: All 3 required tests passed :)
BUILD SUCCESSFUL in 1m 37s
```

`-PwithGtceu=true` 的同一批次同样 3/3 通过，同一行变为 `GameTest GTCEu presence: loaded=true, expected=true`，隔离扫描结果不变。两次的 `loaded` 与 `expected` 同时翻转，说明该参数确实改变了运行时模组集合，而不只是改了一个构建变量。含 GTCEu 时 GT 还会用 `dev.test.GameTestRegistryMixin`（`gtceu.mixins.json`）混入 `GameTestRegistry`，测试在该 Mixin 生效的情况下依然通过。

两点必须说清的口径：

- 隔离扫描报出的 12 个 class 与发行 JAR 中的 12 个 class 条目一一对应，所以“扫描覆盖全部随发布类”这句成立；但当前 `compat/gtceu/` 下只有不含 GT 类型的 `GtceuPresence`，还没有任何真正的 GT 适配类，因此该测试现在只证明“没有类越界链接 GT”，不证明适配层可用。
- `AspectApi.primals()` 等于六个 vis 通道，是 TC4R 20721 默认要素目录加上本次运行时数据包集合的事实；`AspectDefinition` 只强制“有 vis 通道者必须是 primal”，并不反向强制“primal 必须有 vis 通道”，所以该等式属于对固定依赖版本的检查，不是 API 永久保证。`generation() >= 1` 是有效断言：`AspectRegistryManager` 的类初始化快照为 generation 0，数据包重载时取 `max(1, 上一代 + 1)`，所以该断言确实证明服务端完成了要素注册表的数据包重载。

### 发行 JAR 卫生

先删除 `build/libs`、`build/classes`、`build/resources`、`build/tmp`，再分别以不带参数和 `-PwithGtceu=true` 重新构建，两次产物 `build/libs/technom-1.20.1-0.1.0-dev.jar`（26,943 字节、25 个条目）字节一致，SHA-256：

```text
e5361bec065a1ac7a241f4fbf230197e5e6ea54eb1932783a43ddb2ce7494924
```

JAR 条目检查结果：`com/gregtechceu` 0 条、`theflogat/technomancy/gametest/` 0 条、`data/technom/structures/gametest/` 0 条；解包后对全部 class 做字节级搜索，`com/gregtechceu` 命中 0 个文件。对照同次构建的开发输出确实存在被排除的内容（`build/classes/java/main/theflogat/technomancy/gametest/` 三个 class 与 `build/resources/main/data/technom/structures/gametest/empty_5x5x5.nbt`），说明排除规则在做实事而非空规则。

`python tools/gen_gametest_structures.py --check` 输出 `up to date: empty_5x5x5.nbt`、退出码 0；已提交模板的 SHA-256 为 `1c0892de03aefecd179495276543752537c937d764dfd1725cd20d74f966935e`。

### 本节仍未验证

- **客户端**：`runClient` 未执行。本环境 headless，渲染、界面、模型、材质、客户端/服务端同步、`ImmediateWindowProvider` 路径全部没有验证。S0 验收中客户端可加载的一半仍然空缺。
- **玩法**：工程仍无方块、物品、BlockEntity、配方、研究、战利品或数据生成器；`runData` 未执行。服务器成功启动只说明加载器、依赖与 Mixin 环境可用。
- **能源语义**：`common/energy` 下的账本与 FE 视图只有 JVM 单元测试（`.\gradlew.bat build` 中的 `test`）。没有任何 BlockEntity 能力暴露、FE 邻居转移、GT `IEnergyContainer` 收发、电压/安培整包、过压策略、每 tick 预算、余数守恒或保存重载的游戏内验证；[总工程指导 §7](ENGINEERING_GUIDE.zh-CN.md) 的要求一条都还没有运行时证据。
- **GT 适配**：`compat/gtceu/` 只有存在性网关。含 GTCEu 的服务器只证明两个模组可以共存与本模组能初始化，不证明任何 EU 交互。
- **发行 JAR 的实机安装**：两次运行都使用 ForgeGradle 的 userdev 展开类路径（模组集合里显示为 `minecraft main`），不是把上面那个 JAR 放进 `mods/` 启动的。“同一个 JAR 覆盖有/无 GT 两种环境”目前只由产物层面证明（两次构建哈希相同、无 GT 类、GT API 仅 compileOnly），尚未由一次真实服务器安装证明。
- **存档与多人**：两次服务器各自新建世界（`world-plain`、`world-gtceu`）。没有验证同一存档在有/无 GTCEu 之间切换（缺少 GT 注册项时预期会失败，本轮不做任何兼容承诺）、多人联机、换维度、死亡重生、区块卸载、重启后持久化。
- **依赖组合遗留问题**：上面 GTCEu × TC4R 的 4 条空产物配方错误未定位、未修复。
- **平台与性能**：只在 Windows 11 + Temurin JDK 17.0.18 + Gradle 8.8 上验证过，未测其他系统或 JDK。没有做性能或长时间稳定性测量；GameTest 日志中的 `Can't keep up!` 来自测试服务器自身的世界生成负载，不是性能结论。

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

下一阶段开始后，每批功能都应更新此记录，写明实际运行命令、观察结果和未覆盖的环境；不要用 `test NO-SOURCE`、启动成功或 GameTest 冒烟结果代替功能验收。

## 源质发电机验证（2026-09-28）

本节只覆盖 `technom:essentia_dynamo`（源质发电机）、其数据驱动燃料表、共享的三态红石控制件，以及效能宝石在发电机上的行为。它不代表凝聚器、研究、配方或客户端渲染已经验证。分支 `s1b/dynamo`，基线 `53141d2`。

### 已执行的命令

以 JDK 17（Temurin 17.0.18+8，`JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot`，与 PATH 上的 JDK 21 不同）在本工作树执行：

```powershell
.\gradlew.bat build --console=plain --no-daemon --offline
.\gradlew.bat build -PwithGtceu=true --console=plain --no-daemon --offline
.\gradlew.bat runGameTestServer --console=plain --no-daemon --offline
.\gradlew.bat runGameTestServer -PwithGtceu=true --console=plain --no-daemon --offline
```

### JUnit（无世界层）

`build` 中的 `test` 共 **116 项通过、0 失败**（原有 88 项；新增 28 项：`EssentiaFuelTableTest` 16、`DynamoFuelBankTest` 12）。

`EssentiaFuelTableTest` 从 classpath 读取**实际随包发布的** `data/technom/technomancy/essentia_fuel/default.json`，用真实 codec 解析，因此断言的是玩家会拿到的数据本身，不是它的副本：

| 测试 | 断言内容 |
|---|---|
| `shippedTableCoversTheOriginalRowsAndNothingElse` | 44 个 aspect 有行（原表 43 行 + 补写的 `terra`）、兜底 25；`gelum`/`victus`/`lucrum`/`tutamen` 四个原版确实未提及的 aspect 仍走兜底；外部 mod 的未知 aspect 也是兜底 25 而不是 0；`null` aspect 为 0 |
| `unconditionalRowsMatchTheOriginal` | 800（ignis/potentia）、75（aer/ordo/vitreus/perditio 加 8 个器械类）、30（12 个生物类）、40（5 个植物类）逐条核对 |
| `terraIsWorthTheSameAsTheOtherNonFirePrimals` | `terra` = 75（A-15） |
| `aquaFollowsBiomeHumidity` | 50 / 湿润群系 200 |
| `magicAndEldritchTakeEitherTheEndOrAMagicalForest` | 75 / 末地 300 / 魔法森林 300（两个条件的“或”由有序列表首个命中实现） |
| `undeadIsWorthMoreInTheEndAndNoLongerFallsThroughOutsideIt` | 非末地 50、末地 100（A-15） |
| `voidIsWorthMoreBelowSeaLevelWhereverTheFloorIs` | 海平面 63 时 y=63/120 得 50，y=62/-40 得 200；海平面 32 时边界随之移动（A-17） |
| `flightKeepsItsAbsoluteCeiling` | y=150 得 50，y=151 得 200，y=-60 得 50 |
| `slimeLightDarknessAuraAndTaintFollowTheirConditions` | 史莱姆区块 200/100、昼夜 300/50 两组、魔法森林 600/100、污染 600/100 |
| `exchangeRollsInTheCorrectedRangeAndNeverZero` | 4000 次取样全部落在 200..1199，且确实覆盖两端（A-16） |
| `energyPerUnitIsFuelValueTimesEightyTimesTheScale` | `fuelValue × 80 × scale`：scale=1.0 时 ignis 为 64000 Q；scale=0.25 时 16000 Q（与一块煤直接烧打平）；四舍五入只发生一次 |
| `firstMatchingConditionWins` | 条件列表有序、首个命中生效 |
| `laterFilesOverrideRowsAndReplaceClearsThem` | 逐 aspect 覆盖、`"replace": true` 可清空 |
| `aDataPackOverridesTheShippedTableWhateverItsNamespaceSortsAs` | 命名空间 `aaa_pack`（字典序早于 `technom`）的数据包仍然覆盖本模组的默认值。审查发现的真实缺陷：原先按资源 ID 纯字典序合并，于是排在 `technom` 之前的数据包会被本模组的默认文件反向覆盖 —— 一次静默回退。现在本模组命名空间先合并、其余在后 |
| `badFilesAreSkippedWithoutTakingTheTableDown` | 未知条件类型、负数燃料值、缺 `aspects` 的文件整份被拒并写日志，有效文件照常生效 |
| `anEmptyTableIsTheStartingState` | 数据包加载前表为空，机器必须容忍 0 燃料值 |

`DynamoFuelBankTest` 覆盖 tick 级燃料记账：满缓冲既不买料也不询价（`aFullBufferBuysNoFuelAndDoesNotEvenPriceIt` 用调用计数断言连“定价”都没发生）、满缓冲不烧掉已有燃料、产量不超过剩余空间、被拒能量退回燃料槽、不足一次完整装料时一点都不烧、燃料值为 0 时不扣源质、32 tick 前瞻只买一次料，以及 `thePotencyGemChangesThroughputAndNotEfficiency`：对 7 个燃料值分别跑满 64 点源质，未升级与升级两条路径消耗的源质与产出的总能量**完全相等**，时间约为四分之一。

**为什么燃料表条件用 `ResourceLocation` 而不是 `TagKey`/`ResourceKey`**：注册表型 codec 在 `Bootstrap.bootStrap()` 之前无法构造，而 Forge 的 eventbus 在纯 JUnit 环境下无法完成 bootstrap（实测 `NetworkHooks.init` 抛 `NoSuchMethodException: NetworkEvent.<init>()`）。条件因此只持有 id，由 `FuelEnvironment` 这一层把 id 变成注册表键——这同时是干净的边界划分，也是这一整套条件分支能被单元测试覆盖的前提。

### GameTest（真实世界层）

`runGameTestServer` 与 `runGameTestServer -PwithGtceu=true` 两次均报告 **All 17 required tests passed**（原有 6 项加新增 `technom_dynamo` 批次 11 项），两次的发电机结论一致。

| GameTest | 日志实测结果 |
|---|---|
| `theFuelTableIsLoadedFromTheDataPack` | 运行时表含 44 个 aspect、兜底 25 —— 证明 `AddReloadListenerEvent` 真的挂上了，不只是写了 |
| `faceRulesAgreeAndTheOutputFaceCarriesOnlyEnergy` | `up-facing dynamo accepts essentia on the other five faces only, canInputFrom == isConnectable on all six, canOutputTo and takeEssentia are 0 everywhere, suction 128/128 and FE extract only on up`（A-7）。测试先塞进 10 点源质再断言，因此“什么都取不出来”不会因为缓存本来是空的而侥幸通过；同时验证 `addEssentia` 返回的是**已接收量** |
| `turningTheOutputMovesEveryFaceRule` | 六次无条件旋转回到 `up`，每次源质面与 FE 视图同步跟随（A-19） |
| `theBlockCanBeMinedForItsItem` | 在 `minecraft:mineable/pickaxe` 内、石镐即为正确工具、剪刀不是；战利品表产出恰好 1 个 `essentia_dynamo` |
| `savedStateIsRestoredVerbatim` | `6400 Q, 57600 Q of banked fuel, 4 units of thaumcraft:ignis, the potency gem and redstone mode LOW all survived a save and load` |
| `aWardedJarFeedsTheDynamoWhichPowersARealConsumer` | `in 80 ticks a real warded jar gave up 16 units of ignis (16000 Q each), the dynamo produced 6000 Q and a real Forge Energy receiver took 6000 Q of it` —— 每 5 tick 1 点的节流与 80 Q/t 的速率都符合 |
| `essentiaAndEnergyAreConservedAcrossTheChain` | `jar 40 + cache 23 + burned 1 = 64 units; burned x 16000 Q = 16000 Q = 9200 Q produced + 6800 Q still banked as fuel` —— 精确等式，不依赖 tick 计数 |
| `redstoneGatingStopsGenerationAndTheEssentiaPull` | `60 ticks on HIGH with no signal took 0 essentia and produced 0 Q; 60 ticks after a redstone block it had pulled 12 units and delivered 4400 Q`（A-18） |
| `aFullBufferTakesNoEssentiaAndWastesNoFuel` | `60 ticks at 40000 of 40000 Q burned no fuel (13200 Q banked throughout) and bought no charge (1 units burned throughout)`（A-14） |
| `thePotencyGemQuadruplesThroughputAndNotEfficiency` | `320 Q/t on 4 units per charge burned 4 units worth 64000 Q and produced exactly 64000 Q, delivering 32000 Q in 120 ticks` —— 每点 16000 Q，与未升级完全一致 |
| `essentiaReachesTheDynamoThroughRealTubes` | 罐 + **2 节真实 `thaumcraft:essentia_tube`** + 发电机（该布置已占满模板的 4 层空气，故这条测试不带 FE 接收端，改用发电机自身缓冲计量）：`through 2 essentia tubes in 160 ticks the jar gave up 24 units, 22 are cached, 1 were burned into 16000 Q and 1 are in flight inside the tubes`。**160 tick 内跨越管道的点数在多次运行中实测为 8..24**，取决于两节管道的 BlockEntity 相对 tick 顺序（TC4R 管道每 5 tick 只移动 1 点，顺序不利时一个点要 15 tick 才能走完两跳）；这是 TC4R 管道自身的性质，不是本移植的行为。**每一次运行守恒等式与“管道中在途点数 ≤ 管道节数”都精确成立**，所以断言只对下界和在途上界设限。这条链路才是吸力规则真正管辖的路径：每节管道把 128 的吸力衰减 1、一次只持有 1 点，发电机的吸力或最小吸力写错的话它根本不会流动 |

真实 Forge Energy 消费者由 `GameTestEnergySink` 通过 `AttachCapabilitiesEvent` 挂在普通木桶上：原版与 Thaumcraft 都没有接收 FE 的方块，本模组自己的方块是发电机，所以必须专门造一个接收端。发电机因此是用它对任何第三方机器都会用的那一次 capability 查询找到它的，**发电机代码里没有任何测试钩子**。该监听器只从测试代码注册、只在测试要求的坐标上挂载，且整个 `gametest` 包不进发行 JAR。

### 发行 JAR 卫生

先删除 `build/libs`、`build/classes`、`build/resources`、`build/tmp`，再分别以不带参数和 `-PwithGtceu=true` 重新构建，两次产物逐字节相同（890,304 字节、332 个条目），SHA-256：

```text
066d80b9f58f54178cd3b44140ace4953730fded529636eee5ac5bdeb66a74c4
```

JAR 内 `theflogat/technomancy/gametest/` 0 条、`com/gregtechceu` 0 条；`data/technom/technomancy/essentia_fuel/default.json`、`assets/technom/blockstates/essentia_dynamo.json`、两个模型与战利品表均在。GTCEu 隔离扫描从 12 个类增长到 56 个类，仍然 0 个类在 `compat/gtceu/` 之外链接 GT（发电机确实引用了 `compat.gtceu.EuTier`，但那是不含 GT 类型的电压表，按 `EuTier` 自身文档“机器的 EU 限额必须从 `voltage()` 推导”使用）。

### 本轮采纳的平衡决定（规格书第 10 章）

| 条目 | 采纳值 | 位置 |
|---|---|---|
| 1 每点源质能量 | `essentiaFuelScale = 0.25`（上一批已加入配置，本批真正开始使用）。ignis/potentia 因此为 16000 Q/点，一块煤经坩埚的 4 点与直接烧打平 | `TechnomancyConfig.ESSENTIA_FUEL_SCALE` |
| 3 发电机默认红石模式 | **`NONE`**（原版为 `HIGH`）。三态与三种编程物品全部保留 | `EssentiaDynamoBlockEntity.DEFAULT_REDSTONE_MODE` |
| 7 `EARTH`/terra 燃料值 | **75** | `essentia_fuel/default.json` |
| 8 `UNDEAD`/exanimis 非末地 | **50** | 同上 |
| 9 `EXCHANGE`/permutatio 随机范围 | **200 + `level.random.nextInt(1000)`** | 同上 |
| 10 燃料表数据驱动 | **是**，`data/<ns>/technomancy/essentia_fuel/*.json` 加 codec 加载器 | `EssentiaFuelLoader` |
| 12 源质连接面收紧 | 发电机 `canInputFrom == isConnectable == (face != facing)` | `EssentiaDynamoBlockEntity.essentiaPorts` |

发电速率、缓冲、输出上限、源质缓存与吸力全部 1:1 保留（80 / 320 / 40,000 / 320 / 64 / 128）。

### 代码审查发现并已修掉的问题

本批次对自己的 diff 做了一轮独立代码审查，修掉的实质问题：

1. **数据包合并顺序**（见上表 `aDataPackOverridesTheShippedTableWhateverItsNamespaceSortsAs`）：纯字典序会让排在 `technom` 之前的命名空间被本模组默认值反向覆盖。已改为本模组命名空间先合并。
2. **`essentiaAmount(face)` 会让 TC4R 缓冲管道永久卡死**：`EssentiaTubeBlockEntity.fillBuffer()`（`:436`，已核对该行）先判断 `neighbor.essentiaAmount(face) > 0` 与吸力比较，**在 `canOutputTo` 之前**，命中后调用 `EssentiaApi.take`（对发电机返回 0）然后**无条件 `return`**，不再扫描自己其余的面。因此发电机只要在任何面报出非零可取量，紧贴它的缓冲管道每 5 tick 就会卡在这一面上。现已与 `canOutputTo` / `takeEssentia` / `availableEssentia` 一致地返回 0；缓存内容仍由 `AspectContainerView.visibleAspects()` 提供给护目镜与探测器。
3. **`VoxelShape` 比模型矮 1 像素**：模型的中轴画到 y=16，选择框只到 y=15，准星会穿过方块可见的顶端。已改为 16。
4. **旋转在纯净环境下无法触发**：见下文“尚未验证”中的扳手条目，已补一条空手潜行右键输出面的触发方式。
5. **`essentiaReachesTheDynamoThroughRealTubes` 会把木桶放到模板外**：2 节管道时接收端落在相对 y=5，越出 5×5×5 模板且不会被结构清理带走。已改为该测试不带接收端，并在 `DynamoChain.place` 里加了高度断言。
6. **除零**：管道测试在 `essentiaFuelScale = 0` 时会抛 `ArithmeticException` 而不是给出可读的失败信息。已补 `perUnit > 0` 断言。
7. **死代码**：`RedstoneControl.cycle()`、`RedstoneMode.cycle()`、`RedstoneControl.defaultMode()`、`EssentiaFuelTable.entry()` 无任何调用方，已删除（原版的 `nextRedstoneSet()` 同样没有调用方）；`EssentiaFuelLoader.publish` 改为私有。

另外修正了一处注释性矛盾：发电机对每一次取货都要求“吸力严格大于”与“达到来源最小吸力”两条，而共享的 `EssentiaSuction.canDiscover` 文档说明后者只属于罐的“发现”分支。现在发电机直接写出这两个条件，并注明为什么它与罐不同（原版发电机与 TC4R 管道的 `equalizeWithNeighbours` 都是每次都判）。

### 规格书之外新发现、且未复现的两个缺陷

1. **`fill()` 会销毁源质**。`TileEssentiaDynamo.fill()`（`:180`）取邻居持有的任意 aspect 并交给 `addToContainer`；后者在已有别的 aspect 时拒收，并把这一点作为“未接收量”返回，而调用方**丢弃了返回值** —— 这一点源质已经被 `takeEssentia` 从管道里取出，于是被凭空销毁。本移植只索取自己能存下的 aspect，`EssentiaApi.take` 在邻居持有别的 aspect 时返回 0，安全跳过。
2. **生物群系采样坐标错误**。`getAspectFuel` 用 `worldObj.getBiomeGenForCoords(xCoord, yCoord)`（`:69,75,138,144`），而 1.7.10 该方法的两个形参是 `(x, z)` —— 把 Y 坐标传进了 Z 的位置。因此原版所有依赖群系的燃料值（aqua/praecantatio/alienis/auram/vitium）读的都是错误的那一列。本移植按方块坐标取群系。

### 本节尚未验证

- **客户端与渲染**：`runClient` 未执行，本环境 headless。发电机只提供**静态 JSON 模型**，没有 `BlockEntityRenderer`。原版 `ModelEssentiaDynamo` 的四个喷口是 **30°**（`0.5235988` rad），而方块模型的旋转只允许 ±22.5/±45，因此喷口按最接近的 **22.5°** 实现，这是**刻意的几何偏离**。64×32 的 `textures/models/essentiadynamo.png` 因此没有被使用；模型六面统一采用同图案的 16×16 `block/essentiadynamo`。模型是否好看、`LIT` 方块状态在客户端的表现、手持与物品栏渲染、贴图 UV 是否对位，**全部未经肉眼验证**。若后续要做 BER，请先在有显示的环境下核对。
- **`LIT` 目前没有视觉差异**：12 个 variant 指向同一个模型。它是真实同步的状态（比较器、Jade、资源包可用），但没有发光贴图。
- **扳手 tag 在纯 TC4R 环境下是空的**：`technom:tools/wrench` 只含可选引用 `#forge:tools/wrench` 与 `#c:wrenches`，没有任何 mod 提供时无物品命中。因此**另加了一条不依赖任何 mod 的触发方式：潜行空手右键能量输出面即旋转**（其余五面仍是取回效能宝石），否则修掉 A-19 反而会让旋转在纯净环境下无法触发。旋转逻辑本身由 GameTest 直接驱动 `cycleFacing()` 验证过，但 `use()` 里这两条分派路径都**未在游戏内点击验证**。
- **配方与研究**：发电机与效能宝石都还没有配方或研究条目，生存中不可获得（战利品表已验证，但那只解决“挖了能拿回来”）。这部分属于其他批次。
- **管道**：验证了紧贴 `thaumcraft:warded_jar` 与经过 **2 节** `thaumcraft:essentia_tube`。**没有**验证长链路与吸力衰减的实际极限（按 §8.5 推算约 59 节到已贴标签的量子罐）、`restricted_essentia_tube`（吸力砍半）、`filtered_essentia_tube` 与 `directional_essentia_tube`。
- **原生 EU 输出**：发电机按 `320 / (32 × qPerEu)` 推导出 LV 2 安培（默认 4 Q/EU），但**没有**任何 GameTest 让它向真实 GT 机器推送 EU —— 现有的 EU 交换测试仍然用木桶托管一个独立的 `MachineEnergy`。发电机的 EU 通路目前只有代码审查依据。
- **多人、跨维度、区块卸载、长时间运行**：均未验证。数据包 `/reload` 在运行中更换燃料表的行为也未验证（代码上是发布一张新的不可变表）。
- **凝聚器联动**：永动机边界（凝聚器成本必须严格大于发电机烧 potentia 的产出）未加启动期断言，也未联动验证；凝聚器尚未实现。
- 功能矩阵开头那句“当前工程只有初始化骨架，以下游戏内容全部待迁移”在量子罐落地时就已过时，本批次未改动它以免与其它分支冲突。
