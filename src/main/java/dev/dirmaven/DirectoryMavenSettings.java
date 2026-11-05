package dev.dirmaven;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import dev.dirmaven.core.MavenConfigResolver;
import dev.dirmaven.core.MavenRule;
import dev.dirmaven.core.ResolvedMavenConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author lt_xiaoyi
     * @Description 保存项目级 Maven 目录规则并解析命中配置
 * @Version 1.0
 *
 * 项目级配置服务：保存「目录 → Maven 配置」规则，并对外提供解析能力。
 *
 * <p>因为用的是项目级 {@code PersistentStateComponent}，规则会落在
 * {@code .idea/directoryMavenSettings.xml}，跟着仓库走，团队成员开箱即用。</p>
 */
@State(name = "DirectoryMavenSettings", storages = {@Storage("directoryMavenSettings.xml")})
public final class DirectoryMavenSettings implements PersistentStateComponent<DirectoryMavenSettings.State> {

    public static final class State {
        /**
         * 项目打开 / Maven 导入完成后，自动把命中的 settings.xml 设为项目当前配置并重新导入。
         * 覆盖 git clone 新项目的场景，不需要手动点 Tools 菜单。
         */
        public boolean autoApply = true;

        public List<MavenRule> rules = new ArrayList<>();
    }

    private State state = new State();

    /**
     * 获取配置服务实例。
     * @param project 当前 IDE 项目
     * @return 当前作用域的配置服务实例
     */
    public static DirectoryMavenSettings getInstance(@NotNull Project project) {
        return project.getService(DirectoryMavenSettings.class);
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
     * 判断导入后是否自动应用规则。
     * @return 启用自动应用时为 true
     */
    public boolean isAutoApply() {
        return state.autoApply;
    }

    /**
     * 设置导入后自动应用规则的开关。
     * @param autoApply 是否在导入完成后自动应用
     */
    public void setAutoApply(boolean autoApply) {
        state.autoApply = autoApply;
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

    /**
     * 按目标目录解析出该用的 Maven 配置（自动叠加全局规则）；返回 {@code null} 表示无需注入。
     * @param project 当前 IDE 项目
     * @param directory 待匹配的目标目录
     * @return 匹配到的配置；没有可用规则时为 null
     */
    public @Nullable ResolvedMavenConfig resolve(@Nullable Project project, @Nullable String directory) {
        String base = project == null ? null : project.getBasePath();
        List<MavenRule> globalRules = java.util.Collections.emptyList();
        DirectoryMavenGlobalSettings global = DirectoryMavenGlobalSettings.getInstance();
        if (global != null) {
            globalRules = global.getRules();
        }
        return MavenConfigResolver.resolve(getRules(), globalRules, base, directory);
    }
}
