# 데이터 저장 설계와 승부예측 준비 상태

정리 기준일: **2026-11-06**. 설계 원본은 [basketball-analyze.erd](../basketball-analyze.erd)이며, [프로젝트 현황](project-status.md)과 [API 조사](kbl-api-data.md)를 함께 참고합니다. API 조사 수치·샘플의 기준일은 **2026-10-26**입니다.

## 반영 범위와 구현 상태

이 문서의 현재 설계는 ERD에 반영한 테이블·컬럼·관계·DDL 후처리 정의를 뜻합니다. 실제 DB 생성·적재, Entity·수집기·업무 API·예측 모델 구현 완료를 뜻하지 않습니다. 이번 작업에서는 DB 적용과 빌드·자동 테스트를 실행하지 않았습니다.

ERD의 DB 종류는 MariaDB로 설정했습니다. CHECK 제약조건은 `settings.ddlScripts.after`에 있으며, SQL 내보내기와 DB 적용 시 후처리 구문이 포함되는지 확인해야 합니다. JPA 설정은 `ddl-auto: none`이므로 스키마 적용 방식도 별도로 정해야 합니다.

## 현재 테이블 구성

| 테이블 | 컬럼 수 | 역할 | PK |
| --- | ---: | --- | --- |
| `cc_season` | 6 | 시즌 기본정보 | `id` |
| `cc_team` | 6 | 팀 기본정보 | `id` |
| `cc_player` | 14 | 선수 기본정보·신체 정보·은퇴 여부 | `id` |
| `cc_season_team` | 11 | 시즌별 참가 팀·현재 순위와 성적 | `id` |
| `cc_season_team_roster` | 23 | 시즌 팀 소속 선수·선수 누적 기록 | `cc_season_team_id`, `cc_player_id` |
| `cc_season_match` | 21 | 경기 일정·홈원정·진행 상태·쿼터 및 최종점수 | `id` |
| `cc_season_match_overtime` | 6 | 경기별 연장 차수 점수 | `cc_season_match_id`, `overtime_no` |
| `cc_season_match_player_record` | 26 | 경기별 선수 개인기록, 연장 포함 | `cc_season_match_id`, `cc_player_id` |

### 공통 식별·단위·결측 규칙

- 선수·팀 외부 코드는 문자열로 보존합니다. `cc_player.id`는 `VARCHAR(5)`인 내부 키, `cc_player.code`는 `VARCHAR(6)`인 외부 선수 코드이므로 구분합니다. 팀 코드도 앞자리 0을 유지합니다.
- `cc_season_team.id`는 시즌 ID와 팀 ID를 조합하는 `VARCHAR(12)` 키입니다. 조합·외부 시즌 코드 매핑은 수집 구현 시 명확히 정의해야 합니다.
- 선수 키·몸무게는 `DECIMAL(5,2)`이며 cm·kg 단위로 저장하는 방향입니다. 승차는 `DECIMAL(3,1)`입니다.
- 출전시간은 초 단위 정수입니다. 날짜·시각의 변환 규칙을 통일하고 `match_datetime`은 한국시간(KST)으로 다룹니다.
- 기록 미진행·미확인은 `NULL`, 확인된 무기록은 `0`으로 구분합니다. 미확인 선발 여부를 `FALSE`로 채우지 않습니다.
- `data_status`는 사용·미사용·삭제 상태이며 경기 진행 상태·부상 상태와 별개입니다.
- 현재 로스터의 `injury`, `transfer`와 선수의 `retirement`는 기본값 `FALSE`입니다. 외부 자료로 확인되지 않은 값을 정상 출전·이적 없음·활동 중의 확정 근거로 사용하지 않습니다. 미확인 상태 표현과 시점 이력은 보완 대상입니다.
- `cc_season_team.win_percentage`는 현재 `DECIMAL(3,2)`입니다. 수집 구현 전에 승률을 0~1 비율로 저장할지 0~100 백분율로 저장할지 확정하고 자료형 범위를 맞춰야 합니다.

