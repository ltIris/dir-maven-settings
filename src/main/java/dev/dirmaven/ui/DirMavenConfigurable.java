package dev.dirmaven.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import dev.dirmaven.DirectoryMavenGlobalSettings;
import dev.dirmaven.DirectoryMavenSettings;
import dev.dirmaven.core.MavenConfigResolver;
import dev.dirmaven.core.MavenRule;
import dev.dirmaven.core.ResolvedMavenConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author lt_xiaoyi
 * @Description 提供目录规则的设置页面与匹配预览
 * @Version 1.0
 *
 * Settings → Build, Execution, Deployment → Build Tools → Maven → Directory Settings 页面。
 *
 * <p>一个规则列表：每条规则带「全局」勾选 —— 勾选后存在 IDE 全局配置（本机所有项目生效），
 * 不勾则存在项目 {@code .idea/directoryMavenSettings.xml}（仅本项目、随 Git 共享）。
 * 运行时两层合并解析，同等精度时项目规则优先。</p>
 */
public final class DirMavenConfigurable implements Configurable {

    private final Project project;
    private final List<RuleEditor> editors = new ArrayList<>();

    private JPanel rootPanel;
    private JBCheckBox autoApplyCheckBox;
    private JPanel rulesPanel;
    private JBTextField testField;
    private JBLabel testResult;

    /** 编辑器里的完整快照（含 global 标记），用于 isModified 比较。 */
    private List<MavenRule> snapshot = new ArrayList<>();
    private boolean snapshotAutoApply = true;

    /** 折叠状态按规则内容记忆，rebuild 后不丢。 */
    private final java.util.Set<String> collapsedKeys = new java.util.HashSet<>();

    /**
     * 初始化 DirMavenConfigurable。
     * @param project 当前 IDE 项目
     */
    public DirMavenConfigurable(Project project) {
        this.project = project;
    }

    /**
     * 返回设置页面的显示名称。
     * @return 设置页面名称
     */
    @Override
    public String getDisplayName() {
        return "Directory Settings";
    }

    /**
     * 创建并返回设置页面组件。
     * @return 设置页面组件
     */
    @Override
    public @Nullable JComponent createComponent() {
        if (rootPanel == null) {
            rootPanel = buildPanel();
            reset();
        }
        return rootPanel;
    }

    /**
     * 构建设置页面的交互控件。
     * @return 完整设置页面
     */
    private JPanel buildPanel() {
        JPanel root = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        root.setBorder(JBUI.Borders.empty(8));

        autoApplyCheckBox = new JBCheckBox("项目打开 / Maven 导入完成后，自动切换 User settings file 并重新导入（git clone 新项目免手动操作）");
        JBLabel hint = new JBLabel("<html><body style='width:620px'>"
                + "规则按<b>目录前缀</b>匹配，越具体的目录（静态前缀越长）优先级越高，同精度时列表靠前的优先。"
                + "路径支持相对项目根目录、<code>${PROJECT_DIR$}</code>、<code>~</code>，以及 <code>*</code>、<code>?</code>、<code>**</code> 通配符。"
                + "<b>直接填目录前缀即可覆盖其下所有子项目</b>；注意 <code>*</code> 只匹配一层子目录，<code>**</code> 才匹配所有层级。"
                + "勾选规则的<b>全局</b>后，它对所有项目生效；不勾则只对本项目生效。"
                + "勾选<b>默认兜底</b>的规则会在所有目录都没命中时生效（可以留空目录），同一层只需一条。"
                + "</body></html>");
        hint.setForeground(JBColor.GRAY);

        JButton addButton = new JButton("添加规则", AllIcons.General.Add);
        addButton.addActionListener(e -> {
            editors.add(new RuleEditor(new MavenRule()));
            rebuildRuleList();
        });
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.add(addButton, BorderLayout.WEST);

        JPanel header = FormBuilder.createFormBuilder()
                .addComponent(autoApplyCheckBox)
                .addComponent(hint, 4)
                .addComponent(toolbar, 8)
                .getPanel();

        rulesPanel = new JPanel();
        rulesPanel.setLayout(new BoxLayout(rulesPanel, BoxLayout.Y_AXIS));
        rulesPanel.setBorder(JBUI.Borders.empty());

        JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(rulesPanel);
        scrollPane.setBorder(JBUI.Borders.empty());
        scrollPane.getViewport().setOpaque(false);

        testField = new JBTextField();
        testField.getEmptyText().setText("输入一个目录，实时查看会命中哪条规则，例如 /Users/me/work/demo/order-service");
        testField.getDocument().addDocumentListener(new DocumentAdapter() {
            /**
             * 在输入变化后刷新预览结果。
             * @param e 输入内容变化事件
             */
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                updateTestResult();
            }
        });

