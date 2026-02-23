package com.qaima.repository;

import com.qaima.domain.PeerClusterCache;
import com.qaima.domain.PeerClusterCacheId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeerClusterCacheRepository extends JpaRepository<PeerClusterCache, PeerClusterCacheId> {
}
