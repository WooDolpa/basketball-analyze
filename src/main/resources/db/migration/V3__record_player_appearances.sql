-- 0초 경기의 실제 출전을 기록하고 기존 자료의 미확인을 구분합니다.
ALTER TABLE cc_season_match_player_record
    ADD COLUMN appeared BOOLEAN NULL COMMENT '실제 출전 여부(미확인 NULL)' AFTER is_starter,
    ADD CONSTRAINT chk_match_player_record_appeared CHECK (appeared IS NULL OR appeared IN (0, 1));

UPDATE cc_season_match_player_record
SET appeared = TRUE
WHERE playing_time_seconds > 0 OR is_starter = TRUE;
