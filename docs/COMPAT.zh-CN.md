# 兼容与联动分析（`compat`）

核对日期：2026-09-28。本文是“Technomancy 与其它模组联动”的唯一记录，覆盖原版 1.7、Technomancy-2 1.12 的 `lib/compat`，以及目标整合包 Society Sunlit Valley（1.20.1）的实际 mod 环境。

## 1. 原版 1.7 与 1.12 的 `lib/compat` 清单

| 模块 | 目标 mod | 内容 | 1.7 | 1.12 |
|---|---|---|---|---|
| `Thaumcraft` | Thaumcraft | 本体入口：TC 机器/物品/研究/配方注册（含 `thaumcraft/TechnoResearch`、`ScepterRecipe`） | ✔ | ✔（少两个类） |
| `ThermalExpansion` | ThermalExpansion | 只提供材料：powerCoilElectrum/Silver/Gold、capacitorResonant、三个 frame | ✔ | ✔ |
| `Mekanism` | Mekanism | TE 的互斥材料替代：energyCube、steelCasing、energyTablet、enrichedAlloy、lithiumDust | ✔ | ✔ |
| `Botania` | Botania | 整套可选机器：Flower/Hippie Dynamo、Mana Fabricator、BO Processor、Mana Exchanger、魔力流体+桶、材料、Lexicon 页 | ✔ | ✔ |
| `BloodMagic` | AWWayofTime | 整套可选机器：Blood Dynamo、Blood Fabricator、BM Processor、life essence、祭坛配方 | ✔ | ✔ |
| `ThaumicEnergistics` | thaumicenergistics | 无新内容：把 TC 机器登记为 ME 源质容器 | ✔ | ✔ |
| `waila/*` | Waila | 纯 HUD | ✔ | 子集 |
| `IModModule`/`ModuleBase` | — | 模块契约与公共注册帮助 | ✔ | ✔ |

`CompatibilityHandler` 用 `Loader.isModLoaded` 决定加载；`Conf.mkfirst` 决定 TE 与 Mekanism 二选一。

**结论**：真正带机器的可选联动只有 Botania 与 Blood Magic；TE/Mek/Thaumic Energistics/Waila 都只是材料、HUD 或外部登记；1.12 侧没有新增联动（其改动是修 bug）。

## 2. 目标整合包的现有环境

Society Sunlit Valley（1.20.1 Forge）已加载与本工程相关的：

| 目标 | 包内版本 | 处理 |
|---|---|---|
| Thaumcraft（TC4R） | `thaumcraft-forge-4.2.3.5-1.20.1-port.0.1.0-20711` | 本体；**版本 20711，与本工程锁定的 20721 不同** |
| Thaumic Energistics | `thaumic-energistics-1.20.1-forge-1.1.3.0-port.0.1.0-20711` | 见第 4 节 |
| Mekanism | `10.4.16.80`（+Additions/Generators/Tools） | TE 不在、Mek 在；材料分支按 Mek 走 |
| Botania | `1.20.1-454` | 保留为 S3 选装 |
| AE2 系 | `appliedenergistics2 15.4.10` + ExtendedAE/mae2/ae2wtlib | 与 Thaumic Energistics 配合 |
| GregTech | `gtceu 7.5.3` + GTMFO/gtmutils/gtmthings/gtnn/gtse/gtca/gregmek/applied_greg/gregfluxology | FE/EU 能源与内容 |
| Jade | `11.13.2` + JadeAddons | 现代 HUD 替代 Waila；本工程锁定 `11.13.3+forge`（同一 1.20.1-Forge 线的更高构建），第 7 节的插件只用两侧都存在的稳定 API |
| Blood Magic 系 | `forbidden-magic`/`tainted-magic` | **用户已排除，不做** |

## 3. TC4R 20711 vs 20721 的 API 差异

对两边 API 包做类级与方法级对比：

- API 类：20711 有 319 个，20721 有 336 个。
- **20721 相对 20711 新增 17 个类**：`aspect/AspectQueryApi`（+内部 `Consumption`）、`player/PlayerKnowledgeApi`、`player/PlayerKnowledgeView`、`research/ResearchTableApi`、`research/ResearchRecipeDisplay`/`ResearchRecipeDisplayApi`、`research/ResearchCraftingRecipe`/`ResearchCraftingExample`、`golem/GolemActivityView`、`ObjectAspectPhase`、`client/ResearchRecipeRenderContext`/`ResearchRecipeRendererApi`、`client/ThaumcraftClientRenderApiCommon$EffectUv`。
- **20711 独有、20721 删除**：`entity/EntityAspectApi`（一个类）。
- 方法级，对本工程用到的类逐签名对比：`AspectApi`、`EssentiaApi`、`WandApi` **完全一致**；`ThaumcraftApiHelper` 在 20721 多出 `cullTags`、`getObjectAspects`、`getBonusObjectTags`、`generateTags` 四个方法。

**对本工程的影响**：

