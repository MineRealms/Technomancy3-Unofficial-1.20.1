# Claude Code 交接入口

这是独立的 Technomancy Forge 1.20.1 移植工程。当前只建立基础工程；用户将后续实现交给 Claude Code。不要把项目能编译、依赖已配置或本文档已有规划理解为玩法完成。

## 先读这些文件

1. [README.md](README.md)：本机准备、构建与运行命令。
2. [总工程指导](docs/ENGINEERING_GUIDE.zh-CN.md)：架构、能源语义、TC4R API 边界、S0–S5 验收。
3. [功能矩阵](docs/FEATURE_MATRIX.zh-CN.md)：实际原版范围、待迁移功能和验证条件。
4. [源码基线](docs/SOURCE_BASELINE.zh-CN.md)：取舍、固定提交、已确认旧问题。
5. [基础验证记录](docs/VALIDATION.zh-CN.md)：实际完成的检查及未验证事项。

## 已确定的方向

- Minecraft 1.20.1、Forge 47.4.23、Java 17、Gradle 8.8、ForgeGradle 6.0.54。
- `mod_id=technom`，Java 包 `theflogat.technomancy`。版本以 `gradle.properties` 为准。
- TC4R `0.1.0-20721` 为必需依赖；运行时版本和 Maven 版本不同，保留元数据中的准确范围。
- 去掉 Thermal Expansion 和 CoFH RF。Forge Energy 为基础电力协议，GTCEu 7.5.3 为可选原生 EU 集成。
- GT API 始终为 compileOnly；`-PwithGtceu=true` 仅控制开发运行时。使用同一个发行 JAR 验证有/无 GT 的环境。
- FE/EU 共用一份能源余额和吞吐预算，EU 保留电压/安培语义；魔法资源独立建模。该实现目前不存在。
- Botania、Blood Magic 保留为后续可选模块。原版未注册实验内容单独列出。

## 来源和文件边界

相邻 `../Technomancy` 的 master `37bf9a5` 是玩法基准；`../Technomancy-2` 的 `223160a` 提供经审查的改进与资源。两者没有共同 Git 祖先，不做全量机械合并、不用“较新文件全覆盖”策略。按功能组在本工程重写，并记录来源与刻意改变的行为。

本地 TC4R 由 `tools/bootstrap-local-deps.ps1` 导入并校验，JAR/POM 不提交 Git、不打入产物。新代码采用 Apache-2.0，旧源码/美术引入时保留来源与适用署名，MDK 通知另存 `LICENSES/`。

旧版本存在真实缺陷，重点包括满槽扣能、自动化槽占位、玩家同步禁用、连接工具未注册、合成原料语义、Affinity 赋值和融合核心共享状态。详见源码基线文档，不能以复刻原版为理由照搬缺陷。

## 下一步建议

收到用户继续实施的指令后，从 S1-A 开始：注册和 BlockEntity 基础、物品/流体能力、唯一能源存储、FE 视图和隔离的 GT EU 适配。完成有意义的守恒/方向/模拟/电压测试后，进入 S1-B 精华罐→精华发电机→耗能设备→研究配方闭环。不要一次复制全部旧源码再以大批 stub 掩盖缺口。

日常构建：

```powershell
.\tools\bootstrap-local-deps.ps1
.\gradlew.bat build --console=plain --no-daemon
```

使用 JDK 17；注意 `JAVA_HOME` 可能与 PATH 上的 `java` 版本不同。不要把本机绝对 JDK 路径、代理或缓存路径写进跟踪配置。运行目录、日志和生成缓存均已忽略。

目前没有游戏内容、数据提供器或 GameTest。`test NO-SOURCE`、空数据生成或单纯打包成功不能作为功能验收。后续每批提交记录具体实现、验证命令、结果及剩余限制，更新功能矩阵与验证记录。
