package com.qaima.domain;

import com.qaima.common.DictionaryTermNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "dictionary")
public class DictionaryTerm {

    /**
     * 표준 용어 키입니다.
     * - 서비스/임포터에서 정규화(앞뒤 공백 제거 + 연속 공백 축약 + 대문자화) 후 저장됩니다.
     */
    @Id
    @Column(name = "term", length = 255, nullable = false, unique = true)
    private String term;

    @Column(name = "initial", length = 2, nullable = false)
    private String initial = "#";

    @Lob
    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "source", length = 255)
    private String source;

    @Column(name = "tag", length = 255)
    private String tag;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public DictionaryTerm(String term) {
        this.term = term;
    }

    @PrePersist
    @PreUpdate
    private void updateInitial() {
        this.initial = DictionaryTermNormalizer.computeInitial(this.term);
    }
}