        testResult = new JBLabel(" ");
        testResult.setForeground(JBColor.GRAY);

        JButton testButton = new JButton("测试");
        testButton.addActionListener(e -> showTestDialog());

        JPanel testFieldWithButton = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        testFieldWithButton.add(testField, BorderLayout.CENTER);
        testFieldWithButton.add(testButton, BorderLayout.EAST);

        JPanel testPanel = FormBuilder.createFormBuilder()
                .addSeparator()
                .addLabeledComponent("目录匹配测试：", testFieldWithButton)
                .addComponent(testResult, 4)
                .getPanel();

        root.add(header, BorderLayout.NORTH);
        root.add(scrollPane, BorderLayout.CENTER);
        root.add(testPanel, BorderLayout.SOUTH);
        return root;
    }

    /**
     * 比较当前编辑状态与已保存配置。
     * @return 编辑值与已保存配置不同时为 true
     */
    @Override
    public boolean isModified() {
        if (autoApplyCheckBox == null) {
            return false;
        }
        return autoApplyCheckBox.isSelected() != snapshotAutoApply
                || !collectRules().equals(snapshot);
    }

    /**
     * 校验并保存用户编辑的规则。
     * @throws ConfigurationException 配置或输入无效时抛出
     */
    @Override
    public void apply() throws ConfigurationException {
        List<MavenRule> rules = collectRules();
        validateRules(rules);

        // 按规则的「全局」标记拆成两层分别持久化
        List<MavenRule> projectRules = new ArrayList<>();
        List<MavenRule> globalRules = new ArrayList<>();
        for (MavenRule rule : rules) {
            (rule.global ? globalRules : projectRules).add(rule.copy());
        }

        DirectoryMavenSettings settings = DirectoryMavenSettings.getInstance(project);
        settings.setAutoApply(autoApplyCheckBox.isSelected());
        settings.setRules(projectRules);

        DirectoryMavenGlobalSettings global = DirectoryMavenGlobalSettings.getInstance();
        if (global != null) {
            global.setRules(globalRules);
        }

        snapshotAutoApply = autoApplyCheckBox.isSelected();
        snapshot = copyOf(rules);

        StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
        if (statusBar != null) {
            statusBar.updateWidget(DirMavenStatusBarWidgetFactory.ID);
        }
    }

    /**
     * 校验规则中的目录和兜底配置。
     * @param rules 待保存或解析的规则列表
     * @throws ConfigurationException 配置或输入无效时抛出
     */
    private static void validateRules(List<MavenRule> rules) throws ConfigurationException {
        for (int i = 0; i < rules.size(); i++) {
            MavenRule rule = rules.get(i);
            // 默认兜底规则允许不填目录：它的作用就是接住所有没命中的目录
            if (rule.enabled && rule.directory.isBlank() && !rule.fallback) {
                throw new ConfigurationException("规则「" + rule.displayLabel(i) + "」缺少目录，填一个目录或把它删掉。");
            }
            for (String entry : rule.envVars.split("[;\\n；]")) {
                String trimmedEntry = entry.trim();
                if (!trimmedEntry.isEmpty() && trimmedEntry.indexOf('=') <= 0) {
                    throw new ConfigurationException("规则「" + rule.displayLabel(i)
                            + "」的环境变量格式不对：\"" + trimmedEntry + "\"（应为 NAME=value，分号分隔）");
                }
            }
        }
    }

    /**
     * 从已保存配置恢复编辑页面。
     */
    @Override
    public void reset() {
        if (autoApplyCheckBox == null) {
            return;
        }
        DirectoryMavenSettings settings = DirectoryMavenSettings.getInstance(project);
        DirectoryMavenGlobalSettings global = DirectoryMavenGlobalSettings.getInstance();

        snapshotAutoApply = settings.isAutoApply();
        autoApplyCheckBox.setSelected(snapshotAutoApply);

        // 项目规则在前、全局规则在后，合并成一个列表展示
        snapshot = new ArrayList<>();
        for (MavenRule rule : settings.getRules()) {
            MavenRule copy = rule.copy();
            copy.global = false;
            snapshot.add(copy);
        }
        if (global != null) {
            for (MavenRule rule : global.getRules()) {
                MavenRule copy = rule.copy();
                copy.global = true;
                snapshot.add(copy);
            }
        }

        editors.clear();
        for (MavenRule rule : snapshot) {
            editors.add(new RuleEditor(rule.copy()));
        }
        rebuildRuleList();

        String basePath = project.getBasePath();
        testField.setText(basePath == null ? "" : basePath);
        updateTestResult();
    }

    /**
     * 释放设置页面持有的界面资源。
     */
    @Override
    public void disposeUIResources() {
        editors.clear();
        rootPanel = null;
        autoApplyCheckBox = null;
        rulesPanel = null;
        testField = null;
        testResult = null;
    }

    /**
     * 按当前规则重建编辑卡片。
     */
    private void rebuildRuleList() {
        if (rulesPanel == null) {
            return;
        }
        rulesPanel.removeAll();
        if (editors.isEmpty()) {
            JBLabel empty = new JBLabel("还没有规则。点上方「添加规则」，为某个模块目录指定它自己的 settings.xml。");
            empty.setForeground(JBColor.GRAY);
            empty.setBorder(JBUI.Borders.empty(8));
            rulesPanel.add(empty);
        } else {
            for (int i = 0; i < editors.size(); i++) {
                RuleEditor editor = editors.get(i);
                editor.setIndex(i + 1);
                rulesPanel.add(editor.getPanel());
                rulesPanel.add(Box.createVerticalStrut(JBUI.scale(8)));
            }
        }
        rulesPanel.revalidate();
        rulesPanel.repaint();
        updateTestResult();
    }

    /**
     * 收集各编辑卡片的规则值。
     * @return 当前编辑卡片中的规则列表
     */
    private List<MavenRule> collectRules() {
        List<MavenRule> rules = new ArrayList<>();
        for (RuleEditor editor : editors) {
            rules.add(editor.toRule());
        }
        return rules;
    }

    /**
     * 按当前 UI 的规则做两层解析（global 勾选的进全局层，其余进项目层）。
     * @param directory 待匹配的目标目录
     * @return 合并后的匹配结果；未命中时为 null
     */
    private @Nullable ResolvedMavenConfig resolveMerged(String directory) {
        List<MavenRule> projectRules = new ArrayList<>();
        List<MavenRule> globalRules = new ArrayList<>();
        for (MavenRule rule : collectRules()) {
            (rule.global ? globalRules : projectRules).add(rule);
        }
        return MavenConfigResolver.resolve(projectRules, globalRules, project.getBasePath(), directory);
    }

    /**
     * 刷新目录匹配的即时预览。
     */
    private void updateTestResult() {
        if (testResult == null || testField == null) {
            return;
        }
        String directory = testField.getText().trim();
        if (directory.isEmpty()) {
            testResult.setText("输入目录后，这里会显示命中的规则");
            testResult.setForeground(JBColor.GRAY);
            return;
        }
        ResolvedMavenConfig resolved = resolveMerged(directory);
        if (resolved == null) {
            String suggestion = MavenConfigResolver.nearMissSuggestion(collectRules(),
                    project == null ? null : project.getBasePath(), directory);
            testResult.setText(suggestion == null
                    ? "未命中任何规则，运行 Maven 时不会额外注入参数"
                    : "<html><b>提示：</b>" + escapeHtml(suggestion) + "</html>");
            testResult.setForeground(suggestion == null ? JBColor.GRAY : JBColor.ORANGE);
            return;
        }
        StringBuilder text = new StringBuilder(resolved.isFallback()
                ? "目录未命中任何规则，走默认兜底「" + resolved.label() + "」→ "
                : "命中规则「" + resolved.label() + "」→ ")
                .append(resolved.describe());
        boolean missingSettings = !resolved.getSettingsPath().isEmpty()
                && !new File(resolved.getSettingsPath()).isFile();
        if (missingSettings) {
            text.append("　（警告：settings.xml 不存在）");
            testResult.setForeground(JBColor.ORANGE);
        } else {
            testResult.setForeground(JBColor.GREEN.darker());
        }
        testResult.setText(text.toString());
    }

    /**
     * 「测试」按钮：弹窗展示完整解析结果，包括 settings.xml 是否存在、参数与环境变量明细。
     */
    private void showTestDialog() {
        String directory = testField.getText().trim();
        if (directory.isEmpty()) {
            new TestResultDialog(project, "Directory Maven Settings",
                    new JBLabel("请先在上面输入要测试的目录。"), AllIcons.General.InformationDialog).show();
            return;
        }
        ResolvedMavenConfig resolved = resolveMerged(directory);

        if (resolved == null) {
            String suggestion = MavenConfigResolver.nearMissSuggestion(collectRules(),
                    project == null ? null : project.getBasePath(), directory);
            JPanel noMatch = buildTestResultPanel(directory, null, false, suggestion);
            new TestResultDialog(project, "测试结果：未命中", noMatch,
                    AllIcons.General.InformationDialog).show();
            return;
        }

        boolean missingSettings = !resolved.getSettingsPath().isEmpty()
                && !new File(resolved.getSettingsPath()).isFile();
        JPanel panel = buildTestResultPanel(directory, resolved, missingSettings, null);
        String title = missingSettings ? "测试结果：settings.xml 不存在"
                : resolved.isFallback() ? "测试结果：默认兜底" : "测试结果：命中";
        new TestResultDialog(project, title, panel,
                missingSettings ? AllIcons.General.WarningDialog : AllIcons.General.InformationDialog).show();
    }

    /**
     * 测试结果对话框内容：分组排版，命令行可一键复制。{@code suggestion} 仅在未命中时给出修改建议。
     * @param directory 待匹配的目标目录
     * @param resolved 已解析的 Maven 配置
     * @param missingSettings settings.xml 文件是否缺失
     * @param suggestion 规则未命中时的修改建议
     * @return 用于展示解析结果的面板
     */
    private JPanel buildTestResultPanel(String directory, @Nullable ResolvedMavenConfig resolved,
                                        boolean missingSettings, @Nullable String suggestion) {
        JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(12)));
        panel.setPreferredSize(JBUI.size(620, -1));

        if (resolved == null) {
            JPanel noMatch = new JPanel(new java.awt.GridLayout(suggestion == null ? 1 : 2, 0, 0, JBUI.scale(10)));
            noMatch.add(new JBLabel("<html>目录 <b>" + escapeHtml(directory) + "</b> 未命中任何规则，"
                    + "运行 Maven 时不会注入额外配置。</html>"));
            if (suggestion != null) {
                JBLabel hint = new JBLabel("<html><font color='#E8A33D'><b>💡 " + escapeHtml(suggestion) + "</b></font></html>");
                hint.setVerticalAlignment(javax.swing.SwingConstants.TOP);
                noMatch.add(hint);
            }
            panel.add(noMatch, BorderLayout.CENTER);
            return panel;
        }

        JBLabel headline = new JBLabel(resolved.isFallback()
                ? "目录未命中任何规则，使用默认兜底「" + resolved.label() + "」"
                : "命中规则「" + resolved.label() + "」");
        headline.setFont(headline.getFont().deriveFont(java.awt.Font.BOLD, headline.getFont().getSize2D() + 2f));

        JBLabel settingsLabel;
        if (resolved.getSettingsPath().isEmpty()) {
            settingsLabel = new JBLabel("未指定（只注入 profile / 参数 / 环境变量）");
            settingsLabel.setForeground(JBColor.GRAY);
        } else {
            settingsLabel = new JBLabel("<html>" + escapeHtml(resolved.getSettingsPath())
                    + (missingSettings
                    ? "　<b><font color='#E55765'>✗ 文件不存在</font></b>"
                    : "　<b><font color='#5FA855'>✓ 存在</font></b>")
                    + "</html>");
        }

        JBTextArea commandArea = new JBTextArea(resolved.getArguments().isEmpty()
                ? "（无额外参数）" : String.join(" ", resolved.getArguments()), 3, 58);
        commandArea.setLineWrap(true);
        commandArea.setWrapStyleWord(true);
        commandArea.setEditable(false);
        commandArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        JButton copyButton = new JButton("复制命令行", AllIcons.Actions.Copy);
        copyButton.addActionListener(e -> {
            CopyPasteManager.getInstance().setContents(
                    new java.awt.datatransfer.StringSelection(String.join(" ", resolved.getArguments())));
        });

        FormBuilder builder = FormBuilder.createFormBuilder()
                .addLabeledComponent("测试目录：", new JBLabel(directory))
                .addLabeledComponent("settings.xml：", settingsLabel)
                .addLabeledComponent("激活 Profile：", new JBLabel(resolved.getProfiles().isEmpty()
                        ? "（无）" : resolved.getProfiles()))
                .addLabeledComponent("环境变量：", new JBLabel(resolved.getEnvVars().isEmpty()
                        ? "（无）" : describeEnv(resolved.getEnvVars())))
                .addLabeledComponent("追加到 mvn 命令行：", commandArea)
                .addComponent(copyButton, 4);

        panel.add(headline, BorderLayout.NORTH);
        panel.add(builder.getPanel(), BorderLayout.CENTER);
        return panel;
    }

    /**
     * 格式化环境变量的展示文本。
     * @param envVars 环境变量配置
     * @return 经过格式化的环境变量文本
     */
    private static String describeEnv(java.util.Map<String, String> envVars) {
        StringBuilder text = new StringBuilder("<html>");
        boolean first = true;
        for (java.util.Map.Entry<String, String> entry : envVars.entrySet()) {
            if (!first) {
                text.append("<br/>");
            }
            first = false;
            text.append("<code>").append(escapeHtml(entry.getKey())).append("</code> = ")
                    .append(escapeHtml(entry.getValue()));
        }
        return text.append("</html>").toString();
    }

    /**
     * 转义展示文本中的 HTML 特殊字符。
     * @param text 待解析或展示的文本
     * @return 可安全展示的 HTML 文本
     */
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 轻量结果对话框：只有 OK，带图标、自适应内容高度。 */
    private static final class TestResultDialog extends DialogWrapper {
        private final JComponent panel;
        private final javax.swing.Icon icon;

        /**
         * 创建只展示解析结果的非模态对话框。
         * @param project 当前 IDE 项目
         * @param title 对话框标题
         * @param panel 解析结果面板
         * @param icon 结果图标
         */
        TestResultDialog(Project project, String title, JComponent panel, javax.swing.Icon icon) {
            super(project, false);
            this.panel = panel;
            this.icon = icon;
            setTitle(title);
            init();
            setModal(false);
        }

        /**
         * 提供对话框中心内容。
         * @return 对话框中心面板
         */
        @Override
        protected JComponent createCenterPanel() {
            JPanel wrapper = new JPanel(new BorderLayout(16, 0));
            wrapper.add(new JBLabel(icon), BorderLayout.WEST);
            wrapper.add(panel, BorderLayout.CENTER);
            return wrapper;
        }

        /**
         * 提供结果对话框的操作按钮。
         * @return 对话框操作按钮
         */
        @Override
        protected javax.swing.Action @NotNull [] createActions() {
            return new javax.swing.Action[]{getOKAction()};
        }
    }

    /**
     * 复制规则列表以保存编辑快照。
     * @param rules 待保存或解析的规则列表
     * @return 与原列表独立的规则副本
     */
    private static List<MavenRule> copyOf(List<MavenRule> rules) {
        List<MavenRule> copy = new ArrayList<>(rules.size());
        for (MavenRule rule : rules) {
            copy.add(rule.copy());
        }
        return copy;
    }

    /** 单条规则的编辑卡片。 */
    private final class RuleEditor {

        /** 最大高度锁定为内容实际高度：避免 BoxLayout 在空间富余时把卡片垂直拉伸出一大块空白。 */
        private final JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(6))) {
            /**
             * 限制编辑卡片的最大高度。
             * @return 受内容高度限制的最大尺寸
             */
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        private JPanel formPanel;
        private final JBTextField nameField = new JBTextField();
        private final JBCheckBox global = new JBCheckBox("全局");
        private final JBCheckBox fallback = new JBCheckBox("默认兜底");
        private final JBCheckBox enabled = new JBCheckBox("启用");
        private final JPanel directoriesPanel = new JPanel();
        private final List<TextFieldWithBrowseButton> directoryFields = new ArrayList<>();
        private final TextFieldWithBrowseButton settingsFile =
                new TextFieldWithBrowseButton(new JBTextField());
        private final JBTextField profiles = new JBTextField();
        private final JBTextField extraArgs = new JBTextField();
        private final JBTextField envVars = new JBTextField();

        /** 折叠状态：折叠后只显示标题行 + 摘要。 */
        private boolean expanded = true;
        private final JButton collapseToggle = new JButton();
        private final JBLabel summaryLabel = new JBLabel();
        private JComponent bodyComponent;

        /**
         * 根据已有规则填充编辑卡片，保留用户可见的配置值。
         * @param rule 待编辑的规则
         */
        RuleEditor(MavenRule rule) {
            nameField.setText(rule.name);
            nameField.setFont(nameField.getFont().deriveFont(java.awt.Font.BOLD));
            nameField.getEmptyText().setText("规则名称（留空显示为「规则 N」）");
            settingsFile.setText(rule.settingsFile);
            profiles.setText(rule.profiles);
            extraArgs.setText(rule.extraArgs);
            envVars.setText(rule.envVars);
            global.setSelected(rule.global);
            global.setToolTipText("勾选后这条规则保存在 IDE 全局配置里，对所有项目生效；不勾则只对当前项目生效");
            fallback.setSelected(rule.fallback);
            fallback.setToolTipText("当所有规则的目录都没命中时，用这条规则兜底（可以留空目录）。同一层只能有一条默认规则");
            enabled.setSelected(rule.enabled);

            // 每行一个目录，浏览按钮直接覆盖本行；多路径 = 多行
            List<String> paths = rule.directories();
            if (paths.isEmpty()) {
                addDirectoryRow("");
            } else {
                for (String path : paths) {
                    addDirectoryRow(path);
                }
            }
            settingsFile.addBrowseFolderListener(project, FileChooserDescriptorFactory.singleFile());

            profiles.getEmptyText().setText("例如 dev 或 dev,!prod（可留空）");
            extraArgs.getEmptyText().setText("例如 -Dmaven.repo.local=/data/repo -U（可留空）");
            envVars.getEmptyText().setText("例如 MAVEN_OPTS=-Xmx2g;ENV_NAME=prod（分号分隔，可留空）");

            DocumentAdapter refresh = new DocumentAdapter() {
                /**
                 * 在输入变化后刷新预览结果。
                 * @param e 输入内容变化事件
                 */
                @Override
                protected void textChanged(@NotNull DocumentEvent e) {
                    updateTestResult();
                }
            };
            nameField.getDocument().addDocumentListener(refresh);
            settingsFile.getTextField().getDocument().addDocumentListener(refresh);
            profiles.getDocument().addDocumentListener(refresh);
            extraArgs.getDocument().addDocumentListener(refresh);
            envVars.getDocument().addDocumentListener(refresh);
            enabled.addActionListener(e -> {
                updateEnabledState();
                updateTestResult();
            });
            // 默认兜底是「唯一」的：勾一条自动取消其它条
            fallback.addActionListener(e -> {
                if (fallback.isSelected()) {
                    for (RuleEditor other : editors) {
                        if (other != this) {
                            other.fallback.setSelected(false);
                        }
                    }
                }
                updateSummary();
                updateTestResult();
            });

            JButton remove = new JButton("删除", AllIcons.General.Remove);
            remove.addActionListener(e -> {
                editors.remove(this);
                rebuildRuleList();
            });

            JPanel headerRight = new JPanel();
            headerRight.setLayout(new BoxLayout(headerRight, BoxLayout.X_AXIS));
            headerRight.add(global);
            headerRight.add(Box.createHorizontalStrut(JBUI.scale(8)));
            headerRight.add(fallback);
            headerRight.add(Box.createHorizontalStrut(JBUI.scale(8)));
            headerRight.add(enabled);
            headerRight.add(Box.createHorizontalStrut(JBUI.scale(8)));
            headerRight.add(remove);
            headerRight.add(Box.createHorizontalStrut(JBUI.scale(4)));

            JPanel header = new JPanel(new BorderLayout(8, 0));
            collapseToggle.setToolTipText("折叠 / 展开这条规则");
            collapseToggle.setBorder(BorderFactory.createEmptyBorder());
            collapseToggle.setContentAreaFilled(false);
            collapseToggle.setFocusable(false);
            collapseToggle.addActionListener(e -> setExpanded(!expanded, true));
            header.add(collapseToggle, BorderLayout.WEST);
            header.add(nameField, BorderLayout.CENTER);
            header.add(headerRight, BorderLayout.EAST);

            JPanel directoryBlock = new JPanel(new BorderLayout(0, JBUI.scale(4)));
            directoryBlock.add(directoriesPanel, BorderLayout.CENTER);
            JButton addDirectory = new JButton("添加目录", AllIcons.General.Add);
            addDirectory.putClientProperty("JButton.buttonType", "tool");
            addDirectory.addActionListener(e -> {
                addDirectoryRow("");
                updateEnabledState();
            });
            JPanel addDirectoryRow = new JPanel(new BorderLayout());
            addDirectoryRow.add(addDirectory, BorderLayout.WEST);
            directoryBlock.add(addDirectoryRow, BorderLayout.SOUTH);

            JPanel form = FormBuilder.createFormBuilder()
                    .addLabeledComponent("目录：", directoryBlock)
                    .addLabeledComponent("settings.xml：", settingsFile)
                    .addLabeledComponent("激活 Profile：", profiles)
                    .addLabeledComponent("额外参数：", extraArgs)
                    .addLabeledComponent("环境变量：", envVars)
                    .getPanel();
            formPanel = form;
            bodyComponent = form;
            updateEnabledState();

            // 恢复上次的折叠状态（按规则内容记忆）
            if (collapsedKeys.contains(collapseKey())) {
                applyCollapsedView();
            } else {
                applyExpandedView();
            }

            panel.add(header, BorderLayout.NORTH);
            panel.setBorder(BorderFactory.createCompoundBorder(
                    JBUI.Borders.customLine(JBColor.border(), 1),
                    JBUI.Borders.empty(8)));
        }

        /**
         * 折叠键：按名称 + 目录内容记忆折叠状态，重新打开设置页也能恢复。
         * @return 规则折叠状态的键
         */
        private String collapseKey() {
            StringBuilder dirs = new StringBuilder();
            for (TextFieldWithBrowseButton field : directoryFields) {
                dirs.append(field.getText().trim()).append(',');
            }
            return nameField.getText().trim() + "|" + dirs;
        }

        /**
         * 更新摘要文案：目录最多展示 2 个（多了用「共 N 个目录」概括）+ settings.xml 文件名，保证一行放得下。
         */
        private void updateSummary() {
            StringBuilder summary = new StringBuilder();
            List<String> dirs = new ArrayList<>();
            for (TextFieldWithBrowseButton field : directoryFields) {
                String path = field.getText().trim();
                if (!path.isEmpty()) {
                    dirs.add(path);
                }
            }
            if (fallback.isSelected()) {
                summary.append("【默认兜底】");
            }
            if (dirs.isEmpty()) {
                summary.append(fallback.isSelected() ? "未填目录，所有未命中的目录都走它" : "（未填目录）");
            } else if (dirs.size() <= 2) {
                summary.append(String.join("  ,  ", dirs));
            } else {
                summary.append(dirs.get(0)).append("  ,  ").append(dirs.get(1))
                        .append("　… 共 ").append(dirs.size()).append(" 个目录");
            }
            String settings = settingsFile.getText().trim();
            if (!settings.isEmpty()) {
                int slash = Math.max(settings.lastIndexOf('/'), settings.lastIndexOf('\\'));
                summary.append("　→　").append(slash >= 0 ? settings.substring(slash + 1) : settings);
            }
            summaryLabel.setText(summary.toString());
            summaryLabel.setForeground(JBColor.GRAY);
        }

        /**
         * 切换卡片展开状态并按需保存。
         * @param value 待处理的值
         * @param persist 是否保存折叠状态
         */
        private void setExpanded(boolean value, boolean persist) {
            expanded = value;
            if (persist) {
                if (expanded) {
                    collapsedKeys.remove(collapseKey());
                } else {
                    collapsedKeys.add(collapseKey());
                }
            }
            if (expanded) {
                applyExpandedView();
            } else {
                applyCollapsedView();
            }
            panel.revalidate();
            panel.repaint();
        }

        /**
         * 显示展开后的规则编辑控件。
         */
        private void applyExpandedView() {
            collapseToggle.setIcon(AllIcons.Actions.ArrowCollapse);
            panel.remove(summaryLabel);
            if (bodyComponent != null) {
                panel.add(bodyComponent, BorderLayout.CENTER);
            }
        }

        /**
         * 显示折叠后的规则摘要。
         */
        private void applyCollapsedView() {
            collapseToggle.setIcon(AllIcons.Actions.ArrowExpand);
            updateSummary();
            if (bodyComponent != null) {
                panel.remove(bodyComponent);
            }
            panel.add(summaryLabel, BorderLayout.CENTER);
        }

        JPanel getPanel() {
            return panel;
        }

        /** 加一行目录输入（带浏览与移除按钮）。首行提示支持的表达式语法。 */
        void addDirectoryRow(String path) {
            TextFieldWithBrowseButton field =
                    new TextFieldWithBrowseButton(new JBTextField(path));
            if (directoryFields.isEmpty() && path.isEmpty()) {
                ((JBTextField) field.getTextField()).getEmptyText()
                        .setText("填目录前缀即可覆盖其下所有子项目；* 只匹配一层，** 匹配所有层级");
            }
            field.addBrowseFolderListener(project, FileChooserDescriptorFactory.singleDir());
            field.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
                /**
                 * 在输入变化后刷新预览结果。
                 * @param e 输入内容变化事件
                 */
                @Override
                protected void textChanged(@NotNull DocumentEvent e) {
                    updateTestResult();
                }
            });

            JButton removeRow = new JButton(AllIcons.General.Remove);
            removeRow.setToolTipText("移除这一行目录");
            removeRow.addActionListener(e -> {
                directoryFields.remove(field);
                directoriesPanel.remove(field.getParent());
                if (directoryFields.isEmpty()) {
                    addDirectoryRow("");
                }
                directoriesPanel.revalidate();
                directoriesPanel.repaint();
                updateTestResult();
            });

            JPanel row = new JPanel(new BorderLayout(JBUI.scale(4), 0));
            row.add(field, BorderLayout.CENTER);
            row.add(removeRow, BorderLayout.EAST);
            directoriesPanel.add(row);
            directoriesPanel.setLayout(new javax.swing.BoxLayout(directoriesPanel, javax.swing.BoxLayout.Y_AXIS));
            directoryFields.add(field);
        }

        /** 关掉「启用」后，把整张卡片的输入项置灰，视觉上明确这条规则不会生效。 */
        void updateEnabledState() {
            boolean on = enabled.isSelected();
            nameField.setEnabled(on);
            global.setEnabled(on);
            fallback.setEnabled(on);
            if (formPanel != null) {
                setEnabledRecursive(formPanel, on);
            }
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 1, 1, 1, on ? JBColor.border() : JBColor.GRAY),
                    JBUI.Borders.empty(8)));
        }

        /**
         * 递归设置规则表单控件的可用状态。
         * @param component 待更新的界面组件
         * @param enabled 是否启用
         */
        private static void setEnabledRecursive(java.awt.Component component, boolean enabled) {
            component.setEnabled(enabled);
            if (component instanceof java.awt.Container container) {
                for (java.awt.Component child : container.getComponents()) {
                    setEnabledRecursive(child, enabled);
                }
            }
        }

        void setIndex(int index) {
            nameField.getEmptyText().setText("规则 " + index + "（可点击输入名称）");
        }

        MavenRule toRule() {
            MavenRule rule = new MavenRule();
            rule.name = nameField.getText().trim();
            StringBuilder directoryText = new StringBuilder();
            for (TextFieldWithBrowseButton field : directoryFields) {
                String path = field.getText().trim();
                if (!path.isEmpty()) {
                    if (directoryText.length() > 0) {
                        directoryText.append(", ");
                    }
                    directoryText.append(path);
                }
            }
            rule.directory = directoryText.toString();
            rule.settingsFile = settingsFile.getText().trim();
            rule.profiles = profiles.getText().trim();
            rule.extraArgs = extraArgs.getText().trim();
            rule.envVars = envVars.getText().trim();
            rule.enabled = enabled.isSelected();
            rule.global = global.isSelected();
            rule.fallback = fallback.isSelected();
            return rule;
        }
    }
}
