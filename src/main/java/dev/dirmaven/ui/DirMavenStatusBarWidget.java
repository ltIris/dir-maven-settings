package dev.dirmaven.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import dev.dirmaven.DirectoryMavenSettings;
import dev.dirmaven.core.ResolvedMavenConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;
import java.io.File;

/**
 * @Author lt_xiaoyi
 * @Description 展示当前目录命中的 Maven 规则
 * @Version 1.0
 * <p>
 * 状态栏上的小提示：当前编辑器所在目录命中哪条规则。
 */
public final class DirMavenStatusBarWidget implements StatusBarWidget, StatusBarWidget.TextPresentation, Disposable {

    private final Project project;
    private @Nullable StatusBar statusBar;

    /**
     * 初始化 DirMavenStatusBarWidget。
     *
     * @param project 当前 IDE 项目
     */
    public DirMavenStatusBarWidget(Project project) {
        this.project = project;
    }

    /**
     * 返回状态栏组件标识。
     *
     * @return 组件唯一标识
     */
    @Override
    public @NotNull String ID() {
        return DirMavenStatusBarWidgetFactory.ID;
    }

    /**
     * 返回状态栏组件的文本展示对象。
     *
     * @return 状态栏展示对象
     */
    @Override
    public @NotNull WidgetPresentation getPresentation() {
        return this;
    }

    /**
     * 订阅编辑器切换事件以刷新状态栏。
     *
     * @param statusBar 目标 IDE 状态栏
     */
    @Override
    public void install(@NotNull StatusBar statusBar) {
        this.statusBar = statusBar;
        project.getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
                new FileEditorManagerListener() {
                    /**
                     * 在编辑器选中文件改变时刷新状态。
                     * @param event IDE 动作或编辑器事件
                     */
                    @Override
                    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                        update();
                    }
                });
    }

    /**
     * 生成当前目录命中规则的简短文本。
     *
     * @return 当前命中规则的简短名称
     */
    @Override
    public String getText() {
        String directory = currentDirectory();
        if (directory == null || directory.isEmpty()) {
            return "";
        }
        ResolvedMavenConfig resolved = DirectoryMavenSettings.getInstance(project).resolve(project, directory);
        if (resolved == null) {
            return "Maven: 默认";
        }
        if (!resolved.getSettingsPath().isEmpty()) {
            return "Maven: " + new File(resolved.getSettingsPath()).getName();
        }
        return "Maven: " + (resolved.getProfiles().isEmpty()
                ? resolved.label()
                : resolved.getProfiles());
    }

    /**
     * 返回状态栏文本的对齐方式。
     *
     * @return 文本对齐系数
     */
    @Override
    public float getAlignment() {
        return Component.CENTER_ALIGNMENT;
    }

    /**
     * 生成当前配置的详细悬浮提示。
     *
     * @return 当前配置的详细提示
     */
    @Override
    public @Nullable String getTooltipText() {
        String directory = currentDirectory();
        if (directory == null) {
            return null;
        }
        ResolvedMavenConfig resolved = DirectoryMavenSettings.getInstance(project).resolve(project, directory);
        if (resolved == null) {
            return "<html>目录 " + escape(directory) + " 未命中任何规则，使用 Maven 默认配置。<br/>点击打开设置。</html>";
        }
        return "<html>目录 " + escape(directory) + " 命中规则「"
                + escape(resolved.label()) + "」：<br/>"
                + escape(resolved.describe()) + "<br/>点击打开设置。</html>";
    }

    /**
     * 提供状态栏点击操作。
     *
     * @return 点击状态栏时执行的操作
     */
    @Override
    public @Nullable com.intellij.util.Consumer<java.awt.event.MouseEvent> getClickConsumer() {
        return event -> ShowSettingsUtil.getInstance().showSettingsDialog(project, DirMavenConfigurable.class);
    }

    /**
     * 更新菜单动作的可用状态。
     */
    private void update() {
        if (statusBar != null) {
            statusBar.updateWidget(ID());
        }
    }

    /**
     * 获取当前文件或项目对应的目录。
     *
     * @return 当前文件所在目录；不可用时为项目根目录
     */
    private @Nullable String currentDirectory() {
        FileEditorManager manager = FileEditorManager.getInstance(project);
        VirtualFile[] files = manager.getSelectedFiles();
        if (files.length > 0) {
            VirtualFile file = files[0];
            VirtualFile dir = file.isDirectory() ? file : file.getParent();
            if (dir != null) {
                return dir.getPath();
            }
        }
        return project.getBasePath();
    }

    /**
     * 转义状态栏提示中的 HTML 特殊字符。
     *
     * @param text 待解析或展示的文本
     * @return 可安全写入 HTML 提示文本的字符串
     */
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * 释放状态栏组件持有的资源。
     */
    @Override
    public void dispose() {
        statusBar = null;
    }
}
