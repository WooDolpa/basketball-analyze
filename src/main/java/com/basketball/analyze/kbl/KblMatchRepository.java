package com.basketball.analyze.kbl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
class KblMatchRepository {

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

    /** 조회 시각 이후부터 다음 날 자정 전까지 시작하는 예정 경기와 양 팀을 읽습니다. */
    List<MatchRow> findScheduledMatches(String seasonId, LocalDateTime queriedAt, LocalDateTime nextDayStart) {
        return jdbc.query("""
                SELECT m.id, m.match_code, m.match_datetime, m.match_status,
                       home_team.id AS home_team_code, home_team.name AS home_team_name,
                       away_team.id AS away_team_code, away_team.name AS away_team_name
                FROM cc_season_match m
                JOIN cc_season s ON s.id = m.cc_season_id AND s.data_status = 'Y'
                JOIN cc_season_team home_st ON home_st.cc_season_id = m.cc_season_id
                    AND home_st.id = m.home_season_team_id AND home_st.data_status = 'Y'
                JOIN cc_season_team away_st ON away_st.cc_season_id = m.cc_season_id
                    AND away_st.id = m.away_season_team_id AND away_st.data_status = 'Y'
                JOIN cc_team home_team ON home_team.id = home_st.cc_team_id AND home_team.data_status = 'Y'
                JOIN cc_team away_team ON away_team.id = away_st.cc_team_id AND away_team.data_status = 'Y'
                WHERE m.cc_season_id = ? AND m.match_status = 'SCHEDULED' AND m.data_status = 'Y'
                  AND m.match_datetime > ? AND m.match_datetime < ?
                ORDER BY m.match_datetime, m.match_code
                """, (rs, rowNum) -> new MatchRow(rs.getInt("id"), rs.getString("match_code"),
                rs.getObject("match_datetime", LocalDateTime.class), rs.getString("match_status"),
                rs.getString("home_team_code"), rs.getString("home_team_name"),
                rs.getString("away_team_code"), rs.getString("away_team_name")), seasonId, queriedAt, nextDayStart);
    }

    record MatchRow(
            int id,
            String matchCode,
            LocalDateTime startsAt,
            String status,
            String homeTeamCode,
            String homeTeamName,
            String awayTeamCode,
            String awayTeamName
    ) {}
}
