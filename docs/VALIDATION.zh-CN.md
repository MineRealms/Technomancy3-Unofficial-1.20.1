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

## 量子源质罐实机验证（2026-09-28）

这是本工程第一次在**运行中的专用服务器**里验证玩法行为，而不是只跑单元测试或加载期冒烟。手段是用户提供的 `RosettaRemoteDebugBridge`：它能在服务器线程上编译并执行任意 Java，因此可以真的摆出 TC4R 管道网络、观察源质流动、读取双方内部状态并断言守恒——这一段是 GameTest 不容易覆盖的跨模组部分。

### 环境与部署

- `.\gradlew.bat runServer`（`run-server/`，端口 25567，RCON 25577），JDK 17，平坦世界 `world-probe`。
- 桥的预构建产物是 SRG 重混淆的，在 ForgeGradle 开发环境里无法加载，因此用 `./gradlew.bat jar -x reobfJar` 重新构建了开发映射版本放进 `run-server/mods/rosetta_bridge_dev.jar`（该目录已被 `.gitignore` 忽略，不入库）。**用户原始产物已立即还原并用 `cmp` 校验字节一致（SHA-256 `93b9aaf7e442d8c04d6930532cc6413155dbbe22090d7fc496fb0f4d935ac506`）。**
- 关服使用桥的 `console stop`，日志出现 `Stopping the server` 与四个维度的 `All chunks are saved`，`BUILD SUCCESSFUL`，事后 `Get-Process java` 为空。

### 注册与拓扑

- 注册确认：`technom:quantum_jar` → `QuantumJarBlock`、`technom:quantized_glass` → `GlassBlock`、BE 类型 `technom:quantum_jar`、7 个物品。
- TC4R 实际 ID 由注册表读出而非猜测：`thaumcraft:warded_jar`、`thaumcraft:essentia_tube`（另有 `void_jar`、`node_jar`、`brain_jar`、三种特殊管道）。
- 拓扑：**未贴标签的 TC4R 封印罐（装入 64 ignis）→ 源质管道 → 源质管道 → 我们的量子罐**。选未贴标签的源罐是因为已贴标签罐的 `minimumSuction` 是 64，两节管道各衰减 1 之后吸力不足，本来就取不出来——这是 TC4 的设计，不是缺陷。
- `ThaumcraftApiHelper.getConnectableTransport` 双向互认：管道向下看到 `QuantumJarBlockEntity`，我们向上看到 `EssentiaTubeBlockEntity`；管道方块状态自动连成 `down=true,west=true` 与 `down=true,east=true`。

### 一个必须记下的陷阱

初次观察**完全没有流动**，管道的 `count` 字段在 gameTime 4507 时仍是 0——**没有玩家在线时没有任何区块在 tick**，方块实体根本没跑。`level.setChunkForced(0, 0, true)` 之后立刻开始流动。任何无人值守的实机验证都必须先强制加载区块，否则会把"没 tick"误判成"逻辑不工作"。

### 实测结果

- **守恒**：强制加载 15 秒后，源罐 8 + 管道在途 1 + 我们 55 = 64，与初始 64 一致；抽干后源罐 0 + 我们 64 = 64。整条链路没有复制也没有销毁源质。
- **未贴标签罐自动采纳来料类型**：量子罐由空、无 aspect 变为 `thaumcraft:ignis`，走的正是 TC4 只在"尚未绑定 aspect"分支才检查对方 `minimumSuction` 的那条路径。
- **吸力公式逐档实测**（未贴标签基数 48，每 50 点 +1）：存量 0→48、49→48（整数除法，不是四舍五入）、50→49、100→50、320→54、639→60、**640→0**（满罐停止抽取）而 `minimumSuction` 仍为 60。满罐时两者不一致是 TC4R 参考实现的既有语义，不是缺陷。
- **贴标签档**：存量 100 时吸力 66（64 + 2），确实严格强于 TC4R 已贴标签罐的 64——这正是本次刻意偏离要达到的效果。
- **比较器输出**：0 → 15 随存量线性变化，满罐 15。
- **容量硬上限**：`add(10000)` 只接收 640；满罐后 `addEssentia` 返回 0；第二种 aspect 返回 0。
- **`EssentiaSource` 全有或全无**：从 640 中取 641 返回 0 且存量不变；`SIMULATE` 取 640 返回 640 且存量不变（模拟无副作用）。
- **面规则（在空罐上单独复测，避免被"满罐"混淆结论）**：只有 `up` 面 `canInputFrom`/`canOutputTo` 为真、`addEssentia(SIMULATE)` 返回 5、吸力 48；其余五面全部为 0。`takeEssentia` 从 `up` 面实际取出 5（20→15），从 `down` 面返回 0 且存量不变。
- **未注册 aspect 被拒**：`addEssentia` 返回 0 且不落库，不会用伪 ID 存东西。
- **标签语义**：`clearFilter()` 保留已记住的 aspect（TC4 原行为，TC4R 也刻意保留）；`clearContents()` 之后 aspect 变为 `null`，即附录 A-2 的修复生效。
- **掉落物往返**：`Block.getDrops` 产出 `1 quantum_jar`，NBT 为 `{BlockEntityTag:{Essentia:{aspects:[{id:"ignis",n:321}],filters:["ignis"],v:1}}}`——内容、标签和 schema 版本都带走了，loot table 的 `copy_nbt` 有效。

### 本节尚未验证

- **渲染**：无头环境无法执行 `runClient`。液面与标签渲染器还没写，罐目前是静态模型；静态模型、方块状态和贴图路径都没有在客户端看过。
- **持久化跨重启**：本轮只验证了掉落物 NBT，没有存盘后重启再读取同一个方块。
- **右键交互**：`use(...)` 的三条分支（清空/贴摘标签/小瓶与罐物品互倒）是用 `EssentiaContainerApi` 实现的，但本轮没有模拟玩家右键，只验证了它们调用的底层存储与 API 语义。
- **发电机与凝聚器**：尚未实现，因此"管道 → 罐 → 发电机 → 耗能设备"闭环只走通了前两段。
- 只在单机专用服务器、无 GTCEu 的运行时下验证；没有联机、没有与 GT 共存时重跑这组探针。

## S1-B 配方与研究数据验证（2026-09-28）

本节只覆盖 `data/technom/**` 的 S1-B 数据包（8 条配方、4 条研究、要素数据）与 `tools/` 下的两个脚本。它**不代表**任何方块实体、能源或精华行为已迁移；数据加载成功只说明"游戏读到了这些文件并接受了它们的内容"。

以 JDK 17（`C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot`）作为 `JAVA_HOME`，在本 worktree 依次执行：

```powershell
.\gradlew.bat build --console=plain --no-daemon --offline
.\gradlew.bat runGameTestServer --console=plain --no-daemon --offline
python tools/validate_technom_data.py --inventory run-gametest/technom-data-inventory.json
python tools/gen_research_lang.py --check
```

### 实际结果

- `build`：BUILD SUCCESSFUL，单元测试通过。发行 JAR 内本次新增 12 个数据文件（8 配方 + 1 研究 + 3 要素；`data/technom/**` 连既有的 2 个战利品表共 14 个 JSON），`theflogat/technomancy/gametest/**` 与 `data/technom/structures/gametest/**` 仍为 0 条，排除规则未被新文件绕过。
- `runGameTestServer`：**All 10 required tests passed**。批次 `technom_data:1` 4 项为本次新增，`technom_smoke:1` 3 项与 `technom_gtceu:1` 3 项为既有测试。**整份服务器日志 `/ERROR]` 计数为 0**——这一点是关键：配方的坏 id 只会记一行日志然后被丢弃，构建成功证明不了任何事。
- TC4R 自己的加载日志写明 `Loaded 196 TC4 research entries in 7 categories`。第 7 个分类就是 `technom:TECHNOMANCY`（TC4R 自带 6 个）。
- `tools/validate_technom_data.py`：14 个文件、309 项检查、0 错误、0 警告。
- `tools/gen_research_lang.py --check`：两个 lang 文件均为 up to date，退出码 0。

### GameTest 断言了什么

新增 `theflogat.technomancy.gametest.DataPackGameTests`（4 项，批次 `technom_data`）：

| 测试 | 断言内容 |
|---|---|
| `s1bRecipesLoadWithTheAuthoredContent` | 8 条配方逐条：在 `RecipeManager` 中按 id 存在、由预期的**序列化器**反序列化（用序列化器而非 recipe type，因为原版 shaped/shapeless 共用 `minecraft:crafting`）、产物 id 与数量、非空原料条数、原料接受的 item id 全集；奥术配方另外断言 `research()` 与 `vis()` |
| `s1bResearchEntriesLoadIntoTheTc4rCatalog` | 分类存在；4 条研究逐条：分类、网格坐标、complexity、flags、parents、要素成本，以及**每一页**的 type / `value` / `recipe_link_policy` / `recipe_ids`，并要求每个 `recipe_ids` 在 `RecipeManager` 里真的能取到 |
| `s1bObjectAspectsResolveThroughAspectQueryApi` | 每件物品经 `AspectQueryApi.item()` 解析出的要素等于文件里写的值——即 `direct` 条目确实压过了 TC4R 的配方推断 |
| `s1bWritesRegistryInventoryForTooling` | 把运行中服务器的物品 / 物品 tag / 要素 / 配方序列化器 / 本模组配方 / 研究条目导出到 `run-gametest/technom-data-inventory.json`，供上面的 Python 检查器使用 |

### 条件门控的两个方向都验证过

`technom:essentia_dynamo` 与 `technom:energy_condenser` 在本分支尚未注册，因此 2 条配方、2 条研究与 2 个要素文件带 `forge:item_exists` 条件（配方用 `conditions`，TC4R 目录用 `forge:conditions`）。

- **否方向（当前状态）**：导出的注册表快照里只有 6 条本模组配方与 2 条本模组研究，被门控的四项全部缺席，且日志里**没有**任何 `Unknown item` 报错。如果条件成员名写错，配方会被照常解析并因产物物品不存在而报错——没有该报错，正说明条件确实生效了。
- **是方向（临时探针）**：临时加入两份门控在**已存在**物品 `technom:quantum_jar` 上的探针数据后重跑，本模组配方变为 7 条、研究条目变为 197 条，含 `STACK` 图标与指向门控配方的页面全部正常加载。探针文件随后删除，未提交。

### 检查器确实会失败（18 项注入故障）

只有会失败的检查器才算检查器。逐项注入并在之后从 git 恢复工作树，每一项都被捕获且退出码为 1：

配方侧：`research` 写成 `researh`（MapCodec 会静默忽略未知字段，于是研究门控被无声移除）、item id 打错、tag id 打错、`vis` 用了非原初要素、`key` 声明了 pattern 用不到的符号、`components` 写成候选列表（即原版把多个必需原料塌缩进一个 `Ingredient.fromStacks` 的缺陷）、未注册 id 却没有对应条件。

研究侧：页面缺 `aspects`（`ResearchCatalog.aspectMap` 无 null 保护，会 NPE 掉整个文件）、`complexity` 为 0、未知 flag、未知条目字段、`TEXT` 页面带 `recipe_ids`、`recipe_ids` 指向不存在的配方文件、页面文本键不在 lang 文件里。

要素侧：要素名打错、写入只属于同步输出的根字段。

