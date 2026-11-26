package com.basketball.analyze.kbl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
class KblMatchRepository {

    private static final String SELECT_ACTIVE_MATCHES = """
            SELECT m.id, m.match_code, m.match_datetime, m.match_status,
                   home_team.id AS home_team_code, home_team.name AS home_team_name,
                   away_team.id AS away_team_code, away_team.name AS away_team_name,
                   m.home_total_score, m.away_total_score
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

    /** 시작과 종료 시각을 모두 포함해 기간에 해당하는 경기와 점수를 읽습니다. */
    List<MatchRow> findMatches(String seasonId, LocalDateTime fromInclusive, LocalDateTime toInclusive) {
        return jdbc.query(SELECT_ACTIVE_MATCHES + """
                AND m.match_datetime >= ? AND m.match_datetime <= ?
                ORDER BY m.match_datetime, m.match_code
                """, this::readMatchRow, seasonId, fromInclusive, toInclusive);
    }

    /** 조회 시각 이후부터 다음 날 자정 전까지 시작하는 예정 경기와 양 팀을 읽습니다. */
    List<MatchRow> findScheduledMatches(String seasonId, LocalDateTime queriedAt, LocalDateTime nextDayStart) {
        return jdbc.query(SELECT_ACTIVE_MATCHES + """
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

    /** 오늘 자정 전에 시작해 종료된 사용 상태의 경기를 Elo 반영 순서로 읽습니다. */
    List<FinishedMatchRow> findFinishedMatchesBefore(String seasonId, LocalDateTime todayStart) {
        return jdbc.query("""
                SELECT home_team.id AS home_team_code, away_team.id AS away_team_code, m.win_type
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
                """, (rs, rowNum) -> new FinishedMatchRow(rs.getString("home_team_code"),
                rs.getString("away_team_code"), "HOME".equals(rs.getString("win_type"))), seasonId, todayStart);
    }

    record FinishedMatchRow(String homeTeamCode, String awayTeamCode, boolean homeWon) {}

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
}
