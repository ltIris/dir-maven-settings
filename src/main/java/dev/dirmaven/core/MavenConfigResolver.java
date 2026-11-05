package dev.dirmaven.core;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author lt_xiaoyi
 * @Description 按目录精度合并项目与全局 Maven 规则
 * @Version 1.0
 * <p>
 * 规则解析：把「一组规则 + 项目根目录 + 目标目录」变成一个可直接使用的 Maven 命令行片段。
 *
 * <p>纯 Java 实现，没有任何 IntelliJ 依赖 —— 便于单测，也方便把同样的规则用在脚本 / CI 里。</p>
 */
public final class MavenConfigResolver {

    /**
     * 初始化 MavenConfigResolver。
     */
    private MavenConfigResolver() {
    }

    /**
     * @param rules       规则列表，顺序即书写顺序（同精度的规则先出现者胜）
     * @param projectBase 项目根目录，用于补全相对路径与 {@code ${PROJECT_DIR$}} 占位符
     * @param directory   目标目录（Maven 运行配置的工作目录）
     * @return 命中的配置；没有任何规则命中时返回 {@code null}
     */
    public static ResolvedMavenConfig resolve(List<MavenRule> rules, String projectBase, String directory) {
        return resolve(rules, java.util.Collections.emptyList(), projectBase, directory);
    }

    /**
     * 两层规则合并解析：{@code projectRules}（本项目）+ {@code globalRules}（全局共享）。
     * 同等精度时项目规则优先；任何一层更精确都会胜出。
     */
    public static ResolvedMavenConfig resolve(List<MavenRule> projectRules, List<MavenRule> globalRules,
                                              String projectBase, String directory) {
        String dir = DirectoryPattern.normalize(directory);
        if (dir.isEmpty()) {
            return fallbackOf(projectRules, globalRules, projectBase);
        }
        ResolvedMavenConfig projectBest = matchLayer(projectRules, false, projectBase, dir);
        ResolvedMavenConfig globalBest = matchLayer(globalRules, true, projectBase, dir);
        if (projectBest == null && globalBest == null) {
            // 目录全都没命中：用标了「默认」的规则兜底
            return fallbackOf(projectRules, globalRules, projectBase);
        }
        if (projectBest == null) {
            return globalBest;
        }
        if (globalBest == null) {
            return projectBest;
        }
        // 同精度项目规则优先，因此用严格大于
        return globalBest.getMatchScore() > projectBest.getMatchScore() ? globalBest : projectBest;
    }

    /**
     * 兜底规则的得分：低于任何真实匹配，只用来标记「这条是兜底命中的」。
     */
    public static final int FALLBACK_SCORE = -2;

    /**
     * 两层里找默认兜底规则：项目级默认优先于全局默认。
     *
     * @param projectRules 项目级规则
     * @param globalRules  全局规则
     * @param projectBase  项目根目录
     * @return 优先命中的兜底配置；不存在时为 null
     */
    private static ResolvedMavenConfig fallbackOf(List<MavenRule> projectRules, List<MavenRule> globalRules,
                                                  String projectBase) {
        ResolvedMavenConfig fallback = findFallback(projectRules, false, projectBase);
        return fallback != null ? fallback : findFallback(globalRules, true, projectBase);
    }

    /**
     * 找第一条启用且带注入内容的默认兜底规则（不要求填目录）。
     *
     * @param rules        待保存或解析的规则列表
     * @param globalSource 规则是否来自全局配置
     * @param projectBase  项目根目录
     * @return 首条有效兜底配置；不存在时为 null
     */
    private static ResolvedMavenConfig findFallback(List<MavenRule> rules, boolean globalSource, String projectBase) {
        if (rules == null) {
            return null;
        }
        for (int i = 0; i < rules.size(); i++) {
            MavenRule rule = rules.get(i);
            if (rule == null || !rule.enabled || !rule.hasPayload() || !rule.fallback) {
                continue;
            }
            return build(rule, i, projectBase, "", globalSource, FALLBACK_SCORE, true);
        }
        return null;
    }

