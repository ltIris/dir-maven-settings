package dev.dirmaven.run;

import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import dev.dirmaven.DirectoryMavenSettings;
import dev.dirmaven.core.ResolvedMavenConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author lt_xiaoyi
 * @Description 向 Maven 运行配置注入匹配的参数与环境变量
 * @Version 1.0
 *
 * 关键实现：在 Maven 运行配置真正启动进程之前，把当前工作目录命中的
 * {@code -s <settings.xml>} / {@code -P <profiles>} / 额外参数追加到 mvn 命令行里。
 *
 * <p>为什么挂 {@code updateJavaParameters} 而不是 {@code patchCommandLine}：
 * Maven 运行配置（{@link MavenRunConfiguration}）在 {@code createJavaParameters()} 里
 * 会主动调用 {@code JavaRunConfigurationExtensionManager.updateJavaParameters(...)}，
 * 这是 JetBrains 自己给 Java 系 run configuration 预留的官方钩子；
 * 而 Maven 进程由 {@link JavaParameters} 启动，{@code GeneralCommandLine} 那条钩子根本不会被走到。
 * 挂在这里既能拿到未修改的参数列表，又不会污染用户保存的运行配置。</p>
 */
public final class DirMavenRunConfigurationExtension extends RunConfigurationExtension {

    private static final Logger LOG = Logger.getInstance(DirMavenRunConfigurationExtension.class);
    private static final String NOTIFICATION_GROUP = "dirmaven.notifications";

    /** 同一个缺失文件只提醒一次，避免每次运行都弹气泡。 */
    private static final Set<String> REPORTED_MISSING = ConcurrentHashMap.newKeySet();

    /**
     * 判断当前运行配置是否支持注入 Maven 参数。
     * @param configuration 当前运行配置
     * @return 支持注入时为 true
     */
    @Override
    public boolean isApplicableFor(@NotNull RunConfigurationBase<?> configuration) {
        return configuration instanceof MavenRunConfiguration;
    }

    /**
     * 将命中规则的参数与环境变量加入 Maven 运行配置。
     * @param configuration 当前运行配置
     * @param params 待追加 Maven 参数与环境变量的 Java 运行参数
     * @param runnerSettings 当前运行器设置
     */
    @Override
    public <T extends RunConfigurationBase<?>> void updateJavaParameters(@NotNull T configuration,
                                                                        @NotNull JavaParameters params,
                                                                        @Nullable RunnerSettings runnerSettings) {
        if (!(configuration instanceof MavenRunConfiguration mavenConfiguration)) {
            return;
        }
        Project project = mavenConfiguration.getProject();
        if (project.isDisposed()) {
            return;
        }
        DirectoryMavenSettings settings = DirectoryMavenSettings.getInstance(project);
        if (settings == null) {
            return;
        }

        MavenRunnerParameters runnerParameters = mavenConfiguration.getRunnerParameters();
        if (hasSettingsArgument(runnerParameters.getGoals())) {
            // 用户自己在 Command line 里写了 -s，插件的效果应该是“显式配置优先”
            return;
        }

        ResolvedMavenConfig resolved = resolveFor(settings, project, runnerParameters);
        if (resolved == null || resolved.isEmpty()) {
            return;
        }

        ParametersList programParameters = params.getProgramParametersList();
        if (programParameters.hasParameter("-s") || containsAll(programParameters, resolved.getArguments())) {
            return;
        }

        for (String argument : resolved.getArguments()) {
            programParameters.add(argument);
        }
        for (Map.Entry<String, String> entry : resolved.getEnvVars().entrySet()) {
            params.addEnv(entry.getKey(), entry.getValue());
        }

        LOG.info("Directory Maven Settings: 规则「" + resolved.label()
                + "」为 [" + mavenConfiguration.getName() + "] 注入 "
                + resolved.describe() + "（工作目录 " + runnerParameters.getWorkingDirPath() + "）");
        warnIfSettingsMissing(project, resolved);
    }

    /**
     * 按运行配置的项目和工作目录解析规则。
     * @param settings 项目规则配置服务
     * @param project 当前 IDE 项目
     * @param parameters 当前 Maven 运行参数
     * @return 当前工作目录命中的配置；未命中时为 null
     */
    private static ResolvedMavenConfig resolveFor(DirectoryMavenSettings settings,
                                                  Project project,
                                                  MavenRunnerParameters parameters) {
        ResolvedMavenConfig resolved = settings.resolve(project, parameters.getWorkingDirPath());
        if (resolved != null) {
            return resolved;
        }
        // 回退：运行配置可能用 -f 指定了另一个目录下的 pom.xml
        String pomFileName = parameters.getPomFileName();
        String workingDir = parameters.getWorkingDirPath();
        if (pomFileName == null || pomFileName.isBlank() || workingDir == null || workingDir.isBlank()) {
            return null;
        }
        try {
            File pom = new File(workingDir, pomFileName);
            File parent = pom.getAbsoluteFile().getParentFile();
            return parent == null ? null : settings.resolve(project, parent.getPath());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 检查命令行是否已显式指定 settings 文件。
     * @param goals Maven 命令行目标及选项
     * @return 已指定 settings 文件时为 true
     */
    private static boolean hasSettingsArgument(@Nullable List<String> goals) {
        if (goals == null) {
            return false;
        }
        for (String goal : goals) {
            if (goal == null) {
                continue;
            }
            String trimmed = goal.trim();
            if (trimmed.equals("-s") || trimmed.equals("--settings")
                    || trimmed.equals("-gs") || trimmed.equals("--global-settings")
                    || trimmed.startsWith("-s=") || trimmed.startsWith("--settings=")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查参数列表是否已经包含全部待注入参数。
     * @param parameters 当前 Maven 参数列表
     * @param arguments 待检测或追加的参数
     * @return 所有参数均已存在时为 true
     */
    private static boolean containsAll(ParametersList parameters, List<String> arguments) {
        for (String argument : arguments) {
            if (!parameters.hasParameter(argument)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 在 settings 文件不存在时向用户发出一次提醒。
     * @param project 当前 IDE 项目
     * @param resolved 已解析的 Maven 配置
     */
    private static void warnIfSettingsMissing(Project project, ResolvedMavenConfig resolved) {
        String path = resolved.getSettingsPath();
        if (path.isEmpty() || !REPORTED_MISSING.add(path)) {
            return;
        }
        File file = new File(path);
        if (file.isFile()) {
            return;
        }
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification("找不到 settings.xml：" + path,
                        "目录规则命中了，但文件不存在，本次运行会带上一个无效的 -s 参数。"
                                + "请检查 Settings → Build Tools → Maven → Directory Maven Settings。",
                        NotificationType.WARNING)
                .notify(project);
    }
}
