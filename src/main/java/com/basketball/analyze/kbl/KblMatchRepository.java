package com.basketball.analyze.kbl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
class KblMatchRepository {

    private static final String SELECT_MATCH_COLUMNS = """
            SELECT m.id, m.match_code, m.match_datetime, m.match_status,
                   home_team.id AS home_team_code, home_team.name AS home_team_name,
                   away_team.id AS away_team_code, away_team.name AS away_team_name,
                   m.home_total_score, m.away_total_score
            """;

    private static final String SELECT_FINISHED_MATCH_COLUMNS = """
            SELECT home_team.id AS home_team_code, away_team.id AS away_team_code, m.win_type
            """;

    private static final String FROM_ACTIVE_MATCHES = """
            FROM cc_season_match m
            JOIN cc_season s ON s.id = m.cc_season_id AND s.data_status = 'Y'
            JOIN cc_season_team home_st ON home_st.cc_season_id = m.cc_season_id
                AND home_st.id = m.home_season_team_id AND home_st.data_status = 'Y'
            JOIN cc_season_team away_st ON away_st.cc_season_id = m.cc_season_id
                AND away_st.id = m.away_season_team_id AND away_st.data_status = 'Y'
            JOIN cc_team home_team ON home_team.id = home_st.cc_team_id AND home_team.data_status = 'Y'
            JOIN cc_team away_team ON away_team.id = away_st.cc_team_id AND away_team.data_status = 'Y'
            WHERE m.cc_season_id = ? AND m.data_status = 'Y'
            """;

    private static final String KNOWN_FINISHED_MATCH = """
            AND m.match_status = 'FINISHED' AND m.win_type IN ('HOME', 'AWAY') AND m.match_datetime IS NOT NULL
            """;

    private final JdbcTemplate jdbc;

