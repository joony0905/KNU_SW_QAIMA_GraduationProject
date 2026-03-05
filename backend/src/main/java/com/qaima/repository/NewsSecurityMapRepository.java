package com.qaima.repository;

import com.qaima.domain.NewsSecurityMap;
import com.qaima.domain.NewsSecurityMapId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsSecurityMapRepository extends JpaRepository<NewsSecurityMap, NewsSecurityMapId> {
}
