package com.basketball.analyze.kbl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
class KblStandingsRepository {

    private final JdbcTemplate jdbc;

    /** 적재된 KBL 순위 데이터를 조회할 JDBC 도구를 연결합니다. */
    KblStandingsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 사용 상태인 시즌, 참가팀과 구단의 순위 스냅샷을 순위와 팀 코드 순으로 읽습니다. */
    List<StandingRow> findStandings(String seasonId) {
        return jdbc.query("""
                SELECT st.`rank`, t.id AS team_code, t.name AS team_name, st.win, st.lose,
                       st.win_percentage, st.games_behind
                FROM cc_season_team st
                JOIN cc_season s ON s.id = st.cc_season_id AND s.data_status = 'Y'
                JOIN cc_team t ON t.id = st.cc_team_id AND t.data_status = 'Y'
                WHERE st.cc_season_id = ? AND st.data_status = 'Y'
                ORDER BY st.`rank`, t.id
                """, (rs, rowNumber) -> new StandingRow(rs.getInt("rank"), rs.getString("team_code"),
                rs.getString("team_name"), rs.getInt("win"), rs.getInt("lose"),
                rs.getBigDecimal("win_percentage"), rs.getBigDecimal("games_behind")), seasonId);
    }

    record StandingRow(int rank, String teamCode, String teamName, int wins, int losses,
                       BigDecimal winRate, BigDecimal gamesBehind) {}
}
