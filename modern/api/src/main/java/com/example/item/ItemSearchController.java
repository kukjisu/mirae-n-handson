package com.example.item;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 문항 검색 API — 레거시 {@code search.php} 의 이관. 파라미터 이름은 레거시와 같다(q, unit, level, tag, sort, dir, page).
 * 값은 모두 문자열로 받는다: 레거시처럼 이상한 값도 400 없이 해석해 200 으로 응답한다.
 */
@RestController
@RequestMapping("/api/items")
public class ItemSearchController {

    private final ItemSearchService itemSearchService;

    public ItemSearchController(ItemSearchService itemSearchService) {
        this.itemSearchService = itemSearchService;
    }

    /** {@code GET /api/items/search} — 공개 문항 검색. */
    @GetMapping("/search")
    public ItemSearchResponse search(@RequestParam MultiValueMap<String, String> params) {
        return itemSearchService.search(ItemSearchCondition.from(params));
    }
}
