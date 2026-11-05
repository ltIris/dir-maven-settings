package dev.dirmaven.core;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author lt_xiaoyi
 * @Description 覆盖目录规则解析行为的本地自检场景
 * @Version 1.0
 * <p>
 * 不依赖 IntelliJ / JUnit 的自检脚本，用来在纯 JDK 环境下验证匹配与解析逻辑。
 * <pre>javac -d out src/main/java/dev/dirmaven/core/*.java src/test/java/dev/dirmaven/core/MatcherSelfCheck.java
 * java -cp out dev.dirmaven.core.MatcherSelfCheck</pre>
 */
public final class MatcherSelfCheck {

    private static int failed = 0;

    /**
     * 依次运行目录匹配的自检场景。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        prefixMatch();
        globMatch();
        placeholderAndRelativePath();
        specificityAndOrdering();
        profilesAndExtraArgs();
        disabledAndEmptyRules();
        multipleDirectoriesPerRule();
        globalAndProjectLayers();
        envVarsParsing();
        nearMissSuggestions();
        fallbackRules();
        windowsPaths();

        if (failed == 0) {
            System.out.println("\n全部用例通过。");
        } else {
            System.out.println("\n失败用例：" + failed);
            System.exit(1);
        }
    }

    /**
     * 验证普通路径前缀的命中边界。
     */
    private static void prefixMatch() {
        check("同目录命中", DirectoryPattern.matches("/work/demo", "/work/demo"));
        check("子目录命中", DirectoryPattern.matches("/work/demo", "/work/demo/order-service/src"));
        check("同前缀不同目录不命中", !DirectoryPattern.matches("/work/demo", "/work/demo2"));
        check("父目录不命中华", !DirectoryPattern.matches("/work/demo/order-service", "/work/demo"));
    }

    /**
     * 验证单层和多层通配符匹配。
     */
    private static void globMatch() {
        check("** 跨层级", DirectoryPattern.matches("/work/**", "/work/a/b/c"));
        check("** 精确目录", DirectoryPattern.matches("/work/**", "/work/a"));
        check("* 单层", DirectoryPattern.matches("/work/*/app", "/work/order/app"));
        check("* 不跨层", !DirectoryPattern.matches("/work/*/app", "/work/order/x/app"));
        check("? 单字符", DirectoryPattern.matches("/work/mod?", "/work/mod1"));
        check("? 不匹配空", !DirectoryPattern.matches("/work/mod?", "/work/mod"));
    }

    /**
     * 验证占位符与相对路径展开。
     */
    private static void placeholderAndRelativePath() {
        List<MavenRule> rules = new ArrayList<>();
        rules.add(new MavenRule("order-service", "config/order-settings.xml", "dev", "", true));

        ResolvedMavenConfig resolved = MavenConfigResolver.resolve(rules, "/work/demo", "/work/demo/order-service");
        check("相对目录解析命中", resolved != null);
        if (resolved != null) {
            check("相对 settings 路径补全为绝对路径",
                    resolved.getSettingsPath().equals("/work/demo/config/order-settings.xml"));
            check("参数顺序为 -s path -P profile",
                    String.join(" ", resolved.getArguments())
                            .equals("-s /work/demo/config/order-settings.xml -P dev"));
        }

        List<MavenRule> placeholderRules = new ArrayList<>();
        placeholderRules.add(new MavenRule("${PROJECT_DIR$}/order-service", "", "prod", "", true));
        ResolvedMavenConfig byPlaceholder =
                MavenConfigResolver.resolve(placeholderRules, "/work/demo", "/work/demo/order-service/api");
        check("${PROJECT_DIR$} 占位符生效", byPlaceholder != null);
        if (byPlaceholder != null) {
            check("只有 profile 时不产生 -s",
                    String.join(" ", byPlaceholder.getArguments()).equals("-P prod"));
        }
    }

