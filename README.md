# Directory Maven Settings

一个 IntelliJ IDEA 插件：**让 Maven 的 settings.xml 跟着目录走**。

**免费使用。**做这个插件只是因为自己反复切换 Maven 配置太繁琐：默认的 `~/.m2/settings.xml`、阿里云镜像、公司内网仓库、AtomGit
等场景，各自需要不同的 `settings.xml`。

在 IDEA 里切换项目或目录时，手动修改 Maven 设置或给命令加 `-s` 很容易忘。这个插件让你按目录配置对应的 `settings.xml`，之后运行
Maven 时自动选用命中的配置。

---

## 功能

| 能力       | 说明                                                                                                                                                           |
|------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 目录规则   | 目录 → 名称 + settings.xml + 激活 profile + 额外参数，一次配置；**一条规则可填多个目录**——每个目录独立一行（点「添加目录」增行、逐行浏览选择），任一命中即生效 |
| 规则名称   | 每条规则可命名（如「公司私服」「客户内网」），留空自动编号；命中提示、状态栏、日志都显示名称                                                                   |
| 环境变量   | 每条规则可配置 `VAR=value;VAR2=value2`，运行 Maven 时注入到进程环境（类似运行配置里的 Environment variables）                                                  |
| 规则开关   | 每条规则可单独启用/停用，停用后卡片置灰、不参与匹配                                                                                                            |
| 默认兜底   | 勾选某条规则的「默认兜底」：所有规则的目录都没命中时用它，目录可留空（例如「其它项目一律走公司 Nexus」）；同一层只需一条，项目级兜底优先于全局兜底             |
| 运行注入   | 运行/调试 Maven 配置前，自动追加 `-s <settings.xml>`、`-P <profiles>`、自定义参数                                                                              |
| 优先级     | 最长目录前缀优先，同精度按列表顺序；显式 `-s` 优先于插件                                                                                                       |
| 匹配测试   | 设置页里输入任意目录，实时显示命中哪条规则；点「测试」按钮弹出完整明细：命中规则、settings.xml 存在性、Profile、环境变量、最终命令行（可一键复制）             |
| 状态栏提示 | 当前文件所在目录用的是哪份 settings，一眼可见，点一下跳到设置                                                                                                  |
| 同步辅助   | `Tools → Apply Directory Maven Settings and Reload`：把当前目录的 settings.xml 设为项目全局配置并重新导入（补上依赖解析那一环）                                |

规则保存在 `.idea/directoryMavenSettings.xml`，随仓库提交 → 团队共享，新人拉代码即用。

---

## 安装

### 方式一：直接用打包好的 zip

`dist/directory-maven-settings-1.1.3.zip` →

`Settings → Plugins → ⚙️ → Install Plugin from Disk…` → 选择该 zip → 重启 IDE。

### 方式二：从源码构建

仅修改或构建插件源码时才需要此步骤。普通用户安装上面的 zip 即可，**不需要配置 IDEA 路径、Gradle 或额外 JAR**。项目自带
Gradle Wrapper，Gradle 运行环境需使用 JDK 21 或更新版本。

```bash
cd dir-maven-settings

# 默认下载并使用 2025.2.5 作为编译平台
./gradlew buildPlugin
# 产物：dist/directory-maven-settings-1.1.3.zip

# 直接起一个带插件的沙箱 IDE 调试
./gradlew runIde
```

- 想用本机 IDEA 编译时，可在各自的 `~/.gradle/gradle.properties` 中设置 `ideaHome=/本机/IDEA/安装路径`，或设置 `IDEA_HOME`
  环境变量。此路径不提交到仓库；未设置时使用上面的公开编译平台。
- 如果 Gradle 默认 JDK 与所选 IDEA 平台版本不匹配，可以在同一份用户配置中设置 `org.gradle.java.home` 和
  `org.gradle.java.installations.paths`，指向本机 IDEA 自带的 JBR。
