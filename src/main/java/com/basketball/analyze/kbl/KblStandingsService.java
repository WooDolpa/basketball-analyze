package com.basketball.analyze.kbl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.basketball.analyze.kbl.KblStandingsResponse.Result.LOSS;
import static com.basketball.analyze.kbl.KblStandingsResponse.Result.WIN;

@Service
class KblStandingsService {

    private static final String LEAGUE_KEY = "S49G01";
    private static final String SEASON_ID = "2026-2027";
    private static final int RECENT_GAME_LIMIT = 5;

    private final KblStandingsRepository standingsRepository;
    private final KblMatchRepository matchRepository;

    /** 순위 스냅샷과 경기 결과를 읽을 저장소를 연결합니다. */
    KblStandingsService(KblStandingsRepository standingsRepository, KblMatchRepository matchRepository) {
        this.standingsRepository = standingsRepository;
        this.matchRepository = matchRepository;
    }

    /** 시즌 순위와 사용 가능한 종료 경기 이력을 묶어 조회합니다. */
    @Transactional(readOnly = true)
    public KblStandingsResponse findStandings(String leagueKey) {
        if (!LEAGUE_KEY.equals(leagueKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하는 정규리그 키는 " + LEAGUE_KEY + "입니다.");
        }
        List<KblStandingsRepository.StandingRow> rows = standingsRepository.findStandings(SEASON_ID);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "조회 가능한 순위 데이터가 없습니다. 시즌·구단 적재 후 POST /api/kbl/seasons/"
                            + LEAGUE_KEY + "/standings/sync를 호출해 주세요.");
        }
        Map<String, List<KblStandingsResponse.Result>> recentResults = new HashMap<>();
        for (KblMatchRepository.FinishedMatchRow match : matchRepository.findFinishedMatchesNewestFirst(SEASON_ID)) {
            addRecentResult(recentResults, match.homeTeamCode(), match.homeWon() ? WIN : LOSS);
            addRecentResult(recentResults, match.awayTeamCode(), match.homeWon() ? LOSS : WIN);
        }
        List<KblStandingsResponse.Standing> standings = rows.stream()
                .map(row -> new KblStandingsResponse.Standing(row.rank(),
                        new KblMatchResponse.Team(row.teamCode(), row.teamName()), row.wins() + row.losses(),
                        row.wins(), row.losses(), row.winRate(), row.gamesBehind(),
                        List.copyOf(recentResults.getOrDefault(row.teamCode(), List.of()))))
                .toList();
        int completedGames = rows.stream().mapToInt(KblStandingsRepository.StandingRow::wins).sum();
        return new KblStandingsResponse(SEASON_ID, completedGames, standings);
    }

    /** 최신순으로 읽은 결과 중 팀별 처음 다섯 경기만 보존합니다. */
    private void addRecentResult(Map<String, List<KblStandingsResponse.Result>> recentResults, String teamCode,
                                 KblStandingsResponse.Result result) {
        List<KblStandingsResponse.Result> teamResults = recentResults.computeIfAbsent(teamCode,
                code -> new ArrayList<>(RECENT_GAME_LIMIT));
        if (teamResults.size() < RECENT_GAME_LIMIT) {
            teamResults.add(result);
        }
    }
}