## 시즌별 선수 누적 기록

`cc_season_team_roster` 한 행은 특정 시즌 팀에 연결된 선수 한 명입니다. 기존 시즌 팀·선수 키와 이적·부상 여부에 다음 누적 기록 **19개 컬럼**을 추가했습니다.

| 컬럼 | 자료형 | 설명 |
| --- | --- | --- |
| `game_count` | `SMALLINT` | 해당 시즌 해당 팀 출전 경기 수 |
| `playing_time_seconds` | `INT` | 누적 출전시간(초) |
| `points` | `INT` | 누적 득점 |
| `offensive_rebounds`, `defensive_rebounds`, `rebounds` | `INT` | 공격·수비·전체 리바운드 |
| `assists`, `steals`, `blocks` | `INT` | 어시스트·스틸·블록 |
| `turnovers`, `fouls` | `INT` | 턴오버·파울 |
| `field_goals_made`, `field_goals_attempted` | `INT` | 전체 야투 성공·시도 |
| `two_pointers_made`, `two_pointers_attempted` | `INT` | 2점슛 성공·시도 |
| `three_pointers_made`, `three_pointers_attempted` | `INT` | 3점슛 성공·시도 |
| `free_throws_made`, `free_throws_attempted` | `INT` | 자유투 성공·시도 |

19개 기록은 모두 `NULL`을 허용하고 0 기본값을 두지 않았습니다. 진행 중 시즌은 조회 시점까지의 누적값입니다. 시즌 중 이적한 선수는 해당 팀에서 기록한 값과 시즌 전체 합계를 구분해야 하며 API 반환 방식은 수집 전에 확인해야 합니다.

평균 기록은 누적값 ÷ 경기 수, 성공률은 성공 수 ÷ 시도 수로 계산하는 방향입니다. 분모 0·결측 처리를 정의해야 합니다. 시즌·통산 한 경기 최다 기록은 누적과 별개이며 저장 구조는 아직 없습니다.

## 경기·연장전

### cc_season_match

| 컬럼 | 자료형 | 저장 규칙 |
| --- | --- | --- |
| `id` | `INT` | 자동 증가 PK |
| `match_code` | `VARCHAR(30)` | KBL 경기 코드, NOT NULL·UNIQUE |
| `cc_season_id` | `VARCHAR(9)` | 시즌 FK, NOT NULL |
| `match_datetime` | `DATETIME` | 예정 시작 일시(KST), 일시 미정이면 NULL |
| `match_status` | `VARCHAR(10)` | NOT NULL, 기본값 `SCHEDULED` |
| `home_season_team_id`, `away_season_team_id` | `VARCHAR(12)` | 홈·원정 시즌 팀, NOT NULL |
| `win_type` | `VARCHAR(4)` | 종료 시 `HOME` 또는 `AWAY`, 종료 전 NULL |
| `home_1q_score`~`home_4q_score` | `TINYINT(2)` | 홈팀 쿼터별 점수, 미진행·미확인 NULL |
| `away_1q_score`~`away_4q_score` | `TINYINT(2)` | 원정팀 쿼터별 점수, 미진행·미확인 NULL |
| `home_total_score`, `away_total_score` | `SMALLINT` | 진행 중 현재 점수, 종료 시 연장 포함 최종점수, 미확인 NULL |
| `data_status` | `CHAR(1)` | 데이터 상태, NOT NULL |
| `created_date`, `modified_date` | `TIMESTAMP` | 등록·수정일 |

진행 상태는 `SCHEDULED`(예정), `LIVE`(진행), `FINISHED`(종료), `POSTPONED`(연기), `CANCELLED`(취소)를 사용합니다. API 원본 상태 코드와의 변환 규칙은 아직 구현하지 않았습니다.

최종점수는 128점 이상을 담을 수 있도록 `SMALLINT`로 조정했습니다. `TINYINT(3)`의 표시 폭은 저장 범위를 늘리지 않습니다.

