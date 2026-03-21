package com.qaima.repository;

import com.qaima.domain.DictionaryTerm;
import com.qaima.dto.dictionary.DictionaryInitialCountDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DictionaryRepository extends JpaRepository<DictionaryTerm, String> {

    List<DictionaryTerm> findAllByOrderByTermAsc(Pageable pageable);

    List<DictionaryTerm> findByTermContainingIgnoreCaseOrderByTermAsc(String q, Pageable pageable);

    List<DictionaryTerm> findByTermStartingWithIgnoreCaseOrderByTermAsc(String prefix, Pageable pageable);

    List<DictionaryTerm> findByInitialOrderByTermAsc(String initial, Pageable pageable);

    List<DictionaryTerm> findByInitialAndTermContainingIgnoreCaseOrderByTermAsc(String initial, String q, Pageable pageable);

    @Query("""
        select new com.qaima.dto.dictionary.DictionaryInitialCountDto(d.initial, count(d))
        from DictionaryTerm d
        group by d.initial
        order by d.initial asc
    """)
    List<DictionaryInitialCountDto> countByInitial();
}
