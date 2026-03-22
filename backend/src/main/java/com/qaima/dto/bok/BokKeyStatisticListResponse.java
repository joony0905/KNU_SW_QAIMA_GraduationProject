package com.qaima.dto.bok;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class BokKeyStatisticListResponse {

    @JsonProperty("KeyStatisticList")
    private Payload keyStatisticList;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {

        @JsonProperty("list_total_count")
        private Integer listTotalCount;

        @JsonProperty("row_count")
        private Integer rowCount;

        private List<Row> row;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Row {

        @JsonProperty("CLASS_NAME")
        private String className;

        @JsonProperty("KEYSTAT_NAME")
        private String keyStatName;

        @JsonProperty("DATA_VALUE")
        private String dataValue;

        @JsonProperty("CYCLE")
        private String cycle;

        @JsonProperty("UNIT_NAME")
        private String unitName;
    }
}
