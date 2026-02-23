package com.qaima.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "industry_index_map", uniqueConstraints = {
        @UniqueConstraint(name = "uk_industry_index_map", columnNames = {"industry_id", "index_id"})
})
public class IndustryIndexMap {

    @EmbeddedId
    private IndustryIndexMapId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("industryId")
    @JoinColumn(name = "industry_id", nullable = false)
    private Industry industry;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("indexId")
    @JoinColumn(name = "index_id", nullable = false)
    private IndustryIndex industryIndex;
}
