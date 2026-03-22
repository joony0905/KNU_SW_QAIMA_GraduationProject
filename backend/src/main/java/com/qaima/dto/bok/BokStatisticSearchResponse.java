package com.qaima.dto.bok;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class BokStatisticSearchResponse {

    @JsonProperty("StatisticSearch")
    private Payload statisticSearch;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {

        @JsonProperty("list_total_count")
        private Integer listTotalCount;

        private List<Row> row;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Row {

        @JsonProperty("STAT_CODE")
        private String statCode;

        @JsonProperty("STAT_NAME")
        private String statName;

        @JsonProperty("ITEM_CODE1")
        private String itemCode1;

        @JsonProperty("ITEM_NAME1")
        private String itemName1;

        @JsonProperty("ITEM_CODE2")
        private String itemCode2;

        @JsonProperty("ITEM_NAME2")
        private String itemName2;

        @JsonProperty("ITEM_CODE3")
        private String itemCode3;

        @JsonProperty("ITEM_NAME3")
        private String itemName3;

        @JsonProperty("ITEM_CODE4")
        private String itemCode4;

        @JsonProperty("ITEM_NAME4")
        private String itemName4;

        @JsonProperty("UNIT_NAME")
        private String unitName;

        @JsonProperty("WGT")
        private String wgt;

        @JsonProperty("TIME")
        private String time;

        @JsonProperty("DATA_VALUE")
        private String dataValue;
    }
}
