package dev.dirmaven.core;

/**
 * @Author lt_xiaoyi
 * @Description 保存一条目录与 Maven 配置的映射规则
 * @Version 1.0
 *
 * 一条「目录 → Maven 配置」规则。
 *
 * <p>字段之所以保持 public 且类不可变，是为了让 IntelliJ 的 {@code XmlSerializer}
 * 可以直接把规则序列化到项目里的 {@code .idea/directoryMavenSettings.xml}，
 * 从而让整个团队共享同一份映射（跟着 Git 走）。</p>
 */
public final class MavenRule {

    /** 规则的显示名称，例如「公司内网」「客户现场」。留空时 UI 会回退显示为「规则 N」。 */
    public String name = "";

    /**
     * 目录匹配表达式（可填多个，用逗号 / 分号 / 换行分隔，任一命中即生效）。
     * 绝对路径、相对项目根目录的路径、{@code ${PROJECT_DIR$}}、{@code ~}、{@code *} {@code ?} {@code **} 都支持。
     */
    public String directory = "";

    /** settings.xml 的路径。可以留空：此时只注入 profile / 额外参数。 */
    public String settingsFile = "";

    /** 要激活的 profile，逗号分隔，例如 {@code dev} 或 {@code dev,!prod}。 */
    public String profiles = "";

    /** 追加到 mvn 命令行的其它参数，例如 {@code -Dmaven.repo.local=/data/repo -U}。 */
    public String extraArgs = "";

    /**
     * 注入到 Maven 进程的环境变量，格式 {@code VAR=value;VAR2=value2}（分号或换行分隔，可留空）。
     */
    public String envVars = "";

    /** 这条规则是否启用。 */
    public boolean enabled = true;

    /**
     * 是否为全局规则：{@code true} 时存在 IDE 全局配置里，本机所有项目生效；
     * {@code false} 时存在项目的 {@code .idea/} 里，仅本项目生效。
     */
    public boolean global = false;

    /**
     * 默认兜底规则：当所有规则的目录都没命中时，用这条规则。
     * 可以作为「其它项目一律用公司 settings.xml」的兜底，此时目录可以留空。
     */
    public boolean fallback = false;

    /**
     * 初始化 MavenRule。
     */
    public MavenRule() {
    }

    /**
     * 初始化 MavenRule。
     * @param directory 待匹配的目标目录
     * @param settingsFile Maven settings.xml 文件路径
     * @param profiles 需要激活的 Maven profile
     * @param extraArgs 额外 Maven 命令行参数
     * @param enabled 是否启用
     */
    public MavenRule(String directory, String settingsFile, String profiles, String extraArgs, boolean enabled) {
        this("", directory, settingsFile, profiles, extraArgs, enabled);
    }

    /**
     * 初始化 MavenRule。
     * @param name 规则的显示名称
     * @param directory 待匹配的目标目录
     * @param settingsFile Maven settings.xml 文件路径
     * @param profiles 需要激活的 Maven profile
     * @param extraArgs 额外 Maven 命令行参数
     * @param enabled 是否启用
     */
    public MavenRule(String name, String directory, String settingsFile, String profiles, String extraArgs, boolean enabled) {
        this(name, directory, settingsFile, profiles, extraArgs, "", enabled);
    }

    /**
     * 初始化 MavenRule。
     * @param name 规则的显示名称
     * @param directory 待匹配的目标目录
     * @param settingsFile Maven settings.xml 文件路径
     * @param profiles 需要激活的 Maven profile
     * @param extraArgs 额外 Maven 命令行参数
     * @param envVars 环境变量配置
     * @param enabled 是否启用
     */
    public MavenRule(String name, String directory, String settingsFile, String profiles, String extraArgs,
                     String envVars, boolean enabled) {
        this.name = safe(name);
        this.directory = directory;
        this.settingsFile = settingsFile;
        this.profiles = profiles;
        this.extraArgs = extraArgs;
        this.envVars = safe(envVars);
        this.enabled = enabled;
    }

    /**
     * 复制规则以隔离编辑状态。
     * @return 与原规则独立的副本
     */
    public MavenRule copy() {
        MavenRule copied = new MavenRule(name, directory, settingsFile, profiles, extraArgs, envVars, enabled);
        copied.global = global;
        copied.fallback = fallback;
        return copied;
    }

    /**
     * 显示用标签：有名称用名称，没有则回退为「规则 N」（{@code index} 从 0 开始）。
     */
    public String displayLabel(int index) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.isEmpty() ? "规则 " + (index + 1) : trimmed;
    }

    /**
     * 拆出这条规则的多个目录表达式。支持英文/中文逗号、英文/中文分号、换行作分隔符。
     */
    public java.util.List<String> directories() {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (directory == null) {
            return result;
        }
        for (String part : directory.split("[,;，；\\n]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 规则里是否有真正需要注入的内容。
     * @return 规则包含有效注入内容时为 true
     */
    public boolean hasPayload() {
        return !isBlank(settingsFile) || !isBlank(profiles) || !isBlank(extraArgs) || !isBlank(envVars);
    }

    /**
     * 判断配置文本是否为空白。
     * @param value 待处理的值
     * @return 文本为空白时为 true
     */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 比较两条规则的配置内容。
     * @param o 待比较的对象
     * @return 内容相同时为 true
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MavenRule)) return false;
        MavenRule other = (MavenRule) o;
        return enabled == other.enabled
                && global == other.global
                && fallback == other.fallback
                && safe(name).equals(safe(other.name))
                && safe(directory).equals(safe(other.directory))
                && safe(settingsFile).equals(safe(other.settingsFile))
                && safe(profiles).equals(safe(other.profiles))
                && safe(extraArgs).equals(safe(other.extraArgs))
                && safe(envVars).equals(safe(other.envVars));
    }

    /**
     * 计算规则内容的哈希值。
     * @return 与规则内容一致的哈希值
     */
    @Override
    public int hashCode() {
        int result = safe(name).hashCode();
        result = 31 * result + safe(directory).hashCode();
        result = 31 * result + safe(settingsFile).hashCode();
        result = 31 * result + safe(profiles).hashCode();
        result = 31 * result + safe(extraArgs).hashCode();
        result = 31 * result + safe(envVars).hashCode();
        result = 31 * result + (enabled ? 1 : 0);
        result = 31 * result + (global ? 1 : 0);
        result = 31 * result + (fallback ? 1 : 0);
        return result;
    }

    /**
     * 生成规则的调试描述。
     * @return 规则的文本描述
     */
    @Override
    public String toString() {
        return "MavenRule{name='" + name + "', directory='" + directory + "', settings='" + settingsFile
                + "', profiles='" + profiles + "', extra='" + extraArgs + "', envVars='" + envVars
                + "', enabled=" + enabled + ", global=" + global + '}';
    }

    /**
     * 将空值转换为可比较的字符串。
     * @param value 待处理的值
     * @return 非 null 的规则字段值
     */
    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
