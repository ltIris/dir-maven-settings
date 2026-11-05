package dev.dirmaven.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @Author lt_xiaoyi
 * @Description 为项目创建目录规则状态栏组件
 * @Version 1.0
 *
 * 状态栏小组件：显示「当前文件所在目录会命中哪条 Maven 规则」，点一下直接跳到设置页。
 */
public final class DirMavenStatusBarWidgetFactory implements StatusBarWidgetFactory {

    public static final String ID = "DirectoryMavenSettings";

    /**
     * 返回状态栏组件的唯一标识。
     * @return 组件唯一标识
     */
    @Override
    public @NotNull String getId() {
        return ID;
    }

    /**
     * 返回设置页面的显示名称。
     * @return 设置页面名称
     */
    @Override
    public @NotNull String getDisplayName() {
        return "Directory Maven Settings";
    }

    /**
     * 判断项目是否可显示状态栏组件。
     * @param project 当前 IDE 项目
     * @return 可显示时为 true
     */
    @Override
    public boolean isAvailable(@NotNull Project project) {
        return true;
    }

    /**
     * 为项目创建状态栏组件。
     * @param project 当前 IDE 项目
     * @return 项目专属状态栏组件
     */
    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new DirMavenStatusBarWidget(project);
    }

    /**
     * 释放状态栏组件。
     * @param widget 待释放的状态栏组件
     */
    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) {
        widget.dispose();
    }

    /**
     * 判断组件是否可添加到状态栏。
     * @param statusBar 目标 IDE 状态栏
     * @return 允许启用时为 true
     */
    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
        return true;
    }
}