其中"item id 打错""tag id 打错""要素名打错""未注册 id 无条件"四项只有在注册表快照存在时才能捕获；缺少 `--inventory` 时脚本会把这些检查报为 `SKIP` 而不是假装通过，`--require-inventory` 可把缺快照本身变成错误。

### 刻意偏离原版的地方

| 项 | 原版 | 本次 | 理由 |
|---|---|---|---|
| 研究 key | 裸 `TECHNOBASICS` 等 | `technom:TECHNOBASICS` 等 | TC4R `ResearchKey` 的文档约定：裸 key 归属 `thaumcraft` 命名空间，插件应使用 `addon_namespace:KEY`。`DYNAMO` / `CONDENSER` 这种通名尤其容易与他人撞车 |
| `complexity` | TECHNOBASICS / QUANTUMJARS 为 0 | 1 | TC4R 取值域 1..3；`ResearchEntryDefinition` 会静默钳制，写 0 会让文件与游戏实际值不一致 |
| `CONDENSER` 注册条件 | 需要 `Ids.dynNode`（节点发电机） | 只依赖自身方块与父条目 `DYNAMO` | 附录 A-21 的死线缺陷 |
| 中子化金属坩埚配方的研究门 | TC4 自带的 `THAUMIUM` | `technom:TECHNOBASICS` | 去掉对 TC4R 内部 key 的耦合（规格书 6.5 建议） |
| 神秘金属锭原料 | 具体物品 `itemResource:2` | `forge:ingots/thaumium` tag | 让其他模组的神秘金属也能用 |
| 发电机配方的守护罐 | 普通物品匹配 | TC4R 的 `thaumcraft:empty_warded_jar` 自定义原料 | 1.20.1 的罐内容会随物品 NBT 走，普通物品匹配会吃掉装满或贴了标签的罐 |
| 研究文案 | "Redstone Flux" / "RF" | "Forge Energy (Q)" / "Q" | TE/CoFH RF 不在移植范围 |
| `DYNAMO.1` 文案 | 明写"默认需要红石信号" | 只说响应方式可就地重设 | 默认红石模式属规格书 10.3，由发电机分支决定，本分支无法验证 |
| `CONDENSER.1` 中文文案 | 写死"大概 100W 点 RF 产 1 点 Potentia" | 指向配置项 `condenserCostQ`（默认 200,000 Q） | 成本已是配置项，写死数字就是错的 |
| 注魔配方的 `blockCosmeticSolid:4` | meta 4，TC4R 源码无注释 | `thaumcraft:thaumium_block` ×2 | 规格书 10.6 的推断，未经游戏内确认；若结论有变只需改这一个 JSON |

### 本节仍未验证

- **客户端一次都没跑过**。`runClient` 未执行，因此 Thaumonomicon 的四个条目页面、`technom:textures/misc/technomancycategory.png` 分类图标、`technomancybasics.png` 条目图标、研究树布局（坐标 (0,0) / (1,-2) / (2,2) / (2,3)）、页面文本换行与中文字形全部**没有被渲染过一次**。文件里的贴图路径只被校验为"磁盘上存在同名文件"。
- **没有在游戏里合成过任何一件东西**。奥术工作台的 vis 扣费、坩埚的要素消耗、注魔祭坛的 instability 8 与 200 点要素成本、研究解锁后配方是否真的可用，全部只是"数据已按预期加载"，不是"玩家能做出来"。
- **被门控的四项从未以真实 id 激活过**。`technom:essentia_dynamo` / `technom:energy_condenser` 落地后必须重跑 `runGameTestServer`：届时 GameTest 会自动从"断言缺席"切换为"断言存在并逐项核对"，无需改测试代码。
- **要素数值是本次拟定的，不是原版行为**。原版 Technomancy 从未给自己的物品注册过任何要素（全树检索 `registerObjectTag` 无命中），所以这 8 组数值是按 TC4R 自身惯例推的设计选择，没有原版依据，也没有做过平衡测试。
- **`pen_core` 没有要素也没有配方**。它已注册但属 S2 书写笔范围，本次刻意没给它编数值。
- **没有验证含 GTCEu 的组合**。本节两次 `runGameTestServer` 都未加 `-PwithGtceu=true`，因此此前记录的 4 条 `thaumcraft:compat/native_*_cluster_smelting` 空产物报错在本节日志中不出现；那 4 条属 TC4R × GTCEu 的第三方交互，不在本次范围，也没有被本次改动掩盖（本节日志 `/ERROR]` 为 0）。
- **多人、存档与重载**未验证：没有测过数据包 `/reload`、联机同步研究条目、或玩家知识数据在命名空间化 key 下的持久化。

## 能量凝聚器验证（2026-09-28）

本节只覆盖 `technom:energy_condenser`（方块、BlockEntity、模型、战利品表与测试），分支 `s1b/condenser`。它不代表配方、研究、客户端渲染或存档兼容已完成。

以 JDK 17 作为 `JAVA_HOME`，在本工程执行。两次 `runGameTestServer` 之前都删掉 `run-gametest/`：含 GTCEu 的世界在无 GTCEu 的运行时会产出两万多行"缺失注册项"转储（测试仍全部通过，但记录不干净），这也再次说明同一存档不能在有/无 GTCEu 之间切换。

```powershell
.\gradlew.bat build --console=plain --no-daemon --offline
.\gradlew.bat build -PwithGtceu=true --console=plain --no-daemon --offline
.\gradlew.bat runGameTestServer --console=plain --no-daemon --offline
.\gradlew.bat runGameTestServer -PwithGtceu=true --console=plain --no-daemon --offline
```

### 单元测试

`test` 共 120 项通过、0 失败；本轮新增 32 项，原有 88 项不变。

| 测试类 | 项数 | 覆盖内容 |
|---|---:|---|
| `CondenserProductionTest` | 10 | 一个完整生产周期**逐 tick 对账**（离开账本的 Q = 进度增量 + 产量 × 成本）；速率上限与最后一 tick 只取余量（200,000 = 24 × 8,192 + 3,392）；空账本与不足一 tick 的供电都不产生部分扣款；源质缓存满时完全不耗电（A-14 的镜像情形）；进度经存档往返后整个周期仍只花一份成本；越界与未知 schema 的存档被夹紧或丢弃并上报 |
| `EssentiaPushTest` | 10 | A-9 的守恒性质：接收方全收 / 部分收 / 全不收，以及对 0…64 每一种胃口各跑一遍后"仓库 + 接收方"的总量不变；越报与负数报被夹到 `[0, 提供量]`；提供量同时受库存与请求量约束；提供量在调用邻居**之前**已离开仓库（重入的邻居看不到同一批）；邻居抛异常时一分不花；退款放不回去时抛异常而不是静默销毁 |
| `CondenserBalanceTest` | 8 | 出厂默认（200,000 Q，`essentiaFuelScale=0.25` → 发电机每点产 16,000 Q）判定为 `SAFE` 且保有推荐的 4 倍余量；原版 1,000,000 / 1.0 同样 `SAFE`；`cost == yield` 已经算永动机（边界严格）；1～4 倍为警告带；把 `essentiaFuelScale` 调回 1.0 会把默认成本压进警告带、调到 4.0 就变成永动机；`fuelScale <= 0` 与溢出输入都不会绕出"不安全"判定 |
| `RedstoneModeTest` | 4 | 三态真值表；`{NONE, HIGH, LOW}` 轮转；存档 id 往返；无法解析时回落到机器自身的默认（原版硬编码回落 `HIGH`，会让默认 `LOW` 的凝聚器悄悄停机）|

### GameTest

两种运行时都报 `All 16 required tests passed`；本轮新增 10 项，批次 `technom_condenser`，模板 `technom:gametest/empty_5x5x5`。位置与方向一律用绝对值，因为能源/源质面掩码是绝对世界方向。

| 测试方法 | 断言内容 |
|---|---|
| `condenserTurnsForgeEnergyIntoEssentia` | 唯一一项走游戏自身 tick 循环（`startSequence().thenExecuteFor(150, …)`），因此同时验证注册、`getTicker` 接线与配置成本。每 tick 经 `ForgeCapabilities.ENERGY` 灌入 8,192 Q，结束时要求 `已灌入 == 缓存 + 进度 + 产量 × 成本`。实测 **1,228,800 Q 真实 Forge Energy → 6 点 `thaumcraft:potentia`，缓存 28,800 Q，进度 0 Q**（6 × 200,000 + 28,800 = 1,228,800，逐 Q 平衡） |
| `condenserFillsARealWardedJar` | 凝聚器位于真实 `thaumcraft:warded_jar` 正上方并开启 DOWN 输出面；20 点 potentia 全部进入真实罐、凝聚器归零、总量不变、罐内 aspect 为 `thaumcraft:potentia` |
| `aReceiverFillingUpNeitherDuplicatesNorDestroys` | A-9 的世界内版本：罐预装 60、凝聚器持有 20，一次推送后罐 64 / 凝聚器 16 / 总量仍 80；再推一次仍是 64 / 16。原版在这里会复制（全收分支）或销毁（不收分支） |
| `redstoneGatingStopsAndStartsProduction` | 默认 `LOW`（无信号运行）；未加电时确实在消耗 Q，且 `isWorking()` 为真、`progress()` 落在 (0, 1) 内；放下红石块后 10 tick 内能量与进度完全不变且 `isWorking()` 为假；切到 `HIGH` 后同一个信号又让它工作。最后把源质缓存填到 64 点再跑 10 tick：**能量与进度一个都不动，`isWorking()` 为假**，即功能矩阵要求的「满槽不耗电」 |
| `noFaceAcceptsEssentiaAndTheClaimMatches` | A-10：六面 `canInputFrom` 全 `false`、`addEssentia` 恒 0、经 `EssentiaApi.add` 也是 0 且缓存不变；`suctionAmount`/`suctionType`/`minimumSuction` 为 `0`/`null`/`0`；未开启的面不可连接且 `essentiaAmount` 为 0；开启后可连接并交出真实量；`takeEssentia` 返回**实际取出量**（请求 100、库存 10 → 10），`SIMULATE` 不改状态，关闭的面返回 0 |
| `everyFaceTakesEnergyAndNoFaceGivesItBack` | A-11：六面都有 FE 能力、`canReceive` 为真、`canExtract` 为假、`extractEnergy` 恒 0；`null` 面只拿到只读视图；模拟不动余额；5,000 Q 真实灌入成功后无法被抽回 |
| `interactingWithoutABlockEntityDoesNotThrow` | A-12：在空气与木桶（外来 BE）两个位置，以空手 / 玻璃瓶 / 红石三种手持、潜行与非潜行两种状态，共 12 次调用 `use`，全部返回 `PASS` 且不抛异常。原版正是在"非潜行且手上有东西"这条分支上解引用 null |
| `sneakClickSwitchesFacesAndProgrammingItemsSetTheMode` | 潜行右键切换 EAST 面开/关；对 `facing` 所在的正面无效；红石粉选 `HIGH` 并消耗 1 个、记录 `modified`；再用火药选 `NONE` 时把上一次的红石粉退回背包 |
| `theBlockIsMineableAndDropsItself` | 方块在 `minecraft:mineable/pickaxe` 内，`requiresCorrectToolForDrops` 生效（空手判定为不可采集、石镐可以），且战利品表确实被数据包加载并产出 1 个 `technom:energy_condenser`。这一项专门防"需要正确工具却没进任何工具标签"和"战利品表路径写错"这两类不报错的失败 |
| `condenserAcceptsNativeEuPackets` | 无 GTCEu 时按存在性检查跳过并在日志写明原因；有 GTCEu 时用真实 `IEnergyContainer` 检查面权限、额定 EV(2048 V) × 2 A、无输出额定、容量读数（100,000 Q ÷ 4 = 25,000 EU）、超压整包被整体拒绝且不动余额、5 安培请求被额定 2 安培截断。**实测 2 A × 128 EU = 1,024 Q 原生到账，并在下一 tick 全部变成转换进度**，即 EU 与 FE 用的是同一份余额 |

