package com.example.item;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문항 검색(레거시 search.php 이관). 공개 문항만, 조건 · 정렬 · 페이지는 {@link ItemSearchCondition} 이 정한다.
 * 엔티티는 트랜잭션 안에서 DTO로 바꿔 돌려준다(OSIV 꺼져 있음).
 */
@Service
@Transactional(readOnly = true)
public class ItemSearchService {

    /** 전체 건수가 0 일 때의 안내 문구(search.php:658). */
    public static final String NO_RESULT_MESSAGE = "검색 결과가 없습니다";

    private static final Logger log = LoggerFactory.getLogger(ItemSearchService.class);

    private final ItemSearchRepository itemSearchRepository;

    public ItemSearchService(ItemSearchRepository itemSearchRepository) {
        this.itemSearchRepository = itemSearchRepository;
    }

    /**
     * 검색. 없는 단원 · 범위 밖 난이도도 오류가 아니라 0건이다.
     * 전체 건수가 있는데 마지막 페이지를 넘으면 안내 문구 없이 빈 목록이다(BR-26).
     */
    public ItemSearchResponse search(ItemSearchCondition condition) {
        Page<Integer> idPage = itemSearchRepository.findIdsByCondition(
            ItemStatus.ACTIVE,
            condition.keywordPattern(),
            condition.unitCode(),
            condition.level(),
            ItemSearchCondition.DEFAULT_LEVEL_UPPER_BOUND,
            condition.tag(),
            condition.pageable());

        List<ItemSearchRow> rows = loadInOrder(idPage.getContent());
        long total = idPage.getTotalElements();
        log.debug("item search page {} → {} rows of {}", condition.page(), rows.size(), total);
        return new ItemSearchResponse(rows, total, total == 0 ? NO_RESULT_MESSAGE : null);
    }

    /** id 페이지의 순서(= DB 정렬 순서)대로 행을 만든다. */
    private List<ItemSearchRow> loadInOrder(List<Integer> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Integer, Item> byId = itemSearchRepository.findAllByIdIn(ids).stream()
            .collect(Collectors.toMap(Item::getId, Function.identity()));
        return ids.stream()
            .map(byId::get)
            .map(ItemSearchRow::from)
            .toList();
    }
}
