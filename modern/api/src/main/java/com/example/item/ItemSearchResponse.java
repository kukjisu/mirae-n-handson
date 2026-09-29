package com.example.item;

import java.util.List;

/**
 * 문항 검색 응답.
 *
 * @param items   현재 페이지의 행(응답 순서가 곧 정렬 순서)
 * @param count   조건에 맞는 전체 건수(현재 페이지 행 수가 아님)
 * @param message 전체 건수가 0 이면 안내 문구, 아니면 null
 */
public record ItemSearchResponse(
    List<ItemSearchRow> items,
    long count,
    String message) {
}
