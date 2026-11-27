package com.basketball.analyze.kbl;

import java.math.BigDecimal;
import java.util.List;

record KblStandingsResponse(String seasonId, int completedGames, List<Standing> standings) {

    /** 순위 스냅샷과 최신 경기부터 나열한 최대 다섯 개의 승패를 담습니다. */
    record Standing(
            int rank,
            KblMatchResponse.Team team,
            int games,
            int wins,
            int losses,
            BigDecimal winRate,
            BigDecimal gamesBehind,
            List<Result> recentResults
    ) {}

    enum Result { WIN, LOSS }
}
