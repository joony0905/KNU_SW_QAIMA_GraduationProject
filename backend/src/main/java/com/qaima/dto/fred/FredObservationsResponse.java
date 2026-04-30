package com.qaima.dto.fred;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FredObservationsResponse {

    @JsonProperty("observations")
    private List<Observation> observations;

    @Getter
    @Setter
    public static class Observation {
        private String date;
        private String value;
    }
}
