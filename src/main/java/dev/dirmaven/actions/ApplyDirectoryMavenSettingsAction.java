package dev.dirmaven.actions;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import dev.dirmaven.DirectoryMavenSettings;
import dev.dirmaven.core.ResolvedMavenConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

/**
 * @Author lt_xiaoyi
 * @Description 手动应用当前目录的 Maven 配置
 * @Version 1.0
 * <p>
 * 「应用当前目录的 Maven Settings 并重新导入」。
 *
 * <p>插件运行时注入只影响 mvn 命令行；而 IDEA 的 <b>依赖解析/项目导入</b> 走的是项目级全局
 * Maven 设置（IDEA 本身不支持按模块指定 settings.xml）。这个动作用来补上那一环：
 * 把当前文件所在目录命中的 settings.xml 设为项目当前使用的全局配置，然后重新导入。</p>
 */
public final class ApplyDirectoryMavenSettingsAction extends AnAction {

    private static final String NOTIFICATION_GROUP = "dirmaven.notifications";

    /**
     * 手动应用当前目录命中的 Maven 设置。
     *
     * @param event IDE 动作或编辑器事件
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        String directory = currentDirectory(event, project);
        ResolvedMavenConfig resolved = DirectoryMavenSettings.getInstance(project).resolve(project, directory);
        if (resolved == null || resolved.getSettingsPath().isEmpty()) {
            notify(project, "当前目录没有命中带 settings.xml 的规则",
                    "目录：" + directory + "。请先在 Settings → Build Tools → Maven → Directory Maven Settings 里配置规则。",
                    NotificationType.WARNING);
            return;
        }

        MavenProjectsManager manager = MavenProjectsManager.getInstanceIfCreated(project);
        if (manager == null) {
            notify(project, "当前项目没有 Maven 支持", "无法切换 Maven settings.xml。", NotificationType.WARNING);
            return;
        }

        manager.getGeneralSettings().setUserSettingsFile(resolved.getSettingsPath());
        // 菜单仅对 Maven 项目可用；通过公开入口重新导入，使新的 settings.xml 参与依赖解析。
        manager.forceUpdateAllProjectsOrFindAllAvailablePomFiles();
        notify(project, "已切换到 " + resolved.getSettingsPath(),
                "已按目录规则切换 Maven settings.xml，并触发重新导入。注意这是项目级全局设置，切换后其它模块也会使用它。",
                NotificationType.INFORMATION);
    }

    /**
     * 更新菜单动作的可用状态。
     *
     * @param event IDE 动作或编辑器事件
     */
    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        MavenProjectsManager manager = project == null ? null : MavenProjectsManager.getInstanceIfCreated(project);
        event.getPresentation().setEnabledAndVisible(project != null && manager != null && manager.isMavenizedProject());
    }

    /**
     * 指定动作状态更新所用线程。
     *
     * @return 后台线程标识
     */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * 获取当前文件或项目对应的目录。
     *
     * @param event   IDE 动作或编辑器事件
     * @param project 当前 IDE 项目
     * @return 当前文件所在目录；不可用时为项目根目录
     */
    private static String currentDirectory(AnActionEvent event, Project project) {
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file != null) {
            VirtualFile dir = file.isDirectory() ? file : file.getParent();
            if (dir != null) {
                return dir.getPath();
            }
        }
        String basePath = project.getBasePath();
        return basePath == null ? "" : basePath;
    }

    /**
     * 显示操作结果通知。
     *
     * @param project 当前 IDE 项目
     * @param title   通知标题
     * @param content 通知正文
     * @param type    通知级别
     */
    private static void notify(Project project, String title, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(title, content, type)
                .notify(project);
    }
}