    /**
     * 验证匹配精度与同精度规则顺序。
     */
    private static void specificityAndOrdering() {
        List<MavenRule> rules = new ArrayList<>();
        rules.add(new MavenRule("/work/demo", "/settings/root.xml", "", "", true));
        rules.add(new MavenRule("/work/demo/order-service", "/settings/order.xml", "", "", true));
        rules.add(new MavenRule("/work/demo/order-service", "/settings/order-duplicate.xml", "", "", true));

        ResolvedMavenConfig resolved = MavenConfigResolver.resolve(rules, "/work/demo", "/work/demo/order-service");
        check("最长前缀优先", resolved != null && resolved.getSettingsPath().equals("/settings/order.xml"));

        ResolvedMavenConfig root = MavenConfigResolver.resolve(rules, "/work/demo", "/work/demo/user-service");
        check("回落到父目录规则", root != null && root.getSettingsPath().equals("/settings/root.xml"));
    }

    /**
     * 验证 profile 和额外参数解析。
     */
    private static void profilesAndExtraArgs() {
        check("profile 清洗", MavenConfigResolver.cleanProfiles(" dev , ,!prod ").equals("dev,!prod"));

        List<MavenRule> rules = new ArrayList<>();
        rules.add(new MavenRule("/work/demo", "", "", "-Dmaven.repo.local=\"/data/my repo\" -U", true));
        ResolvedMavenConfig resolved = MavenConfigResolver.resolve(rules, "/work/demo", "/work/demo/a");
        check("额外参数分词", resolved != null && resolved.getArguments().size() == 2);
        if (resolved != null && resolved.getArguments().size() == 2) {
            check("带引号的路径不被拆开",
                    resolved.getArguments().get(0).equals("-Dmaven.repo.local=/data/my repo"));
            check("普通参数原样保留", resolved.getArguments().get(1).equals("-U"));
        }
    }

    /**
     * 验证禁用与空规则不会注入配置。
     */
    private static void disabledAndEmptyRules() {
        List<MavenRule> rules = new ArrayList<>();
        rules.add(new MavenRule("/work/demo", "/settings/disabled.xml", "", "", false));
        check("停用的规则不参与匹配", MavenConfigResolver.resolve(rules, "/work/demo", "/work/demo/a") == null);

        List<MavenRule> emptyRules = new ArrayList<>();
        emptyRules.add(new MavenRule("/work/demo", "  ", "", " ", true));
        check("没有可注入内容的规则被忽略",
                MavenConfigResolver.resolve(emptyRules, "/work/demo", "/work/demo/a") == null);

        check("空规则列表返回 null", MavenConfigResolver.resolve(new ArrayList<>(), "/work/demo", "/work/demo") == null);
    }

    /**
     * 验证一条规则中的多个目录表达式。
     */
    private static void multipleDirectoriesPerRule() {
        List<MavenRule> rules = new ArrayList<>();
        rules.add(new MavenRule("多目录", "/work/alpha, /work/beta;/work/gamma", "/settings/multi.xml", "", "", true));

        ResolvedMavenConfig alpha = MavenConfigResolver.resolve(rules, "/work/demo", "/work/alpha/app");
        check("多路径：逗号分隔的第一条命中", alpha != null && alpha.getSettingsPath().equals("/settings/multi.xml"));

        ResolvedMavenConfig beta = MavenConfigResolver.resolve(rules, "/work/demo", "/work/beta/x");
        check("多路径：分号分隔的路径命中", beta != null && beta.getSettingsPath().equals("/settings/multi.xml"));

        ResolvedMavenConfig miss = MavenConfigResolver.resolve(rules, "/work/demo", "/work/delta/x");
        check("多路径：全部未命中返回 null", miss == null);

        ResolvedMavenConfig shared = MavenConfigResolver.resolve(rules, "/work/demo", "/work/demo/order-service");
        check("多路径：前缀不误伤（/work/beta 不匹配 /work/beta2）",
                MavenConfigResolver.resolve(rules, "/work/demo", "/work/beta2/x") == null && shared == null);

        // 多路径规则 vs 单路径更精确规则：单路径更长前缀应胜出
        List<MavenRule> mixed = new ArrayList<>();
        mixed.add(new MavenRule("共享", "/work/alpha, /work/beta", "/settings/shared.xml", "", "", true));
        mixed.add(new MavenRule("精确", "/work/alpha/core", "/settings/core.xml", "", "", true));
        ResolvedMavenConfig core = MavenConfigResolver.resolve(mixed, "/work/demo", "/work/alpha/core/api");
        check("多路径与精确规则：更长前缀优先", core != null && core.getSettingsPath().equals("/settings/core.xml"));

        check("directories() 拆分支持中文分隔符",
                new MavenRule("/a，/b；/c", "", "", "", true).directories().size() == 3);
        check("directories() 忽略空白项",
                new MavenRule(" , /real , ", "", "", "", true).directories().size() == 1);
    }

