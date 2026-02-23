package com.qaima.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "peer_cluster_cache")
public class PeerClusterCache {

    @EmbeddedId
    private PeerClusterCacheId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("industryId")
    @JoinColumn(name = "industry_id", nullable = false)
    private Industry industry;

    @Column(name = "params_json")
    private String paramsJson; // jsonb mapped as String

    @Column(name = "cluster_json", nullable = false)
    private String clusterJson; // jsonb mapped as String

    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("now()")
    private OffsetDateTime updatedAt;
}
