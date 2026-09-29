package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** 레거시 search.php 의 입력 해석을 그대로 옮겼는지 — 서비스 · DB 없이 돈다. */
class ItemSearchConditionTest {

    private static MultiValueMap<String, String> params(String... keyValues) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.add(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("파라미터가 없으면 조건 없음 · 기본 정렬 · 1페이지")
    void emptyParamsMeanNoConditions() {
        ItemSearchCondition condition = ItemSearchCondition.from(params());

        assertThat(condition.keyword()).isNull();
        assertThat(condition.unitCode()).isNull();
        assertThat(condition.level()).isNull();
        assertThat(condition.tag()).isNull();
        assertThat(condition.sort()).isEqualTo(ItemSearchCondition.DEFAULT_SORT);
        assertThat(condition.page()).isEqualTo(1);
        assertThat(condition.pageable().getPageSize()).isEqualTo(ItemSearchCondition.PAGE_SIZE);
    }

    @Test
    @DisplayName("키워드: 앞뒤 공백을 지우고, 공백뿐이면 조건 없음, %·_ 는 이스케이프하지 않는다")
    void keywordIsTrimmedAndNotEscaped() {
        assertThat(ItemSearchCondition.from(params("q", "   ")).keyword()).isNull();
        assertThat(ItemSearchCondition.from(params("q", " 분수 ")).keywordPattern()).isEqualTo("%분수%");
        assertThat(ItemSearchCondition.from(params("q", "a_b%")).keywordPattern()).isEqualTo("%a_b%%");
    }

    @Test
    @DisplayName("키워드는 100자, 태그는 50자까지만 쓴다(글자 기준)")
    void keywordAndTagAreTruncated() {
        ItemSearchCondition condition = ItemSearchCondition.from(params("q", "분".repeat(101), "tag", "태".repeat(51)));

        assertThat(condition.keyword()).isEqualTo("분".repeat(ItemSearchCondition.MAX_KEYWORD_LENGTH));
        assertThat(condition.tag()).isEqualTo("태".repeat(ItemSearchCondition.MAX_TAG_LENGTH));
    }

    @Test
    @DisplayName("단원 코드는 대소문자 · 형식을 고치지 않고 그대로 쓴다")
    void unitCodeIsUsedAsIs() {
        assertThat(ItemSearchCondition.from(params("unit", " m5-1 ")).unitCode()).isEqualTo("m5-1");
        assertThat(ItemSearchCondition.from(params("unit", "Z9-9")).unitCode()).isEqualTo("Z9-9");
    }

    @Test
    @DisplayName("난이도: 빈값은 null(기본 조건), 그 밖은 PHP (int) 변환값")
    void levelFollowsPhpIntConversion() {
        assertThat(ItemSearchCondition.parseLevel("")).isNull();
        assertThat(ItemSearchCondition.parseLevel("1")).isEqualTo(1);
        assertThat(ItemSearchCondition.parseLevel("5")).isEqualTo(5);
        assertThat(ItemSearchCondition.parseLevel("0")).isEqualTo(0);
        assertThat(ItemSearchCondition.parseLevel("6")).isEqualTo(6);
        assertThat(ItemSearchCondition.parseLevel("-1")).isEqualTo(-1);
        assertThat(ItemSearchCondition.parseLevel("3abc")).isEqualTo(3);
        assertThat(ItemSearchCondition.parseLevel("05")).isEqualTo(5);
        assertThat(ItemSearchCondition.parseLevel("2.9")).isEqualTo(2);
        assertThat(ItemSearchCondition.parseLevel("abc")).isEqualTo(0);
        assertThat(ItemSearchCondition.parseLevel("99999999999999999999")).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("phpIntval: 지수 표기 · 부호 · 소수점 앞자리 생략도 PHP 처럼 읽는다")
    void phpIntvalReadsLeadingNumber() {
        assertThat(ItemSearchCondition.phpIntval("1e2")).isEqualTo(100L);
        assertThat(ItemSearchCondition.phpIntval("+3")).isEqualTo(3L);
        assertThat(ItemSearchCondition.phpIntval(".5")).isEqualTo(0L);
        assertThat(ItemSearchCondition.phpIntval("-2.9x")).isEqualTo(-2L);
        assertThat(ItemSearchCondition.phpIntval("x3")).isEqualTo(0L);
    }

    @Test
    @DisplayName("페이지: 숫자만이면 그 값, 아니면 1, 1 미만은 1, 999 초과는 999, 공백은 지우지 않는다")
    void pageIsClamped() {
        assertThat(ItemSearchCondition.parsePage("")).isEqualTo(1);
        assertThat(ItemSearchCondition.parsePage("2")).isEqualTo(2);
        assertThat(ItemSearchCondition.parsePage("0")).isEqualTo(1);
        assertThat(ItemSearchCondition.parsePage("-1")).isEqualTo(1);
        assertThat(ItemSearchCondition.parsePage(" 2")).isEqualTo(1);
        assertThat(ItemSearchCondition.parsePage("abc")).isEqualTo(1);
        assertThat(ItemSearchCondition.parsePage("1000")).isEqualTo(ItemSearchCondition.MAX_PAGE);
        assertThat(ItemSearchCondition.parsePage("99999999999999999999")).isEqualTo(ItemSearchCondition.MAX_PAGE);
        assertThat(ItemSearchCondition.from(params("page", "2")).pageable().getOffset()).isEqualTo(20L);
    }

    @Test
    @DisplayName("정렬: 기준별 기본 방향, 동률은 항상 id 오름차순, 모르는 값은 기본 정렬")
    void sortFollowsLegacyRules() {
        assertThat(ItemSearchCondition.toSort("id", "")).isEqualTo(Sort.by(Sort.Order.asc("id")));
        assertThat(ItemSearchCondition.toSort("id", "desc")).isEqualTo(Sort.by(Sort.Order.desc("id")));
        assertThat(ItemSearchCondition.toSort("title", "desc"))
            .isEqualTo(Sort.by(Sort.Order.desc("title"), Sort.Order.asc("id")));
        assertThat(ItemSearchCondition.toSort("unit", "desc"))
            .isEqualTo(Sort.by(Sort.Order.desc("unit.code"), Sort.Order.desc("level"), Sort.Order.asc("id")));
        assertThat(ItemSearchCondition.toSort("level", ""))
            .isEqualTo(Sort.by(Sort.Order.desc("level"), Sort.Order.asc("id")));
        assertThat(ItemSearchCondition.toSort("level", "asc"))
            .isEqualTo(Sort.by(Sort.Order.asc("level"), Sort.Order.asc("id")));
        assertThat(ItemSearchCondition.toSort("created", "up"))
            .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        assertThat(ItemSearchCondition.toSort("nope", "asc")).isEqualTo(ItemSearchCondition.DEFAULT_SORT);
    }

    @Test
    @DisplayName("sort · dir 는 공백을 지우고 소문자로 바꿔 비교한다")
    void sortAndDirAreNormalized() {
        ItemSearchCondition condition = ItemSearchCondition.from(params("sort", " LEVEL ", "dir", "ASC"));

        assertThat(condition.sort()).isEqualTo(Sort.by(Sort.Order.asc("level"), Sort.Order.asc("id")));
    }

    @Test
    @DisplayName("같은 이름이 여럿이면 name[] 는 첫 값, name 은 마지막 값을 쓴다")
    void repeatedParamsFollowPhp() {
        assertThat(ItemSearchCondition.from(params("tag", "계산", "tag", "문장제")).tag()).isEqualTo("문장제");
        assertThat(ItemSearchCondition.from(params("tag[]", "계산", "tag[]", "문장제")).tag()).isEqualTo("계산");
    }
}
