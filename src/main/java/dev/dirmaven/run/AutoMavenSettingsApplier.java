package dev.dirmaven.run;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import dev.dirmaven.DirectoryMavenSettings;
import dev.dirmaven.core.ResolvedMavenConfig;
import org.jetbrains.idea.maven.project.MavenImportListener;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @Author lt_xiaoyi
 * @Description 在 Maven 导入完成后自动切换匹配的 settings.xml
 * @Version 1.0
 * <p>
 * Maven 导入完成后的自动切换：项目打开 / git clone 后第一次导入结束时，
 * 按项目根目录解析规则，命中带 settings.xml 的规则且与当前设置不同时，
 * 自动把「User settings file」切过去并触发重新导入。
 *
 * <p>通过 importFinished 的相等性守卫避免循环：切换后再次导入时发现已一致，直接跳过。</p>
 */
public final class AutoMavenSettingsApplier implements MavenImportListener {

    private static final String NOTIFICATION_GROUP = "dirmaven.notifications";

    private final Project project;

    /**
     * {@code projectListeners} 注册的监听器由平台注入 Project。
     *
     * @param project 当前 IDE 项目
     */
    public AutoMavenSettingsApplier(@NotNull Project project) {
        this.project = project;
    }

    /**
     * 在 Maven 导入结束后按目录规则更新用户配置并重新导入。
     *
     * @param mavenProjects 本次导入的 Maven 项目
     * @param modules       本次导入的模块
     */
    @Override
    public void importFinished(@NotNull Collection<org.jetbrains.idea.maven.project.MavenProject> mavenProjects,
                               @NotNull List<Module> modules) {
        if (project.isDisposed()) {
            return;
        }
        DirectoryMavenSettings settings = DirectoryMavenSettings.getInstance(project);
        if (!settings.isAutoApply()) {
            return;
        }
        String directory = project.getBasePath();
        if (directory == null || directory.isEmpty()) {
            return;
        }
        ResolvedMavenConfig resolved = settings.resolve(project, directory);
        if (resolved == null || resolved.getSettingsPath().isEmpty()) {
            return;
        }
        MavenProjectsManager manager = MavenProjectsManager.getInstanceIfCreated(project);
        if (manager == null) {
            return;
        }
        String current = manager.getGeneralSettings().getUserSettingsFile();
        String target = resolved.getSettingsPath();
        if (target.equals(current == null ? "" : current)) {
            return; // 已经是目标 settings，避免无限循环
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            String before = manager.getGeneralSettings().getUserSettingsFile();
            if (target.equals(before == null ? "" : before)) {
                return; // 双重保险：可能已被其它路径切换
            }
            manager.getGeneralSettings().setUserSettingsFile(target);
            // 项目已经完成 Maven 导入；使用公开入口执行完整重新导入，避免调用平台内部 API。
            manager.forceUpdateAllProjectsOrFindAllAvailablePomFiles();
            NotificationGroupManager.getInstance()
                    .getNotificationGroup(NOTIFICATION_GROUP)
                    .createNotification(
                            "已按目录规则自动切换 Maven settings.xml",
                            "规则「" + resolved.label() + "」：" + target
                                    + "（原值：" + (before == null || before.isEmpty() ? "默认" : before) + "），正在重新导入…",
                            NotificationType.INFORMATION)
                    .notify(project);
        }, project.getDisposed());
    }
}