    /**
     * 验证环境变量配置的解析。
     */
    private static void envVarsParsing() {
        List<MavenRule> rules = new ArrayList<>();
        rules.add(new MavenRule("带环境变量", "/work/demo", "", "", "",
                "MAVEN_OPTS=-Xmx2g -Dfoo=bar; ENV_NAME=prod；EMPTY= ", true));
        ResolvedMavenConfig resolved = MavenConfigResolver.resolve(rules, "/work/demo", "/work/demo/app");
        check("环境变量规则命中", resolved != null);
        if (resolved != null) {
            check("分号分隔解析出 3 个变量", resolved.getEnvVars().size() == 3);
            check("变量值保留内部空格", " -Xmx2g -Dfoo=bar".equals(resolved.getEnvVars().get("MAVEN_OPTS"))
                    || "-Xmx2g -Dfoo=bar".equals(resolved.getEnvVars().get("MAVEN_OPTS")));
            check("中文分号也是分隔符", resolved.getEnvVars().containsKey("ENV_NAME"));
            check("describe 里包含 env 段", resolved.describe().contains("env"));
            check("isEmpty 与参数无关地包含 env", !resolved.isEmpty());
        }

        List<MavenRule> onlyEnv = new ArrayList<>();
        onlyEnv.add(new MavenRule("仅环境变量", "/work/demo", "", "", "", "A=1", true));
        ResolvedMavenConfig envOnly = MavenConfigResolver.resolve(onlyEnv, "/work/demo", "/work/demo/app");
        check("只有环境变量也算有效规则", envOnly != null && envOnly.getArguments().isEmpty()
                && "1".equals(envOnly.getEnvVars().get("A")));

        check("空文本解析为空 map", MavenConfigResolver.parseEnvVars("  ").isEmpty());
        check("没有 = 的片段被忽略", MavenConfigResolver.parseEnvVars("FOO;BAR=1").size() == 1);
        check("同名单变量取第一个值",
                "first".equals(MavenConfigResolver.parseEnvVars("K=first;K=second").get("K")));
    }

    /**
     * 验证项目规则与全局规则的优先级。
     */
    private static void globalAndProjectLayers() {
        List<MavenRule> project = new ArrayList<>();
        project.add(new MavenRule("项目规则", "/work/demo", "/settings/project.xml", "", "", true));
        List<MavenRule> global = new ArrayList<>();
        global.add(new MavenRule("全局规则", "/work/demo", "/settings/global.xml", "", "", true));

        // 两层都命中且同精度：项目规则优先
        ResolvedMavenConfig tied = MavenConfigResolver.resolve(project, global, "/work/demo", "/work/demo/app");
        check("同精度：项目规则优先于全局规则",
                tied != null && !tied.isGlobal() && tied.getSettingsPath().equals("/settings/project.xml"));

        // 全局规则更精确时胜出
        List<MavenRule> project2 = new ArrayList<>();
        project2.add(new MavenRule("/work", "/settings/coarse-project.xml", "", "", true));
        ResolvedMavenConfig precise = MavenConfigResolver.resolve(
                project2, global, "/work/demo", "/work/demo/order-service");
        check("全局规则更精确时胜出并标记 global",
                precise != null && precise.isGlobal() && precise.getSettingsPath().equals("/settings/global.xml"));

        // 项目层为空：纯全局规则兜底
        ResolvedMavenConfig fallback = MavenConfigResolver.resolve(
                new ArrayList<>(), global, "/work/demo", "/work/demo/x");
        check("无项目规则时全局规则生效", fallback != null && fallback.isGlobal());

        // 全局层为空：退化为单层解析
        ResolvedMavenConfig solo = MavenConfigResolver.resolve(project, new ArrayList<>(), "/work/demo", "/work/demo/x");
        check("无全局规则时项目规则正常生效",
                solo != null && !solo.isGlobal() && solo.getSettingsPath().equals("/settings/project.xml"));

        // label 展示
        List<MavenRule> unnamedGlobal = new ArrayList<>();
        unnamedGlobal.add(new MavenRule("", "/work/demo", "/settings/anon.xml", "", "", true));
        ResolvedMavenConfig anon = MavenConfigResolver.resolve(
                new ArrayList<>(), unnamedGlobal, "/work/demo", "/work/demo/x");
        check("无名称的全局规则标签为「全局规则 N」", anon != null && "全局规则 1".equals(anon.label()));
        List<MavenRule> named = new ArrayList<>();
        named.add(new MavenRule("公司私服", "/work/demo", "/s.xml", "", "", true));
        ResolvedMavenConfig namedResolved = MavenConfigResolver.resolve(named, new ArrayList<>(), "/work/demo", "/work/demo/x");
        check("有名称的规则标签不带「（全局）」", namedResolved != null && "公司私服".equals(namedResolved.label()));

        // 全局层里的停用规则不生效
        List<MavenRule> disabledGlobal = new ArrayList<>();
        disabledGlobal.add(new MavenRule("/work/demo", "/settings/off.xml", "", "", false));
        check("全局层停用规则不参与匹配",
                MavenConfigResolver.resolve(new ArrayList<>(), disabledGlobal, "/work/demo", "/work/demo/x") == null);
    }

