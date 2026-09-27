# 本地 TC4R 开发依赖

TC4R 20721 来自用户提供的本地构建。JAR 和 POM 保留在本机，不提交 Git，也不打入 Technomancy 成品。

在项目根目录执行：

```powershell
.\tools\bootstrap-local-deps.ps1
```

默认来源为相邻 `Pollution-Unofficial-1.20.1/local-repo`。也可指定另一个包含 `dev/tc4port/...` 的 Maven 仓库根目录：

```powershell
.\tools\bootstrap-local-deps.ps1 -SourceRepository 'D:\local-repo'
```

脚本根据 [DEPENDENCIES.json](DEPENDENCIES.json) 校验所有源文件，再复制缺失文件；已存在的相同文件会复用，校验不同则停止。需要这四个文件：

```text
dev/tc4port/thaumcraft-forge/0.1.0-20721/
  thaumcraft-forge-0.1.0-20721.jar
  thaumcraft-forge-0.1.0-20721-api.jar
  thaumcraft-forge-0.1.0-20721-sources.jar
  thaumcraft-forge-0.1.0-20721.pom
```

也可不复制，构建时使用 `-Ptc4Repository=D:/local-repo`。Gradle 的 `verifyLocalDependencies` 按相同清单验证所选仓库。

编译使用完整运行时 JAR 的反混淆视图，源码 JAR 供 IDE 和 API 审查使用；仅有 sources.jar 不能启动模组。该 Maven 版本为 `0.1.0-20721`，运行时模组版本为 `4.2.3.5-1.20.1-port.0.1.0-20721`，两者不能混用。

升级依赖时同时更新 `gradle.properties`、运行时版本、此清单和 API/运行验证记录。校验值只能确认文件身份，不能证明新版本兼容。
