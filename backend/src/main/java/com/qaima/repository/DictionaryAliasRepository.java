package com.qaima.repository;

import com.qaima.domain.DictionaryAlias;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DictionaryAliasRepository extends JpaRepository<DictionaryAlias, Long> {

    @EntityGraph(attributePaths = "canonicalTerm")
    Optional<DictionaryAlias> findByNormalizedAliasTerm(String normalizedAliasTerm);

    List<DictionaryAlias> findByNormalizedAliasTermStartingWithOrderByAliasTermAsc(String prefix, Pageable pageable);

    List<DictionaryAlias> findByNormalizedAliasTermContainingOrderByAliasTermAsc(String q, Pageable pageable);

    @EntityGraph(attributePaths = "canonicalTerm")
    @Query("""
        select a
        from DictionaryAlias a
        where a.normalizedAliasTerm like :containsPattern escape '\\'
        order by
            case
                when a.normalizedAliasTerm = :normalizedQuery then 0
                when a.normalizedAliasTerm like :prefixPattern escape '\\' then 1
                else 2
            end asc,
            a.aliasTerm asc
    """)
    List<DictionaryAlias> findAutocompleteAliasCandidates(
            @Param("normalizedQuery") String normalizedQuery,
            @Param("prefixPattern") String prefixPattern,
            @Param("containsPattern") String containsPattern,
            Pageable pageable
    );

    List<DictionaryAlias> findByCanonicalTerm_TermOrderByAliasTermAsc(String canonicalTerm);

    boolean existsByNormalizedAliasTerm(String normalizedAliasTerm);
}