시즌 FK에 더해 홈·원정 각각 `(cc_season_id, 시즌 팀 ID)`를 `cc_season_team.(cc_season_id, id)`와 연결하는 복합 FK를 정의했습니다. 부모에 `uq_cc_season_team_season_id` UNIQUE 인덱스를 추가해 같은 시즌 참가 팀만 연결하도록 했습니다.

### cc_season_match_overtime

| 컬럼 | 자료형 | 설명 |
| --- | --- | --- |
| `cc_season_match_id` | `INT` | 경기 FK, 복합 PK |
| `overtime_no` | `SMALLINT` | 1부터 시작하는 연장 차수, 복합 PK |
| `home_score`, `away_score` | `SMALLINT` | 해당 연장의 점수, 미확인 NULL |
| `created_date`, `modified_date` | `TIMESTAMP` | 등록·수정일 |

연장이 없으면 행을 만들지 않습니다. 경기 전체 최종점수는 경기 테이블에, 각 연장 구간 점수는 이 테이블에 저장합니다.

## 경기별 선수 개인기록

`cc_season_match_player_record`의 한 행은 선수 한 명의 특정 경기 전체 기록이며 연장을 포함합니다. 총 **26개 컬럼**입니다.

| 컬럼 | 자료형 | 설명 |
| --- | --- | --- |
| `cc_season_match_id` | `INT` | 경기 ID, 복합 PK·FK |
| `cc_player_id` | `VARCHAR(5)` | 선수 ID, 복합 PK |
| `cc_season_team_id` | `VARCHAR(12)` | 경기 당시 소속 시즌 팀, NOT NULL |
| `is_starter` | `BOOLEAN` | 선발 여부, 미확인 NULL |
| `playing_time_seconds` | `INT` | 해당 경기 전체 출전시간(초) |
| `points` | `SMALLINT` | 득점 |
| `offensive_rebounds`, `defensive_rebounds`, `rebounds` | `SMALLINT` | 공격·수비·전체 리바운드 |
| `assists`, `steals`, `blocks` | `SMALLINT` | 어시스트·스틸·블록 |
| `turnovers`, `fouls` | `SMALLINT` | 턴오버·파울 |
| `field_goals_made`, `field_goals_attempted` | `SMALLINT` | 전체 야투 성공·시도 |
| `two_pointers_made`, `two_pointers_attempted` | `SMALLINT` | 2점슛 성공·시도 |
| `three_pointers_made`, `three_pointers_attempted` | `SMALLINT` | 3점슛 성공·시도 |
| `free_throws_made`, `free_throws_attempted` | `SMALLINT` | 자유투 성공·시도 |
| `plus_minus` | `SMALLINT` | 해당 선수 출전 중 팀 득실 마진, 음수 허용 |
| `data_status` | `CHAR(1)` | 데이터 상태, NOT NULL |
| `created_date`, `modified_date` | `TIMESTAMP` | 등록·수정일 |

- PK는 `(cc_season_match_id, cc_player_id)`이며 경기별 선수 중복 기록을 방지합니다.
- `cc_season_match_id`는 `cc_season_match.id`를 참조합니다.
- `(cc_season_team_id, cc_player_id)`는 `cc_season_team_roster`의 복합 PK를 참조합니다.
- `idx_match_player_record_player_match`는 `(cc_player_id, cc_season_match_id)`의 조회 인덱스입니다. 경기 날짜순 조회는 경기 테이블과 연결해 처리합니다.
- 평균·성공률은 원본 기록에서 계산합니다. 고급 효율 지표의 별도 컬럼은 아직 없습니다.
- 실제 출전 여부는 API의 출전 정보를 기준으로 판단하며, 출전시간 0만으로 미출전을 단정하지 않습니다.

## 제약조건과 향후 저장 로직의 구분

### ERD에 정의한 제약조건

