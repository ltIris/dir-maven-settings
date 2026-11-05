package dev.dirmaven.core;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * @Author lt_xiaoyi
 * @Description 匹配普通目录与 glob 目录表达式
 * @Version 1.0
 *
 * 纯 Java 的目录匹配工具，不依赖 IntelliJ 平台，方便单测和脱离 IDE 复用。
 *
 * <p>匹配规则：</p>
 * <ul>
 *   <li>不含通配符时按「路径前缀 + 层级边界」匹配，{@code /work/demo} 命中 {@code /work/demo} 与 {@code /work/demo/order-service}；</li>
 *   <li>含 {@code *} / {@code ?} / {@code **} 时转换成正则做整串匹配；</li>
 *   <li>Windows 盘符路径大小写不敏感。</li>
 * </ul>
 */
public final class DirectoryPattern {

    /**
     * 初始化 DirectoryPattern。
     */
    private DirectoryPattern() {
    }

    /**
     * 统一成 {@code /} 分隔、去掉结尾斜杠的形式。
     * @param path 待处理的路径
     * @return 统一格式的目录路径
     */
    public static String normalize(String path) {
        if (path == null) {
            return "";
        }
        String result = path.trim().replace('\\', '/');
        while (result.length() > 1 && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 判断目录表达式是否含通配符。
     * @param pattern 目录匹配表达式
     * @return 存在通配符时为 true
     */
    public static boolean hasWildcard(String pattern) {
        return pattern != null && (pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0);
    }

    /**
     * {@code directory} 是否落在 {@code pattern} 描述的目录（含其子目录）内。
     * @param pattern 目录匹配表达式
     * @param directory 待匹配的目标目录
     * @return 目录命中规则时为 true
     */
    public static boolean matches(String pattern, String directory) {
        String p = normalize(pattern);
        String d = normalize(directory);
        if (p.isEmpty() || d.isEmpty()) {
            return false;
        }
        if (hasWildcard(p)) {
            return compile(p).matcher(d).matches();
        }
        if (isCaseInsensitive(p) || isCaseInsensitive(d)) {
            return d.equalsIgnoreCase(p) || d.toLowerCase(Locale.ROOT).startsWith(p.toLowerCase(Locale.ROOT) + "/");
        }
        return d.equals(p) || d.startsWith(p + "/");
    }

    /**
     * 匹配的「具体程度」：通配符之前的静态前缀越长越具体，用于在多条规则同时命中时取最精确的那条。
     */
    public static int specificity(String pattern) {
        String p = normalize(pattern);
        int index = 0;
        while (index < p.length() && p.charAt(index) != '*' && p.charAt(index) != '?') {
            index++;
        }
        return index;
    }

    /**
     * 把 glob 编译成正则（结果做了缓存，规则数量很少，缓存也就很小）。
     * @param glob 目录通配表达式
     * @return 已缓存的目录正则表达式
     */
    public static Pattern compile(String glob) {
        String key = normalize(glob);
        return CACHE.computeIfAbsent(key, DirectoryPattern::globToRegex);
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Pattern> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 将 glob 中的单层与多层通配符转为目录正则，避免把路径分隔符误匹配成普通字符。
     * @param glob 目录通配表达式
     * @return 等价的正则表达式
     */
    static Pattern globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*':
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        i++;
                        if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') {
                            i++;
                            sb.append("(?:.*/)?");
                        } else {
                            sb.append(".*");
                        }
                    } else {
                        sb.append("[^/]*");
                    }
                    break;
                case '?':
                    sb.append("[^/]");
                    break;
                case '.':
                case '(':
                case ')':
                case '[':
                case ']':
                case '{':
                case '}':
                case '+':
                case '^':
                case '$':
                case '|':
                case '\\':
                    sb.append('\\').append(c);
                    break;
                default:
                    sb.append(c);
            }
        }
        sb.append('$');
        return Pattern.compile(sb.toString(), isCaseInsensitive(glob) ? Pattern.CASE_INSENSITIVE : 0);
    }

    /**
     * 判断路径是否使用不区分大小写的匹配规则。
     * @param path 待处理的路径
     * @return 需要忽略大小写时为 true
     */
    private static boolean isCaseInsensitive(String path) {
        return path.length() > 1 && path.charAt(1) == ':' && Character.isLetter(path.charAt(0));
    }
}
