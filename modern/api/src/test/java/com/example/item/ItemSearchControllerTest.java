package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 컨트롤러 슬라이스 — 서비스는 목. 응답 모양이 동작 보존 테스트의 정규화 결과와 같은지 본다. */
@WebMvcTest(ItemSearchController.class)
@ActiveProfiles("test")
class ItemSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ItemSearchService itemSearchService;

    @Test
    @DisplayName("GET /api/items/search → 200, {items, count, message}, 행에는 id · title · unit · level · tags 만")
    void searchReturnsNormalizedShape() throws Exception {
        when(itemSearchService.search(any())).thenReturn(new ItemSearchResponse(
            List.of(new ItemSearchRow(18, "분수의 뺄셈 문장제", "M5-1", 4, List.of("문장제", "오답률높음"))), 1, null));

        mockMvc.perform(get("/api/items/search").param("unit", "M5-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.message").doesNotExist())
            .andExpect(jsonPath("$.items[0].id").value(18))
            .andExpect(jsonPath("$.items[0].title").value("분수의 뺄셈 문장제"))
            .andExpect(jsonPath("$.items[0].unit").value("M5-1"))
            .andExpect(jsonPath("$.items[0].level").value(4))
            .andExpect(jsonPath("$.items[0].tags[1]").value("오답률높음"))
            .andExpect(jsonPath("$.items[0].stem").doesNotExist())
            .andExpect(jsonPath("$.items[0].status").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/items/search?level=3abc → 400 이 아니라 200, 난이도 3 으로 해석")
    void searchAcceptsNonNumericLevel() throws Exception {
        when(itemSearchService.search(any())).thenReturn(new ItemSearchResponse(List.of(), 0, "검색 결과가 없습니다"));

        mockMvc.perform(get("/api/items/search").param("level", "3abc").param("page", "abc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.message").value("검색 결과가 없습니다"))
            .andExpect(jsonPath("$.items.length()").value(0));

        ArgumentCaptor<ItemSearchCondition> captor = ArgumentCaptor.forClass(ItemSearchCondition.class);
        verify(itemSearchService).search(captor.capture());
        assertThat(captor.getValue().level()).isEqualTo(3);
        assertThat(captor.getValue().page()).isEqualTo(1);
    }
}
