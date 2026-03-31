package com.qaima.repository;

import com.qaima.domain.News;
import com.qaima.domain.NewsSecurityMap;
import com.qaima.domain.NewsSecurityMapId;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsSecurityMapRepository extends JpaRepository<NewsSecurityMap, NewsSecurityMapId> {
    @Query("select m.news from NewsSecurityMap m where m.stock.stockId = :stockId order by m.news.publishedAt desc")
    List<News> findLatestNewsByStockId(@Param("stockId") Long stockId, Pageable pageable);
}
