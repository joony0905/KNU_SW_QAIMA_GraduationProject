package com.qaima.repository;

import com.qaima.domain.DictionaryTerm;
import com.qaima.dto.dictionary.DictionaryInitialCountDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DictionaryRepository extends JpaRepository<DictionaryTerm, String> {

    List<DictionaryTerm> findAllByOrderByTermAsc(Pageable pageable);

    List<DictionaryTerm> findByTermContainingIgnoreCaseOrderByTermAsc(String q, Pageable pageable);

    List<DictionaryTerm> findByTermStartingWithIgnoreCaseOrderByTermAsc(String prefix, Pageable pageable);

    List<DictionaryTerm> findByInitialOrderByTermAsc(String initial, Pageable pageable);

    List<DictionaryTerm> findByInitialAndTermContainingIgnoreCaseOrderByTermAsc(String initial, String q, Pageable pageable);

    @Query("""
        select d
        from DictionaryTerm d
        where d.term like :containsPattern escape '\\'
        order by
            case
                when d.term = :normalizedQuery then 0
                when d.term like :prefixPattern escape '\\' then 1
                else 2
            end asc,
            d.term asc
    """)
    List<DictionaryTerm> findAutocompleteTermCandidates(
            @Param("normalizedQuery") String normalizedQuery,
            @Param("prefixPattern") String prefixPattern,
            @Param("containsPattern") String containsPattern,
            Pageable pageable
    );

    @Query("""
        select d
        from DictionaryTerm d
        where (:initial is null or d.initial = :initial)
          and (
              :normalizedQuery is null
              or d.term like :containsPattern escape '\\'
              or exists (
                  select a.aliasId
                  from DictionaryAlias a
                  where a.canonicalTerm = d
                    and a.normalizedAliasTerm like :containsPattern escape '\\'
              )
          )
        order by
            case
                when d.term = :normalizedQuery then 0
                when exists (
                    select exactAlias.aliasId
                    from DictionaryAlias exactAlias
                    where exactAlias.canonicalTerm = d
                      and exactAlias.normalizedAliasTerm = :normalizedQuery
                ) then 1
                when d.term like :prefixPattern escape '\\' then 2
                when exists (
                    select prefixAlias.aliasId
                    from DictionaryAlias prefixAlias
                    where prefixAlias.canonicalTerm = d
                      and prefixAlias.normalizedAliasTerm like :prefixPattern escape '\\'
                ) then 3
                when d.term like :containsPattern escape '\\' then 4
                when exists (
                    select containsAlias.aliasId
                    from DictionaryAlias containsAlias
                    where containsAlias.canonicalTerm = d
                      and containsAlias.normalizedAliasTerm like :containsPattern escape '\\'
                ) then 5
                else 6
            end asc,
            d.term asc
    """)
    List<DictionaryTerm> searchByQueryIncludingAliases(
            @Param("normalizedQuery") String normalizedQuery,
            @Param("prefixPattern") String prefixPattern,
            @Param("containsPattern") String containsPattern,
            @Param("initial") String initial,
            Pageable pageable
    );

    @Query("""
        select new com.qaima.dto.dictionary.DictionaryInitialCountDto(d.initial, count(d))
        from DictionaryTerm d
        group by d.initial
        order by d.initial asc
    """)
    List<DictionaryInitialCountDto> countByInitial();
}