| 대상 | 정의 내용 |
| --- | --- |
| 경기 | 홈·원정팀이 서로 다름, 허용된 경기 상태·승리팀 값, 점수 음수 방지 |
| 경기 결과 | `FINISHED`이면 양 팀 최종점수·승리팀 필수, 동점 불가, 승리팀과 점수 대소관계 일치. 그 외 상태는 승리팀 NULL |
| 연장 | 연장 차수 1 이상, 점수 음수 방지 |
| 선수 경기 기록 | 출전시간·기본 기록 음수 방지, 득실 마진은 음수 허용 |
| 선수 슈팅 | 성공 수 ≤ 시도 수, 전체 야투 성공·시도 = 2점 + 3점 |
| 선수 리바운드 | 전체 = 공격 + 수비 |
| 선수 경기 관리 값 | 선발 여부 NULL·0·1, 데이터 상태 Y·N·D |

합계·비교 CHECK는 관계된 값이 모두 알려졌을 때 검사하도록 정의했습니다. NULL 허용은 미확인 값 보존을 위한 것이며 기록 완전성을 보장하지 않습니다. PK·UNIQUE·FK와 이 CHECK들은 실제 DB 적용 전까지 실행된 제약조건이 아닙니다.

### 수집·저장 구현 시 필요한 검증

- 선수 경기 기록의 소속팀이 해당 경기의 홈팀 또는 원정팀이며 경기와 같은 시즌인지 확인합니다. 현재 경기 FK와 로스터 FK만으로 이 조건 전체가 보장되지는 않습니다.
- 경기 최종점수와 1~4쿼터·모든 연장 점수의 합계를 대조합니다.
- 선수·팀·시즌 기록 집계의 기준, API 필드 의미와 정정 처리 방식을 확인합니다. 불일치를 임의로 0 또는 합계 값으로 덮어쓰지 않습니다.
- 실제 출전한 선수, 등록만 된 선수, 미확인 출전 상태를 구분합니다.
- 선수 시즌 누적은 경기별 원본과 대조하고 이적 전후 구단별 기록을 중복 집계하지 않습니다.

위 로직은 현재 구현하지 않았습니다.

## 승부예측 데이터 충분성 검토

현재 ERD는 기본 승부예측 모델을 시작할 기반을 갖췄습니다. 다만 실제 데이터 수집량·누락·정확성과 예측 성능은 별도 검증이 필요하며, 테이블 구성이 성능을 보장하지 않습니다.

| 초기 예측 입력 후보 | 현재 구조에서의 확보 방법 |
| --- | --- |
| 과거 승패·점수차 | 경기 결과 |
| 홈·원정별 성적 | 홈·원정팀과 과거 결과 집계 |
| 최근 5·10경기 승률·평균 득실점 | 예측 기준 시점 이전에 완료된 경기 집계 |
| 휴식 기간·최근 일정 밀집도 | 과거 경기 일시로 계산 |
| 선수별 최근 출전시간·득점·슈팅 | 과거 선수 경기 기록 집계 |
| Elo 등 팀 전력 점수 | 시간순 과거 경기 결과로 계산 |

이 계산값을 모두 원본 테이블 컬럼으로 추가할 필요는 없습니다. 입력 생성 규칙과 당시 입력값을 보존하는 방식을 먼저 정해야 합니다.

### 가장 우선할 시점 관리

예를 들어 11월 10일 경기를 예측하면서 11월 15일까지 갱신된 시즌 승률·선수 누적값을 사용하면 미래 정보가 섞입니다. 최신 누적값을 과거 예측에 그대로 사용하지 않고 기준 시점 이전 경기에서 다시 집계하거나 당시 입력 스냅샷을 사용해야 합니다.

경기 시작·종료·기록 공개·수집·정정 시각을 구분합니다. 현재 `match_datetime`은 예정 시작 시각이고, `created_date`·`modified_date`만으로 정보의 적용 시점과 당시 공개 여부를 재현할 수는 없습니다. 과거 원본으로 다시 집계해도 나중에 정정된 값만 남아 있으면 당시 정보와 완전히 같다고 보장할 수 없습니다.

