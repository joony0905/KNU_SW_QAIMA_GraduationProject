package com.qaima.domain;

import com.qaima.common.DictionaryTermNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "dictionary_alias",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dictionary_alias_normalized", columnNames = "normalized_alias_term")
        },
        indexes = {
                @Index(name = "idx_dictionary_alias_canonical_term", columnList = "canonical_term"),
                @Index(name = "idx_dictionary_alias_alias_term", columnList = "alias_term"),
                @Index(name = "idx_dictionary_alias_normalized", columnList = "normalized_alias_term")
        }
)
public class DictionaryAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alias_id")
    private Long aliasId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canonical_term", nullable = false)
    private DictionaryTerm canonicalTerm;

    @Column(name = "alias_term", nullable = false, length = 255)
    private String aliasTerm;

    @Column(name = "normalized_alias_term", nullable = false, length = 255)
    private String normalizedAliasTerm;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "notes", length = 255)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    private void normalizeAliasTerm() {
        this.normalizedAliasTerm = DictionaryTermNormalizer.normalizeTerm(this.aliasTerm);
    }
}
