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

下一阶段开始后，每批功能都应更新此记录，写明实际运行命令、观察结果和未覆盖的环境；不要用 `test NO-SOURCE`、启动成功或 GameTest 冒烟结果代替功能验收。
