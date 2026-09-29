package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;

/**
 * 검색 저장소 슬라이스 — H2(MariaDB 모드)에 실제 SQL 을 날린다.
 * H2 는 MariaDB 콜레이션(대소문자 무시 · 한글 정렬)과 다르므로 그 부분은 동작 보존 테스트가 맡는다.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ItemSearchRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ItemSearchRepository itemSearchRepository;

    private Integer level4Id;
    private Integer level5Id;
    private Integer level2Id;
    private Integer decimalId;

    @BeforeEach
    void seed() {
        Unit fraction = entityManager.persist(ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5));
        Unit decimal = entityManager.persist(ItemFixtures.unit(3, "M5-3", "소수의 곱셈", 5));
        Tag calc = entityManager.persist(ItemFixtures.tag(1, "계산"));
        Tag word = entityManager.persist(ItemFixtures.tag(2, "문장제"));

        level2Id = entityManager.persist(ItemFixtures.item(null, fraction, "분모가 같은 분수의 덧셈", 2, ItemStatus.ACTIVE, calc)).getId();
        level4Id = entityManager.persist(ItemFixtures.item(null, fraction, "분수 문장제", 4, ItemStatus.ACTIVE, calc, word)).getId();
        entityManager.persist(ItemFixtures.item(null, fraction, "검수 중 문항", 3, ItemStatus.REVIEWING));
        entityManager.persist(ItemFixtures.item(null, fraction, "삭제된 문항", 4, ItemStatus.DELETED));
        level5Id = entityManager.persist(ItemFixtures.item(null, fraction, "난이도 5 분수", 5, ItemStatus.ACTIVE, word)).getId();
        decimalId = entityManager.persist(ItemFixtures.item(null, decimal, "소수 곱셈", 2, ItemStatus.ACTIVE)).getId();
        entityManager.flush();
        entityManager.clear();
    }

    private Page<Integer> search(String... keyValues) {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.add(keyValues[i], keyValues[i + 1]);
        }
        ItemSearchCondition condition = ItemSearchCondition.from(params);
        return itemSearchRepository.findIdsByCondition(
            ItemStatus.ACTIVE,
            condition.keywordPattern(),
            condition.unitCode(),
            condition.level(),
            ItemSearchCondition.DEFAULT_LEVEL_UPPER_BOUND,
            condition.tag(),
            condition.pageable());
    }

    @Test
    @DisplayName("조건 없음: 공개 문항만, 난이도 5 제외, level DESC · id ASC")
    void noConditionsExcludesLevel5AndNonActive() {
        Page<Integer> page = search();

        assertThat(page.getContent()).containsExactly(level4Id, level2Id, decimalId);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("level=5 를 명시하면 난이도 5 문항이 나온다")
    void explicitLevel5IsIncluded() {
        assertThat(search("level", "5").getContent()).containsExactly(level5Id);
    }

    @Test
    @DisplayName("범위 밖 난이도 · 없는 단원은 오류 없이 0건")
    void outOfRangeLevelAndUnknownUnitReturnNothing() {
        assertThat(search("level", "6").getTotalElements()).isZero();
        assertThat(search("level", "-1").getTotalElements()).isZero();
        assertThat(search("unit", "Z9-9").getTotalElements()).isZero();
    }

    @Test
    @DisplayName("키워드는 제목 또는 지문 부분 일치, 단원 · 태그와 AND")
    void keywordUnitAndTagAreCombined() {
        assertThat(search("q", "분수").getContent()).containsExactly(level4Id, level2Id);
        assertThat(search("q", "문제 본문").getContent()).containsExactly(level4Id, level2Id, decimalId);
        assertThat(search("unit", "M5-3").getContent()).containsExactly(decimalId);
        assertThat(search("tag", "문장제").getContent()).containsExactly(level4Id);
        assertThat(search("tag", "계산", "unit", "M5-3").getTotalElements()).isZero();
    }

    @Test
    @DisplayName("정렬 sort=level&dir=asc · sort=unit&dir=desc")
    void sortIsAppliedInDatabase() {
        assertThat(search("sort", "level", "dir", "asc").getContent()).containsExactly(level2Id, decimalId, level4Id);
        assertThat(search("sort", "unit", "dir", "desc").getContent()).containsExactly(decimalId, level4Id, level2Id);
    }

    @Test
    @DisplayName("마지막 페이지를 넘으면 빈 목록, 전체 건수는 유지")
    void pageBeyondLastIsEmptyWithTotal() {
        Page<Integer> page = search("page", "2");

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("findAllByIdIn: 단원 · 태그를 함께 가져온다(태그는 id 순)")
    void findAllByIdInLoadsUnitAndTags() {
        List<Item> items = itemSearchRepository.findAllByIdIn(List.of(level4Id));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getUnit().getCode()).isEqualTo("M5-1");
        assertThat(items.get(0).getTags()).extracting(Tag::getName).containsExactly("계산", "문장제");
    }
}