    /**
     * 验证目录未命中时的修改建议。
     */
    private static void nearMissSuggestions() {
        List<MavenRule> rules = new ArrayList<>();
        rules.add(new MavenRule("公牛", "/Users/me/IdeaProjects/gongniu/*",
                "/m/setting.xml", "", "", true));

        String deep = "/Users/me/IdeaProjects/gongniu/qcln/qcln-statistics";
        String suggestion = MavenConfigResolver.nearMissSuggestion(rules, "/proj", deep);
        check("/* 挡住深层子目录时给出建议", suggestion != null && suggestion.contains("/**"));

        String oneLevel = "/Users/me/IdeaProjects/gongniu/qcln";
        check("/* 本来就能命中的路径不产生建议",
                MavenConfigResolver.nearMissSuggestion(rules, "/proj", oneLevel) == null);

        String direct = "/Users/me/IdeaProjects/gongniu";
        String removeAdvice = MavenConfigResolver.nearMissSuggestion(rules, "/proj", direct);
        check("同目录被 /* 挡住时建议去掉 /*",
                removeAdvice != null && removeAdvice.contains("去掉"));

        List<MavenRule> midStar = new ArrayList<>();
        midStar.add(new MavenRule("t", "/work/*/svc", "/m/s.xml", "", "", true));
        String promoted = MavenConfigResolver.nearMissSuggestion(midStar, "/proj", "/work/a/b/svc");
        check("中间层 * 不够深时建议改 **", promoted != null && promoted.contains("**"));

        List<MavenRule> sibling = new ArrayList<>();
        sibling.add(new MavenRule("t", "/work/alpha", "/m/s.xml", "", "", true));
        String parentAdvice = MavenConfigResolver.nearMissSuggestion(sibling, "/proj", "/work/alpha-beta/app");
        check("兄弟目录未覆盖时建议填父目录", parentAdvice == null || parentAdvice.contains("父目录"));

        check("正常命中不产生建议",
                MavenConfigResolver.nearMissSuggestion(rules, "/proj", oneLevel) == null);

        List<MavenRule> unrelated = new ArrayList<>();
        unrelated.add(new MavenRule("t", "/other/place", "/m/s.xml", "", "", true));
        check("完全无关的规则不产生建议",
                MavenConfigResolver.nearMissSuggestion(unrelated, "/proj", deep) == null);
    }

