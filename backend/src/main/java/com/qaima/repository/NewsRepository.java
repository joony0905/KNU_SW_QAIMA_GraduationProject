package com.qaima.repository;

import com.qaima.domain.News;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsRepository extends JpaRepository<News, Long> {
    Optional<News> findByUrl(String url);
}