대상 경기의 실제 선발·출전시간·결과는 사전 예측 입력에 넣지 않습니다. 이전 경기의 선수 기록과 예측 전에 확인된 당일 정보만 사용합니다. 데이터 분할·전처리·변수 계산도 기준 시점을 지켜야 합니다. [데이터 누출 안내](https://scikit-learn.org/stable/common_pitfalls), [시간순 검증 안내](https://scikit-learn.org/stable/modules/cross_validation.html)

### 아직 ERD에 반영하지 않은 보완안

아래 이름과 범위는 검토 후보이며 추가 승인·상세 설계가 필요합니다. 기본 모델을 시작하기 전에 모두 구현해야 한다는 뜻은 아닙니다.

| 항목 | 제안 구조·내용 | 목적 |
| --- | --- | --- |
| 예측 입력 보관 | 입력 스냅샷, 예측 기준 시각·사용 데이터 범위·변수 정의 버전 | 당시 예측 입력 재현 |
| 예측 결과 이력 | `cc_season_match_prediction` 후보: 경기·예측 시각·홈 승률·모델 버전·입력 스냅샷 연결 | 경기 전 확률 보존·실제 결과 평가 |
| 경기별 팀 기록 | `cc_season_match_team_record` 후보: 팀 API의 경기별 슈팅·리바운드·턴오버 등 | 팀 단위 통계 입력 확보·선수 기록과 대조 |
| 선수 출전 가능 상태 | `cc_player_status_history` 후보: 상태·적용 시점·확인 시각·출처 | 부상·회복·출전 불확실성 재현 |
| 소속 기간 | 로스터 시작·종료 기간 또는 별도 소속 이력 | 이적 전후 소속 시점 구분 |
| 수집·정정 이력 | 원본 출처·수집 시각·응답 버전·원본 보관 | 데이터 출처와 과거 정보 재현 |
| 추가 맥락 | 감독 재임 이력·경기장·중립 경기·시즌별 규정·대회 구분 | 여러 시즌·조건 차이 반영 검토 |

KBL의 부상·당일 출전 정보 제공 여부는 기존 조사에서 미확인입니다. 부상 여부를 얻지 못한 상태를 정상 출전으로 채우지 않습니다. 감독도 경기 당시 이름만 확인한 범위이며, 현재 코칭스태프·개인 프로필·재임 기간은 추가 조사 대상입니다.

여러 시즌을 사용할 때는 규정 변경·과거 당시 소속과 데이터 완전성을 구분합니다. 최근 5시즌 API 조사 범위는 모델 학습 범위의 확정과 별개입니다.

### 다음 검토 순서

1. 예측 기준 시점과 입력 스냅샷·수집 및 정정 이력의 최소 범위를 정합니다.
2. 수집·저장·DB 적용 범위를 확정하고, 확보한 과거 기록으로 Elo 등 기본 모델을 비교합니다.
3. 통계 모델에 필요한 경기별 팀 기록과 예측 결과 이력을 구체화합니다.
4. 확보 가능한 출처에 맞춰 선수 출전 상태·소속 기간·추가 맥락을 확장합니다.

평가는 날짜순으로 진행하고 적중률과 함께 Brier 점수·Log Loss로 승률의 품질도 확인하는 방향입니다. 학습 시즌·변수·가중치·평가 구간·모델은 아직 확정하거나 구현하지 않았습니다. [확률 평가 안내](https://sklearn.org/stable/modules/calibration.html)

## 수행한 확인과 남은 확인

- ERD 작업에서 공식 JSON 스키마, 컬럼·FK·인덱스 참조, 변경 범위와 diff 검사를 수행했습니다.
- 이번 문서 갱신은 실제 ERD의 8개 테이블·컬럼 수·키·자료형·DDL 후처리 정의와 대조했습니다.
- GUI 최종 표시·내보낸 SQL·실제 MariaDB 제약조건·수집기·예측 동작은 이번에 검증하지 않았습니다.
- 빌드·자동 테스트와 DB 적용은 실행하지 않았습니다.