    /** KBL 수집 결과가 적재된 테이블을 조회합니다. */
    KblMatchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 해당 시즌이 사용 상태로 적재되어 있는지 확인합니다. */
    boolean hasActiveSeason(String seasonId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM cc_season WHERE id = ? AND data_status = 'Y'
                )
                """, Boolean.class, seasonId));
    }

    /** 사용 가능한 구단과 연결된 시즌 참가팀이 있는지 확인합니다. */
    boolean hasActiveSeasonTeam(String seasonId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM cc_season_team st
                    JOIN cc_team t ON t.id = st.cc_team_id AND t.data_status = 'Y'
                    WHERE st.cc_season_id = ? AND st.data_status = 'Y'
                )
                """, Boolean.class, seasonId));
    }

    /** 시작과 종료 시각을 모두 포함해 경기와 합계·쿼터 점수를 읽습니다. */
    List<ScheduleMatchRow> findMatches(String seasonId, LocalDateTime fromInclusive, LocalDateTime toInclusive) {
        return jdbc.query(SELECT_MATCH_COLUMNS + """
                , m.home_1q_score, m.away_1q_score,
                  m.home_2q_score, m.away_2q_score,
                  m.home_3q_score, m.away_3q_score,
                  m.home_4q_score, m.away_4q_score
                """ + FROM_ACTIVE_MATCHES + """
                AND m.match_datetime >= ? AND m.match_datetime <= ?
                ORDER BY m.match_datetime, m.match_code
                """, this::readScheduleMatchRow, seasonId, fromInclusive, toInclusive);
    }

    /** 조회 시각 이후부터 다음 날 자정 전까지 시작하는 예정 경기와 양 팀을 읽습니다. */
    List<MatchRow> findScheduledMatches(String seasonId, LocalDateTime queriedAt, LocalDateTime nextDayStart) {
        return jdbc.query(SELECT_MATCH_COLUMNS + FROM_ACTIVE_MATCHES + """
                AND m.match_status = 'SCHEDULED' AND m.match_datetime > ? AND m.match_datetime < ?
                ORDER BY m.match_datetime, m.match_code
                """, this::readMatchRow, seasonId, queriedAt, nextDayStart);
    }

    /** 점수가 미확인된 경우 SQL NULL을 유지하며 조회 행을 변환합니다. */
    private MatchRow readMatchRow(ResultSet rs, int rowNumber) throws SQLException {
        return new MatchRow(rs.getInt("id"), rs.getString("match_code"),
                rs.getObject("match_datetime", LocalDateTime.class), rs.getString("match_status"),
                rs.getString("home_team_code"), rs.getString("home_team_name"),
                rs.getString("away_team_code"), rs.getString("away_team_name"),
                rs.getObject("home_total_score", Integer.class), rs.getObject("away_total_score", Integer.class));
    }

    /** 기간 조회 행에서 실제 0점과 SQL NULL을 구분해 쿼터 점수를 읽습니다. */
    private ScheduleMatchRow readScheduleMatchRow(ResultSet rs, int rowNumber) throws SQLException {
        return new ScheduleMatchRow(readMatchRow(rs, rowNumber),
                rs.getObject("home_1q_score", Integer.class), rs.getObject("away_1q_score", Integer.class),
                rs.getObject("home_2q_score", Integer.class), rs.getObject("away_2q_score", Integer.class),
                rs.getObject("home_3q_score", Integer.class), rs.getObject("away_3q_score", Integer.class),
                rs.getObject("home_4q_score", Integer.class), rs.getObject("away_4q_score", Integer.class));
    }

    /** 오늘 자정 전에 시작해 종료된 사용 상태의 경기를 Elo 반영 순서로 읽습니다. */
    List<FinishedMatchRow> findFinishedMatchesBefore(String seasonId, LocalDateTime todayStart) {
        return jdbc.query(SELECT_FINISHED_MATCH_COLUMNS + """
                FROM cc_season_match m
                JOIN cc_season_team home_st ON home_st.cc_season_id = m.cc_season_id
                    AND home_st.id = m.home_season_team_id AND home_st.data_status = 'Y'
                JOIN cc_season_team away_st ON away_st.cc_season_id = m.cc_season_id
                    AND away_st.id = m.away_season_team_id AND away_st.data_status = 'Y'
                JOIN cc_team home_team ON home_team.id = home_st.cc_team_id AND home_team.data_status = 'Y'
                JOIN cc_team away_team ON away_team.id = away_st.cc_team_id AND away_team.data_status = 'Y'
                WHERE m.cc_season_id = ? AND m.match_status = 'FINISHED' AND m.data_status = 'Y'
                  AND m.win_type IN ('HOME', 'AWAY') AND m.match_datetime < ?
                ORDER BY m.match_datetime, m.match_code
                """, this::readFinishedMatchRow, seasonId, todayStart);
    }

    /** 경기 일시와 승리팀이 확인된 종료 경기를 최신 시작 일시부터 읽습니다. */
    List<FinishedMatchRow> findFinishedMatchesNewestFirst(String seasonId) {
        return jdbc.query(SELECT_FINISHED_MATCH_COLUMNS + FROM_ACTIVE_MATCHES + KNOWN_FINISHED_MATCH + """
                ORDER BY m.match_datetime DESC, m.match_code DESC
                """, this::readFinishedMatchRow, seasonId);
    }

    /** 선택한 팀이 홈 또는 원정으로 치른 최근 종료 경기와 승리팀을 읽습니다. */
    List<TeamMatchRow> findRecentTeamMatches(String seasonId, String teamCode, int limit) {
        return jdbc.query(SELECT_MATCH_COLUMNS + ", m.win_type\n" + FROM_ACTIVE_MATCHES + KNOWN_FINISHED_MATCH + """
                AND (home_team.id = ? OR away_team.id = ?)
                ORDER BY m.match_datetime DESC, m.match_code DESC
                LIMIT ?
                """, (rs, rowNumber) -> new TeamMatchRow(readMatchRow(rs, rowNumber),
                "HOME".equals(rs.getString("win_type"))), seasonId, teamCode, teamCode, limit);
    }

    /** 팀 코드를 문자열로 읽고 홈팀 승리 여부를 변환합니다. */
    private FinishedMatchRow readFinishedMatchRow(ResultSet rs, int rowNumber) throws SQLException {
        return new FinishedMatchRow(rs.getString("home_team_code"), rs.getString("away_team_code"),
                "HOME".equals(rs.getString("win_type")));
    }

    record FinishedMatchRow(String homeTeamCode, String awayTeamCode, boolean homeWon) {}

    record TeamMatchRow(MatchRow match, boolean homeWon) {}

    record MatchRow(
            int id,
            String matchCode,
            LocalDateTime startsAt,
            String status,
            String homeTeamCode,
            String homeTeamName,
            String awayTeamCode,
            String awayTeamName,
            Integer homeScore,
            Integer awayScore
    ) {}

    /** 기간별 경기 조회에 필요한 공통 경기 정보와 홈·원정 쿼터 점수입니다. */
    record ScheduleMatchRow(
            MatchRow match,
            Integer homeFirstQuarterScore,
            Integer awayFirstQuarterScore,
            Integer homeSecondQuarterScore,
            Integer awaySecondQuarterScore,
            Integer homeThirdQuarterScore,
            Integer awayThirdQuarterScore,
            Integer homeFourthQuarterScore,
            Integer awayFourthQuarterScore
    ) {}
}
