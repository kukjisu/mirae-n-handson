package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ItemSearchServiceTest {

    @Mock
    private ItemSearchRepository itemSearchRepository;

    @InjectMocks
    private ItemSearchService itemSearchService;

    private final Unit unit = ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5);

    private static ItemSearchCondition noConditions() {
        return ItemSearchCondition.from(new LinkedMultiValueMap<>());
    }

    @Test
    @DisplayName("search: 공개 상태 · 기본 난이도 상한으로 저장소에 묻고, id 페이지 순서대로 행을 만든다")
    void searchKeepsIdPageOrder() {
        ItemSearchCondition condition = noConditions();
        when(itemSearchRepository.findIdsByCondition(
            eq(ItemStatus.ACTIVE), isNull(), isNull(), isNull(),
            eq(ItemSearchCondition.DEFAULT_LEVEL_UPPER_BOUND), isNull(), eq(condition.pageable())))
            .thenReturn(new PageImpl<>(List.of(2, 1), condition.pageable(), 2));
        when(itemSearchRepository.findAllByIdIn(List.of(2, 1))).thenReturn(List.of(
            ItemFixtures.item(1, unit, "난이도 2 문항", 2, ItemStatus.ACTIVE),
            ItemFixtures.item(2, unit, "난이도 4 문항", 4, ItemStatus.ACTIVE,
                ItemFixtures.tag(1, "계산"), ItemFixtures.tag(2, "문장제"))));

        ItemSearchResponse response = itemSearchService.search(condition);

        assertThat(response.items()).extracting(ItemSearchRow::id).containsExactly(2, 1);
        assertThat(response.items().get(0).unit()).isEqualTo("M5-1");
        assertThat(response.items().get(0).tags()).containsExactly("계산", "문장제");
        assertThat(response.items().get(1).tags()).isEmpty();
        assertThat(response.count()).isEqualTo(2);
        assertThat(response.message()).isNull();
    }

    @Test
    @DisplayName("search: 전체 건수가 0 이면 안내 문구, 오류가 아니다")
    void searchWithNoResultReturnsMessage() {
        ItemSearchCondition condition = noConditions();
        when(itemSearchRepository.findIdsByCondition(
            any(), any(), any(), any(), anyInt(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), condition.pageable(), 0));

        ItemSearchResponse response = itemSearchService.search(condition);

        assertThat(response.items()).isEmpty();
        assertThat(response.count()).isZero();
        assertThat(response.message()).isEqualTo("검색 결과가 없습니다");
        verify(itemSearchRepository, never()).findAllByIdIn(any());
    }

    @Test
    @DisplayName("search: 마지막 페이지를 넘으면 안내 문구 없이 빈 목록, 건수는 전체 건수")
    void searchBeyondLastPageReturnsEmptyRowsWithoutMessage() {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", "2");
        ItemSearchCondition condition = ItemSearchCondition.from(params);
        when(itemSearchRepository.findIdsByCondition(
            any(), any(), any(), any(), anyInt(), any(), eq(condition.pageable())))
            .thenReturn(new PageImpl<>(List.of(), condition.pageable(), 20));

        ItemSearchResponse response = itemSearchService.search(condition);

        assertThat(response.items()).isEmpty();
        assertThat(response.count()).isEqualTo(20);
        assertThat(response.message()).isNull();
    }
}
