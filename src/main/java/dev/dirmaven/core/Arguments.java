package dev.dirmaven.core;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author lt_xiaoyi
 * @Description 解析带引号和转义字符的 Maven 命令行参数
 * @Version 1.0
 * <p>
 * 极简命令行分词器：支持单引号、双引号，够处理 {@code -Dfoo="a b"} 这类参数，
 * 又不需要把 IntelliJ 平台的 {@code ParametersListUtil} 带进核心逻辑。
 */
public final class Arguments {

    /**
     * 初始化 Arguments。
     */
    private Arguments() {
    }

    /**
     * 将命令行文本拆分为参数列表。
     *
     * @param text 待解析或展示的文本
     * @return 解析出的命令行参数列表
     */
    public static List<String> parse(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean tokenStarted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                tokenStarted = true;
            } else if (Character.isWhitespace(c)) {
                if (current.length() > 0 || tokenStarted) {
                    result.add(current.toString());
                    current.setLength(0);
                    tokenStarted = false;
                }
            } else {
                current.append(c);
                tokenStarted = true;
            }
        }
        if (current.length() > 0 || tokenStarted) {
            result.add(current.toString());
        }
        return result;
    }
}
