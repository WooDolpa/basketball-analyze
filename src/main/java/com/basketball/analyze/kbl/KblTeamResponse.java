package com.basketball.analyze.kbl;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

record KblTeamResponse(String seasonId, KblMatchResponse.Team team, SeasonStanding standing, List<Game> recentGames) {

    /** 리그 순위와 동일한 시즌 집계 값을 담습니다. */
    record SeasonStanding(int rank, int games, int wins, int losses, BigDecimal winRate, BigDecimal gamesBehind) {}

    /** 요청한 팀을 기준으로 상대, 점수와 승패를 담습니다. */
    record Game(
            String matchCode,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul")
            OffsetDateTime startsAt,
            KblMatchResponse.Team opponent,
            Integer scored,
            Integer conceded,
            KblStandingsResponse.Result result
    ) {}
}