### 其它实测结果

- 启动期平衡检查在两种运行时都记录：`balance.condenserCostQ=200000 against a dynamo yield of 16000 Q per unit of potentia at balance.essentiaFuelScale=0.25: the condenser is a net energy sink, as intended.`
- 两次 `build` 的 `build/libs/technom-1.20.1-0.1.0-dev.jar` 逐字节相同：857,360 字节、312 个条目，SHA-256 `7416ec018f2587592889fa247ce9a6746a48d70fe6d14b29869ebc3d77948850`；`com/gregtechceu` 0 条、`gametest` 0 条。`GtceuIsolationGameTests` 扫描 42 个发行类、0 个越界引用。
- 方块状态与模型的**离线**校验（不是渲染验证）：按原版 multipart 匹配规则枚举全部 4 × 64 = 256 个状态，六个面各**恰好**命中 1 个模型（0 个缺面、0 个重叠），包括被方块逻辑禁止的"正面开输出"组合也不会重叠；`blockstates/energy_condenser.json` 引用的 7 个单面模型、`block/energy_condenser`、`item/energy_condenser` 及其 `parent` 链上引用的全部纹理都存在于磁盘。
- 中文方块名取自 `reference/legacy-1.12/lang/zh_cn.json` 的 `tile.techno:condenserblock.name`，以程序方式复制并按码位核对（U+80FD U+91CF U+8F6C U+6362 U+7535 U+5BB9）；两个 lang 文件改动后仍是合法 JSON，各 13 个键。

### 本节尚未验证

- **客户端**：`runClient` 未执行。模型、纹理朝向（含 A-13 顶/底交换的修正）、multipart 的贴图反馈、手持与物品栏渲染、破坏粒子、翻译文本全部只有上面的离线校验，没有任何渲染证据。
- **同步与存档**：`getUpdateTag`/`getUpdatePacket` 以及"只在跨越显示粒度时发包"的节流策略没有在真实客户端上观察过；`saveAdditional`/`load`（能量、源质、进度、红石模式）只有组件级的 JUnit 往返，没有做过区块卸载/重进或服务器重启的实机验证。
- **可获得性**：没有配方、没有研究，`technom:energy_condenser` 目前只能从创造物品栏取得。扳手物品与 `technom:tools/wrench` 标签未实现，旋转只能经 `rotate()`/`mirror()`（结构方块、`/clone` 等）触发。
- **与发电机的联动**：永动机约束目前只按配置值与规格书给出的发电机常数（potentia `fuelValue = 800`、80 Q/燃料点）计算，没有和真实的 `TileEssentiaDynamo` 移植对接过。"凝聚器 → 源质管 → 发电机 → 凝聚器"的完整闭环没有在世界里跑过。
- **管道**：只验证了对 `WardedJarBlockEntity` 的直接推送和逐面协议取值，没有放置真实 `EssentiaTubeBlockEntity` 验证"管道主动从凝聚器取货"这条路径（凝聚器 `minimumSuction = 0`，理论上任意吸力都能抽取）。
- **性能**：没有测量大规模布置下的 tick 与网络开销。"不再每 tick 发包"目前由代码与 GameTest 的状态断言支持，不是由实际包计数支持。
- **专用服务器与多人**：本轮未执行；`runServer`、联机、换维度、死亡重生均未涉及。

## S1 合并后整体 GameTest（有/无 GTCEu，2026-09-28）

前面每条分支都各自跑过自己的 GameTest，但**合并后的整体**此前从未在游戏里跑过。本节是合并树的第一次整体运行，两种运行时都跑了。

```powershell
Remove-Item -Recurse run-gametest
.\gradlew.bat runGameTestServer --console=plain --no-daemon
Remove-Item -Recurse run-gametest
.\gradlew.bat runGameTestServer -PwithGtceu=true --console=plain --no-daemon
```

- **无 GTCEu：31/31 通过**；整份日志只有 1 条 ERROR，是测试服务器没有 `server.properties`（`minecraft/Settings`），与本模组无关。
- **有 GTCEu：31/31 通过**；日志确认 GT 真的加载（`GTCEu common proxy init`），且本模组记录 `Technomancy and GTCEu agree on 4 FE per EU (nativeEUToFE=true, FE converters=true)` 与 `GTCEu native EU integration enabled at 4 FE per EU`。
- 两种运行时都记录了永动机检查的两次结果（启动时用内置值、燃料表加载后用实际数据）：`balance.condenserCostQ=200000 against a dynamo yield of 16000 Q per unit of potentia (fuel value 800) at balance.essentiaFuelScale=0.25: the condenser is a net energy sink, as intended.`，以及 `Loaded essentia fuel values for 44 aspects (fallback 25)`、`Loaded 198 TC4 research entries in 7 categories`（第 7 个分类是我们的 `technom:TECHNOMANCY`）。
- 每次运行前都删掉 `run-gametest/`。**不要**在有/无 GT 之间复用同一个世界：那会产生约 29000 行缺失注册项转储（测试仍会通过，但日志被淹没）。

### 带 GT 时那 4 条 ERROR 的定性（已核实，非本模组问题）

有 GTCEu 时日志多出 4 条 `Output item 0 of recipe thaumcraft:compat/native_{copper,tin,lead,silver}_cluster_smelting is empty`。此前两次都被当作"第三方问题"带过而没人验证，这次查清了：

- 配方文件是 TC4R 自己的 `data/thaumcraft/recipes/compat/native_copper_cluster_smelting.json`，类型 `thaumcraft:common_metal_smelting`，**产物写的是一个 tag**（`"result": {"tag": "forge:ingots/copper", "count": 2}`），并且带 `forge:tag_empty` 取反的加载条件。
- 报错的是 **GregTechCEu**：它扫描熔炼配方来自动生成自己的机器配方，从一个 tag 产物里读不出具体物品，于是报 empty。
- 配方文件不是我们的，报错代码也不是我们的，且 `src/main/resources/` 里没有任何地方引用该配方或该 tag。

**结论：TC4R × GTCEu 的交互问题，本移植不涉及，也无法在本工程内修。** 记录在此以免再被反复排查；如要修，应向 TC4R 反馈（tag 产物对 GT 的配方扫描不可见）。

### 本节尚未验证

- 客户端：`runClient` 仍未执行。为此新增了 `tools/client_check.py`（见下一批验证），模型、blockstate 与贴图图集只在客户端加载，服务端与探针全部通过也不能说明它们没坏。
- 合并后的**游戏内玩法链路**：`probes/` 下的探针（含新增的 S1 闭环与重启持久化两轮）尚未对合并树执行。



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

下一阶段开始后，每批功能都应更新此记录，写明实际运行命令、观察结果和未覆盖的环境；不要用 `test NO-SOURCE`、启动成功或 GameTest 冒烟结果代替功能验收。

## S2 剩余两台机器与合并修复（2026-09-28）

本节覆盖合并 `s2/machines`（`2a2ae1c`）之后补全的两台 S2 机器，以及同一次核对中发现的合并破损。

### 合并修复

合并提交 `2a2ae1c` 是在没有构建合并树的情况下提交的，其中两处按行拼接的冲突结果无法编译：`TechnomBlocks` 的 `NODE_FABRICATOR` 注册丢失了 `.sound/.requiresCorrectToolForDrops/.noOcclusion/.isValidSpawn` 及右括号，`TechnomancyClient` 丢了 `FMLClientSetupEvent` 导入而方法签名仍在用。功能矩阵则有 6 台机器同时保留旧「待迁移」行与新「已实现」行。修复提交 `af58c9e`（语言文件经键集合核对无缺失无重复）。`s1x/assets` 的 24 张纹理随 `ca5f18d` 合入。

### 新增的两台机器

| 机器 | id | 已执行 |
|---|---|---|
| 高级分解台 | `technom:adv_decon_table` | 逻辑 `DeconstructionTable`（原始要素递归、两段掷骰、倍率）JUnit `DeconstructionTableTest` 5 项；GameTest `technom_s2_decon` 3 项：活体注册表下 `potentia → ignis+ordo`、无主机器持续进食、六面 `IItemHandler` |
| 邪术吞噬器 | `technom:eldritch_consumer` | GameTest `technom_s2_consumer` 5 项：无能量不进食、掉落物按要素与整份能量被吞、方块只取要素不掉落物、基岩与箱子被放过、六面可抽出要素 |

命令与结果：

```powershell
.\gradlew.bat build --offline --console=plain --no-daemon          # 成功；JUnit 229 项通过 0 失败（新增 5）
python tools\validate_technom_data.py                              # OK: no errors（13 条 category 警告为既有惯例）
.\gradlew.bat runGameTestServer --offline --console=plain --no-daemon                 # All 80 required tests passed
.\gradlew.bat runGameTestServer -PwithGtceu=true --offline --console=plain --no-daemon  # All 80 required tests passed
```

### 本节尚未验证

- 两台机器的客户端渲染、实机点击与 Tooltip；消费者的生物击杀与带方块实体方块的实机行为。
- 消费者的容器容量：原版 `canFillList` 在已有 4 种要素或任一超过 4 点时停止，本实现取「4 种要素、合计 256、每种 64」，已写在类注释里，是刻意的放宽。

### 合并核对发现的既有 GameTest 失败（已全部修复）

首次运行合并树时另有 9 项来自更早 S2 分支的失败——那些测试随提交写入但从未运行。逐项定位后，其中 8 项是测试自身的问题，1 项是真实缺陷：

| 失败 | 根因 | 处理 |
|---|---|---|
| `S2FusorGameTests.aFullOutputAndTheFaceRulesCostNothing` | **真实缺陷**：`FusorSides.space` 只给 INPUT 面返回容量，测试直接向输出槽装料被静默拒绝，于是"满输出"从未成立。输出槽本来就该有容量。 | 改 `FusorSides.space`，INPUT 与 OUTPUT 都按其自身要素给出余量 |
| `S2ProcessingGameTests.everyFaceReachesTheRightSlot` | `ProcessorBlockEntity` 的 `items` 处理器对输出槽一律 `isItemValid=false`，无侧面视图因此填不进输出槽；但输出槽是机器自管的。 | `isItemValid` 只对输入槽应用加工规则，输出槽放行；面视图仍按槽位限制 |
| `WandChargeGameTests` 2 项 | `makeMockServerPlayerInLevel()` 会真正登录，登录欢迎包写向 headless 测试端不存在的 netty 通道，`Connection.channel()` 空指针；充电本身不碰连接。 | 测试改为直接 `new ServerPlayer(...)`，不登录、不发包 |
| `NodeFabricatorGameTests` 5 项 | 测试把控制器放在相对 y=1，而 GameTest 把结构整体下移一格、地面在相对 y=1，于是 3×3 外壳与地面重叠而无法成型；另外多处把绝对坐标当相对坐标用。 | 控制器改到相对 y=2；绝对/相对位置分开；节点改用 `level.setBlockAndUpdate(nodePosition(), ...)` |

