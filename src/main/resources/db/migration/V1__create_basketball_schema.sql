-- basketball-analyze.erd의 초기 MariaDB 스키마.
-- 적용 이후 스키마 변경은 새 Flyway 버전 파일에 작성한다.

-- 시즌
CREATE TABLE `cc_season` (
    `id` VARCHAR(9) NOT NULL COMMENT '시즌 아이디',
    `start_year` VARCHAR(4) NOT NULL COMMENT '시작년도',
    `end_year` VARCHAR(4) NOT NULL COMMENT '종료년도',
    `data_status` CHAR(1) NOT NULL COMMENT '데이터 상태 (Y : 사용, N : 미사용, D : 삭제)',
    `created_date` TIMESTAMP NULL COMMENT '등록일',
    `modified_date` TIMESTAMP NULL COMMENT '수정일',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='시즌';

-- 팀
CREATE TABLE `cc_team` (
    `id` VARCHAR(3) NOT NULL COMMENT '팀아이디',
    `name` VARCHAR(10) NOT NULL COMMENT '팀명',
    `eng_name` VARCHAR(20) NULL COMMENT '영문 이름',
    `data_status` CHAR(1) NOT NULL COMMENT '데이터 상태 (Y : 사용, N : 미사용, D : 삭제)',
    `created_date` TIMESTAMP NULL COMMENT '등록일',
    `modified_date` TIMESTAMP NULL COMMENT '수정일',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팀';

-- 선수
CREATE TABLE `cc_player` (
    `id` VARCHAR(5) NOT NULL COMMENT '선수 아이디',
    `name` VARCHAR(10) NOT NULL COMMENT '선수 이름',
    `eng_name` VARCHAR(30) NULL COMMENT '선수 이름(영문)',
    `code` VARCHAR(6) NOT NULL COMMENT '선수 코드',
    `profile_url` VARCHAR(128) NULL COMMENT '프로필 이미지',
    `birth_date` VARCHAR(8) NULL COMMENT '생년월일',
    `nationality` VARCHAR(10) NULL COMMENT '국적',
    `division` CHAR(1) NULL COMMENT '선수 구분(국내 : 0, 외국인 : 1, 아시아쿼터 : 2)',
    `height` DECIMAL(5,2) NOT NULL COMMENT '키',
    `weight` DECIMAL(5,2) NULL COMMENT '몸무게',
    `retirement` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '은퇴여부',
    `data_status` CHAR(1) NOT NULL COMMENT '데이터 상태 (Y : 사용, N : 미사용, D : 삭제)',
    `created_date` TIMESTAMP NULL COMMENT '등록일',
    `modified_date` TIMESTAMP NULL COMMENT '수정일',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='선수';

-- 시즌 팀
CREATE TABLE `cc_season_team` (
    `id` VARCHAR(12) NOT NULL COMMENT '시즌 팀 아이디(시즌 아이디 + 팀아이디)',
    `cc_season_id` VARCHAR(9) NOT NULL COMMENT '시즌 아이디',
    `cc_team_id` VARCHAR(3) NOT NULL COMMENT '팀아이디',
    `rank` TINYINT(2) NOT NULL COMMENT '순위',
    `win` TINYINT(2) NOT NULL COMMENT '승리',
    `lose` TINYINT(2) NOT NULL COMMENT '패배',
    `win_percentage` DECIMAL(3,2) NOT NULL COMMENT '승률',
    `games_behind` DECIMAL(3,1) NOT NULL COMMENT '승차',
    `data_status` CHAR(1) NOT NULL COMMENT '데이터 상태 (Y : 사용, N : 미사용, D : 삭제)',
    `created_date` TIMESTAMP NULL COMMENT '등록일',
    `modified_date` TIMESTAMP NULL COMMENT '수정일',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_cc_season_team_season_id` (`cc_season_id`, `id`),
    CONSTRAINT `fk_season_team_season` FOREIGN KEY (`cc_season_id`) REFERENCES `cc_season` (`id`),
    CONSTRAINT `fk_season_team_team` FOREIGN KEY (`cc_team_id`) REFERENCES `cc_team` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='시즌 팀';

-- 시즌 팀 로스터
CREATE TABLE `cc_season_team_roster` (
    `cc_season_team_id` VARCHAR(12) NOT NULL COMMENT '시즌 팀 아이디(시즌 아이디 + 팀아이디)',
    `cc_player_id` VARCHAR(5) NOT NULL COMMENT '선수 아이디',
    `transfer` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '이적여부',
    `injury` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '부상여부',
    `game_count` SMALLINT NULL COMMENT '해당 시즌 소속팀 출전 경기 수',
    `playing_time_seconds` INT NULL COMMENT '해당 시즌 소속팀 누적 출전시간(초)',
    `points` INT NULL COMMENT '해당 시즌 소속팀 누적 득점',
    `offensive_rebounds` INT NULL COMMENT '해당 시즌 소속팀 누적 공격 리바운드',
    `defensive_rebounds` INT NULL COMMENT '해당 시즌 소속팀 누적 수비 리바운드',
    `rebounds` INT NULL COMMENT '해당 시즌 소속팀 누적 전체 리바운드',
    `assists` INT NULL COMMENT '해당 시즌 소속팀 누적 어시스트',
    `steals` INT NULL COMMENT '해당 시즌 소속팀 누적 스틸',
    `blocks` INT NULL COMMENT '해당 시즌 소속팀 누적 블록',
    `turnovers` INT NULL COMMENT '해당 시즌 소속팀 누적 턴오버',
    `fouls` INT NULL COMMENT '해당 시즌 소속팀 누적 파울',
    `field_goals_made` INT NULL COMMENT '해당 시즌 소속팀 누적 전체 야투 성공 수',
    `field_goals_attempted` INT NULL COMMENT '해당 시즌 소속팀 누적 전체 야투 시도 수',
    `two_pointers_made` INT NULL COMMENT '해당 시즌 소속팀 누적 2점슛 성공 수',
    `two_pointers_attempted` INT NULL COMMENT '해당 시즌 소속팀 누적 2점슛 시도 수',
    `three_pointers_made` INT NULL COMMENT '해당 시즌 소속팀 누적 3점슛 성공 수',
    `three_pointers_attempted` INT NULL COMMENT '해당 시즌 소속팀 누적 3점슛 시도 수',
    `free_throws_made` INT NULL COMMENT '해당 시즌 소속팀 누적 자유투 성공 수',
    `free_throws_attempted` INT NULL COMMENT '해당 시즌 소속팀 누적 자유투 시도 수',
    PRIMARY KEY (`cc_season_team_id`, `cc_player_id`),
    CONSTRAINT `fk_roster_season_team` FOREIGN KEY (`cc_season_team_id`) REFERENCES `cc_season_team` (`id`),
    CONSTRAINT `fk_roster_player` FOREIGN KEY (`cc_player_id`) REFERENCES `cc_player` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='시즌 팀 로스터';

-- 시즌경기
CREATE TABLE `cc_season_match` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '시즌경기 아이디',
    `match_code` VARCHAR(30) NOT NULL COMMENT 'KBL 경기 코드(재수집 중복 방지)',
    `cc_season_id` VARCHAR(9) NOT NULL COMMENT '시즌 아이디',
    `match_datetime` DATETIME NULL COMMENT '예정 경기 시작 일시(KST), 일시 미정 NULL',
    `match_status` VARCHAR(10) NOT NULL DEFAULT 'SCHEDULED' COMMENT '경기 상태(SCHEDULED: 예정, LIVE: 진행, FINISHED: 종료, POSTPONED: 연기, CANCELLED: 취소)',
    `home_season_team_id` VARCHAR(12) NOT NULL COMMENT '홈팀 시즌 팀 아이디(경기와 동일 시즌)',
    `away_season_team_id` VARCHAR(12) NOT NULL COMMENT '원정팀 시즌 팀 아이디(경기와 동일 시즌)',
    `win_type` VARCHAR(4) NULL COMMENT '승리팀(HOME, AWAY), 경기 종료 전 NULL',
    `home_1q_score` TINYINT(2) NULL COMMENT '홈팀 1쿼터 점수(미진행·미확인 NULL)',
    `home_2q_score` TINYINT(2) NULL COMMENT '홈팀 2쿼터 점수(미진행·미확인 NULL)',
    `home_3q_score` TINYINT(2) NULL COMMENT '홈팀 3쿼터 점수(미진행·미확인 NULL)',
    `home_4q_score` TINYINT(2) NULL COMMENT '홈팀 4쿼터 점수(미진행·미확인 NULL)',
    `home_total_score` SMALLINT NULL COMMENT '홈팀 점수(진행 중 현재 점수, 종료 시 연장 포함 최종점수), 미확인 NULL',
    `away_1q_score` TINYINT(2) NULL COMMENT '원정팀 1쿼터 점수(미진행·미확인 NULL)',
    `away_2q_score` TINYINT(2) NULL COMMENT '원정팀 2쿼터 점수(미진행·미확인 NULL)',
    `away_3q_score` TINYINT(2) NULL COMMENT '원정팀 3쿼터 점수(미진행·미확인 NULL)',
    `away_4q_score` TINYINT(2) NULL COMMENT '원정팀 4쿼터 점수(미진행·미확인 NULL)',
    `away_total_score` SMALLINT NULL COMMENT '원정팀 점수(진행 중 현재 점수, 종료 시 연장 포함 최종점수), 미확인 NULL',
    `data_status` CHAR(1) NOT NULL COMMENT '데이터 상태 (Y : 사용, N : 미사용, D : 삭제)',
    `created_date` TIMESTAMP NULL COMMENT '등록일',
    `modified_date` TIMESTAMP NULL COMMENT '수정일',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_match_match_code` (`match_code`),
    CONSTRAINT `fk_match_season` FOREIGN KEY (`cc_season_id`) REFERENCES `cc_season` (`id`),
    CONSTRAINT `fk_match_home_season_team` FOREIGN KEY (`cc_season_id`, `home_season_team_id`) REFERENCES `cc_season_team` (`cc_season_id`, `id`),
    CONSTRAINT `fk_match_away_season_team` FOREIGN KEY (`cc_season_id`, `away_season_team_id`) REFERENCES `cc_season_team` (`cc_season_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='시즌경기';

-- 시즌 경기 연장 차수별 점수
CREATE TABLE `cc_season_match_overtime` (
    `cc_season_match_id` INT NOT NULL COMMENT '시즌경기 아이디',
    `overtime_no` SMALLINT NOT NULL COMMENT '연장 차수(1부터 시작)',
    `home_score` SMALLINT NULL COMMENT '해당 연장의 홈팀 점수(미확인 NULL)',
    `away_score` SMALLINT NULL COMMENT '해당 연장의 원정팀 점수(미확인 NULL)',
    `created_date` TIMESTAMP NULL COMMENT '등록일',
    `modified_date` TIMESTAMP NULL COMMENT '수정일',
    PRIMARY KEY (`cc_season_match_id`, `overtime_no`),
    CONSTRAINT `fk_overtime_match` FOREIGN KEY (`cc_season_match_id`) REFERENCES `cc_season_match` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='시즌 경기 연장 차수별 점수';

-- 경기별 선수 개인기록(연장 포함)
CREATE TABLE `cc_season_match_player_record` (
    `cc_season_match_id` INT NOT NULL COMMENT '시즌경기 아이디',
    `cc_player_id` VARCHAR(5) NOT NULL COMMENT '선수 아이디',
    `cc_season_team_id` VARCHAR(12) NOT NULL COMMENT '경기 당시 소속 시즌 팀 아이디',
    `is_starter` BOOLEAN NULL COMMENT '선발 출전 여부(미확인 NULL)',
    `playing_time_seconds` INT NULL COMMENT '해당 경기 전체 출전시간(초, 연장 포함)',
    `points` SMALLINT NULL COMMENT '해당 경기 득점',
    `offensive_rebounds` SMALLINT NULL COMMENT '해당 경기 공격 리바운드',
    `defensive_rebounds` SMALLINT NULL COMMENT '해당 경기 수비 리바운드',
    `rebounds` SMALLINT NULL COMMENT '해당 경기 전체 리바운드',
    `assists` SMALLINT NULL COMMENT '해당 경기 어시스트',
    `steals` SMALLINT NULL COMMENT '해당 경기 스틸',
    `blocks` SMALLINT NULL COMMENT '해당 경기 블록',
    `turnovers` SMALLINT NULL COMMENT '해당 경기 턴오버',
    `fouls` SMALLINT NULL COMMENT '해당 경기 파울',
    `field_goals_made` SMALLINT NULL COMMENT '해당 경기 전체 야투 성공 수',
    `field_goals_attempted` SMALLINT NULL COMMENT '해당 경기 전체 야투 시도 수',
    `two_pointers_made` SMALLINT NULL COMMENT '해당 경기 2점슛 성공 수',
    `two_pointers_attempted` SMALLINT NULL COMMENT '해당 경기 2점슛 시도 수',
    `three_pointers_made` SMALLINT NULL COMMENT '해당 경기 3점슛 성공 수',
    `three_pointers_attempted` SMALLINT NULL COMMENT '해당 경기 3점슛 시도 수',
    `free_throws_made` SMALLINT NULL COMMENT '해당 경기 자유투 성공 수',
    `free_throws_attempted` SMALLINT NULL COMMENT '해당 경기 자유투 시도 수',
    `plus_minus` SMALLINT NULL COMMENT '해당 선수 출전 중 팀 득실 마진(음수 허용)',
    `data_status` CHAR(1) NOT NULL COMMENT '데이터 상태(Y: 사용, N: 미사용, D: 삭제)',
    `created_date` TIMESTAMP NULL COMMENT '등록일',
    `modified_date` TIMESTAMP NULL COMMENT '수정일',
    PRIMARY KEY (`cc_season_match_id`, `cc_player_id`),
    KEY `idx_match_player_record_player_match` (`cc_player_id`, `cc_season_match_id`),
    CONSTRAINT `fk_match_player_record_match` FOREIGN KEY (`cc_season_match_id`) REFERENCES `cc_season_match` (`id`),
    CONSTRAINT `fk_match_player_record_roster` FOREIGN KEY (`cc_season_team_id`, `cc_player_id`) REFERENCES `cc_season_team_roster` (`cc_season_team_id`, `cc_player_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='경기별 선수 개인기록(연장 포함)';

-- 경기 상태·결과·점수의 무결성 검사(MariaDB).
ALTER TABLE cc_season_match
    ADD CONSTRAINT chk_cc_season_match_different_teams
        CHECK (home_season_team_id <> away_season_team_id),
    ADD CONSTRAINT chk_cc_season_match_status
        CHECK (match_status IN ('SCHEDULED', 'LIVE', 'FINISHED', 'POSTPONED', 'CANCELLED')),
    ADD CONSTRAINT chk_cc_season_match_win_type
        CHECK (win_type IS NULL OR win_type IN ('HOME', 'AWAY')),
    ADD CONSTRAINT chk_cc_season_match_nonnegative_scores
        CHECK ((home_1q_score IS NULL OR home_1q_score >= 0)
            AND (home_2q_score IS NULL OR home_2q_score >= 0)
            AND (home_3q_score IS NULL OR home_3q_score >= 0)
            AND (home_4q_score IS NULL OR home_4q_score >= 0)
            AND (home_total_score IS NULL OR home_total_score >= 0)
            AND (away_1q_score IS NULL OR away_1q_score >= 0)
            AND (away_2q_score IS NULL OR away_2q_score >= 0)
            AND (away_3q_score IS NULL OR away_3q_score >= 0)
            AND (away_4q_score IS NULL OR away_4q_score >= 0)
            AND (away_total_score IS NULL OR away_total_score >= 0)),
    ADD CONSTRAINT chk_cc_season_match_result
        CHECK (
            (match_status = 'FINISHED'
                AND home_total_score IS NOT NULL
                AND away_total_score IS NOT NULL
                AND win_type IS NOT NULL
                AND ((home_total_score > away_total_score AND win_type = 'HOME')
                    OR (away_total_score > home_total_score AND win_type = 'AWAY')))
            OR (match_status <> 'FINISHED' AND win_type IS NULL)
        );

ALTER TABLE cc_season_match_overtime
    ADD CONSTRAINT chk_cc_season_match_overtime_no
        CHECK (overtime_no >= 1),
    ADD CONSTRAINT chk_cc_season_match_overtime_nonnegative_scores
        CHECK ((home_score IS NULL OR home_score >= 0)
            AND (away_score IS NULL OR away_score >= 0));

-- 선수 경기 기록의 수치·집계 무결성 검사(MariaDB).
ALTER TABLE cc_season_match_player_record
    ADD CONSTRAINT chk_match_player_record_nonnegative
        CHECK ((playing_time_seconds IS NULL OR playing_time_seconds >= 0)
            AND (points IS NULL OR points >= 0)
            AND (offensive_rebounds IS NULL OR offensive_rebounds >= 0)
            AND (defensive_rebounds IS NULL OR defensive_rebounds >= 0)
            AND (rebounds IS NULL OR rebounds >= 0)
            AND (assists IS NULL OR assists >= 0)
            AND (steals IS NULL OR steals >= 0)
            AND (blocks IS NULL OR blocks >= 0)
            AND (turnovers IS NULL OR turnovers >= 0)
            AND (fouls IS NULL OR fouls >= 0)
            AND (field_goals_made IS NULL OR field_goals_made >= 0)
            AND (field_goals_attempted IS NULL OR field_goals_attempted >= 0)
            AND (two_pointers_made IS NULL OR two_pointers_made >= 0)
            AND (two_pointers_attempted IS NULL OR two_pointers_attempted >= 0)
            AND (three_pointers_made IS NULL OR three_pointers_made >= 0)
            AND (three_pointers_attempted IS NULL OR three_pointers_attempted >= 0)
            AND (free_throws_made IS NULL OR free_throws_made >= 0)
            AND (free_throws_attempted IS NULL OR free_throws_attempted >= 0)),
    ADD CONSTRAINT chk_match_player_record_shooting
        CHECK ((field_goals_made IS NULL OR field_goals_attempted IS NULL OR field_goals_made <= field_goals_attempted)
            AND (two_pointers_made IS NULL OR two_pointers_attempted IS NULL OR two_pointers_made <= two_pointers_attempted)
            AND (three_pointers_made IS NULL OR three_pointers_attempted IS NULL OR three_pointers_made <= three_pointers_attempted)
            AND (free_throws_made IS NULL OR free_throws_attempted IS NULL OR free_throws_made <= free_throws_attempted)),
    ADD CONSTRAINT chk_match_player_record_field_goals_made
        CHECK (field_goals_made IS NULL OR two_pointers_made IS NULL OR three_pointers_made IS NULL
            OR field_goals_made = two_pointers_made + three_pointers_made),
    ADD CONSTRAINT chk_match_player_record_field_goals_attempted
        CHECK (field_goals_attempted IS NULL OR two_pointers_attempted IS NULL OR three_pointers_attempted IS NULL
            OR field_goals_attempted = two_pointers_attempted + three_pointers_attempted),
    ADD CONSTRAINT chk_match_player_record_rebounds
        CHECK (rebounds IS NULL OR offensive_rebounds IS NULL OR defensive_rebounds IS NULL
            OR rebounds = offensive_rebounds + defensive_rebounds),
    ADD CONSTRAINT chk_match_player_record_starter
        CHECK (is_starter IS NULL OR is_starter IN (0, 1)),
    ADD CONSTRAINT chk_match_player_record_data_status
        CHECK (data_status IN ('Y', 'N', 'D'));
