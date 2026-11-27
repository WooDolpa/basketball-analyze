-- 등록 명단의 신장 미제공을 NULL로 저장하고 선수 코드 중복을 방지합니다.
ALTER TABLE `cc_player`
    MODIFY COLUMN `height` DECIMAL(5,2) NULL COMMENT '키(cm, 미확인 NULL)',
    ADD CONSTRAINT `uq_player_code` UNIQUE (`code`);

-- 내부 ID는 기존 다섯 자리 형식을 유지하며 36진수로 표시합니다.
CREATE SEQUENCE `cc_player_id_seq` START WITH 1 MAXVALUE 60466175 NOCYCLE;
