package com.basketball.analyze.kbl;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 한국시간 기준 조회 기간과 해당 시즌의 경기 일정을 반환합니다. */
public record KblScheduleResponse(
        String seasonId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate fromDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate toDate,
        List<Match> matches
) {

    /** 경기의 예정 시작 시각과 DB에 확인된 양 팀의 점수를 담습니다. */
    public record Match(
            int id,
            String matchCode,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM-dd'T'HH:mm:ssXXX",
                    timezone = "Asia/Seoul")
            OffsetDateTime startsAt,
            String status,
            KblMatchResponse.Team homeTeam,
            KblMatchResponse.Team awayTeam,
            Integer homeScore,
            Integer awayScore
    ) {}
}