    /**
     * 在一层规则里找最精确的命中；返回值自带得分。
     *
     * @param rules        待保存或解析的规则列表
     * @param globalSource 规则是否来自全局配置
     * @param projectBase  项目根目录
     * @param dir          已规范化的目标目录
     * @return 当前层最精确的匹配；未命中时为 null
     */
    private static ResolvedMavenConfig matchLayer(List<MavenRule> rules, boolean globalSource,
                                                  String projectBase, String dir) {
        int bestScore = -1;
        int bestIndex = -1;
        String bestPattern = null;
        if (rules == null) {
            return null;
        }
        for (int i = 0; i < rules.size(); i++) {
            MavenRule rule = rules.get(i);
            if (rule == null || !rule.enabled || !rule.hasPayload() || rule.directories().isEmpty()) {
                continue;
            }
            // 一条规则可以声明多个目录，任一命中即生效；规则内取最精确的那条参与全局比较
            int ruleScore = -1;
            String rulePattern = null;
            for (String entry : rule.directories()) {
                String pattern = expandPath(entry, projectBase);
                if (pattern.isEmpty() || !DirectoryPattern.matches(pattern, dir)) {
                    continue;
                }
                int score = DirectoryPattern.specificity(pattern);
                if (score > ruleScore) {
                    ruleScore = score;
                    rulePattern = pattern;
                }
            }
            if (ruleScore > bestScore) {
                bestScore = ruleScore;
                bestIndex = i;
                bestPattern = rulePattern;
            }
        }
        if (bestIndex < 0) {
            return null;
        }
        return build(rules.get(bestIndex), bestIndex, projectBase, bestPattern, globalSource, bestScore);
    }

    /**
     * 根据项目规则生成解析结果。
     *
     * @param rule           命中的原始规则
     * @param index          规则在列表中的下标
     * @param projectBase    项目根目录
     * @param matchedPattern 实际命中的目录表达式
     * @return 可用于注入的 Maven 配置
     */
    static ResolvedMavenConfig build(MavenRule rule, int index, String projectBase, String matchedPattern) {
        return build(rule, index, projectBase, matchedPattern, false, -1);
    }

    /**
     * 根据指定来源生成解析结果。
     *
     * @param rule           命中的原始规则
     * @param index          规则在列表中的下标
     * @param projectBase    项目根目录
     * @param matchedPattern 实际命中的目录表达式
     * @param globalSource   是否来自全局规则
     * @param matchScore     目录匹配精度
     * @return 可用于注入的 Maven 配置
     */
    static ResolvedMavenConfig build(MavenRule rule, int index, String projectBase, String matchedPattern,
                                     boolean globalSource, int matchScore) {
        return build(rule, index, projectBase, matchedPattern, globalSource, matchScore, false);
    }

    /**
     * 展开路径、参数与环境变量，生成完整的注入配置。
     *
     * @param rule           命中的原始规则
     * @param index          规则在列表中的下标
     * @param projectBase    项目根目录
     * @param matchedPattern 实际命中的目录表达式
     * @param globalSource   是否来自全局规则
     * @param matchScore     目录匹配精度
     * @param fallbackHit    是否由兜底规则命中
     * @return 完整的 Maven 注入配置
     */
    static ResolvedMavenConfig build(MavenRule rule, int index, String projectBase, String matchedPattern,
                                     boolean globalSource, int matchScore, boolean fallbackHit) {
        String settingsPath = expandPath(rule.settingsFile, projectBase);
        String profiles = cleanProfiles(rule.profiles);

        List<String> args = new ArrayList<>();
        if (!settingsPath.isEmpty()) {
            args.add("-s");
            args.add(settingsPath);
        }
        if (!profiles.isEmpty()) {
            args.add("-P");
            args.add(profiles);
        }
        args.addAll(Arguments.parse(rule.extraArgs));

        return new ResolvedMavenConfig(index, rule, settingsPath, profiles, args, parseEnvVars(rule.envVars),
                globalSource, matchScore, fallbackHit);
    }

