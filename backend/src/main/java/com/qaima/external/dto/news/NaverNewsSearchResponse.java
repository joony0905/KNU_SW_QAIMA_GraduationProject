package com.qaima.external.dto.news;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NaverNewsSearchResponse {
    @JsonProperty("items")
    private List<NaverNewsItemDto> items;

    @Getter
    @Setter
    public static class NaverNewsItemDto {
        @JsonProperty("title")
        private String title;

        @JsonProperty("link")
        private String link;

        @JsonProperty("description")
        private String description;

        @JsonProperty("pubDate")
        private String pubDate;

        @JsonProperty("originallink")
        private String originalLink;
    }
}
