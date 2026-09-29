package com.example.item;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.MultiValueMap;

/**
 * 문항 검색 조건. 레거시 {@code legacy/item-bank-php/search.php} 의 입력 해석을 그대로 옮긴다.
 * 레거시 동작이 이상해 보여도 고치지 않는다(docs/item-bank/BUSINESS-RULES.md BR-05 ~ BR-25).
 *
 * @param keyword  제목 · 지문 부분 일치 키워드. 없으면 null
 * @param unitCode 단원 코드(대소문자 · 형식 검증 없이 입력값 그대로). 없으면 null
 * @param level    난이도. null 이면 기본 조건 {@code level < DEFAULT_LEVEL_UPPER_BOUND}
 * @param tag      태그 이름(정확히 일치). 없으면 null
 * @param sort     정렬
 * @param page     페이지 번호(1 ~ {@link #MAX_PAGE})
 */
public record ItemSearchCondition(
    String keyword,
    String unitCode,
    Integer level,
    String tag,
    Sort sort,
    int page) {

    /** 한 페이지 건수(search.php:349). */
    public static final int PAGE_SIZE = 20;
    /** 페이지 번호 상한(search.php:345). */
    public static final int MAX_PAGE = 999;
    /** 키워드 최대 글자 수(search.php:86). */
    public static final int MAX_KEYWORD_LENGTH = 100;
    /** 태그 이름 최대 글자 수(search.php:240). */
    public static final int MAX_TAG_LENGTH = 50;
    /** 난이도가 비었을 때 쓰는 상한(미포함). 주석은 "1~5 모두 포함"이지만 코드는 5 를 뺀다(search.php:213-215). */
    public static final int DEFAULT_LEVEL_UPPER_BOUND = 5;

    /** 기본 정렬: 난이도 내림차순, 같은 난이도면 id 오름차순(search.php:317-326). */
    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("level"), Sort.Order.asc("id"));

    private static final Pattern EXACT_LEVEL = Pattern.compile("[1-5]");
    /** preg_match('/^[0-9]+$/') — PHP 의 $ 는 끝의 줄바꿈 하나 앞에서도 맞는다(search.php:337). */
    private static final Pattern PAGE_DIGITS = Pattern.compile("([0-9]+)\n?");
    /** PHP 숫자 문자열의 앞부분(정수 · 소수 · 지수). */
    private static final Pattern LEADING_NUMBER =
        Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    /** PHP trim() 의 기본 제거 문자. */
    private static final String PHP_TRIM_CHARS = " \t\n\r\0\u000B";

    /** GET 파라미터 → 검색 조건. 파라미터 이름은 레거시와 같다: q, unit, level, tag, sort, dir, page. */
    public static ItemSearchCondition from(MultiValueMap<String, String> params) {
        String keyword = truncate(phpTrim(param(params, "q")), MAX_KEYWORD_LENGTH);
        String unitCode = phpTrim(param(params, "unit"));
        String levelRaw = phpTrim(param(params, "level"));
        String tag = truncate(phpTrim(param(params, "tag")), MAX_TAG_LENGTH);
        String sortKey = asciiLower(phpTrim(param(params, "sort")));
        String dir = asciiLower(phpTrim(param(params, "dir")));
        String pageRaw = param(params, "page");

        return new ItemSearchCondition(
            emptyToNull(keyword),
            emptyToNull(unitCode),
            parseLevel(levelRaw),
            emptyToNull(tag),
            toSort(sortKey, dir),
            parsePage(pageRaw));
    }

    /** LIKE 패턴. % · _ 는 이스케이프하지 않는다 — 와일드카드로 동작한다(BR-07). */
    public String keywordPattern() {
        return keyword == null ? null : "%" + keyword + "%";
    }

    public Pageable pageable() {
        return PageRequest.of(page - 1, PAGE_SIZE, sort);
    }

    /**
     * 같은 이름이 여럿이면 PHP 처럼 고른다(BR-05): {@code name[]=a&name[]=b} 는 첫 값,
     * {@code name=a&name=b} 는 마지막 값. 없으면 빈 문자열.
     */
    static String param(MultiValueMap<String, String> params, String name) {
        List<String> arrayValues = params.get(name + "[]");
        if (arrayValues != null && !arrayValues.isEmpty()) {
            return nullToEmpty(arrayValues.get(0));
        }
        List<String> values = params.get(name);
        if (values != null && !values.isEmpty()) {
            return nullToEmpty(values.get(values.size() - 1));
        }
        return "";
    }

    /** 빈값 → null(기본 조건), 1~5 한 자리 → 그 값, 그 밖 → PHP (int) 변환값(BR-11 ~ BR-13). */
    static Integer parseLevel(String level) {
        if (level.isEmpty()) {
            return null;
        }
        if (EXACT_LEVEL.matcher(level).matches()) {
            return Integer.valueOf(level);
        }
        return clampToInt(phpIntval(level));
    }

    /** 숫자만이면 그 값, 아니면 1. 1 미만은 1, 999 초과는 999. 앞뒤 공백을 지우지 않는다(BR-24, BR-25). */
    static int parsePage(String pageRaw) {
        Matcher m = PAGE_DIGITS.matcher(pageRaw);
        if (!m.matches()) {
            return 1;
        }
        BigInteger page = new BigInteger(m.group(1));
        if (page.compareTo(BigInteger.valueOf(MAX_PAGE)) > 0) {
            return MAX_PAGE;
        }
        return Math.max(page.intValue(), 1);
    }

    /** 정렬 기준 · 방향 → Sort(BR-16 ~ BR-21). id 이외 기준의 동률은 방향과 무관하게 id 오름차순. */
    static Sort toSort(String sortKey, String dir) {
        boolean desc = "desc".equals(dir);
        boolean asc = "asc".equals(dir);
        return switch (sortKey) {
            case "id" -> Sort.by(desc ? Sort.Order.desc("id") : Sort.Order.asc("id"));
            case "title" -> Sort.by(
                desc ? Sort.Order.desc("title") : Sort.Order.asc("title"),
                Sort.Order.asc("id"));
            case "unit" -> Sort.by(
                desc ? Sort.Order.desc("unit.code") : Sort.Order.asc("unit.code"),
                Sort.Order.desc("level"),
                Sort.Order.asc("id"));
            case "level" -> Sort.by(
                asc ? Sort.Order.asc("level") : Sort.Order.desc("level"),
                Sort.Order.asc("id"));
            case "created" -> Sort.by(
                asc ? Sort.Order.asc("createdAt") : Sort.Order.desc("createdAt"),
                Sort.Order.asc("id"));
            default -> DEFAULT_SORT;
        };
    }

    /**
     * PHP 7.4 의 {@code (int)$string}. 앞부분의 숫자만 읽고 나머지는 버린다.
     * 예: "3abc" → 3, "05" → 5, "2.9" → 2, "1e2" → 100, "abc" → 0.
     * long 범위를 넘으면 부호에 따라 long 최댓값 · 최솟값.
     */
    static long phpIntval(String value) {
        Matcher m = LEADING_NUMBER.matcher(value);
        if (!m.lookingAt()) {
            return 0L;
        }
        String number = m.group();
        BigInteger integral;
        if (number.contains(".") || number.contains("e") || number.contains("E")) {
            integral = new BigDecimal(number).toBigInteger();
        } else {
            integral = new BigInteger(number.startsWith("+") ? number.substring(1) : number);
        }
        if (integral.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            return Long.MAX_VALUE;
        }
        if (integral.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) {
            return Long.MIN_VALUE;
        }
        return integral.longValue();
    }

    /** PHP trim() — 공백 · 탭 · 줄바꿈 · NUL · 수직 탭만 지운다. */
    static String phpTrim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && PHP_TRIM_CHARS.indexOf(value.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && PHP_TRIM_CHARS.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(start, end);
    }

    /** mb_substr(value, 0, max, 'UTF-8') — 글자(코드포인트) 기준으로 자른다. */
    static String truncate(String value, int max) {
        if (value.codePointCount(0, value.length()) <= max) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, max));
    }

    /** strtolower — ASCII 대문자만 바꾼다. */
    private static String asciiLower(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            sb.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return sb.toString();
    }

    /** 난이도 컬럼과 비교할 값. int 범위를 넘으면 어느 문항과도 맞지 않는 끝값으로 둔다. */
    private static int clampToInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
