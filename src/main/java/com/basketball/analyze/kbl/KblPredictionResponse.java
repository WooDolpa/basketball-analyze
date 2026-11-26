package com.basketball.analyze.kbl;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record KblPredictionResponse(
        String seasonId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate date,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM-dd'T'HH:mm:ssXXX",
                timezone = "Asia/Seoul")
        OffsetDateTime queriedAt,
        List<Match> matches
) {

    public record Match(
            int id,
            String matchCode,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM-dd'T'HH:mm:ssXXX",
                    timezone = "Asia/Seoul")
            OffsetDateTime startsAt,
            String status,
            KblMatchResponse.Team homeTeam,
            KblMatchResponse.Team awayTeam,
            Prediction prediction
    ) {}

    public record Prediction(
            PredictionStatus status,
            BigDecimal homeElo,
            BigDecimal awayElo,
            BigDecimal homeWinPercentage,
            BigDecimal awayWinPercentage,
            String reason
    ) {}

    public enum PredictionStatus {
        AVAILABLE, INSUFFICIENT_DATA
    }
}
