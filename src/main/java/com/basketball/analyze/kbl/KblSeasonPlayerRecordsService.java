package com.basketball.analyze.kbl;

import com.basketball.analyze.kbl.KblSeasonPlayerRecordsRepository.PlayerTeamRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
class KblSeasonPlayerRecordsService {

    private static final String LEAGUE_KEY = "S49G01";
    private static final String SEASON_ID = "2026-2027";

    private final KblSeasonPlayerRecordsRepository repository;

    /** 시즌 선수 기록과 적재 범위를 읽는 저장소를 연결합니다. */
    KblSeasonPlayerRecordsService(KblSeasonPlayerRecordsRepository repository) {
        this.repository = repository;
    }

    /** 종료 경기의 출전 기록을 선수별로 집계해 반환합니다. */
    @Transactional(readOnly = true)
    public KblSeasonPlayerRecordsResponse findPlayerRecords(String leagueKey) {
        if (!LEAGUE_KEY.equals(leagueKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하는 정규리그 키는 " + LEAGUE_KEY + "입니다.");
        }
        KblSeasonPlayerRecordsRepository.Coverage coverage = repository.findCoverage(SEASON_ID);
        Map<String, List<PlayerTeamRow>> byPlayer = repository.findPlayerTeamTotals(SEASON_ID).stream()
                .collect(Collectors.groupingBy(PlayerTeamRow::code, LinkedHashMap::new, Collectors.toList()));
        List<KblSeasonPlayerRecordsResponse.Player> players = byPlayer.values().stream().map(this::summarize).toList();
        return new KblSeasonPlayerRecordsResponse(SEASON_ID, coverage.recordedGames(), coverage.unverifiedAppearances(), players);
    }

    /** 시즌 중 여러 팀에서 뛴 선수의 팀별 합계를 한 행으로 묶습니다. */
    private KblSeasonPlayerRecordsResponse.Player summarize(List<PlayerTeamRow> teams) {
        PlayerTeamRow first = teams.getFirst();
        int games = teams.stream().mapToInt(PlayerTeamRow::games).sum();
        Integer points = total(teams, PlayerTeamRow::points);
        Integer rebounds = total(teams, PlayerTeamRow::rebounds);
        Integer assists = total(teams, PlayerTeamRow::assists);
        return new KblSeasonPlayerRecordsResponse.Player(first.code(), first.name(),
                teams.stream().map(PlayerTeamRow::team).toList(), games,
                new KblSeasonPlayerRecordsResponse.Totals(points, rebounds, assists),
                new KblSeasonPlayerRecordsResponse.Averages(average(points, games), average(rebounds, games), average(assists, games)));
    }

    /** 모든 팀의 값이 확인된 경우에만 선수의 시즌 합계를 계산합니다. */
    private Integer total(List<PlayerTeamRow> teams, Function<PlayerTeamRow, Integer> metric) {
        int total = 0;
        for (PlayerTeamRow team : teams) {
            Integer value = metric.apply(team);
            if (value == null) {
                return null;
            }
            total += value;
        }
        return total;
    }

    /** 시즌 합계를 출전 경기 수로 나누고 없는 지표는 미확인 상태로 반환합니다. */
    private BigDecimal average(Integer total, int games) {
        return total == null ? null : BigDecimal.valueOf(total).divide(BigDecimal.valueOf(games), 2, RoundingMode.HALF_UP);
    }
}
