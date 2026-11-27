package com.basketball.analyze.kbl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Repository
class KblPlayerRepository {

    private static final String INSERT_PLAYER = """
            INSERT INTO cc_player
                (id, code, name, eng_name, birth_date, nationality, division, height, weight, data_status, created_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'Y', CURRENT_TIMESTAMP)
            """;

    private final JdbcTemplate jdbc;

    /** 선수 기본 정보를 저장하는 JDBC 접근 객체를 사용합니다. */
    KblPlayerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 코드가 같은 선수는 기존 ID를 유지하고 확인된 기본 정보만 갱신합니다. */
    @Transactional
    public void savePlayers(List<Player> players) {
        jdbc.batchUpdate(INSERT_PLAYER + """
                ON DUPLICATE KEY UPDATE name = VALUES(name),
                    eng_name = COALESCE(VALUES(eng_name), eng_name), birth_date = COALESCE(VALUES(birth_date), birth_date),
                    nationality = COALESCE(VALUES(nationality), nationality), division = COALESCE(VALUES(division), division),
                    height = COALESCE(VALUES(height), height), weight = COALESCE(VALUES(weight), weight),
                    modified_date = CURRENT_TIMESTAMP
                """, playerValues(players));
    }

    /** 이미 적재된 선수 코드를 확인해 보완 요청에서 중복 프로필 조회를 건너뜁니다. */
    Set<String> findExistingCodes() {
        return new HashSet<>(jdbc.queryForList("SELECT code FROM cc_player", String.class));
    }

    /** 보완 중 같은 선수 코드가 적재되면 기존 행을 유지하고 신규 선수만 추가합니다. */
    @Transactional
    public void insertMissingPlayers(List<Player> players) {
        jdbc.batchUpdate(INSERT_PLAYER + "ON DUPLICATE KEY UPDATE id = id", playerValues(players));
    }

    /** 기존 내부 ID를 유지하며 신규 선수에게만 시퀀스 ID를 할당합니다. */
    private List<Object[]> playerValues(List<Player> players) {
        Map<String, String> idsByCode = new HashMap<>();
        Set<String> usedIds = new HashSet<>();
        jdbc.query("SELECT id, code FROM cc_player", row -> {
            String id = row.getString("id");
            idsByCode.put(row.getString("code"), id);
            usedIds.add(id.toUpperCase(Locale.ROOT));
        });

        return players.stream().sorted(Comparator.comparing(Player::code)).map(player -> new Object[]{
                idsByCode.computeIfAbsent(player.code(), code -> nextPlayerId(usedIds)),
                player.code(), player.name(), player.englishName(), player.birthDate(), player.nationality(),
                player.division(), player.height(), player.weight()
        }).toList();
    }

    /** DB 시퀀스로 독립적인 다섯 자리 ID를 만들고 기존에 사용한 ID는 건너뜁니다. */
    private String nextPlayerId(Set<String> usedIds) {
        String id;
        do {
            Long value = jdbc.queryForObject("SELECT NEXT VALUE FOR cc_player_id_seq", Long.class);
            String encoded = Long.toString(value, 36).toUpperCase(Locale.ROOT);
            id = "0".repeat(5 - encoded.length()) + encoded;
        } while (!usedIds.add(id));
        return id;
    }

    record Player(String code, String name, String englishName, String birthDate, String nationality,
                  String division, BigDecimal height, BigDecimal weight) {}

}
