package com.basketball.analyze.kbl;

import java.math.BigDecimal;
import java.util.List;

/** 집계에 반영한 경기 범위와 선수별 시즌 기록을 반환합니다. */
record KblSeasonPlayerRecordsResponse(String seasonId, int recordedGames, int unverifiedAppearances, List<Player> players) {

    record Player(String code, String name, List<KblMatchResponse.Team> teams, int games, Totals totals, Averages averages) {}

    record Totals(Integer points, Integer rebounds, Integer assists) {}

    record Averages(BigDecimal points, BigDecimal rebounds, BigDecimal assists) {}
}