    /**
     * 解析环境变量文本：{@code VAR=value;VAR2=value2}，分号 / 换行分隔（兼容中文分号）。
     * 值保持原样（可含空格、引号不剥离）；没有 {@code =} 的片段忽略。
     */
    public static java.util.LinkedHashMap<String, String> parseEnvVars(String text) {
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }
        for (String part : text.split("[;\\n；]")) {
            String entry = part.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = entry.substring(0, eq).trim();
            if (name.isEmpty()) {
                continue;
            }
            result.putIfAbsent(name, entry.substring(eq + 1).trim());
        }
        return result;
    }

    /**
     * 未命中时尝试给出「差一步就能命中」的修改建议，例如目录写了 {@code /path/*}
     * 但目标在更深的子目录里。返回 {@code null} 表示没有明显的原因。
     */
    public static String nearMissSuggestion(List<MavenRule> rules, String projectBase, String directory) {
        String dir = DirectoryPattern.normalize(directory);
        if (rules == null || dir.isEmpty()) {
            return null;
        }
        for (MavenRule rule : rules) {
            if (rule == null || !rule.enabled) {
                continue;
            }
            for (String entry : rule.directories()) {
                String pattern = expandPath(entry, projectBase);
                if (pattern.isEmpty() || DirectoryPattern.matches(pattern, dir)) {
                    continue;
                }
                // 情况一：末尾 /* 只匹配一层，目标在更深层 —— 去掉 /* 或改成 /** 就能中
                if (pattern.endsWith("/*")) {
                    String withoutStar = pattern.substring(0, pattern.length() - 2);
                    String withDoubleStar = pattern.substring(0, pattern.length() - 1) + "*";
                    if (DirectoryPattern.matches(withoutStar, dir)) {
                        return "规则「" + rule.displayLabel(0) + "」的目录 " + pattern
                                + " 差一点命中：/* 只匹配一层子目录，把 /* 去掉（推荐）或改成 /** 即可";
                    }
                    if (DirectoryPattern.matches(withDoubleStar, dir)) {
                        return "规则「" + rule.displayLabel(0) + "」的目录 " + pattern
                                + " 差一点命中：/* 只匹配一层子目录，改成 /** 即可匹配所有层级";
                    }
                }
                // 情况二：把单层 * 提升成 ** 就能命中
                String promoted = pattern.replace("/*", "/**");
                if (!promoted.equals(pattern) && DirectoryPattern.matches(promoted, dir)) {
                    return "规则「" + rule.displayLabel(0) + "」的目录 " + pattern
                            + " 差一点命中：* 只匹配一层，把 * 改成 ** 即可匹配所有层级";
                }
                // 情况三：目标是某个已填目录的前缀匹配不到的兄弟节点 —— 提示直接填父目录
                String parent = pattern.contains("/") ? pattern.substring(0, pattern.lastIndexOf('/')) : "";
                if (!parent.isEmpty() && dir.startsWith(parent) && DirectoryPattern.matches(parent, dir)) {
                    return "规则「" + rule.displayLabel(0) + "」的目录 " + pattern
                            + " 没覆盖到该路径：规则目录按前缀匹配，直接填它的父目录 " + parent + " 即可覆盖全部子项目";
                }
            }
        }
        return null;
    }

    /**
     * 展开路径类占位符；相对路径按项目根目录补全。
     *
     * @param value       待处理的值
     * @param projectBase 项目根目录
     * @return 展开后的绝对路径
     */
    public static String expandPath(String value, String projectBase) {
        String expanded = expand(value, projectBase);
        if (expanded.isEmpty()) {
            return "";
        }
        if (isAbsolute(expanded)) {
            return DirectoryPattern.normalize(expanded);
        }
        if (isBlank(projectBase)) {
            return DirectoryPattern.normalize(expanded);
        }
        return DirectoryPattern.normalize(DirectoryPattern.normalize(projectBase) + "/" + expanded);
    }

    /**
     * 展开 {@code ${PROJECT_DIR$}}、{@code ~}、{@code ${HOME}} 等占位符（不做绝对化）。
     *
     * @param value       待处理的值
     * @param projectBase 项目根目录
     * @return 已展开占位符的文本
     */
    public static String expand(String value, String projectBase) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        if (result.isEmpty()) {
            return "";
        }
        String base = isBlank(projectBase) ? "" : DirectoryPattern.normalize(projectBase);
        if (!base.isEmpty()) {
            result = result.replace("${PROJECT_DIR$}", base)
                    .replace("$PROJECT_DIR$", base)
                    .replace("${PROJECT_DIR}", base)
                    .replace("${projectDir}", base)
                    .replace("${PROJECT}", base);
        }
        String home = System.getProperty("user.home", "");
        if (!home.isEmpty()) {
            result = result.replace("${HOME}", home).replace("${user.home}", home);
            if (result.equals("~")) {
                result = home;
            } else if (result.startsWith("~/") || result.startsWith("~\\")) {
                result = home + result.substring(1);
            }
        }
        return result;
    }

    /**
     * 去掉 profile 配置中的命令行前缀，避免重复生成 {@code -P}。
     *
     * @param profiles 用户输入的 profile 文本
     * @return 仅包含 profile 名称的文本
     */
    static String cleanProfiles(String profiles) {
        if (isBlank(profiles)) {
            return "";
        }
        List<String> cleaned = new ArrayList<>();
        for (String part : profiles.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                cleaned.add(trimmed);
            }
        }
        return String.join(",", cleaned);
    }

    /**
     * 判断路径是否为绝对路径。
     *
     * @param path 待处理的路径
     * @return 绝对路径时为 true
     */
    private static boolean isAbsolute(String path) {
        return path.startsWith("/")
                || path.matches("^[A-Za-z]:[\\\\/].*")
                || path.startsWith("\\\\");
    }

    /**
     * 判断配置文本是否为空白。
     *
     * @param value 待处理的值
     * @return 文本为空白时为 true
     */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