修复后 `runGameTestServer` 在**有/无 GTCEu** 下均为 **All 80 required tests passed**。

## 客户端实机探针（2026-09-28）

服务端与 GameTest 都不加载模型/blockstate/图集，所以本轮用 RosettaRemoteDebugBridge 把 dev 客户端实机拉起来跑客户端探针。操作流程见 [README 的“客户端实机探针”](../README.md#客户端实机探针)。

### 执行

```powershell
# 非阻塞独立进程；带 GTCEu，quick-join "New World"
Start-Process -FilePath .\tools\start_client_detached.cmd -ArgumentList '"New World"','"true"' -WorkingDirectory $PWD
# 轮询 build\client-run.log 至 "Remote bridge listening on"（桥 127.0.0.1:48791）
python .\tools\client_probe.py probes\client --attach
```

- 客户端确实带 GTCEu（日志含 `GTCEu common proxy init!` 与材料注册）；quick-join 用 `--quickPlaySingleplayer New World`，进世界后 `Dev joined the game`，桥在 `ServerStartedEvent` 监听 48791。
- 非阻塞启动必须用 `Start-Process`：`cmd /c start` 和直接调用都会让后台 java 继承并占住调用方的 stdout 管道，导致 agent 被卡住。
- 带 GTCEu 时 Forge 早期窗口崩在 `DisplayWindow.setupMinecraftWindow → UnionFileSystemProvider.getFileSystem → FileSystemNotFoundException`；`run-client/config/fml.toml` 设 `earlyWindowControl = false` 后正常。
- 关客户端用桥 `exec` 执行 `Minecraft.getInstance().stop()`；执行后 java 进程与 48791 均消失。

### 结果

```
============ 00_models_and_sprites.java ============   PASS
============ 10_renderers_and_tab.java =============   PASS
2/2 probes passed
```

- `00_models_and_sprites` 遍历 technom 的全部 15 个方块、321 个 blockstate 与 42 个物品，断言每个都有真实烘焙模型与粒子贴图，并断言 quantized_glass/quantum_jar 在 translucent 层。
- `10_renderers_and_tab` 放置每个方块、确认客户端 BlockEntity 与渲染器解析，并确认创造标签为 42 项。

### 探针抓到并修掉的缺陷

`technom:node_dynamo` 的方块模型引用了 `technom:models/nodedynamo`，而 `assets/technom/textures/models/` 不存在（贴图在 `textures/entity/nodedynamo.png`）；方块图集因此对该模型报 “Missing textures … missingno”。改引用为 `technom:block/nodedynamo` 并把贴图放入 `textures/block/` 后，客户端探针 2/2 通过。这是纯客户端问题：服务端、GameTest 与数据校验都不会报告它。

### 尚未验证

- 几何、朝向、颜色与 z-order 的“看起来对不对”仍需人眼；探针只保证有模型、有贴图、在正确的渲染层。
- 客户端脚本（CRD SCRIPT）与会话未启用；本轮只用 README 描述的进程与桥路径。

## Botania 收尾（ManaExchanger / 魔力流体 / 配方 / Lexicon / 模型，2026-09-28）

本节覆盖 [BOTANIA 规格][botania] 的对齐收尾：`ManaExchanger` 精确版、魔力流体与桶、Botania 配方、Lexicon 词条与机器模型。它不覆盖客户端实机渲染（见下）与 Botania 缺席时的加载安全。

### 依赖变更（必须先说）

Botania 机器此前是**无条件注册**且引用了 Botania API 类，但 Botania 只在 `compileOnly` 上，开发运行时根本没有它——本轮首次在 `runGameTestServer` 中暴露。修复：`build.gradle` 增加 `runtimeOnly` 的 `vazkii.botania:Botania:1.20.1-454-FORGE` 与 `vazkii.patchouli:Patchouli:1.20.1-85-FORGE`（Botania 的 `mods.toml` 把 Patchouli 与 Curios 列为必需；Curios 已作为 TC4R 依赖在运行时）。这两个只进入开发运行时，不打包进发行 JAR。

### 执行的命令与结果

以 JDK 17（`C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot`）在本 worktree 执行：

```powershell
.\gradlew.bat build --console=plain --no-daemon                              # JUnit 233 通过 0 失败
.\gradlew.bat runGameTestServer --console=plain --no-daemon                  # All 83 required tests passed
.\gradlew.bat runGameTestServer -PwithGtceu=true --console=plain --no-daemon # All 83 required tests passed
python tools\validate_technom_data.py                                        # OK: no errors
```

`compileJava` 仅有既有的 `ResourceLocation(String,String)` 弃用提示。`runGameTestServer` 日志确认 Botania 与 Patchouli 都真实加载（`Loaded 30 Loonium configurations`、`patchouli/ Sending reload packet to clients`），注册表快照 2579 个物品、2929 条配方，7 条新增的 `technom:botania/*` 配方全部加载。

### 覆盖内容

- **ManaExchanger**（`ManaExchangerBlockEntity`）：`EXCHANGER_COST=1000`、能量容量 10,000、`FluidTank(1000)` 只收魔力流体、默认红石 `LOW`；`mode==false` 池→罐、`mode==true` 罐→池，每 tick 各按 1,000 Mana↔1 mB、1,000 Q 结算；池必须在正上方（`ManaPool` 能力），否则不工作。流体面规则照上游：顶面与无侧面解析都不给读写，`mode` 决定可填/可取。方块状态 `OUT`/`ACTIVE` 同时驱动 in/out 侧面模型与池上覆盖层；`ManaExchangerBlock implements PoolOverlayProvider`（1.20.1 的池渲染器在池下方方块上查这个接口）。
- **魔力流体与桶**：`ManaFluidType` + `ForgeFlowingFluid.Source/Flowing` + `ManaFluidBlock` + `ManaBucketItem`；贴图 `block/manafluid_still|flow`（已带 `.mcmeta`），客户端经 `IClientFluidTypeExtensions` 提供。`BucketItem` 带 `FluidBucketWrapper` 能力，容器物品为空桶。
- **配方**（`data/technom/recipes/botania/`，全部带 `forge:mod_loaded=botania` 与 `forge:item_exists` 门控）：魔力灌注 `mana_coil`（红石，3000）与 `mana_bucket`（空桶，50000，1.12 增量）；有序合成 `manasteel_gear`、`flower_dynamo`、`mana_fabricator`、`processor_bo`、`mana_exchanger`，材料映射按无 TE 分支（`ingotManasteel`→`botania:manasteel_ingot`、`manaDiamond`→`botania:mana_diamond`、`livingrock`→`botania:livingrock`、魔力池→`botania:mana_pool`、`powerCoilSilver`→红石、`frameTesseract`→末影之眼）。
- **Lexicon**：`assets/botania/patchouli_books/lexicon/en_us/` 下 1 个分类 + 5 个词条。**必须放在 `botania` 命名空间**：Patchouli 的 `BookContentResourceListenerLoader` 按 `book.id` 分组，放本模组命名空间不会被 `botania:lexicon` 取到（客户端探针已实测）。灌注页用 `botania:mana_infusion` 页型，普通页用 `crafting`；文本键同时写入 `en_us`/`zh_cn`（键集合一致，校验器通过）。
- **模型/贴图**：`ManaExchanger` 拆成 `mana_exchanger_in/out`（底/顶/侧逐面，顶恒为 inactive）、`FlowerDynamo` 底座+机头、`ManaFabricator` 花盆、`processor_bo`/`processor_bo_lit` 用 BO 自己的贴图（此前误用 Blood Magic 的 `processorbm*`）。`blockstates/mana_fluid.json` 覆盖 `level=0..15`（`LiquidBlock` 的 `getRenderShape` 是 `INVISIBLE`，模型不会被绘制，只是为了不让客户端模型探针把它判为缺失）。
- **校验器**：`tools/validate_technom_data.py` 新增 `botania:mana_infusion` schema 与 `input`/`output`/`mana` 读取，因此该数据包不再被误报为未知配方类型。

### GameTest（`technom_botania`，3 项）

| 测试方法 | 断言内容 |
|---|---|
| `drawsManaIntoTheTank` | 真实 `botania:mana_pool` 在交换器正上方且 5,000 Mana、能量满：10 tick 后池空、罐 5 mB、能量余 5,000（5 次操作 × 1,000 Q） |
| `pushesFluidIntoThePool` | 罐预装 5 mB、`mode=true`：10 tick 后池 5,000 Mana、罐空、能量余 5,000 |
| `needsAPoolOnTop` | 正上方无池时 `ACTIVE=false`，20 tick 不扣能量 |

注：魔力池的容量字段在它自己第一次 tick 后才初始化，所以“池→罐”一项把注水放在第 2 tick，否则 `receiveMana` 会被 `manaCap=0` 夹掉——这是 Botania 侧的行为，不是交换器缺陷。

### 刻意偏离上游

| 项 | 上游 | 本次 | 理由 |
|---|---|---|---|
| ManaExchanger 的池判定 | `tile instanceof TilePool` | `ManaPool` 能力 | 1.20.1 不再能按内部类判断，能力是公开契约；副作用是本模组的 ManaFabricator 放在上方也会被接受 |
| FlowerDynamo/ManaFabricator 扳手 | 转向首个相邻能量方块 | 循环六面 | 与 `EssentiaDynamo` 的移植口径一致（A-19 之后的既有决定） |
| FlowerDynamo 模型朝向 | 代码模型按 `facing` 旋转 | JSON 按 `facing` 变体旋转 | 等价做法；但花瓣/花盆细节是近似几何，非逐顶点一致 |
| 魔力流体方块 blockstate | 无（流体由流体渲染器画） | 有（满足客户端模型探针） | `LiquidBlock.getRenderShape=INVISIBLE`，多出的 blockstate 不会被绘制 |

### 本节尚未验证

- **客户端实机**：本轮没有重跑 `probes/client`。模型、blockstate、图集、`IClientFluidTypeExtensions` 的贴图、池覆盖层、Lexicon 在书里的显示，全部只有离线校验（blockstate 组合覆盖、模型/贴图文件存在、JSON 合法性），没有渲染证据。`mana_fluid` 的 blockstate 与 `mana_bucket` 的水桶图标尤其需要人眼确认。
- **Lexicon 是否真的出现在书里**：Patchouli 的 `use_resource_pack` 跨命名空间加载与 `i18n` 文本只在服务端重载日志里看到 Patchouli 活动；词条被写入 `botania:lexicon` 后是否显示、排序、图标解析均未在客户端确认。
- **Botania 缺席安全**：机器类仍无条件注册并引用 Botania API，Botania 不在时会 `NoClassDefFoundError`；本轮只是把开发运行时补上了 Botania。`ENGINEERING_GUIDE` 的“缺席时核心可玩”仍是未完成项。
- **桶的实际灌装/倒出**：`BucketItem` 的右键放液/取液、与交换器罐的交互、`FluidBucketWrapper` 在漏斗等自动化下的行为没有实机验证；只有流体罐 API 层面的 GameTest。
- **能量守恒的桶级边界**：罐满/池满时的“不扣能量、不丢 mana/流体”只在代码里按 `>= / <=` 边界成立，GameTest 未覆盖边界值与 `SIMULATE` 无副作用。
- **与 GTCEu 同 tick 的 EU 侧**：83 项含 GT 运行时通过，但交换器没有 EU 面（上游也只在 FE 上），未测 EU 包与流体转换同 tick 的交互。

[botania]: https://github.com/Mordenkainen/Technomancy/blob/37bf9a56fe1f713258f298d7ef392b104ccae88f/src/main/java/theflogat/technomancy/lib/compat/Botania.java

## 节点创建（S4，2026-09-28）

本节完成 [BOTANIA][botania] 之后的下一个 S4 缺口：`TileNodeGenerator` 的“以金与污染造节点”仪式。之前的结论是 TC4R 公开 `NodeApi` 只能改已有节点、创建需要 Mixin；这次读 TC4R 源码后确认**不需要 Mixin**。

### 创建原语怎么找到的

`dev.tc4port.thaumcraft.block.AuraNodeBlock.setPlacedBy` 自己就是创建路径：玩家放置 `TCBlocks.AURA_NODE` 时它调用 `AuraNodeFactory.create(...)` 得到 `AuraNodeState`，再 `AuraNodeBlockEntity#setNodeState(...)`。`AuraNodeFactory` 是 `worldgen` 包的 public 类，但真正需要的只是“放方块 + 设状态”这两步。`NodeCreation.create(...)` 就做这两步（带空地判定），因此没有新增任何 Mixin/accessor。

### 行为（对照 `TileNodeGenerator`）

- **专属要素**：北/西侧制造器只吸 auram，南/东侧只吸 vitium（`NodeCreationRules.dedicatedAspect`），`suctionType`/`addEssentia`/主动吸取三处都按此门控。
- **启动**：法杖右键（`NodeFabricatorBlock implements WandInteractionTarget`，TC4R 的 `WandItem.onItemUseFirst` 在焦点之前调用 `Block#use`）；要求成对、未加电、之间无节点、空地、两缓冲合计 > 64、能量 ≥ `round(((a+t)/2)^2 * 762.939453125)`。潜行右键留给焦点（`player.isSecondaryUseActive()`）。
- **仪式**：200 刻；只有被点击的一侧为 initiator 并在第 200 刻结算。
- **结算**：类型/修正按 `generateNode` 级联，Vis = 半总量，随机生态群系要素（`BiomeAuraProfile.randomAspect`，无则随机原初）；**只有节点真的建成才扣**两侧精华与能量，位置被占则退款并取消。
- **状态**：`running`/`step`/`initiator` 进 NBT；节点出现即取消仪式并转回回充。

### 命令与结果

```powershell
.\gradlew.bat build --console=plain --no-daemon                              # JUnit 238 通过 0 失败（新增 NodeCreationRulesTest 5）
.\gradlew.bat runGameTestServer --console=plain --no-daemon                  # All 85 required tests passed
.\gradlew.bat runGameTestServer -PwithGtceu=true --console=plain --no-daemon # All 85 required tests passed
python tools\validate_technom_data.py                                        # OK: no errors
python tools\gen_gametest_structures.py --check                              # 两个模板 up to date
```

新增 `gametest/empty_7x5x9` 模板（7×5×9，地板 y=0），是能容下“相距 6 格的一对 + 两侧 3×3×3 壳层 + 节点”的最小房间；由 `tools/gen_gametest_structures.py` 生成。

### GameTest / JUnit 断言

| 测试 | 断言 |
|---|---|
| `NodeFabricatorGameTests.thePairBuildsANodeOutOfAuramAndVitium` | 一对成型的制造器、北侧 200 auram、南侧 200 vitium、能量 = 成本：`startCreation` 成功，210 刻后节点位置出现真实 `AuraNodeBlockEntity`，类型 `HUNGRY`、修正 `PALE`、单一生态要素 200 Vis，两侧缓冲清零 |
| `NodeFabricatorGameTests.creationModeOnlyWantsItsDedicatedAspect` | 建节点模式下北侧 `suctionType` 为 auram，`addEssentia(aer)` 返回 0，`addEssentia(auram)` 返回 4 |
| `NodeCreationRulesTest`（5 项） | 朝向→专属要素；能量 = `((a+b)/2)^2 × 762.939453125`（含 200+200=30,517,578）；Vis = 半总量；类型级联（PURE/HUNGRY/UNSTABLE/TAINTED/DARK/NORMAL）；修正级联（FADING/BRIGHT/PALE/NONE）|

### 刻意偏离上游

| 项 | 上游 | 本次 | 理由 |
|---|---|---|---|
| 创建入口 | `ThaumcraftWorldGenerator.createNodeAt`（1.7 内部） | 放 `TCBlocks.AURA_NODE` + `setNodeState`（TC4R 自带 block 的同一路径） | TC4R 没有对应公开 API；这就是它自己放节点时做的事 |
| 建节点前的消耗 | 先 `generateNode` 再无条件扣精华/能量 | 先建成，成功才扣；失败退款 | 上游在位置被占时仍扣费且可能出问题；本版按本工程既有“提交后才付费”口径 |
| 制造器姿势/音效 | 无 | 启动时 `amethyst_block_resonate` | 纯反馈，无玩法影响 |

### 本节尚未验证

- **客户端实机**：仪式没有光效/雷击/旋转渲染（旧版有 TESR 闪电），法杖右键的左右手、副手盾牌、创造模式等交互分支没有实机点击；只有 `startCreation` 的服务端逻辑与 `WandInteractionTarget` 的编译期接线。
- **吸收节点再创建**：`ItemFusionFocus` 的“吸收一个节点、右键空地造节点”旧手势没有恢复，本焦点仍只合并两节点。
- **节点类型分布的长期行为**：HUNGRY/PURE/TAINTED 等建出后的生态生命周期（饿节点吃方块、腐化扩散）只由 TC4R 自身负责，本节只断言建出时的瞬时状态。
- **音效/周边**：`amethyst_block_resonate` 是否合适、外壳与节点的清理在真实存档中的表现未验证。

## 客户端实机验证（S3/Botania 资产，2026-09-28）

这是 `probes/client` 在 S2 之后第一次重跑。之前那次的结论只覆盖当时存在的 15 个方块；S3 与 Botania 新增的方块从未在客户端加载过，这次的探针一次抓出三个真实缺陷。

### 执行

以 JDK 17 在本 worktree 执行。先用**无 GTCEu** 的客户端定位模型缺陷，再用**含 GTCEu** 的客户端确认 JEI 降版后 GT 运行时可用：

```powershell
Start-Process -FilePath .\tools\start_client_detached.cmd -ArgumentList '"New World"','"false"' -WorkingDirectory $PWD
# 等 build\client-run.log 出现 "Remote bridge listening on 127.0.0.1:48791"
python .\tools\client_probe.py probes\client --attach
# 修完模型缺陷后，再用 "true" 重跑一次；见下“含 GTCEu 的客户端”
```

结果：两种运行时下 `00_models_and_sprites` 与 `10_renderers_and_tab` 都 **2/2 通过**；后者放置 41 个方块、确认 73 项创造标签、各 BlockEntity 与渲染器解析正常。后来又加了 `20_patchouli_lexicon`（见下），最终 **3/3 通过**。

### 抓到并修掉的三个客户端缺陷

| 缺陷 | 现象 | 根因 | 修复 |
|---|---|---|---|
| `technom:fake_air_light` | blockstate 与模型均 `Failed to load`，粒子图标 missingno | 两个 JSON 都带 UTF-8 BOM，`Gson` 报 `MalformedJsonException` | 去掉 BOM（`fake_air_light.json` 的 blockstate 与模型） |
| `technom:existence_fountain` | `Missing textures ... technom:entity/fountain` | 方块模型引用了 `textures/entity/` 下的贴图；方块图集只拼接 `block/`、`item/` 与模型引用的方块级贴图，`entity/` 不进图集 | 把 `fountain.png` 复制到 `textures/block/` 并把模型改引 `technom:block/fountain` |
| `technom:mana_fabricator` | 模型 `Failed to load`，进而 `FileNotFoundException`，粒子 missingno | 模型元素用了 `-15`/`15`/`±30` 度旋转，而 1.20.1 只接受 `-45/-22.5/0/22.5/45`，整个模型解析失败 | 全部改成 `±22.5` |

三者服务端、GameTest 与数据校验都不报（模型只在客户端烘焙）。这与 README 记录的 `node_dynamo` 贴图问题是同一类失败。

### 含 GTCEu 的客户端（已修复：JEI 降到 GTCEu 的编译版本）

`withgtceu=true` 的客户端最初在启动早期崩：

```text
MixinApplyError: gtceu.mixins.json:jei.FluidHelperMixin ... Invalid descriptor
Expected (Ljava/util/List;...) but found (Lmezz/jei/api/gui/builder/ITooltipBuilder;...)
```

GTCEu 7.5.3 的 JEI 兼容 Mixin 期望旧版 JEI 的 `List<String>` tooltip 方法，而运行时解析到 `jei 15.56.0.205` 的新 `ITooltipBuilder` 签名。JEI 是提交 `86c9cac` 加入的 `runtimeOnly`，`runGameTestServer`（服务端、不加载 JEI）不受影响；此前那次客户端实机（`run-client/logs`，19:02）早于该提交，所以没暴露。

**修复**：本工程没有任何 JEI 代码（`rg "mezz.jei" src/main/java` 为空，`86c9cac` 说“JEI 插件随后”但从未写），JEI 纯粹是开发客户端用来显示配方的运行时依赖，因此可以自由选版本。GTCEu 7.5.3 的 `mods.toml` 声明 `jei ("[15.20.0.115,)")`，其下限通常就是它的编译版本；把 `jei_version` 从 `15.56.0.205` 改为 **`15.20.0.115`** 后，含 GT 的客户端正常启动，探针同样 **2/2 通过**。`build` 与 `runGameTestServer -PwithGtceu=true` 仍分别是 238 JUnit / 85 GameTest 通过。

### Botania Lexicon 实机验证（新增 `20_patchouli_lexicon`）

加了这个探针后，第一次跑就发现词条**不在书里**：书的分类 11、词条 245，全是 Botania 自己的；我们的 `technom:technomancy` 分类与 5 个词条一个都没加载，且日志没有任何报错。

根因（反汇编 `BookContentResourceListenerLoader`）：`apply` 把每个 `patchouli_books/<bookId>/...` 按 **`new ResourceLocation(文件命名空间, bookId)`** 分组，而 `findFiles(book, ...)` 只取 `data.get(book.id)`。书 id 是 `botania:lexicon`，我们的文件在 `assets/technom/...`，被分到 `technom:lexicon`，永远匹配不上。也就是说，`use_resource_pack` 给别的书加页时，**文件必须放在那本书的命名空间下**，不是自己的。

修复：把整套 `patchouli_books/lexicon/` 从 `assets/technom/` 移到 `assets/botania/`，并把 5 个词条的 `"category"` 从 `technom:technomancy` 改成 `botania:technomancy`（词条/分类 id 随命名空间变成 `botania:*`）。文本键仍留在 `assets/technom/lang/`，Patchouli 的 `i18n` 按全局键解析，不受影响。

修复后探针实测：书的分类 12、词条 250（各 +1/+5），`techno_basics`/`flower_dynamo`/`mana_fabricator`/`processor_bo`/`mana_exchanger` 全部 `true`。`probes/client` 由 2/2 变 **3/3 通过**。

### 覆盖与未覆盖

- 探针只证明“每个方块/物品都有真实烘焙模型与粒子贴图、渲染器解析、在正确的渲染层”。几何、朝向、颜色、z-order 是否好看仍需人眼。
- 两种客户端运行时（有/无 GTCEu）现在都跑通了 `probes/client`；无 GT 那次用于定位上面三个模型缺陷，含 GT 那次用于确认 JEI 降版后 GT 客户端可启动。
- Botania 手册词条已确认**进了书的分类与词条表**；但“翻开那一页、图标与文字排版是否好看”仍需人眼。
- 本次仍未验证：魔力流体与桶的手持/倒出；仪式创建节点的光效（旧版有雷电 TESR，本版没有）；节点创建仪式没有客户端渲染。
- `run-client/logs` 里 `gardenofglass` 的配方警告来自 Botania 自带词条引用了未安装的 Garden of Glass，与本模组无关。

## Botania 缺席安全（2026-09-28）

[总工程指导](ENGINEERING_GUIDE.zh-CN.md) 要求“Botania/Blood Magic 缺席时，基础 TC4R + FE 路线应完整可玩；公共能源、基础 BE、注册对象和入口字段不要出现 Botania 类型”。之前不满足：四台机器的方块或 BlockEntity 在类加载时链接 Botania API，而它们是无条件注册的。

### 做法

- 新增 `compat/botania/BotaniaPresence`（只用 `ModList`，无 Botania 类型）与 `compat/botania/BotaniaContent`：四个方块、四个 BlockEntity 类型、`mana_coil`/`manasteel_gear` 全部在 `install()` 里注册。
- `Technomancy` 构造函数里 `if (BotaniaPresence.isLoaded()) BotaniaContent.install();`。缺席时这些 `RegistryObject` 保持 null，方块/BE/物品一个都不注册，数据配方本就有 `forge:mod_loaded` 门控。
- `TechnomBlocks`/`TechnomBlockEntities`/`TechnomItems` 里删掉对应字段，公共注册类不再出现 Botania 类型。魔力流体与桶仍无条件注册：它们不引用 Botania，缺席时只是一个无来源的流体。
- 四台机器与 BO 处理器的方块/BE 改为经 `BotaniaContent.*_BE` 取自己的注册对象。
- GameTest：`ManaExchangerGameTests` 变成一个不含 Botania 类型的 `@GameTestHolder`，三个方法在 Botania 缺席时直接 `succeed()`，否则反射调用新类 `ManaExchangerBotaniaTests` 执行真正断言（该类不是 holder，只在有 Botania 时被加载，避免 Forge 注册时 `getDeclaredMethods()` 解析 `ManaPool` 描述符而崩）。

### 命令与结果

```powershell
.\gradlew.bat build --console=plain --no-daemon                              # JUnit 238 通过 0 失败
.\gradlew.bat runGameTestServer --console=plain --no-daemon                  # All 85 required tests passed（有 Botania）
.\gradlew.bat runGameTestServer -PwithGtceu=true --console=plain --no-daemon # All 85 required tests passed（有 Botania + GT）
.\gradlew.bat runGameTestServer -PwithBotania=false --console=plain --no-daemon  # All 85 required tests passed（无 Botania）
```

- 新增 Gradle 开关 `-PwithBotania=false`（默认 true）：只影响开发运行时的 `runtimeOnly` Botania/Patchouli，不改变编译（Botania 仍在 `compileOnly` 上）与发行 JAR。
- 第一次无 Botania 运行**确实崩了**：`NoClassDefFoundError: vazkii/botania/api/mana/ManaPool`，栈顶是 `ForgeGameTestHooks.addGameTestMethods → Class.getDeclaredMethods`——即使主模组已经安全，GameTest holder 的方法描述符里带 `ManaPool` 也会在注册批次时把类解析崩。拆成 holder + 反射后才通过。
- 无 Botania 时日志出现 `Missing data pack mod:botania` 与 Botania 实体类型的缺失转储，属预期（数据包引用了未安装的模组），测试仍 85/85。

### 本节尚未验证

- 只验证了**专用/GameTest 服务端**在无 Botania 下可启动并跑完测试；没有跑无 Botania 的**客户端**（`-PwithBotania=false` 的 `runClient`）。客户端理论上不需要 Botania（模型/词条只在有 Botania 时才注册），但未实测。
- 没有验证“存档先有 Botania 内容、再移除 Botania”的场景（那属于跨模组移除，预期会丢内容，不在缺席安全范围内）。

## S3 遗漏补齐、S4 三台机器与审计修复（2026-09-29）

### 本轮范围

补齐 S3 此前“只有创造栏一条路径”的部分，并一次性做完 S4：

- **S3**：新增 `technom:existence_gem`（上游 `ItemExistenceGem`；此前整个物品缺失，而它是全部 Existence 机器配方的核心材料）；按上游 `CraftingHandler.java:41-179` 补 21 条合成配方（水晶×5、催化器×5、仪式手册、宝石、燃烧器×2、使用器×3、塔×3）；**喷泉接入 Existence 网络**（此前未实现 `IExistenceProducer`，塔抽不到它，整条链是断的）；新增 `world.treasures` 配置并**默认关闭**（对应上游 `treasures && treasureSafeguard`、后者 false 的净效果；此前本工程无条件开启）。
- **S4**：注魔稳定灯 `technom:flux_lamp`、电动风箱 `technom:electric_bellows`、生态转换器 `technom:biome_morpher`（配方/研究/模型齐备）；融合焦点恢复“吸收节点→右键空地再造节点”手势。
- **1.12 增量**：魔力制造器能耗改为配置项 `balance.manaFabricatorCostQ`（默认仍按上游 1,000,000；1.12 fork 的 5000 作为可调档）；纯矿按矿种着色此前已具备。

### 审计发现并修复的缺陷

由独立静态审计（数据/资产核对 + 对抗式复核）发现以下缺陷，全部已修并加测试防线：

| # | 缺陷 | 证据 | 修复 |
|---|---|---|---|
| 1 | 三台新机器的 BlockItem 没有物品模型 → 客户端显示紫黑缺失模型 | `assets/technom/models/item/` 下没有这三个；同类方块（如 `processor_tc`）都有，且客户端探针 `00_models_and_sprites` 会断言每个技术方块物品都能烘出模型 | 补 3 个 item 模型（`parent: technom:block/<name>`）；探针转为 PASS |
| 2 | 三条新研究缺 `tc.research_text`，手册/笔记显示原始 key | 校验器 6 个 ERROR（en_us / zh_cn 各 3） | 补 6 个语言键 |
| 3 | **25 个方块要求正确工具却不在任何 mineable 标签 → 挖了不掉、放下去收不回**；其中 **21 个是本轮之前就存在的旧缺陷** | 新守卫 `everyToolRequiringBlockIsMineable` 首轮即报 `technom:flower_dynamo`；逐条核对 `requiresCorrectToolForDrops`：直注册 14 + 催化器 5 + 塔 3 + 使用器 3 + Botania 4 | pickaxe 标签补到 24 项，新增 axe 标签（风箱）；4 台 Botania 机器用 `"required": false` 条目，避免缺席时数据包加载失败 |
| 4 | 水晶配方染料与上游不符 | 上游 `CraftingHandler.java:42-66` 按 metadata 顺序取染料（nature=黑、火=白、水=红、光=绿、暗=蓝），与催化器按语义取色**不是同一套映射** | 按上游改；这是**上游自身的错位**，照抄并在此登记，不擅自“修正” |
| 5 | 2 个配方含非法 `comment` 字段 | 校验器 ERROR | 删除 |
| 6 | 稳定灯先扣 ordo 再写不稳定度 | 反射不可用时会白扣 5 ordo/次 | 改为“先确认不稳定度下降，再扣料”，并加 `canStabilise()` 闸门 |
| 7 | 稳定灯上报了永远不给的源质（缺陷 A-7 同类） | `essentiaType/Amount` 返回存量但 `takeEssentia` 恒 0，TC4R 缓冲管会在这一面白耗一次转向 | 与处理器/凝聚器一致返回 null/0；并**补上上游漏做的行为**：每 10 tick 从正上方邻居抽 1 点 ordo |
| 8 | 风箱对“烧不出东西”的输入照样收 3000 Q；且照搬了 TC4R 不存在的“奥术炉 2 格”伸距 | 上游原版炉分支要求 `FurnaceRecipes.getSmeltingResult != null` | 加熔炼配方探测；去掉 2 格伸距并记录 |
| 9 | 融合焦点属性写失败时可能把节点复制成两份 | `absorb` 先写属性后移方块、`place` 先建方块后清属性，任一步失败即“世界里一份、焦点里一份” | 两处都加回滚 |
| 10 | 催熟器门槛比上游晚 1 点 | 上游 `power > 605`；本工程 `power <= 606` 才返回，实际从 607 起 | 改为 605 |

### 本轮实际执行

在项目根目录、JDK 17 下执行：

| 命令 | 结果 |
|---|---|
| `gradlew.bat build` | BUILD SUCCESSFUL；**JUnit 238 通过 / 0 失败**（28 个测试类） |
| `gradlew.bat runGameTestServer` | **All 95 required tests passed** |
| `gradlew.bat runGameTestServer -PwithGtceu=true` | **All 95 required tests passed** |
| `gradlew.bat runGameTestServer -PwithBotania=false` | **All 95 required tests passed** |
| `python tools/validate_technom_data.py` | **OK: no errors**（16 warning / 1 skipped）；本轮开始时为 45 errors |
| Rosetta 客户端探针（带 GTCEu，quickJoin "New World"） | **3/3 通过**（`00_models_and_sprites`、`10_renderers_and_tab`、`20_patchouli_lexicon`） |

GameTest 由 91 增至 95：新增批次 `technom_s4_deep_tc` 三项（全工程“要求工具的方块必须在 mineable 标签”遍历断言、三台新机器用对工具掉落自身、三条新配方与研究键可达），并在 `technom_dynamo` 增加服务端 `/reload` 后燃料表不可变替换与内容保持测试。新守卫在首轮就抓到了第 3 项里漏掉的 `flower_dynamo`。新增的 `technom_s3_rituals` / `technom_s3_existence`（6 项）在三种运行时下均通过；`technom_s3_existence` 的日志行 `pylon took 5 from a fountain holding 9995` 是喷泉接网成功的直接证据。此次 `/reload` 测试日志确认重载后重新加载 44 个精华燃料条目、fallback 25，并发布了新表实例。

### 本轮未验证

- 客户端探针**首轮 2/3 失败**：桥在世界加载完成前就已就绪，探针 10/20 把全部 44 个方块报成“放置失败”（放置坐标 10,65,10），Patchouli 词条数为 0。世界真正加载完成后**重跑即 3/3 通过**（放置坐标变为玩家实际位置 214,74,-737）。结论：这是启动时序而非内容缺陷；但“桥就绪 ≠ 世界就绪”，后续探针流程应先确认玩家已进入世界再跑。
- 没有任何**人工实机点击**：仪式阵列的真实搭建与触发、Existence 网络在真实拓扑下的产消、稳定灯对真实注魔祭坛、风箱对真实炼金炉、生态转换器的客户端群系刷新、融合焦点吸收/再造全流程、宝物村民开关，均只有代码与自动测试证据。
- 未跑：专用服务器长期运行、多人联机、跨维度与死亡重生、无 Botania 客户端（`-PwithBotania=false runClient`）、JEI/Jade 集成（至今没有代码）。服务端 `/reload` 已由新增 GameTest 覆盖，但真实专用服务器上的重载流程仍未人工验证。
- 校验器 16 条 warning 未处理：13 条是“category 未在本文件声明”（分类确实声明在 `technomancy.json`，校验器逐文件检查看不到），2 条是未知目录 schema，属既有噪声。
- 校验器读取的 `run-gametest/technom-data-inventory.json` 由最后一次 GameTest 运行写入；因此**必须在“有 Botania”的那次运行之后立刻跑校验器**，否则会把 Botania 物品误报为缺失（本轮第一次跑就踩到了这个顺序问题）。
## 现代化联动、客户端渲染器移植与资产审计（2026-09-30）

### 本轮范围

- **现代化联动**：补齐 Jade / JEI / KubeJS 三处（此前一行代码都没有）。原则是只补"别人推导不出来的信息"，配方这类数据包能表达的一律不写代码。详见 [COMPAT 第 7 节](COMPAT.zh-CN.md)。
- **客户端渲染器移植**：上游用代码画的三台机器搬到新的 `TechneModel` 构建器上——注魔稳定灯（`TileFluxLampRenderer` + `ModelFluxLamp`，13 个盒体）、节点制造器、存在之泉（只画液面，机身仍是 JSON 模型）。
- **资产审计**：`tools/check_unwrap.py` 全量核对 Techne 版式模型的 UV 展开，找出并修掉生成器留下的系统性错位。

### JEI 版本结论被推翻（取代 2026-09-28 一节）

2026-09-28 那一节把 JEI 从 `15.56.0.205` 降到 `15.20.0.115`，理由是"本工程没有任何 JEI 代码，JEI 纯粹是开发客户端用来显示配方的运行时依赖，因此可以自由选版本"。本轮本工程有了自己的 JEI 插件，这个前提不再成立，而两个消费者对 JEI 的要求完全不重叠：

| 消费者 | 需要 | 证据 |
|---|---|---|
| TC4R `0.1.0-20721` | `>= 15.56.0.202`（`ISubtypeInterpreter`、`ITextWidget.setPosition`） | `tools/jei_link_check.py` 对 15.20.0.115 报 9 处失效调用，对 15.56.0.205 / 15.62.0.216 报 0 |
| GTCEu 7.5.3 | `<= 15.35.0.175` | `jei.FluidHelperMixin` 注入 `FluidHelper.getTooltip(ITooltipBuilder, ...)`，而 JEI 在 15.40.0.176 把这个签名改回了 `List<Component>` |

`require = 0` 救不了 GTCEu 那边：它只覆盖"选择器没匹配到目标"，而按名选择器仍会找到存活的 `getTooltip` 重载，随后 `CallbackInjector` 因处理函数描述符不匹配直接抛错——这是致命的。

**决定**：JEI 固定回 `15.56.0.205`（TC4R 那一侧，因为 TC4R 是必需依赖、GTCEu 是可选），并由本工程自己的 `mixin/GtceuJeiFluidHelperMixin.java` 把 GTCEu 要找的那个重载补成一个空壳，注册在 `technom.mixins.json` 的 priority 900（先于 GTCEu 的默认 1000）。没有任何代码调用它——JEI 15.56 里根本没有 `ITooltipBuilder` 重载——唯一效果是让 GTCEu 的选择器找到目标、客户端能启动。

两个后果值得记住：

- 崩溃只在客户端。`mezz.jei.forge.platform.FluidHelper` 在专用服务器上从不加载，所以 `runServer` / `runGameTestServer -PwithGtceu=true` 从来不会踩到。
- 整合包里若已有等价 shim（Sunlit Valley 带 `pollution-*.jar`，其 `FluidHelperCompatMixin` 在同一优先级做同一件事），只是先到先得；落败方会被报成 "Method overwrite conflict ... Skipping method" 警告，结果完全相同，因为两边方法体都是空的。

### 三处联动

| 联动 | 依赖方式 | 做了什么 | 探针证据 |
|---|---|---|---|
| Jade | `compileOnly` 常驻，`runtimeOnly` 由 `-PwithJade` 控制（默认开） | 15 个方块实体登记到 `TechnomJadeProvider`，数值在服务端 `appendServerData` 采集、客户端 `appendTooltip` 只读回传的 NBT | uid `technom:machine_data`；13/13 lang key；15 个方块实体 |
| JEI | 沿用 `compileOnly` + `runtimeOnly`，不加开关 | 唯一类别 `technom:essentia_fuel`，逐要素一页 + 一条兜底页，无槽位手绘 | 类别 `technom:essentia_fuel`；44 要素 + 兜底 25 → 45 页；44/44 页与燃料表逐行一致 |
| KubeJS | **仅 `compileOnly`**，默认不进运行时；`-PwithKubejs=true` 才装（连同 Rhino 与 Architectury） | jar 根目录 `kubejs.plugins.txt` + 只覆写 `registerBindings` 的插件，绑定 `Technom` 只读全局 | 发现文件内容正确；6 个插件加载、本工程在列；`Technom.maxFuelValue(ignis)=800`、`maxEnergyPerUnit=16000` |

`TechnomJadePlugin` 的静态初始化**刻意不含** Botania 三台机器：登记它们会让 common 侧的插件类引用 Botania 方块实体类，在"有 Jade 无 Botania"的整合包里 `NoClassDefFoundError`。

### 修复的客户端缺陷（用户实机报告）

| 现象 | 根因 | 修复 |
|---|---|---|
| 稳定灯与节点制造器**手持/物品栏透明** | 两个 item 模型的 parent 不是 `builtin/entity`。`ItemRenderer` 是按**烘焙后**的模型分支的（`!model.isCustomRenderer()` 走普通路径，否则才调 `getCustomRenderer().renderByItem`），而只有 `builtin/entity` 会烘成 `BuiltInModel`。两个物品因此烘出空模型，渲染器压根没被调用。`IClientItemExtensions` 一直是注册好的——旧断言只查它非空，所以看不见这个缺陷 | 两个 item 模型改成 `builtin/entity` + 显式 `display`（照 vanilla `item/chest.json`） |
| 稳定灯**放置在世界里是黑的** | `FluxLampRenderer.draw` 无条件套用了上游的 `glColor3f(amount, 0, amount)`。上游那句在 `if (tank.getFluidAmount() > 0)` **里面**，空罐时 `amount == 0`，顶点色变成 `(0,0,0,1)`。core 走 cutout，而它 2/3 的像素是 alpha 0 会被丢掉，剩下那 1/3 就是一块纯黑 | 只在 `amount > 0` 时染色，否则纯白 |

`probes/client/14_flux_lamp_and_coil.java` 把两条都钉死了：它断言两个物品的**烘焙模型**是 `BuiltInModel` 且 `customRenderer=true`，并用顶点捕获读回核心的实际顶点色——空罐必须是 `0xffffffff`、满罐必须是 `0xff00ffff`。

同一探针还数值钉死了稳定灯四个水平喷嘴的世界朝向。上游的 `switch` 看起来是错的（模型的 `FrontPost` 在 `z = -7`，读起来像北面），但整个模型是在 `scale(-1, -1, 1)` 之后绘制的，这一步会翻转旋转的输出，变换链实际把 `FrontPost` 送到西面、`BackPost` 送到东面——与上游的 `switch` 完全一致。**上游是对的，不要"修正"它**，探针会挡住。

### Techne UV 展开审计

上游的 `Model*.java` 是 Techne 导出，本工程把它们转成了 JSON 方块模型。转换脚本在写 `up`/`down` 与 `east`/`west` 时搞错了顺序，是一个**系统性**缺陷而非个例。

`tools/check_unwrap.py` 用 vanilla 的展开规则核对每个元素：从六个面反推 `u`、`v`（取最小坐标，对交换稳健），遍历所有可能的贴图尺寸，取能让六面全部自洽的那一个，然后点名生成器实际做的那两种交换。`--fix` 只修"唯一故障就是交换"的元素。

工具自己也有两个 bug，本轮一并修掉：

- `repair()` 原来无条件同时交换 up/down **和** east/west。`coil.json` 本来**只有** up/down 是错的，结果修好了 up/down 却把 east/west 弄坏了——把一种错换成了另一种。现在只交换 `analyse` 实际检测到的那一对。
- 对"逐面手写 uv"的模型（`essentia_dynamo`、`flower_dynamo`、`mana_fabricator`、各 `*_inv`、`fountain` 占位模型）全是误报——它们本来就不遵循 Techne 版式。现在只有半数以上可判定元素已符合规则的文件才判定。

结果：扫描 196 个元素，修掉 `coil.json` 的 11 个（up/down 交换）与 `node_dynamo.json` 的 `panel3`/`panel4`（版式按 1 宽 4 深写、盒子却是 4 宽 1 深，六个面全错，按 64×32 的贴图重算）。

### 本轮实际执行

| 命令 | 结果 |
|---|---|
| `gradlew.bat build` | BUILD SUCCESSFUL；**JUnit 241 通过 / 0 失败**（29 个测试类） |
| Rosetta 客户端探针（`-PwithGtceu=true -PwithKubejs=true`，`probes/client`） | **10/10 通过** |

首轮 8/10，两个失败都不是回归：

- `11_renderers_verify` 报"客户端只看到 0/44 方块"——刚启动的世界同步竞态（"桥就绪 ≠ 世界就绪"）。`--attach` 重跑即 44/44。
- `14_flux_lamp_and_coil` 是探针自身的颜色常量写反：顶点色打包是 `(r << 24) | (g << 16) | (b << 8) | a`，洋红是 `0xff00ffff`，探针写成了 `0xffff00ff`（那是黄色）。

### 本轮未验证

- GameTest 当时**还没有**重跑，只有 `build`（JUnit）与客户端探针；当天的 95/95 是 2026-09-29 的记录。**这一点在第二轮审计后已经补齐**，见下一节：97/97 全绿（含两条新增的全树守卫），但仍然只跑了默认运行时，`-PwithGtceu=true` / `-PwithBotania=false` / `-PwithJade=false` 三种组合仍未在本轮跑过。
- **没有任何人工实机点击**：稳定灯空/满罐的颜色与两个物品的图标只由顶点捕获与烘焙模型断言证明；"看上去对不对"仍需人眼。Jade 提示行与 JEI 燃料页的排版同样只有数值证据。
- `check_unwrap.py` 修好的 `coil.json`（east/west）与 `node_dynamo.json`（panel3/4）只过了规则校验，**没有在游戏里目视确认**。
- KubeJS 那一半只有发现文件与类资源的证据；`-PwithKubejs=true` 的完整运行时只验证了插件被加载（探针 40），没有写任何真实脚本去调用 `Technom` 全局。

## 上游对照审计：掉落表、能量能力与三处数值（2026-09-30 第二轮）

本轮的目标不是加功能，而是**拿上游 1.7.10（`37bf9a56`）逐项对照，把"看起来搬过来了、其实没搬全"的地方找出来**。做法是四个只读子代理分头扫（方块实体 / 方块与注册 / 客户端与 GUI / 仪式与玩家与网络），每条结论都回到源码复核后才动手；子代理报的假阳性（纯矿物品的共享 lang key、`essentia_container` 是 `quantum_jar` 的改名等）一律驳回。

### 最严重的一条：24 个方块破坏后**什么都不掉**

这一条四个子代理都没报，是交叉核对 Forge 补丁时发现的，也是本轮唯一一个"玩家会立刻察觉"的缺陷。

1.20.1 里 Forge 给 `BlockBehaviour` 打了补丁：**只要方块没调用过 `.noLootTable()`**，掉落表 id 就按注册名算出来：

```java
this.lootTableSupplier = () -> {
   ResourceLocation registryName = ForgeRegistries.BLOCKS.getKey((Block) this);
   return new ResourceLocation(registryName.getNamespace(), "blocks/" + registryName.getPath());
};
```

而 `BlockBehaviour.getLootTable()` 直接返回这个 id。关键在于**文件缺失时解析成 `LootTable.EMPTY`，不会回退成掉自己**——所以没有 json 就等于破坏后空气。

全树只有 4 个方块显式选了退出：`creative_jar`、`node_fabricator_shell`、`fake_air_light`（`TechnomBlocks.java` 161/223/270）与 `ManaFluidBlock`（流体方块，本就不该有）。其余 24 个——**五色水晶、五色触媒、全部 Existence 机器（燃烧器 ×2、催熟器、收割者、封存器、喷泉、三种塔座）、`flower_dynamo`、`mana_exchanger`、`mana_fabricator`、`processor_bo`、`basalt`**——一个 json 都没有。上游是自掉落的（`BlockBase` 不覆写，`BlockCosmeticOpaque.quantityDropped` 返回 1），所以这是纯粹的移植遗漏。

补了 24 个掉落表（原有 16 个 + 新增 24 个 = 全树 40 个，守卫日志确认 `40 loot tables present, 4 blocks opted out`）。两个生成脚本的坑也记一下：模板用 `%s` 时误当成 `dict` 格式化直接抛 `TypeError`，崩掉的进程留下了一个 0 字节的 `basalt.json`；另外正则一开始把 `TechnomItems.ITEMS.register` 也当成方块，多生成了 `mana_coil` / `manasteel_gear`（那是物品），已删除。

### 五处数值 / 方向 / 能力缺陷

| 位置 | 现象 | 根因 | 修复 |
|---|---|---|---|
| `ExistenceUserBlockEntity.getMaxRate()` | 整个 Existence 网络注能慢 50～5000 倍 | 硬编码 `4`。上游 `TileExistenceRedstoneBase.getMaxRate()` 是 `maxPower / 50`，作物催熟器/收割者 = 200，封存器 = 20,000。`ExistencePylonBlockEntity.output()` 拿它当每 tick 上限 | 改成 `getPowerCap() / 50` |
| `ExistenceBurnerBlockEntity`（动态） | 免费烧生物，且价值表算错 | 上游先查一次 `energy >= 10000` 再在循环里扣，本工程干脆没接能量。另外 `Mob` 判怪物是错的——`Animal extends Mob`，牛会被当成怪物 | 接 `MachineEnergy`（10,000 FE/杀，上限 100,000），改成**每次击杀前**`tryConsume`；怪物判定改用 `net.minecraft.world.entity.monster.Enemy` |
| `ManaExchangerBlockEntity` | 管道只能从正在耗液的机器里抽液 | `fill` / `drain` 的 `mode()` 判反了：上游 `fill` 只在"喝自己的罐"（`mode == true`）时收液，`drain` 只在产液（`mode == false`）时放液 | 两个方向的 `mode()` 取反对调 |
| `BiomeMorpherBlockEntity` / `ElectricBellowsBlockEntity` | 两台机器**永远充不进电**，从不工作 | 两个方块实体都建了 `MachineEnergy`、也存读档，但**没覆写 `getCapability`**。Forge 的 `CapabilityProvider` 只回答它从 provider 字段收集到的能力，`MachineEnergy` 是普通字段，所以邻居拿到的一律是 `LazyOptional.empty()` | 补 `getCapability` / `invalidateCaps` 委托，与其余机器同型 |
| `ExistenceConversion.getValue` | 蝙蝠只值 1 而不是 5 | 上游价值表里 `creature`（`EntityAnimal`）与 `ambient`（`EntityAmbientCreature`）都是 5，只搬了前半 | 判定改为 `Animal || AmbientCreature` |

`existence_gem` 顺带从默认 64 改成 `stacksTo(1)`（上游 `ItemBase` 构造里 `setMaxStackSize(1)`）。

两处**差点改错、复核后保留原样**的地方，记下来免得下轮再犯：

- 燃烧器的 `getMaxRate()` **本来就是 4，是对的**。我一度把它也改成 `getPowerCap() / 50`，但上游两个燃烧器（`TileExistenceBurner` / `TileExistenceDynamicBurner`）各自硬编码 `return 4;`——它们继承的是 `TileTechnomancyRedstone` / `TileMachineRedstone`，**不是** `TileExistenceRedstoneBase`。`maxPower / 50` 只属于三台用户机器（催熟器/收割者 `maxPower = 10000` → 200，封存器 `1000000` → 20000，与端口里的 `SMALL_CAP` / `SEAL_CAP` 一一对上）。
- 动态燃烧器的能量**只从底面进**（上游 `TileExistenceDynamicBurner.canConnectEnergy`：`from == ForgeDirection.DOWN`）。端口的 `EnergyPorts` 用绝对世界朝向，所以映射成 `mask(Direction.DOWN)`；这条映射约定已用另外两台机器交叉验证过（`TileManaExchanger` 的 `from != UP` ↔ `allExcept(Direction.UP)`，`TileManaFabricator` 的 `from.ordinal() == facing` ↔ `mask(facing())`）。

静态燃烧器上游完全没有能量缓冲，所以它这一侧用 `EnergyPorts.NONE`：能力仍然应答，但每个面的 `canReceive` / `canExtract` 都是 false、传输恒为 0，玩家无法把 FE 灌进一台永远不会花掉它的机器。`TechnomJadeProvider` 也同步跳过 `NONE` 的 `EnergyHolder`，否则静态燃烧器会凭空多出一条 `0 / 100,000 FE` 的能量条。

### 两条全树回归守卫

这两类缺陷的共同点是**单点测试看不见**：掉落表缺失要真去查 `LootData` 才知道，能量能力缺失要有邻居去问才知道。所以把它们写成对整棵树的断言，加进 `S4DeepTcGameTests`：

- `everyBlockEitherHasALootTableOrSaysItDoesNot` —— 遍历 `BuiltInRegistries.BLOCK` 里所有 `technom:` 方块，凡是没 `noLootTable()` 的，都要求 `getServer().getLootData().getLootTable(block.getLootTable()) != LootTable.EMPTY`。日志：`40 loot tables present, 4 blocks opted out`。
- `everyEnergyMachineHandsItsBufferToNeighbours` —— 遍历所有 `technom:` 方块，凡方块实体 `instanceof EnergyHolder` 的，要求它至少从**某一个**朝向（含 `null`）回答出 `ForgeCapabilities.ENERGY`。日志：`10 energy machines expose their buffer`。

第二条守卫还顺带做了一次全仓扫描确认没有反例：12 台能量机器里恰好这 2 台漏了覆写，其余 10 台都有；另外没有别的方块实体持有 `LazyOptional` 却忘了覆写 `getCapability`。

### 本轮实际执行

| 命令 | 结果 |
|---|---|
| `gradlew.bat compileJava` | 退出码 0，0 处 `error:` |
| `gradlew.bat build` | BUILD SUCCESSFUL；**JUnit 241 通过 / 0 失败**（29 个测试类） |
| `gradlew.bat runGameTestServer`（默认运行时） | **97/97 required tests passed**，两条新守卫日志如上 |
| Rosetta 客户端探针（`-PwithGtceu=true -PwithKubejs=true`） | **10/10 通过**（上一轮） |

### 本轮未验证 / 仍未处理

- 新增的 25 个掉落表只过了规则校验（json 可解析、每条 entry 的 id 都在源码里注册过），**没有在游戏里真去挖一遍**；守卫断言的是"表存在且非 EMPTY"，不是"掉的东西对"。
- 两处守卫只覆盖了默认运行时。`-PwithGtceu=true` / `-PwithBotania=false` / `-PwithJade=false` 三种组合本轮都没跑。
- 子代理还报了一批**尚未处理**的差异，按影响排序记录在案（掉落表与能力这两类已闭环，下面是其余）：宝藏的命中/摧毁副作用完全没搬（`ItemTreasure.onUserHit` / `onTreasureDestroyed`，全仓没有 `LivingHurtEvent` 监听）；击杀与仪式激活不再给 Existence 能量（上游 `EventRegister.java:179-188`、`TileCatalyst.java:56-58`），亲和/被动效果因此几乎不可达；`seal` 标记永不清除（上游 80 tick 冷却后清），被封印的村民无法再封印或失去宝藏；宝藏村民每次区块加载都重掷（上游无条件写 `treasureAttempt=true` 防重掷），实际发生率远高于标称的 1/50；没有 HUD 开关且 HUD 固定画在 (6,6)（上游 `showHUD` 默认 false）；配置面约 7 项 vs 上游约 35 方块 / 15 物品 / HUD / recipes.bonus / renderers.fancy / machines.blacklist / Rate 的 8 项能耗；`EldritchConsumerBlockEntity` 把整个 tick 卡在 40 tick 的 `cooldown` 上（上游只拿它驱动动画，吞吐被砍到约 1/40）；`BoProcessorBlockEntity` 用了 TC 处理器那套法力定价（约为上游 `multiplier*150 + 1500*reprocess` 的 1/4～1/5）；收割者不扣补种种子（每次多掉一份）；燃烧器与三台 Existence 机器丢了 `RedstoneControl` 门控；`EssentiaFusorBlockEntity.addEssentia` 多要求 `fullyMarked()`；`CatalystBlockEntity` 命中第一个仪式就 `break`（上游没有 `break`）；`ConsumerRange` 多扫一层；`basalt` 既无配方也无世界生成来源；`creative_jar` 不可破坏且无掉落表（上游硬度 1、自掉落），而 `S2StorageGameTests.java:114` 把这个偏差断言死了。
- 方块硬度/抗性偏差：`crystal_*` 0.3 vs 上游 2.0；`quantum_jar` 0.5 vs 1.0；`mana_exchanger` 3.0/6.0 vs 2.0/10.0。`processor_bo` 没有自己的掉落表（只掉内容物）；`adv_decon_table` / `essentia_reservoir` 不在任何可挖掘标签里。
- 死资源（低优先级，多为改名遗留）：26 个无人引用的方块模型、4 个死物品模型（`coilcoupler` / `existencegem` / `itemboost` / `ritualtome`，贴图仍被正确命名的模型使用）、约 35 张无人引用的贴图，以及重名资产（`coil_coupler` vs `coilcoupler`、`neutronized_metal` vs `neutronizedmetal`）。
- 仍需搬到 `TechneModel` 的渲染器：`node_dynamo` 浮动线、`eldritch_consumer`（14 盒）、`electric_bellows`（5 盒，128×64 图集）、`biome_morpher`（22 盒）、`mana_fabricator`（14 盒）、`adv_decon_table`（9 盒）、`crystal`（`getStage()`）、`catalyst`（`textLoc`）、`existence_burner` 立方体、`essentia_dynamo` 的 `renderFacing`、`flower_dynamo`。
- `docs/FEATURE_MATRIX.zh-CN.md` 仍把"事件副作用"和"配置关闭 HUD"列为验收项，而代码并不满足；这两项在补齐之前应标注为未达成。
