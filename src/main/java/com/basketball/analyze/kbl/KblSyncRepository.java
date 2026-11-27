package com.basketball.analyze.kbl;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
class KblSyncRepository {

    private static final LocalTime LAST_DATABASE_TIME = LocalTime.MAX.truncatedTo(ChronoUnit.MICROS);

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

    /** 소속을 내부 ID로 연결한 뒤 복합 키로 신규 관계만 저장합니다. */
    @Transactional
    public void saveRosters(String seasonId, String leagueKey, List<KblPlayerClient.RegisteredPlayer> players) {
        requireSeason(seasonId, leagueKey);
        Map<String, String> seasonTeamIds = new HashMap<>();
        jdbc.query("SELECT cc_team_id, id FROM cc_season_team WHERE cc_season_id = ?", row -> {
            seasonTeamIds.put(row.getString("cc_team_id"), row.getString("id"));
        }, seasonId);
        Map<String, String> playerIds = new HashMap<>();
        jdbc.query("SELECT code, id FROM cc_player", row -> {
            playerIds.put(row.getString("code"), row.getString("id"));
        });

        List<KblPlayerClient.RegisteredPlayer> ordered = players.stream()
                .sorted(Comparator.comparing(KblPlayerClient.RegisteredPlayer::teamCode)
                        .thenComparing(KblPlayerClient.RegisteredPlayer::code))
                .toList();
        List<Object[]> rows = new ArrayList<>(players.size());
        for (KblPlayerClient.RegisteredPlayer player : ordered) {
            String teamId = seasonTeamIds.get(player.teamCode());
            if (teamId == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "시즌 " + seasonId + "의 참가팀 "
                        + player.teamCode() + "이(가) 누락되었습니다. 시즌 순위 API(POST /api/kbl/seasons/"
                        + leagueKey + "/standings/sync)를 먼저 호출해 주세요.");
            }
            String playerId = playerIds.get(player.code());
            if (playerId == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "선수 " + player.code()
                        + "이(가) 누락되었습니다. 선수 기본 정보 API(POST /api/kbl/players/sync)를 먼저 호출해 주세요.");
            }
            rows.add(new Object[]{teamId, playerId});
        }
        jdbc.batchUpdate("""
                INSERT INTO cc_season_team_roster (cc_season_team_id, cc_player_id)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE cc_player_id = VALUES(cc_player_id)
                """, rows);
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

    /** 한국시간 시작일과 종료일을 포함해 사용 상태인 종료 경기 코드를 시작 시각순으로 읽습니다. */
    List<String> findFinishedMatchCodes(String seasonId, LocalDate fromDate, LocalDate toDate) {
        return jdbc.queryForList("""
                SELECT match_code FROM cc_season_match
                WHERE cc_season_id = ? AND match_status = 'FINISHED' AND data_status = 'Y'
                    AND match_datetime >= ? AND match_datetime <= ?
                ORDER BY match_datetime, match_code
                """, String.class, seasonId, fromDate.atStartOfDay(), toDate.atTime(LAST_DATABASE_TIME));
    }

    /** 쿼터 상세 조회에 앞서 현재 저장된 사용 상태의 종료 경기인지 확인합니다. */
    boolean hasFinishedMatch(String seasonId, String matchCode) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM cc_season_match
                    WHERE cc_season_id = ? AND match_code = ? AND match_status = 'FINISHED' AND data_status = 'Y'
                )
                """, Boolean.class, seasonId, matchCode));
    }

    /** 경기 행을 잠근 뒤 홈·원정팀과 최종점수를 대조하고 1~4쿼터 점수를 갱신합니다. */
    @Transactional
    public void saveMatchQuarters(String seasonId, String leagueKey, MatchQuarters match) {
        List<Integer> matchIds = jdbc.queryForList("""
                SELECT id FROM cc_season_match
                WHERE cc_season_id = ? AND match_code = ? AND match_status = 'FINISHED' AND data_status = 'Y'
                    AND home_season_team_id = ? AND away_season_team_id = ?
                    AND home_total_score = ? AND away_total_score = ?
                FOR UPDATE
                """, Integer.class, seasonId, match.matchCode(),
                seasonId + match.homeTeamCode(), seasonId + match.awayTeamCode(), match.homeTotal(), match.awayTotal());
        if (matchIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "경기 " + match.matchCode()
                    + "의 팀, 종료 상태 또는 최종점수가 저장된 경기와 일치하지 않습니다. 경기 수집 API(POST /api/kbl/seasons/"
                    + leagueKey + "/matches/sync)로 해당 경기를 갱신한 뒤 다시 호출해 주세요.");
        }

        QuarterScore first = match.quarters().getFirst();
        QuarterScore second = match.quarters().get(1);
        QuarterScore third = match.quarters().get(2);
        QuarterScore fourth = match.quarters().get(3);
        jdbc.update("""
                UPDATE cc_season_match
                SET home_1q_score = ?, away_1q_score = ?, home_2q_score = ?, away_2q_score = ?,
                    home_3q_score = ?, away_3q_score = ?, home_4q_score = ?, away_4q_score = ?,
                    modified_date = CURRENT_TIMESTAMP
                WHERE id = ?
                """, first.homeScore(), first.awayScore(), second.homeScore(), second.awayScore(),
                third.homeScore(), third.awayScore(), fourth.homeScore(), fourth.awayScore(), matchIds.getFirst());
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

    /** 기존 경기와 대조할 팀·최종점수와 실제로 저장할 정규 쿼터 점수를 담습니다. */
    record MatchQuarters(
            String matchCode,
            String homeTeamCode,
            String awayTeamCode,
            int homeTotal,
            int awayTotal,
            List<QuarterScore> quarters
    ) {}

    /** 홈·원정의 한 쿼터 점수를 담습니다. */
    record QuarterScore(int quarter, int homeScore, int awayScore) {}
}
