package com.basketball.analyze.kbl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class KblSeasonPlayerRecordsRepository {

    private static final String RECORDS_SCOPE = """
            FROM cc_season_match_player_record r
            JOIN cc_season_match m ON m.id = r.cc_season_match_id
            JOIN cc_season s ON s.id = m.cc_season_id AND s.data_status = 'Y'
            JOIN cc_player p ON p.id = r.cc_player_id AND p.data_status = 'Y'
            JOIN cc_season_team st ON st.id = r.cc_season_team_id AND st.cc_season_id = m.cc_season_id
                AND st.data_status = 'Y'
            JOIN cc_team t ON t.id = st.cc_team_id AND t.data_status = 'Y'
            WHERE m.cc_season_id = ? AND m.match_status = 'FINISHED' AND m.data_status = 'Y' AND r.data_status = 'Y'
                AND r.cc_season_team_id IN (m.home_season_team_id, m.away_season_team_id)
            """;

    private final JdbcTemplate jdbc;

    /** 시즌 선수 기록을 읽는 JDBC 도구를 연결합니다. */
    KblSeasonPlayerRecordsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 선수 기록이 적재된 종료 경기 수와 출전 여부 미확인 행 수를 계산합니다. */
    Coverage findCoverage(String seasonId) {
        return jdbc.queryForObject("""
                SELECT COUNT(DISTINCT m.id) AS recorded_games, COALESCE(SUM(r.appeared IS NULL), 0) AS unverified
                """ + RECORDS_SCOPE, (row, index) -> new Coverage(row.getInt("recorded_games"), row.getInt("unverified")),
                seasonId);
    }

    /** 출전이 확인된 선수의 팀별 합계를 읽고, 지표 누락이 있으면 합계도 미확인으로 반환합니다. */
    List<PlayerTeamRow> findPlayerTeamTotals(String seasonId) {
        return jdbc.query("""
                SELECT p.code, p.name, t.id AS team_code, t.name AS team_name, COUNT(*) AS games,
                    CASE WHEN COUNT(r.points) = COUNT(*) THEN SUM(r.points) END AS points,
                    CASE WHEN COUNT(r.rebounds) = COUNT(*) THEN SUM(r.rebounds) END AS rebounds,
                    CASE WHEN COUNT(r.assists) = COUNT(*) THEN SUM(r.assists) END AS assists
                """ + RECORDS_SCOPE + """
                AND r.appeared = TRUE
                GROUP BY p.code, p.name, t.id, t.name
                ORDER BY p.code, t.id
                """, (row, index) -> new PlayerTeamRow(row.getString("code"), row.getString("name"),
                new KblMatchResponse.Team(row.getString("team_code"), row.getString("team_name")), row.getInt("games"),
                row.getObject("points", Integer.class), row.getObject("rebounds", Integer.class),
                row.getObject("assists", Integer.class)), seasonId);
    }

    record Coverage(int recordedGames, int unverifiedAppearances) {}

    record PlayerTeamRow(String code, String name, KblMatchResponse.Team team, int games,
                         Integer points, Integer rebounds, Integer assists) {}
}