- 绝大多数接口两边相同，本工程主体可在 20711 上工作。
- `AspectQueryApi` 是 20721 新增；`adv_decon_table`（`AspectQueryApi.item`）与 `eldritch_consumer`（`AspectQueryApi.item`）依赖它，**在 20711 上无法链接**。要在包内（20711）运行，需把这两处改成 20711 的等价物（`EntityAspectApi` 或 `ThaumcraftApiHelper.getObjectAspects`），或把整合包的 TC4R 升到 20721。
- 需注意本工程锁定的 TC4R 是 Pollution 仓库的 `0.1.0-20721`，与包内 `20711` 是不同构建；版本对齐前不宣称“即插即用”。

## 4. Thaumic Energistics（1.20.1 移植版）

- 包名 `dev.thaumicenergistics.*`，**没有给外部模组的注册 API**（`jar` 内只有一条 AE2 `TooltipProvider` service 项）。
- 它通过 `integration/EssentiaWorldIntegration.register()` 自注册，容器发现走 `integration/EssentiaTarget`，其字段就是 TC4R 的 `dev.tc4port.thaumcraft.api.essentia.EssentiaTransport`，还有 `EssentiaTarget.canImportFrom()/canExportTo()`。
- **因此任何实现 TC4R `EssentiaTransport`/`AspectContainerView` 的方块都会被 Thaumic Energistics 自动识别**。原版 1.7 需要 `ThEApi...transportPermissions().addAspectContainerTileTo*Permissions(TileX.class, N)` 的显式登记，在 1.20.1 移植版里不再需要。

**决定**：不新增 `compat/thaumicenergistics` 代码模块；兼容性由“正确实现 TC4R 源质接口”保证，这正是 S1/S2 已完成的接口契约（有 GameTest 覆盖每个面的 `canInputFrom/canOutputTo/takeEssentia/addEssentia/availableEssentia`）。需要补的是**文档**与**版本对齐**，而不是注册代码。

**兜底方案（若自动发现不够）**：TE 移植版的发现逻辑是它自己的 `dev.thaumicenergistics.integration.*`（`EssentiaWorldIntegration`/`EssentiaTarget`）。若实际运行发现某台机器没被识别（例如它只按 `EssentiaTransport` 的某些面或缓存了注册表），**用 Mixin 在 TE 侧补齐**，而不是往 Technomancy 加注册 API：目标类是 `EssentiaWorldIntegration.register()` 之后的容器收集点或 `EssentiaTarget` 的构造/`canImportFrom`/`canExportTo`，注入点以运行期实际反编译为准。原则与工程其它 Mixin 一致：优先 TC4R/原版公开接口，Mixin 只作桥接，并单独记录注入目标与退避条件。当前**不实现**，仅登记为备选。

## 5. 处理决定汇总

| 原 compat | 决定 |
|---|---|
| Thaumcraft | TC4R，必需（版本待对齐 20711/20721） |
| ThermalExpansion / Mekanism | TE 排除；Mek 在包内，材料分支按 Mek；能源用 FE/EU |
| Botania | S3 选装模块（5 项 + Lexicon） |
| BloodMagic | **排除，不做** |
| Thaumic Energistics | **自动兼容**（实现 TC4R 接口即可），不写注册代码；补文档与验证 |
| Waila | 用 Jade，**已实现**（见第 7 节） |

## 6. S4 三台机器触及的 TC4R 边界（实施记录）

S4 的注魔稳定灯、电动风箱、生态转换器各自撞到一个 TC4R 20721 的边界，处理方式如下，**不在 `api.*` 范围内的用法都集中隔离，不散落在业务代码里**。

| 机器 | 需要的动作 | 20721 提供什么 | 本工程怎么做 |
|---|---|---|---|
| 注魔稳定灯 | 读取并**降低**运行中祭坛的不稳定度 | `InfusionMatrixBlockEntity` 有公开的 `instability()` / `crafting()` / `active()`，但 `instability` 是私有字段，全仓没有 setter，也没有 `api/infusion/**` | 读走公开 API；写走 `compat/thaumcraft/ThaumcraftInternals`，反射 TC4R 自有类的私有字段。TC4R 是模组、字段名不参与 MC 重混淆，开发与打包环境一致；字段查不到时该机降级为“只存 ordo、产淤泥、不降不稳定度”并在日志里报一次错 |
| 电动风箱 | 给奥术炼金炉续燃、开加速 | `AlchemyFurnaceBlockEntity` 的 `burnTime`/`speedBoost` 私有；`api/alchemy/ArcaneBellowsApi` 只能查询吹向/相邻计数，没有注册自定义风箱的口子；原版熔炉则给了 `nativeimpl/mixin/FurnaceAccessor`（公开 accessor） | 炼金炉走 `ThaumcraftInternals` 反射（同上，失败即停用炼金炉分支）；原版熔炉只用公开的 `FurnaceAccessor` 推进 cooking（不写燃料，改为一充能买 80 tick 推进）；**不引入 Mixin 工具链** |
| 生态转换器 | 把一列群系写成魔法森林/阴森/污染之地 | `api/taint/TaintBiomeApi` 只支持污染；但 `block/TaintSpreadLogic.setSpecialBiomeColumn(ServerLevel, BlockPos, ResourceKey<Biome>)` 是 public static，`worldgen/TCBiomes` 给出四个 biome key | 直接用公开静态方法，**无需 Mixin**。注意它不在 `api.*` 包内，属于版本锁定面：升级 TC4R 时要复核签名 |