- 打包产物只包含插件自己的 JAR。安装后，`com.intellij` 和 Maven 插件的类由**使用者当前安装的 IDEA**提供；`plugin.xml` 中已声明
  Java 和 Maven 插件依赖。支持范围仍需在目标版本验证实际运行。

---

## 使用

1. 打开 `Settings → Build, Execution, Deployment → Build Tools → Maven → Directory Settings`。
2. 点「添加规则」，填目录 + settings.xml（路径都可以用右侧 `…` 选）：

   | 目录                 | settings.xml                   | 激活 Profile   | 额外参数                        |
   |----------------------|--------------------------------|----------------|---------------------------------|
   | `/work/demo`         | `config/settings-company.xml`  |                |                                 |
   | `order-service`      | `config/settings-customer.xml` | `prod`         | `-Dmaven.repo.local=/data/repo` |
   | `/work/demo/billing` |                                | `billing-prod` |                                 |
   | `**/legacy-*`        | `config/settings-legacy.xml`   |                | `-U`                            |

3. 在底部「目录匹配测试」里粘贴一个目录，确认命中的规则和最终参数。
4. 正常运行你的 Maven 配置（Run/Debug、Maven 面板 Lifecycle 节点、`mvn` 目标都走同一个执行链路）。
5. 想确认注入结果：`Help → Show Log in Finder`，搜 `Directory Maven Settings`，日志里会打印实际追加的参数。

---

## 规则语法

**目录表达式**

| 写法                            | 含义                                                                  |
|---------------------------------|-----------------------------------------------------------------------|
| `/abs/path/module`              | 绝对路径                                                              |
| `order-service`                 | 相对项目根目录                                                        |
| `${PROJECT_DIR$}/order-service` | 占位符（`$PROJECT_DIR$`、`${PROJECT_DIR}`、`${projectDir}` 同样支持） |
| `~/work/demo`                   | 家目录                                                                |
| `/work/*/app`                   | 单层通配                                                              |
| `/work/**`                      | 跨层级通配                                                            |
| `/work/mod?`                    | 单字符通配                                                            |

- 前缀匹配按**路径层级**判定：`/work/demo` 命中 `/work/demo/order-service`，但不会命中 `/work/demo2`。
- 多条规则同时命中时：**静态前缀最长者胜**（越具体越优先），同精度取列表中靠前的。

**其它字段**

- `settings.xml` 支持同样的占位符；相对路径按**项目根目录**解析。
- `激活 Profile`：逗号分隔，支持 `!prod` 停用（最终拼成 `-P dev,!prod`）。
- `额外参数`：按 shell 规则分词，支持引号，例如 `-Dmaven.repo.local="/data/my repo" -U`。
- 某条规则只填 Profile 或只填额外参数也行，此时不会注入 `-s`。
- 运行配置的 `Command line` 里已经手写了 `-s` / `--settings` 时，插件不注入，尊重手写值。

---

## 实现说明

### 钩子为什么是 `updateJavaParameters`

Maven 运行配置（`MavenRunConfiguration`）在 `createJavaParameters()` 里会主动调用
`JavaRunConfigurationExtensionManager.updateJavaParameters(...)`，这是 JetBrains 给 Java 系运行配置预留的官方扩展点。

相比之下 `patchCommandLine(GeneralCommandLine)` 对 Maven **无效**：Maven 进程是用 `JavaParameters` 起的，参数由
`MavenExternalParameters` 组装，不是 `GeneralCommandLine`。所以插件注册的是：

```xml
<runConfigurationExtension implementation="dev.dirmaven.run.DirMavenRunConfigurationExtension"/>
```

```java
public <T extends RunConfigurationBase<?>> void updateJavaParameters(T cfg, JavaParameters params, RunnerSettings rs) {
    // 命中目录规则 → params.getProgramParametersList().add("-s"); add(path); ...
}
```

三个特性：

