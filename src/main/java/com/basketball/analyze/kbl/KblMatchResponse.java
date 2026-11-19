package com.basketball.analyze.kbl;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record KblMatchResponse(
        String seasonId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate date,
        @JsonFormat(shape = JsonFormat.Shape.STRING, without = JsonFormat.Feature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE)
        OffsetDateTime queriedAt,
        List<Match> matches
) {

    public record Match(
            int id,
            String matchCode,
            @JsonFormat(shape = JsonFormat.Shape.STRING, without = JsonFormat.Feature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE)
            OffsetDateTime startsAt,
            String status,
            Team homeTeam,
            Team awayTeam
    ) {}

    public record Team(String code, String name) {}
}