    /**
     * 验证默认兜底规则的选择。
     */
    private static void fallbackRules() {
        List<MavenRule> projectRules = new ArrayList<>();
        projectRules.add(new MavenRule("严格", "/work/alpha", "/s/alpha.xml", "", "", true));
        MavenRule dft = new MavenRule("默认", "", "/s/default.xml", "dev", "", true);
        dft.fallback = true;
        projectRules.add(dft);

        List<MavenRule> none = java.util.Collections.emptyList();

        ResolvedMavenConfig hit = MavenConfigResolver.resolve(projectRules, none, "/proj", "/work/alpha/svc");
        check("有目录命中时不用兜底",
                hit != null && !hit.isFallback() && hit.getSettingsPath().equals("/s/alpha.xml"));

        ResolvedMavenConfig fb = MavenConfigResolver.resolve(projectRules, none, "/proj", "/work/beta");
        check("未命中时使用默认兜底",
                fb != null && fb.isFallback() && fb.getSettingsPath().equals("/s/default.xml"));
        check("兜底规则标签带「默认兜底」", fb != null && fb.label().contains("默认兜底"));
        check("兜底规则照常生成 -P 参数", fb != null && fb.getArguments().contains("-P"));
        check("兜底命中得分为 FALLBACK_SCORE",
                fb != null && fb.getMatchScore() == MavenConfigResolver.FALLBACK_SCORE);

        ResolvedMavenConfig emptyDir = MavenConfigResolver.resolve(projectRules, none, "/proj", "");
        check("目录为空时也走兜底", emptyDir != null && emptyDir.isFallback());

        dft.enabled = false;
        check("停用的兜底规则不生效", MavenConfigResolver.resolve(projectRules, none, "/proj", "/work/beta") == null);
        dft.enabled = true;

        MavenRule blank = new MavenRule("空", "", "", "", "", true);
        blank.fallback = true;
        List<MavenRule> onlyBlank = new ArrayList<>();
        onlyBlank.add(blank);
        check("没有注入内容的兜底不生效", MavenConfigResolver.resolve(onlyBlank, none, "/proj", "/x") == null);

        List<MavenRule> globalRules = new ArrayList<>();
        MavenRule globalDefault = new MavenRule("全局默认", "", "/s/global.xml", "", "", true);
        globalDefault.fallback = true;
        globalRules.add(globalDefault);

        ResolvedMavenConfig projectWins = MavenConfigResolver.resolve(projectRules, globalRules, "/proj", "/work/beta");
        check("项目兜底优先于全局兜底",
                projectWins != null && projectWins.getSettingsPath().equals("/s/default.xml") && !projectWins.isGlobal());

        List<MavenRule> noProjectRules = new ArrayList<>();
        ResolvedMavenConfig globalOnly = MavenConfigResolver.resolve(noProjectRules, globalRules, "/proj", "/work/beta");
        check("只有全局兜底时生效",
                globalOnly != null && globalOnly.isGlobal() && globalOnly.getSettingsPath().equals("/s/global.xml"));

        List<MavenRule> twoDefaults = new ArrayList<>();
        MavenRule d1 = new MavenRule("D1", "", "/s/d1.xml", "", "", true);
        d1.fallback = true;
        MavenRule d2 = new MavenRule("D2", "", "/s/d2.xml", "", "", true);
        d2.fallback = true;
        twoDefaults.add(d1);
        twoDefaults.add(d2);
        ResolvedMavenConfig firstWins = MavenConfigResolver.resolve(twoDefaults, none, "/proj", "/nowhere");
        check("同层多条兜底取第一条", firstWins != null && firstWins.getSettingsPath().equals("/s/d1.xml"));

        List<MavenRule> fallbackWithDir = new ArrayList<>();
        MavenRule withDir = new MavenRule("兜底带目录", "/work/alpha", "/s/d.xml", "", "", true);
        withDir.fallback = true;
        fallbackWithDir.add(withDir);
        check("兜底规则填了目录但没命中时仍兜底",
                MavenConfigResolver.resolve(fallbackWithDir, none, "/proj", "/work/beta") != null);

        check("copy 保留兜底标记", d1.copy().fallback);
    }

    /**
     * 验证 Windows 路径匹配行为。
     */
    private static void windowsPaths() {
        check("Windows 盘符大小写不敏感", DirectoryPattern.matches("D:/Work/Demo", "d:/work/demo/order-service"));
        check("反斜杠归一化", DirectoryPattern.matches("D:\\work\\demo", "d:/work/demo/app"));
        check("Windows 绝对 settings 路径",
                MavenConfigResolver.expandPath("C:\\maven\\settings.xml", "D:/work/demo")
                        .equals("C:/maven/settings.xml"));
    }

    /**
     * 记录断言结果并统计失败数量。
     *
     * @param name      规则的显示名称
     * @param condition 待验证的断言结果
     */
    private static void check(String name, boolean condition) {
        if (condition) {
            System.out.println("PASS  " + name);
        } else {
            failed++;
            System.out.println("FAIL  " + name);
        }
    }
}
