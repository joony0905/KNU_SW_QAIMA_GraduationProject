package com.qaima.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "news")
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long newsId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(unique = true, length = 1000)
    private String url;

    @Column(nullable = false)
    private OffsetDateTime publishedAt;

    @Column(length = 100)
    private String source;

    @Column(length = 10)
    private String lang;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(length = 1000)
    private String contentUri;
}
