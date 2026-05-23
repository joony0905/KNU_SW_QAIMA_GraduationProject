package com.qaima.dto.common;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Setter
@Builder
@Jacksonized
public class AnalysisExplainDto {
    private String provider;
    private String model;
    private String text;
    private Sections sections;
    private Overall overall;
    private List<Warning> warnings;

    @Getter
    @Setter
    @Builder
    @Jacksonized
    public static class Sections {
        private Section priceFlow;
        private Section marketSnapshot;
        private Section indicators;
        private Section financialTimeline;
        private Section macroEnvironment;
        private Section investorFlow;
        private Section shortSelling;
        private Section peerCluster;
        private Section newsSentiment;
        private Section crossSignal;
        private Section coreRisk;
        private Section volatilityAnalysis;
        private Section efficiencyAnalysis;
        private Section overlayObservations;
        private Section portfolioComparison;
        private Section finalJudgement;
    }

    @Getter
    @Setter
    @Builder
    @Jacksonized
    public static class Section {
        private String title;
        private String summary;
        private List<String> bullets;
    }

    @Getter
    @Setter
    @Builder
    @Jacksonized
    public static class Overall {
        private String summary;
        private List<String> bullets;
        private List<String> risks;
        private String conclusion;
    }

    @Getter
    @Setter
    @Builder
    @Jacksonized
    public static class Warning {
        private String code;
        private String message;
        private String userMessage;
        private String severity;
        private String target;
    }
}
