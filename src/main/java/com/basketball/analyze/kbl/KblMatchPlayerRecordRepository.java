package com.basketball.analyze.kbl;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
class KblMatchPlayerRecordRepository {

    private static final int UNKNOWN_PLUS_MINUS = 999;

    private final JdbcTemplate jdbc;

    /** 경기 선수 기록과 로스터에 접근하는 JDBC 객체를 사용합니다. */
    KblMatchPlayerRecordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 경기 상태·팀·점수와 선수 ID를 대조한 뒤 로스터와 기록을 한 트랜잭션에 반영합니다. */
    @Transactional
    public void saveMatchPlayerRecords(String seasonId, String leagueKey, String matchCode, List<KblClient.GamePlayerStat> rows) {
        FinishedMatch match = lockFinishedMatch(seasonId, matchCode);
        List<PlayerInMatch> players = validatePlayers(match, leagueKey, matchCode, rows);
        Map<String, String> playerIds = findPlayerIds(players);
        List<String> missingCodes = players.stream().map(PlayerInMatch::code)
                .filter(code -> !playerIds.containsKey(code)).toList();
        if (!missingCodes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "선수 기본 정보가 누락되었습니다. 선수 코드 " + String.join(", ", missingCodes));
        }

        jdbc.batchUpdate("""
                INSERT INTO cc_season_team_roster (cc_season_team_id, cc_player_id)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE cc_player_id = VALUES(cc_player_id)
                """, players.stream().map(player -> new Object[]{player.teamId(), playerIds.get(player.code())}).toList());
        saveRecords(match.id(), players, playerIds);
    }

    /** 경기 결과가 수집 도중 바뀌지 않도록 대상 종료 경기 행을 잠급니다. */
    private FinishedMatch lockFinishedMatch(String seasonId, String matchCode) {
        List<FinishedMatch> matches = jdbc.query("""
                SELECT m.id, m.home_season_team_id, m.away_season_team_id, h.cc_team_id AS home_code,
                    a.cc_team_id AS away_code, m.home_total_score, m.away_total_score
                FROM cc_season_match m
                JOIN cc_season_team h ON h.id = m.home_season_team_id
                JOIN cc_season_team a ON a.id = m.away_season_team_id
                WHERE m.cc_season_id = ? AND m.match_code = ? AND m.match_status = 'FINISHED' AND m.data_status = 'Y'
                FOR UPDATE
                """, (row, index) -> new FinishedMatch(row.getInt("id"),
                new MatchTeam(row.getString("home_season_team_id"), row.getString("home_code"), row.getInt("home_total_score")),
                new MatchTeam(row.getString("away_season_team_id"), row.getString("away_code"), row.getInt("away_total_score"))),
                seasonId, matchCode);
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "경기 " + matchCode + "의 저장된 종료 결과를 확인할 수 없습니다.");
        }
        return matches.getFirst();
    }

    /** 중복 선수, 홈·원정 소속, 개인 수치와 양 팀 득점 합계를 검사합니다. */
    private List<PlayerInMatch> validatePlayers(FinishedMatch match, String leagueKey, String matchCode,
                                                List<KblClient.GamePlayerStat> rows) {
        Set<String> codes = new HashSet<>();
        Map<String, Long> teamPoints = new HashMap<>();
        List<PlayerInMatch> players = new ArrayList<>(rows.size());
        for (KblClient.GamePlayerStat row : rows) {
            KblClient.GamePlayer player = row.player();
            if (player == null || player.code() == null || !player.code().matches("[0-9]{6}")) {
                throw invalidResponse(matchCode, "선수 코드가 없거나 올바르지 않습니다.");
            }
            if (!codes.add(player.code())) {
                throw invalidResponse(matchCode, "선수 코드 " + player.code() + "이(가) 중복되었습니다.");
            }

            MatchTeam team = matchTeam(match, row, matchCode);
            KblClient.PlayerBoxScore stats = row.records();
            validateBoxScore(stats, matchCode, player.code());
            Integer playingTime = playingTimeSeconds(stats, matchCode, player.code());
            Integer startFlag = row.startFlag();
            if (startFlag != null && startFlag != 0 && startFlag != 1) {
                throw invalidResponse(matchCode, "선수 " + player.code() + "의 선발 값이 올바르지 않습니다.");
            }
            Boolean starter = startFlag == null ? null : startFlag == 1;
            Boolean appeared = appearance(starter, playingTime, stats, matchCode, player.code());
            teamPoints.merge(team.id(), stats.points().longValue(), Long::sum);
            players.add(new PlayerInMatch(player.code(), team.id(), starter, appeared, playingTime, stats));
        }

        Long homePoints = teamPoints.get(match.home().id());
        Long awayPoints = teamPoints.get(match.away().id());
        if (homePoints == null || awayPoints == null) {
            throw invalidResponse(matchCode, "양 팀의 선수 기록이 제공되지 않았습니다.");
        }
        if (homePoints != match.home().points() || awayPoints != match.away().points()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "경기 " + matchCode + "의 선수 득점 합계 "
                    + homePoints + "–" + awayPoints + "이(가) 저장된 최종점수 " + match.home().points() + "–"
                    + match.away().points() + "와 일치하지 않습니다. 경기 수집 API(POST /api/kbl/seasons/"
                    + leagueKey + "/matches/sync)로 경기 결과를 확인해 주세요.");
        }
        players.sort(Comparator.comparing(PlayerInMatch::teamId).thenComparing(PlayerInMatch::code));
        return players;
    }

    /** 원본 홈·원정 표시와 팀 코드가 모두 저장된 경기 참가팀과 일치하는지 확인합니다. */
    private MatchTeam matchTeam(FinishedMatch match, KblClient.GamePlayerStat row, String matchCode) {
        if ("1".equals(row.homeAway()) && match.home().code().equals(row.player().teamCode())) {
            return match.home();
        }
        if ("2".equals(row.homeAway()) && match.away().code().equals(row.player().teamCode())) {
            return match.away();
        }
        throw invalidResponse(matchCode, "선수 " + row.player().code() + "의 홈·원정 소속이 저장된 경기와 일치하지 않습니다.");
    }

    /** 확인된 수치의 저장 범위, 슈팅 성공·시도 관계와 합계를 검증합니다. */
    private void validateBoxScore(KblClient.PlayerBoxScore stats, String matchCode, String playerCode) {
        if (stats == null || stats.points() == null) {
            throw invalidResponse(matchCode, "선수 " + playerCode + "의 기록 또는 득점이 누락되었습니다.");
        }
        Integer[] counts = {
                stats.points(), stats.offensiveRebounds(), stats.defensiveRebounds(), stats.rebounds(), stats.assists(),
                stats.steals(), stats.blocks(), stats.turnovers(), stats.fouls(), stats.fieldGoalsMade(),
                stats.fieldGoalsAttempted(), stats.twoPointersMade(), stats.twoPointersAttempted(),
                stats.threePointersMade(), stats.threePointersAttempted(), stats.freeThrowsMade(), stats.freeThrowsAttempted()
        };
        for (Integer count : counts) {
            if (count != null && (count < 0 || count > Short.MAX_VALUE)) {
                throw invalidResponse(matchCode, "선수 " + playerCode + "의 기록이 저장 가능한 범위를 벗어났습니다.");
            }
        }
        Integer plusMinus = stats.plusMinus();
        if ((plusMinus != null && (plusMinus < Short.MIN_VALUE || plusMinus > Short.MAX_VALUE))
                || exceedsAttempts(stats.fieldGoalsMade(), stats.fieldGoalsAttempted())
                || exceedsAttempts(stats.twoPointersMade(), stats.twoPointersAttempted())
                || exceedsAttempts(stats.threePointersMade(), stats.threePointersAttempted())
                || exceedsAttempts(stats.freeThrowsMade(), stats.freeThrowsAttempted())
                || differsFromSum(stats.fieldGoalsMade(), stats.twoPointersMade(), stats.threePointersMade())
                || differsFromSum(stats.fieldGoalsAttempted(), stats.twoPointersAttempted(), stats.threePointersAttempted())
                || differsFromSum(stats.rebounds(), stats.offensiveRebounds(), stats.defensiveRebounds())) {
            throw invalidResponse(matchCode, "선수 " + playerCode + "의 슈팅·리바운드 합계 또는 득실 마진이 올바르지 않습니다.");
        }
    }

    /** 성공과 시도가 모두 제공됐으면 성공 횟수가 시도 횟수를 넘는지 확인합니다. */
    private boolean exceedsAttempts(Integer made, Integer attempted) {
        return made != null && attempted != null && made > attempted;
    }

    /** 세 값이 모두 제공된 경우에만 원본 전체 값과 구성 값의 합을 비교합니다. */
    private boolean differsFromSum(Integer total, Integer first, Integer second) {
        return total != null && first != null && second != null && total != first + second;
    }

    /** 분·초를 검증하고 두 값을 모두 알 수 있을 때 출전시간을 초로 변환합니다. */
    private Integer playingTimeSeconds(KblClient.PlayerBoxScore stats, String matchCode, String playerCode) {
        Integer minutes = stats.minutes();
        Integer seconds = stats.seconds();
        if ((minutes != null && (minutes < 0 || minutes > (Integer.MAX_VALUE - 59) / 60))
                || (seconds != null && (seconds < 0 || seconds > 59))) {
            throw invalidResponse(matchCode, "선수 " + playerCode + "의 출전시간이 올바르지 않습니다.");
        }
        return minutes == null || seconds == null ? null : minutes * 60 + seconds;
    }

    /** 출전시간과 선발·교체 증거를 합쳐 실제 출전 여부를 판정합니다. */
    private Boolean appearance(Boolean starter, Integer playingTime, KblClient.PlayerBoxScore stats,
                               String matchCode, String playerCode) {
        Integer inout = stats.inout();
        Integer inout1 = stats.inout1();
        if ((inout != null && inout < 0) || (inout1 != null && inout1 < 0)) {
            throw invalidResponse(matchCode, "선수 " + playerCode + "의 교체 기록이 올바르지 않습니다.");
        }
        if (Boolean.TRUE.equals(starter) || (playingTime != null && playingTime > 0)
                || (inout != null && inout > 0) || (inout1 != null && inout1 > 0)) {
            return true;
        }
        return starter == null || playingTime == null || inout == null || inout1 == null ? null : false;
    }

    /** 경기에 포함된 외부 선수 코드의 내부 ID를 한 번에 확인합니다. */
    private Map<String, String> findPlayerIds(List<PlayerInMatch> players) {
        String placeholders = String.join(", ", Collections.nCopies(players.size(), "?"));
        Map<String, String> ids = new HashMap<>();
        jdbc.query("SELECT code, id FROM cc_player WHERE code IN (" + placeholders + ")",
                row -> { ids.put(row.getString("code"), row.getString("id")); },
                players.stream().map(PlayerInMatch::code).toArray());
        return ids;
    }

    /** 경기와 선수의 복합 키로 신규 기록을 저장하거나 정정된 값을 갱신합니다. */
    private void saveRecords(int matchId, List<PlayerInMatch> players, Map<String, String> playerIds) {
        jdbc.batchUpdate("""
                INSERT INTO cc_season_match_player_record (
                    cc_season_match_id, cc_player_id, cc_season_team_id, is_starter, appeared, playing_time_seconds,
                    points, offensive_rebounds, defensive_rebounds, rebounds, assists, steals, blocks, turnovers, fouls,
                    field_goals_made, field_goals_attempted, two_pointers_made, two_pointers_attempted,
                    three_pointers_made, three_pointers_attempted, free_throws_made, free_throws_attempted,
                    plus_minus, data_status, created_date
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Y', CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE cc_season_team_id = VALUES(cc_season_team_id), is_starter = VALUES(is_starter),
                    appeared = VALUES(appeared),
                    playing_time_seconds = VALUES(playing_time_seconds), points = VALUES(points),
                    offensive_rebounds = VALUES(offensive_rebounds), defensive_rebounds = VALUES(defensive_rebounds),
                    rebounds = VALUES(rebounds), assists = VALUES(assists), steals = VALUES(steals), blocks = VALUES(blocks),
                    turnovers = VALUES(turnovers), fouls = VALUES(fouls), field_goals_made = VALUES(field_goals_made),
                    field_goals_attempted = VALUES(field_goals_attempted), two_pointers_made = VALUES(two_pointers_made),
                    two_pointers_attempted = VALUES(two_pointers_attempted), three_pointers_made = VALUES(three_pointers_made),
                    three_pointers_attempted = VALUES(three_pointers_attempted), free_throws_made = VALUES(free_throws_made),
                    free_throws_attempted = VALUES(free_throws_attempted), plus_minus = VALUES(plus_minus),
                    modified_date = CURRENT_TIMESTAMP
                """, players.stream().map(player -> recordValues(matchId, playerIds.get(player.code()), player)).toList());
    }

    /** 검증한 박스스코어를 저장 순서로 배치하고 미확인 득실 마진 표시는 NULL로 바꿉니다. */
    private Object[] recordValues(int matchId, String playerId, PlayerInMatch player) {
        KblClient.PlayerBoxScore stats = player.stats();
        Integer plusMinus = Integer.valueOf(UNKNOWN_PLUS_MINUS).equals(stats.plusMinus()) ? null : stats.plusMinus();
        return new Object[]{
                matchId, playerId, player.teamId(), player.starter(), player.appeared(), player.playingTimeSeconds(), stats.points(),
                stats.offensiveRebounds(), stats.defensiveRebounds(), stats.rebounds(), stats.assists(), stats.steals(),
                stats.blocks(), stats.turnovers(), stats.fouls(), stats.fieldGoalsMade(), stats.fieldGoalsAttempted(),
                stats.twoPointersMade(), stats.twoPointersAttempted(), stats.threePointersMade(), stats.threePointersAttempted(),
                stats.freeThrowsMade(), stats.freeThrowsAttempted(), plusMinus
        };
    }

    /** 사용할 수 없는 선수 기록의 경기 코드와 이유를 반환합니다. */
    private ResponseStatusException invalidResponse(String matchCode, String detail) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "KBL 경기 " + matchCode + " 선수 기록 오류. " + detail);
    }

    private record FinishedMatch(int id, MatchTeam home, MatchTeam away) {}

    private record MatchTeam(String id, String code, int points) {}

    private record PlayerInMatch(String code, String teamId, Boolean starter, Boolean appeared, Integer playingTimeSeconds,
                                 KblClient.PlayerBoxScore stats) {}
}
