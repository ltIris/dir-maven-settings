package dev.dirmaven;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import dev.dirmaven.core.MavenRule;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author lt_xiaoyi
     * @Description 保存跨项目共享的 Maven 目录规则
 * @Version 1.0
 *
 * 全局（应用级）配置服务：规则保存在 IDE 用户配置目录的 {@code directoryMavenGlobalSettings.xml}，
 * 与具体项目无关 —— 配置一次，本机所有项目都生效。
 *
 * <p>项目级规则（{@link DirectoryMavenSettings}）可以和全局规则叠加使用：
 * 同等精度时项目规则优先，全局规则兜底。</p>
 */
@State(name = "DirectoryMavenGlobalSettings", storages = {@Storage("directoryMavenGlobalSettings.xml")})
public final class DirectoryMavenGlobalSettings implements PersistentStateComponent<DirectoryMavenGlobalSettings.State> {

    public static final class State {
        /** 全局规则总开关。 */
        public boolean enabled = true;

        public List<MavenRule> rules = new ArrayList<>();
    }

    private State state = new State();

    /**
     * 获取配置服务实例。
     * @return 当前作用域的配置服务实例
     */
    public static DirectoryMavenGlobalSettings getInstance() {
        return ApplicationManager.getApplication().getService(DirectoryMavenGlobalSettings.class);
    }

    /**
     * 返回供 IDE 持久化的配置状态。
     * @return 当前持久化状态
     */
    @Override
    public @NotNull State getState() {
        return state;
    }

    /**
     * 恢复 IDE 保存的配置状态。
     * @param state 从配置文件恢复的状态
     */
    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
        if (this.state.rules == null) {
            this.state.rules = new ArrayList<>();
        }
    }

    /**
     * 在没有历史配置时初始化默认状态。
     */
    @Override
    public void noStateLoaded() {
        this.state = new State();
    }

    /**
     * 判断全局规则是否启用。
     * @return 启用时为 true
     */
    public boolean isEnabled() {
        return state.enabled;
    }

    /**
     * 设置全局规则开关。
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        state.enabled = enabled;
    }

    /**
     * 获取当前规则列表。
     * @return 可编辑的规则列表
     */
    public @NotNull List<MavenRule> getRules() {
        if (state.rules == null) {
            state.rules = new ArrayList<>();
        }
        return state.rules;
    }

    /**
     * 替换当前规则列表。
     * @param rules 待保存或解析的规则列表
     */
    public void setRules(@NotNull List<MavenRule> rules) {
        state.rules = new ArrayList<>(rules);
    }
}
