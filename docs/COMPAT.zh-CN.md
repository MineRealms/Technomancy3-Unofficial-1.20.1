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
| Jade | `11.13.2` + JadeAddons | 现代 HUD 替代 Waila |
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

## 5. 处理决定汇总

| 原 compat | 决定 |
|---|---|
| Thaumcraft | TC4R，必需（版本待对齐 20711/20721） |
| ThermalExpansion / Mekanism | TE 排除；Mek 在包内，材料分支按 Mek；能源用 FE/EU |
| Botania | S3 选装模块（5 项 + Lexicon） |
| BloodMagic | **排除，不做** |
| Thaumic Energistics | **自动兼容**（实现 TC4R 接口即可），不写注册代码；补文档与验证 |
| Waila | 用 Jade；TC4R 有 `api/integration/JadeRegistration`，可后补 |
