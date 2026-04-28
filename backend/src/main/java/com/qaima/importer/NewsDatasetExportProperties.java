package com.qaima.importer;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "batch.news-export")
public class NewsDatasetExportProperties {

    private boolean enabled = false;
    private List<String> stockCodes = new ArrayList<>();
    private int perStock = 150;
    private String output;
}
