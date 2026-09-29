package com.example.item;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 문항 검색 저장소. 레거시 뷰 {@code v_item_public}(status = 'A', 단원 내부 조인)과 같은 대상을 엔티티로 조회한다.
 * 정렬은 DB 에서 한다 — 문자열 정렬이 DB 콜레이션을 따라야 레거시와 순서가 같다.
 */
public interface ItemSearchRepository extends Repository<Item, Integer> {

    /**
     * 조건에 맞는 문항 id 한 페이지와 전체 건수. 조건은 모두 AND(BR-04).
     * 난이도가 null 이면 {@code level < levelUpperBound}(BR-11), 아니면 {@code level = :level}(BR-12, BR-13).
     */
    @Query(
        value = """
            select i.id from Item i
            where i.status = :status
              and (:keywordPattern is null or i.title like :keywordPattern or i.stem like :keywordPattern)
              and (:unitCode is null or i.unit.code = :unitCode)
              and ((:level is null and i.level < :levelUpperBound) or i.level = :level)
              and (:tag is null or exists (
                    select 1 from Item it join it.tags t where it.id = i.id and t.name = :tag))
            """,
        countQuery = """
            select count(i) from Item i
            where i.status = :status
              and (:keywordPattern is null or i.title like :keywordPattern or i.stem like :keywordPattern)
              and (:unitCode is null or i.unit.code = :unitCode)
              and ((:level is null and i.level < :levelUpperBound) or i.level = :level)
              and (:tag is null or exists (
                    select 1 from Item it join it.tags t where it.id = i.id and t.name = :tag))
            """)
    Page<Integer> findIdsByCondition(
        @Param("status") String status,
        @Param("keywordPattern") String keywordPattern,
        @Param("unitCode") String unitCode,
        @Param("level") Integer level,
        @Param("levelUpperBound") int levelUpperBound,
        @Param("tag") String tag,
        Pageable pageable);

    /** id 목록의 문항을 단원 · 태그와 함께 가져온다. 순서는 보장하지 않는다. */
    @EntityGraph(attributePaths = {"unit", "tags"})
    List<Item> findAllByIdIn(Collection<Integer> ids);
}
