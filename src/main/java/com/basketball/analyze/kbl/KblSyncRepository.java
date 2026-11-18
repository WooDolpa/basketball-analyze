package com.basketball.analyze.kbl;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
class KblSyncRepository {

    private final JdbcTemplate jdbc;

    /** Flyway가 관리하는 테이블에 수집 결과를 저장합니다. */
    KblSyncRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 시즌 ID가 이미 있으면 시작·종료 연도를 갱신합니다. */
    @Transactional
    public void saveSeason(String seasonId, String startYear, String endYear) {
        jdbc.update("""
                INSERT INTO cc_season (id, start_year, end_year, data_status, created_date)
                VALUES (?, ?, ?, 'Y', CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE start_year = VALUES(start_year), end_year = VALUES(end_year),
                    modified_date = CURRENT_TIMESTAMP
                """, seasonId, startYear, endYear);
    }

    /** 구단 코드로 구단을 추가하거나 이름을 갱신합니다. */
    @Transactional
    public void saveTeams(List<KblClient.Team> teams) {
        jdbc.batchUpdate("""
                INSERT INTO cc_team (id, name, data_status, created_date)
                VALUES (?, ?, 'Y', CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE name = VALUES(name), modified_date = CURRENT_TIMESTAMP
                """, teams.stream().map(team -> new Object[]{team.code(), team.name()}).toList());
    }

    /** 시즌과 구단이 준비된 상태에서 참가팀별 순위를 한 번에 반영합니다. */
    @Transactional
    public void saveStandings(String seasonId, String leagueKey, List<KblClient.Standing> standings) {
        requireSeason(seasonId, leagueKey);
        Set<String> teamCodes = new HashSet<>(jdbc.queryForList("SELECT id FROM cc_team", String.class));
        for (KblClient.Standing standing : standings) {
            if (!teamCodes.contains(standing.teamCode())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "구단 " + standing.teamCode()
                        + "이(가) 누락되었습니다. 구단 정보 API(POST /api/kbl/teams/sync)를 먼저 호출해 주세요.");
            }
        }

        jdbc.batchUpdate("""
                INSERT INTO cc_season_team
                    (id, cc_season_id, cc_team_id, `rank`, win, lose, win_percentage, games_behind, data_status, created_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'Y', CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE `rank` = VALUES(`rank`), win = VALUES(win), lose = VALUES(lose),
                    win_percentage = VALUES(win_percentage), games_behind = VALUES(games_behind),
                    modified_date = CURRENT_TIMESTAMP
                """, standings.stream().map(standing -> new Object[]{
                seasonId + standing.teamCode(), seasonId, standing.teamCode(), standing.rank(), standing.wins(),
                standing.losses(), standing.winRate().setScale(2, RoundingMode.HALF_UP),
                standing.gamesBehind().setScale(1, RoundingMode.HALF_UP)
        }).toList());
    }

    /** 같은 시즌의 홈·원정 참가팀을 확인하고 경기 코드로 일정과 결과를 갱신합니다. */
    @Transactional
    public void saveMatches(String seasonId, String leagueKey, List<Match> matches) {
        requireSeason(seasonId, leagueKey);
        Set<String> seasonTeamIds = new HashSet<>(jdbc.queryForList(
                "SELECT id FROM cc_season_team WHERE cc_season_id = ?", String.class, seasonId));
        String standingsPath = "POST /api/kbl/seasons/" + leagueKey + "/standings/sync";
        if (seasonTeamIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "시즌 " + seasonId
                    + "의 참가팀이 누락되었습니다. 시즌별 순위 API(" + standingsPath + ")를 먼저 호출해 주세요.");
        }
        for (Match match : matches) {
            String homeSeasonTeamId = seasonId + match.homeTeamCode();
            String awaySeasonTeamId = seasonId + match.awayTeamCode();
            if (!seasonTeamIds.contains(homeSeasonTeamId) || !seasonTeamIds.contains(awaySeasonTeamId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "경기 " + match.code()
                        + "의 참가팀(" + homeSeasonTeamId + ", " + awaySeasonTeamId
                        + ")을 확인해 주세요. 시즌별 순위 API(" + standingsPath + ")를 먼저 호출해 주세요.");
            }
        }
        if (matches.isEmpty()) {
            return;
        }

        jdbc.batchUpdate("""
                INSERT INTO cc_season_match
                    (match_code, cc_season_id, match_datetime, match_status, home_season_team_id, away_season_team_id,
                     win_type, home_total_score, away_total_score, data_status, created_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'Y', CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE match_datetime = VALUES(match_datetime), match_status = VALUES(match_status),
                    home_season_team_id = VALUES(home_season_team_id), away_season_team_id = VALUES(away_season_team_id),
                    win_type = VALUES(win_type), home_total_score = VALUES(home_total_score),
                    away_total_score = VALUES(away_total_score), modified_date = CURRENT_TIMESTAMP
                """, matches.stream().map(match -> new Object[]{
                match.code(), seasonId, match.startsAt(), match.status().name(), seasonId + match.homeTeamCode(),
                seasonId + match.awayTeamCode(), match.winner(), match.homeScore(), match.awayScore()
        }).toList());
    }

    /** 선행 시즌 적재가 빠졌으면 호출할 API를 안내합니다. */
    private void requireSeason(String seasonId, String leagueKey) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cc_season WHERE id = ?)",
                Boolean.class, seasonId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "시즌 " + seasonId
                    + "이(가) 누락되었습니다. 시즌 정보 API(POST /api/kbl/seasons/" + leagueKey + "/sync)를 먼저 호출해 주세요.");
        }
    }

    enum MatchStatus {
        SCHEDULED, LIVE, FINISHED
    }

    record Match(
            String code,
            String homeTeamCode,
            String awayTeamCode,
            LocalDateTime startsAt,
            MatchStatus status,
            String winner,
            Integer homeScore,
            Integer awayScore
    ) {}
}