1. **不改用户保存的配置**：只在启动瞬间往 `JavaParameters` 里追加参数，运行配置 XML 不会被污染。
2. **幂等**：检测到参数已存在就跳过，不会重复注入。
3. **可观测**：每次注入都会写 `idea.log`；settings.xml 不存在时弹一次警告（同一路径不重复弹）。

### 依赖解析 / 项目导入

IDEA 的 import / sync 走的是**项目级全局** `MavenGeneralSettings`，平台没有「按模块指定 settings.xml」的 API，
插件无法在同步阶段做到按目录切换。为此提供了显式动作：

`Tools → Apply Directory Maven Settings and Reload`

把当前文件所在目录命中的 settings.xml 写入 `MavenGeneralSettings.setUserSettingsFile(...)` 并触发重新导入。
一次只对一份 settings 生效，这是 IDEA 的边界，不是插件的妥协。

### 目录结构

```
dir-maven-settings/
├── build.gradle.kts                     # IntelliJ Platform Gradle Plugin 2.x
├── gradle.properties                    # 项目级 Gradle 配置（本机路径放在用户级配置）
├── src/main/java/dev/dirmaven/
│   ├── core/                            # 纯 Java，零 IDE 依赖，可单测
│   │   ├── MavenRule.java               # 规则模型（XmlSerializer 友好）
│   │   ├── DirectoryPattern.java        # 前缀匹配 / glob → 正则 / 精度计算
│   │   ├── Arguments.java               # 带引号的命令行分词
│   │   ├── MavenConfigResolver.java     # 占位符展开 + 规则挑选 + 参数拼装
│   │   └── ResolvedMavenConfig.java     # 解析结果
│   ├── DirectoryMavenSettings.java      # 项目级持久化服务（.idea/directoryMavenSettings.xml）
│   ├── run/DirMavenRunConfigurationExtension.java   # 核心注入点
│   ├── ui/DirMavenConfigurable.java     # 设置页（含实时匹配测试）
│   ├── ui/DirMavenStatusBar*.java       # 状态栏小组件
│   └── actions/ApplyDirectoryMavenSettingsAction.java
├── src/main/resources/META-INF/plugin.xml
└── src/test/java/dev/dirmaven/core/MatcherSelfCheck.java   # 25 条自检用例
```

### 自检

核心逻辑不依赖 IDE，可以直接在 JDK 上跑：

```bash
javac -d /tmp/dm src/main/java/dev/dirmaven/core/*.java src/test/java/dev/dirmaven/core/MatcherSelfCheck.java
java -cp /tmp/dm dev.dirmaven.core.MatcherSelfCheck
# 或用 Gradle：./gradlew selfCheck
```

覆盖：前缀/层级边界、`*` `?` `**`、`${PROJECT_DIR$}`、相对路径补全、最长前缀优先、同精度顺序、
profile 清洗、带引号参数分词、停用规则、Windows 盘符大小写。

---

## 兼容性

- `plugin.xml` 声明 `since-build="251"`、`until-build="263.*"`，目标范围为 IntelliJ IDEA 2025.1 至 2026.3。
  JetBrains Marketplace 的兼容性检查显示 2025.1 至 2026.3 RC 兼容；2024.3 系列存在兼容性问题。
- 若要核验较早版本，请用对应版本的 SDK 编译——
  这条链路上的 API 在 2024–2026 之间做过 Kotlin 化调整（例如 `TextFieldWithBrowseButton` 迁到了
  `com.intellij.openapi.ui`，`RunConfigurationExtensionBase` 迁到了 `com.intellij.execution.configuration`）。

## 已知限制

- 只影响 `mvn` 进程参数；IDEA 的依赖解析仍是全局设置（见上文「依赖解析」）。
- 远程目标（WSL / SSH / Docker）的 Maven 运行配置不会走 `updateJavaParameters`，暂不注入。
- 不处理 `.mvn/maven.config`（该文件只有 Maven 命令行启动器会读，IDEA 不读）。
- 规则里的目录表达式请写**已存在的路径**，IDE 不会帮你校验目录是否存在（settings.xml 会校验并提示）。