结论：S4 只引入**一处集中式反射桥**（`compat/thaumcraft/ThaumcraftInternals`），没有新增 Mixin；`TaintSpreadLogic` 与 `FurnaceAccessor` 都是 TC4R 自己的公开入口，登记为版本升级复核点。

## 7. 现代化联动：Jade / JEI / KubeJS（实施记录）

原版 1.7 的 `lib/compat` 里只有 `waila/*` 是 HUD 类联动，JEI 与 KubeJS 当时不存在。三者的取舍原则一致：**只做别人做不了的那部分**，能由数据包或已有 API 表达的一律不写代码。

| 联动 | 依赖方式 | 做了什么 | 为什么只做这些 |
|---|---|---|---|
| Jade | `compileOnly` 常驻 + 开发运行时可关（`-PwithJade=false`）；插件类在 common 侧，靠 `@WailaPlugin` 注解扫描 | `TechnomJadePlugin` 把 15 个方块实体登记到 `TechnomJadeProvider`；数值在服务端 `appendServerData` 采集（能量/源质/进度/灵气/燃料/仪式/存在），客户端 `appendTooltip` 只读回传的 NBT | Jade 两侧都会加载插件，所以插件类不能碰任何客户端类；用 Jade 自己的 server→client 通道，客户端看到的是服务端真值，而不是自己那份可能过期的副本 |
| JEI | 沿用原有 `compileOnly` + `runtimeOnly`（不加开关） | 新增类别 `technom:essentia_fuel`：把数据驱动的源质燃料表逐要素画成一页（要素图标、燃料值、每单位 Q、地形条件、随机加成），并把源质发电机登记为催化剂 | 本模组所有合成/熔炼/无序配方都是普通数据包配方，JEI 本来就免费展示，重复注册只会多一处需要同步的地方；JEI 唯一推导不出来的是**燃料值表**——它在数据包里，且没有任何物品承载它 |
| KubeJS | **仅 `compileOnly`**，永不进开发运行时 | jar 根目录的 `kubejs.plugins.txt` + `TechnomKubeJSPlugin` 只覆写 `registerBindings`，绑定 `Technom` 全局（`TechnomJS`） | 配方系统无可加（同上），本模组也没有自己的可监听事件。脚本真正拿不到的是燃料表数值、Q/EU 汇率与燃料倍率，`TechnomJS` 只暴露这些 |

要点与坑：

- **Jade 的注册面**：`registerBlockComponent(..., Block.class)` 一次覆盖全部方块，provider 对非本模组的方块实体立刻返回，代价是每次瞄准一个方块一次 `instanceof` 加一次空 NBT 判断。Jade 11 没有 `IComponentProvider`，只有 `IBlockComponentProvider` / `IEntityComponentProvider`。
- **Jade 的 15 个方块实体不含 Botania 三台机器**：它们有隐藏的魔力数值，但登记它们意味着 `TechnomJadePlugin` 的静态初始化要引用 Botania 方块实体类，在装了 Jade 而没装 Botania 的整合包里会 `NoClassDefFoundError`。要覆盖必须另开一个只在 `BotaniaPresence.isLoaded()` 时才加载的类，本轮不做。
- **JEI 的页面没有槽位**，这是刻意的：要素不是物品（TC4R 里它是流体、罐内内容或物品的要素标签），JEI 塞不进槽也搜不到，所以整页由 `draw()` 手绘。`getBackground()` 保持默认 `null`——JEI 15 自带外框并且对返回值判空（`RecipeLayout.drawRecipe` 的 `ifnull`），尺寸改由 `getWidth()/getHeight()` 回答，返回 drawable 反而是已废弃路径且会让外框有两个尺寸来源。
- **JEI 时序**：`registerRecipes` 每次配方重载都会被调用，而数据包也在同一次重载里读取，所以 `/reload` 改燃料表后 JEI 里立刻可见；表为空（首次加载前，或数据包删光了所有行）时**不注册任何页面**，否则兜底页会宣称所有要素都值 0，比没有这个类别更糟。
- **KubeJS 的发现方式不是注解**：是 jar 根目录的 `kubejs.plugins.txt`，每行 `FQCN [side]`（KubeJS 自己的插件也这么列，`client` 表示仅客户端，其余 token 是必需的模组 id）。类名写错完全静默——KubeJS 只是永远不加载它。`TechnomJS` 对未知要素抛异常而不是回落到 `fallback`，否则脚本把 `ignis` 拼成 `ignus` 会读到 25 却不知道错在哪。
- **KubeJS 的编译期限制**：`transitive = false` 意味着 rhino 不在编译类路径上，所以只覆写 `registerBindings`；若要覆写 `registerTypeWrappers` 需另加 `dev.latvian.mods:rhino-forge` 为 `compileOnly`。

