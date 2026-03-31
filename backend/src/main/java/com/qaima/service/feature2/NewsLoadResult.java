package com.qaima.service.feature2;

import com.qaima.dto.news.NewsItemDto;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NewsLoadResult {
    private final List<NewsItemDto> newsList;
    private final List<String> warnings;
}
