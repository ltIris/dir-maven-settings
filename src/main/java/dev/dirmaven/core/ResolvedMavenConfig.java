package dev.dirmaven.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @Author lt_xiaoyi
 * @Description 保存命中规则展开后的 Maven 配置
 * @Version 1.0
 * <p>
 * 一次目录解析的结果：命中哪条规则、要用的 settings.xml、最终要追加到 mvn 命令行的参数与环境变量。
 */
public final class ResolvedMavenConfig {

    private final int ruleIndex;
    private final MavenRule rule;
    private final String settingsPath;
    private final String profiles;
    private final List<String> arguments;
    private final Map<String, String> envVars;
    private final boolean globalSource;
    private final int matchScore;
    private final boolean fallbackHit;

    /**
     * 创建非兜底规则的解析结果。
     *
     * @param ruleIndex    命中规则的下标
     * @param rule         命中的原始规则
     * @param settingsPath 展开后的 settings.xml 路径
     * @param profiles     要激活的 profile
     * @param arguments    要注入的 Maven 参数
     * @param envVars      要注入的环境变量
     * @param globalSource 是否来自全局规则
     * @param matchScore   目录匹配精度
     */
    ResolvedMavenConfig(int ruleIndex, MavenRule rule, String settingsPath, String profiles,
                        List<String> arguments, Map<String, String> envVars, boolean globalSource, int matchScore) {
        this(ruleIndex, rule, settingsPath, profiles, arguments, envVars, globalSource, matchScore, false);
    }

    /**
     * 保存一条规则的完整解析结果，并隔离可变参数集合。
     *
     * @param ruleIndex    命中规则的下标
     * @param rule         命中的原始规则
     * @param settingsPath 展开后的 settings.xml 路径
     * @param profiles     要激活的 profile
     * @param arguments    要注入的 Maven 参数
     * @param envVars      要注入的环境变量
     * @param globalSource 是否来自全局规则
     * @param matchScore   目录匹配精度
     * @param fallbackHit  是否命中默认兜底规则
     */
    ResolvedMavenConfig(int ruleIndex, MavenRule rule, String settingsPath, String profiles,
                        List<String> arguments, Map<String, String> envVars, boolean globalSource, int matchScore,
                        boolean fallbackHit) {
        this.ruleIndex = ruleIndex;
        this.rule = rule;
        this.settingsPath = settingsPath;
        this.profiles = profiles;
        this.arguments = Collections.unmodifiableList(arguments);
        this.envVars = Collections.unmodifiableMap(envVars);
        this.globalSource = globalSource;
        this.matchScore = matchScore;
        this.fallbackHit = fallbackHit;
    }

    /**
     * 是否是「目录都没命中，靠默认规则兜底」得到的配置。
     *
     * @return 由兜底规则命中时为 true
     */
    public boolean isFallback() {
        return fallbackHit;
    }

    /**
     * 匹配精度得分（目录静态前缀越长越高），用于跨层比较。
     *
     * @return 目录匹配精度分数
     */
    public int getMatchScore() {
        return matchScore;
    }

    /**
     * 命中规则在配置列表里的下标（从 0 开始）。
     *
     * @return 命中规则的零基下标
     */
    public int getRuleIndex() {
        return ruleIndex;
    }

    /**
     * 命中的规则是否来自全局规则（跨项目共享），而不是本项目配置。
     *
     * @return 来自全局规则时为 true
     */
    public boolean isGlobal() {
        return globalSource;
    }

    /**
     * 展示用标签：优先规则名称，否则「规则 N」/「全局规则 N」；兜底命中的再标注「默认兜底」。
     *
     * @return 包含来源与兜底信息的展示标签
     */
    public String label() {
        String trimmed = rule.name == null ? "" : rule.name.trim();
        String base;
        if (!trimmed.isEmpty()) {
            base = globalSource ? trimmed + "（全局）" : trimmed;
        } else {
            base = globalSource ? "全局规则 " + (ruleIndex + 1) : "规则 " + (ruleIndex + 1);
        }
        return fallbackHit ? base + " · 默认兜底" : base;
    }

    /**
     * 获取命中的原始规则。
     *
     * @return 命中的原始规则
     */
    public MavenRule getRule() {
        return rule;
    }

    /**
     * 解析后的绝对路径，可能为空字符串。
     *
     * @return 展开后的 settings.xml 路径
     */
    public String getSettingsPath() {
        return settingsPath;
    }

    /**
     * 获取需要激活的 Maven profile。
     *
     * @return 激活的 profile 文本
     */
    public String getProfiles() {
        return profiles;
    }

    /**
     * 可直接追加到 mvn 命令行的参数，例如 {@code [-s, /data/settings-prod.xml, -P, prod]}。
     *
     * @return 可追加到 Maven 命令行的参数
     */
    public List<String> getArguments() {
        return arguments;
    }

    /**
     * 要注入到 Maven 进程的环境变量，例如 {@code {MAVEN_OPTS: -Xmx2g}}。
     *
     * @return 待注入的环境变量
     */
    public Map<String, String> getEnvVars() {
        return envVars;
    }

    /**
     * 判断解析结果是否没有可注入内容。
     *
     * @return 没有可注入内容时为 true
     */
    public boolean isEmpty() {
        return arguments.isEmpty() && envVars.isEmpty();
    }

    /**
     * 生成解析结果的文本描述。
     *
     * @return 解析结果的可读描述
     */
    public String describe() {
        String args = isEmpty() ? "无额外参数" : String.join(" ", arguments);
        if (envVars.isEmpty()) {
            return args;
        }
        StringBuilder text = new StringBuilder(args);
        if (!args.isEmpty()) {
            text.append("  ");
        }
        text.append("[env ");
        boolean first = true;
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            if (!first) {
                text.append("; ");
            }
            first = false;
            text.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return text.append(']').toString();
    }
}
