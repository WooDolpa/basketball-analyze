# 프로젝트 현황과 검토 사항

정리 기준일은 **2026-11-27(한국시간)**입니다. 최근 5시즌 전수 조사 기준일은 **2026-10-26**이며, 첫 적재에 필요한 이번 시즌 API 표본은 **2026-11-17**에 다시 확인했습니다.

## 문서 안내

| 문서 | 역할 |
| --- | --- |
| [Repository Guidelines](../AGENTS.md) | 작업 규칙·구조·컨벤션 |
| [화면 구성](dashboard-ui.md) | 메뉴별 화면·정적 UI 구조·실행 방법 |
| [KBL API 데이터 정리](kbl-api-data.md) | 수집 항목·실제 샘플·조회 경로·검증 결과 |
| [데이터 저장 설계와 예측 준비 상태](data-model.md) | 현재 ERD의 테이블·키·저장 규칙, 예측 데이터 보완안 |
| [로컬 MariaDB 실행·백업·복원](../docker/README.md) | Docker Compose, Flyway 마이그레이션, PC 간 DB 이전 방법 |

## 확정된 방향

- 분석 대상은 국내 남자 프로농구 KBL입니다.
- 로컬에서 개인적으로 사용하는 웹 분석·승부예측 시스템을 목표로 합니다.
- 데이터는 우선 KBL 공식 사이트가 사용하는 조회 API에서 확보하는 방향으로 조사했습니다.
- 화면은 HTML·CSS·JavaScript의 역할을 분리하고, 메뉴별 독립 페이지로 구성합니다.
- 기능 구현 범위는 사용자가 정합니다. 코드 작업 전 변경안을 설명하고 확인받습니다.
- 현재 데이터 조사 범위는 이번 시즌을 포함한 최근 5시즌입니다. 이는 모델의 학습 범위를 확정한 결정은 아닙니다.

## 구현과 조사 상태

2026–2027 정규리그(`S49G01`)의 경기 일정·쿼터 점수, 리그 순위, 팀관리, 선수 기록 화면을 로컬 데이터에 연결했습니다. 2026-11-27에 재시작한 Windows 앱에서 선수 기록 150명과 집계 대상 경기 74건을 확인했습니다. 세 부문을 선택할 때 리더 카드와 전체 기록표가 함께 정렬됐습니다.

| 영역 | 현재 상태 |
| --- | --- |
| 기반 환경 | Java 25, Spring Boot 4.1.1, Gradle, MariaDB, JPA, QueryDSL 7.6 설정 |
| 로컬 DB 운영 | Docker MariaDB 11.4.13, 기본 주소 `127.0.0.1:3307/basketball`. Windows 개발 DB의 `V3` 적용과 현재 적재 건수 확인 |
| 웹 화면 | 대시보드·경기 일정·팀관리·리그 순위·선수 기록 구현. 다섯 화면에 차콜·오렌지·우드 베이지 공통 테마 적용 |
| 팀 메뉴 | `팀 > 팀관리` 2Depth, 펼침·접힘 및 독립 페이지 이동 구현 |
| 화면 데이터 | 대시보드의 오늘 예정 경기·Elo, 경기 일정·쿼터 점수, 2026–2027 리그 순위·팀관리·선수 기록은 API 사용. 대시보드 시즌 지표·CSV, 2025–2026 순위·팀·선수 기록은 예시 데이터 사용 |
| 데이터 확보 | 공식 사이트 조회 API의 접근성과 제공 필드 조사·검증 완료 범위 존재 |
| 데이터 저장 설계 | Flyway `V1`으로 기본 테이블 8개를 생성하고 `V2`로 선수 코드 고유 제약과 내부 ID 시퀀스 추가. `V3`로 경기별 출전 여부 추가. Entity 구현은 미진행 |
| 수집·저장 기능 | 시즌·구단·순위·경기 일정과 결과·쿼터 점수·선수 기본 정보·시즌 로스터·경기별 선수 기록 적재와 누락 선수 보완. 개발 DB에서 종료 경기 74건의 쿼터 및 선수 기록 1,776행, 선수 기본 정보 224건 저장 확인. 수동 호출로 갱신 |
| 업무 백엔드 | **API 16개**. 수집·저장 `POST` 10개와 조회 `GET` 6개. [구현 API 목록](#구현-api-목록) |
| 사진 저장 | 로컬 파일 보관 방식 제안 단계, 실제 다운로드·제공 기능 미구현 |
| 승률 예측 | Elo API와 대시보드 연동 완료. 빈 목록 검수 완료, 실제 예정 경기의 계산값과 카드·상세 화면 검수는 경기일에 진행 |
| 로컬 LLM | 모델 선정·실행·추가 학습 미진행 |

## 구현 API 목록

아래 경로 앞에는 `/api/kbl`을 붙입니다. 시즌 파라미터 `{leagueKey}`에는 `S49G01`을 사용합니다. 표에는 업무 로직이 직접 읽거나 쓰는 테이블을 적었습니다.

| 메서드·경로 | 용도 | 읽기 | 쓰기 |
| --- | --- | --- | --- |
| `POST /seasons/{leagueKey}/sync` | 시즌 기본 정보 적재 | — | `cc_season` |
| `POST /teams/sync` | 구단 코드·이름 적재 | — | `cc_team` |
| `POST /seasons/{leagueKey}/standings/sync` | 시즌 참가팀·순위·승패 갱신 | `cc_season`, `cc_team` | `cc_season_team` |
| `POST /seasons/{leagueKey}/matches/sync` | 기간별 경기 일정·상태·최종점수 갱신 | `cc_season`, `cc_season_team` | `cc_season_match` |
| `POST /matches/{matchCode}/quarters/sync` | 종료 경기 한 건의 1~4쿼터 점수 적재 | `cc_season_match` | `cc_season_match` |
| `POST /seasons/{leagueKey}/matches/quarters/sync` | 기간별 종료 경기의 쿼터 점수 적재, 실패 경기 보고 | `cc_season_match` | `cc_season_match` |
| `POST /players/sync` | 현재 등록선수 명단·기본 정보 갱신 | `cc_player` | `cc_player` |
| `POST /players/backfill` | 선수 코드로 누락 프로필 보완 | `cc_player` | `cc_player` |
| `POST /seasons/{leagueKey}/rosters/sync` | 등록선수를 시즌 팀과 연결 | `cc_season`, `cc_season_team`, `cc_player` | `cc_season_team_roster` |
| `POST /seasons/{leagueKey}/matches/player-records/sync` | 기간별 종료 경기의 선수 기록·출전 여부·당시 소속 적재 | `cc_season_match`, `cc_season_team`, `cc_player` | `cc_season_match_player_record`, `cc_season_team_roster` |
| `GET /matches` | 기간별 경기 일정·합계·쿼터 점수 | `cc_season_match`, `cc_season`, `cc_season_team`, `cc_team` | — |
| `GET /matches/today` | 한국시간 기준 오늘 시작 전 예정 경기 | `cc_season_match`, `cc_season`, `cc_season_team`, `cc_team` | — |
| `GET /predictions/today` | 오늘 예정 경기의 Elo 승률과 계산 근거 | `cc_season_match`, `cc_season`, `cc_season_team`, `cc_team` | — |
| `GET /seasons/{leagueKey}/standings` | 시즌 팀 순위와 팀별 최근 5경기 승패 | `cc_season`, `cc_season_team`, `cc_team`, `cc_season_match` | — |
| `GET /seasons/{leagueKey}/teams/{teamCode}` | 팀의 시즌 성적과 최근 5·10경기 기록 | `cc_season`, `cc_season_team`, `cc_team`, `cc_season_match` | — |
| `GET /seasons/{leagueKey}/player-records` | 선수별 출전 팀·경기 수·득점·리바운드·어시스트 집계 | `cc_season_match_player_record`, `cc_season_match`, `cc_player`, `cc_season`, `cc_season_team`, `cc_team` | — |

## 데이터 저장 설계 진행사항

Windows 개발 DB에 Flyway `V1`~`V3`가 적용됐습니다. `V1`은 [ERD](../basketball-analyze.erd)의 기본 테이블 8개를 만들고, `V2`는 선수 코드의 고유 제약과 내부 ID 시퀀스를 추가합니다. `V3`는 경기별 실제 출전 여부를 보존합니다. [데이터 저장 설계](data-model.md) · [로컬 DB 운영 안내](../docker/README.md)

- 선수 키·몸무게는 `DECIMAL(5,2)`, 시즌 팀 승차는 `DECIMAL(3,1)`로 조정했습니다.
- `cc_season_team_roster`는 시즌 팀·선수 복합 PK를 사용하고, 해당 시즌 해당 팀에서 쌓은 선수 누적 기록 19개 컬럼을 추가했습니다. 평균·성공률은 누적값으로 계산하는 방향이며, 개인 최다 기록 저장 구조는 아직 없습니다.
- `cc_season_match`에 경기 코드·일시·진행 상태를 정의하고, 최종점수를 `SMALLINT`로 조정했습니다. 미확인 점수·종료 전 승리팀은 `NULL`을 허용하며, 홈·원정 시즌 팀은 동일 시즌을 보장하는 복합 FK로 연결했습니다.
- `cc_season_match_overtime`은 경기 ID·연장 차수 복합 PK로 연장별 점수를 저장하도록 정의했습니다.
- `cc_season_match_player_record`는 Flyway `V3`의 출전 여부를 포함해 27개 컬럼으로 경기 전체 선수 기록을 저장합니다. 경기 ID·선수 ID 복합 PK, 경기·로스터 FK, 선수별 조회 인덱스를 포함합니다.
- 경기·연장·선수 경기 기록의 초기 `CHECK`는 Flyway `V1`에 포함됐습니다. `V3`에서 실제 출전 여부에 허용된 값을 검사합니다.

## 첫 데이터 적재

첫 대상은 **2026–2027 KBL 1군 정규리그(`S49G01`)**입니다. 로컬 수집 API를 호출하면 서버가 해당 KBL API에서 데이터를 가져와 기존 스키마에 저장합니다. [KBL 응답 표본](kbl-api-data.md#첫-적재용-api-표본-재확인)

| 순서 | 로컬 수집·저장 API | KBL 조회 경로 | 저장 범위 |
| --- | --- | --- | --- |
| 1. 시즌 정보 | `POST /api/kbl/seasons/{leagueKey}/sync` | `/season/list` | `cc_season`에 시즌 ID `2026-2027`과 시작·종료 연도 저장 |
| 2. 구단 정보 | `POST /api/kbl/teams/sync` | `/common/teamList` | `cc_team`에 구단 코드·이름 저장. `06`처럼 앞자리 0이 있는 코드도 문자열로 보존 |
| 3. 시즌별 순위 | `POST /api/kbl/seasons/{leagueKey}/standings/sync` | `/league/rank/{leagueKey}` | `cc_season_team`에 시즌 참가팀과 순위·승·패·승률·승차 저장. 승률은 0~1 비율을 소수점 둘째 자리까지 저장 |
| 4. 경기 일정·결과 | `POST /api/kbl/seasons/{leagueKey}/matches/sync` | `/match/list` | `cc_season_match`에 경기 코드, 홈·원정팀, 예정 일시(KST), 상태, 점수, 승리팀 저장 |

- **호출 순서.** 시즌 정보와 구단 정보는 각각 호출할 수 있습니다. 두 데이터가 저장되면 시즌별 순위를 적재하고, 해당 시즌의 참가팀을 확보한 뒤 경기 일정·결과를 적재합니다. 이후에는 갱신할 API만 호출합니다. 선행 데이터가 빠진 경우 응답에 필요한 적재 단계와 누락 식별자를 표시합니다.
- **경기 조회 범위.** 경기 API는 본문으로 `fromDate`, `toDate`를 `YYYY-MM-DD` 형식으로 받습니다. 시작일과 종료일을 모두 포함해 최대 366일을 요청할 수 있고, 서버는 KBL 조회를 최대 31일씩 나눕니다. 첫 적재는 2026-10-03~2027-04-11을 요청하고 이후에는 필요한 구간만 갱신합니다.
- **경기 상태.** `gameDate`와 `gameStart`로 한국시간 예정 일시를 구성합니다. 예정 경기는 점수·승리팀을 `NULL`로 저장하고, 진행 경기는 확인된 점수를 저장하며, 종료 경기는 최종점수에서 승리팀을 계산합니다. 연기·취소는 원본 상태 값 확인이 필요합니다.
- **검증과 재호출.** 공통 KBL 조회 클라이언트로 데이터를 받은 뒤 필요한 필드와 참조 관계를 검증합니다. API 호출당 한 트랜잭션으로 저장합니다. 성공 응답의 `fetchedCount`는 KBL 응답 행 수, `processedCount`는 해당 호출에서 신규 저장 또는 갱신 대상으로 처리한 행 수입니다. 요청 오류는 `400`, 선행 데이터 누락은 `409`, KBL 통신·응답 오류는 `502`이며 응답의 `detail`에 원인을 표시합니다. 시즌·구단·시즌 참가팀은 기존 식별자, 경기는 `gmkey`로 갱신해 재호출 시 행이 중복되지 않게 합니다.

### 개발 DB 적재와 백업 결과

아래 건수는 **2026-11-27 Windows 개발 DB**에서 SQL로 확인한 값입니다.

| 테이블 | 저장 데이터 | 건수 |
| --- | --- | ---: |
| `cc_season` | 시즌 정보 | 1 |
| `cc_team` | 구단 기본 정보 | 10 |
| `cc_season_team` | 시즌별 참가팀과 순위 | 10 |
| `cc_season_match` | 시즌 일정 270건 중 종료 74건·예정 196건. 종료 74건의 홈·원정 1~4쿼터 점수 확인 | 270 |
| `cc_player` | 등록선수 218명과 기본 정보를 보완한 6명 | 224 |
| `cc_season_team_roster` | 등록 명단에서 확인한 소속과 경기 당시 소속 | 224 |
| `cc_season_match_player_record` | 종료 경기 74건의 선수 기록. 실제 출전 1,535행·미출전 241행·미확인 0행 | 1,776 |
| `cc_season_match_overtime` | 경기별 연장 점수 | 0 |

- **선수 기록 검수.** 74경기 모두 선수 득점 합계가 종료 점수와 일치했고 경기·선수 조합 중복은 0건이었습니다. 실제 출전이 확인된 선수는 150명입니다.
- **11월 25일 Mac 백업과 복원.** 당시 일정 270건은 종료 77건·예정 193건이었습니다. `basketball-20261125-232448.sql`을 별도 MariaDB에 복원해 저장 데이터와 Flyway `V1` 이력을 확인했습니다. 현재 Windows 개발 DB의 `V3` 데이터는 새 SQL 백업을 만들 때 포함됩니다. [백업·복원 절차](../docker/README.md#백업)

### 로컬 API 호출 예시

[로컬 DB 운영 안내](../docker/README.md)에 따라 MariaDB와 애플리케이션을 실행한 뒤 PowerShell이나 Postman에서 호출합니다. 저장소의 Postman 워크스페이스와 컬렉션은 미설정 상태입니다.

IntelliJ에서는 프로젝트 루트를 작업 디렉터리로 지정하고 활성 프로필을 `docker`로 설정합니다. 아래 URL은 앱 포트 `9001` 기준입니다. 앱 포트가 다르면 URL을 바꿉니다. JDBC 로그에 `host, pipe or local socket must be set to connect socket`이 나오면 `docker` 프로필이 적용됐는지 확인합니다. 기본 프로필은 `MARIADB_URL`이 비어 있으면 호스트 없는 JDBC URL을 사용합니다. [로컬 실행 설정](../docker/README.md#시작)

```powershell
$kblApi = 'http://localhost:9001/api/kbl'
$seasonApi = "$kblApi/seasons/S49G01"
Invoke-RestMethod -Method Post -Uri "$seasonApi/sync"
Invoke-RestMethod -Method Post -Uri "$kblApi/teams/sync"
Invoke-RestMethod -Method Post -Uri "$seasonApi/standings/sync"
Invoke-RestMethod -Method Post -Uri "$seasonApi/matches/sync" -ContentType 'application/json' `
    -Body (@{ fromDate = '2026-10-03'; toDate = '2027-04-11' } | ConvertTo-Json)
```

## 기간별 경기 일정 조회 API

`GET /api/kbl/matches`는 2026–2027 정규리그의 저장된 일정을 읽습니다. 쿼리 파라미터 `fromDate`, `toDate`를 모두 `YYYY-MM-DD` 형식으로 받으며, 한국시간 기준 시작일과 종료일을 포함합니다. 시작 시각순으로 표시하고 같은 시각의 경기는 경기 코드순으로 정렬합니다.

- 응답은 `seasonId`, `fromDate`, `toDate`, `matches`로 구성됩니다. 경기별로 `matchCode`, `startsAt`, `status`, 홈·원정팀, 합계 점수 `homeScore`·`awayScore`, 1~4쿼터의 `quarters`를 반환합니다. `quarters`의 각 항목은 `quarter`, `homeScore`, `awayScore`로 구성됩니다. 시작 시각은 `+09:00`을 포함하고, 미확인 점수는 `null`입니다.
- 경기가 없는 기간은 `200 OK`와 빈 목록을 반환합니다. 필수 날짜가 빠지거나, 날짜가 올바르지 않거나, 시작일이 종료일보다 늦으면 `400`을 반환합니다.
- **실호출 검수.** Postman에서 10월 19일 종료 경기 `S49G01N29`의 원주 DB **104–78** 대구 한국가스공사와 쿼터별 점수를 확인했습니다. 12월 3일 예정 경기의 합계·쿼터 점수는 `null`, 10월 1일에는 `matches: []`가 반환됐습니다. 날짜 탭과 경기 상세는 이 조회 API를 사용합니다. [KBL 원본 표본](kbl-api-data.md#첫-적재용-api-표본-재확인)

## 리그 순위 조회 API

`GET /api/kbl/seasons/S49G01/standings`는 2026–2027 정규리그 순위와 최근 경기 결과를 로컬 DB에서 읽습니다. Postman에서 `GET http://localhost:9001/api/kbl/seasons/S49G01/standings`를 사용합니다. 인증, Query Params, Body는 필요하지 않습니다.

| 응답 항목 | 의미와 기준 |
| --- | --- |
| `seasonId` | `2026-2027` |
| `completedGames` | 조회 가능한 시즌 참가팀의 승수 합계. 순위 동기화 시점 기준 |
| `standings[]` | `cc_season`, `cc_season_team`, `cc_team`의 사용 상태(`Y`) 행으로 구성. 순위 오름차순, 동순위는 팀 코드순 |
| `rank`, `team.code`, `team.name` | 저장된 순위와 구단 식별 정보. 팀 코드 `"06"`과 공동 순위를 보존 |
| `games`, `wins`, `losses`, `winRate`, `gamesBehind` | 경기 수는 승+패. 승률은 `0`~`1` 비율이며 저장 기준으로 소수 둘째 자리까지 제공. 승차는 저장값 |
| `recentResults` | `cc_season_match`에서 사용 상태이고 일시와 승리팀이 확인된 종료 경기 중 팀별 최대 5건. `WIN` 또는 `LOSS`를 최신 시작 일시순으로 제공하며 결과가 없으면 `[]` |

지원 범위 외 리그 키는 `400`, 사용 가능한 순위 적재 행이 없으면 `409`, DB 조회 실패는 `500`을 반환합니다. 새 순위는 기존 `POST /api/kbl/seasons/S49G01/standings/sync`로 갱신합니다. 최근 경기 결과를 갱신할 때는 경기 일정·결과 동기화를 사용합니다.

**Windows 로컬 DB 검수(2026-11-27).** `200 OK`, 참가팀 10팀, 순위 집계 기준 종료 경기 74건을 확인했습니다. 서울삼성 1위(11승 3패), 공동 5위·8위, 수원KT 코드 `"06"`을 확인했습니다. 10개 팀의 코드·승패·승률을 DB와 대조했고, `S48G01` 요청은 `400`을 반환했습니다.

## 팀 상세 조회 API

Postman에서 `GET http://localhost:9001/api/kbl/seasons/S49G01/teams/16?limit=10`을 호출합니다. `S49G01`은 2026–2027 정규리그 키이며, `16`은 원주DB 팀 코드입니다. 팀 코드는 문자열이므로 수원KT는 `06`으로 요청합니다. 인증과 Body는 필요하지 않습니다. `limit`은 `5` 또는 `10`이며, 생략하면 `10`으로 조회합니다.

| 항목 | 기준 |
| --- | --- |
| `seasonId`, `team.code`, `team.name` | `cc_season`, `cc_season_team`, `cc_team`의 사용 상태(`Y`)인 시즌과 팀 |
| `standing` | 순위, 경기 수, 승수, 패수, 승률, 승차. 리그 순위 조회와 같은 저장값을 사용 |
| `recentGames` | `cc_season_match`에서 경기 시작 일시와 승리팀이 확인된 종료 경기 중 최대 5건 또는 10건. 최신 시작 일시순이며 시각이 같으면 경기 코드 내림차순 |
| `matchCode`, `startsAt`, `opponent` | 경기 코드, `+09:00` 시각, 상대팀 코드와 이름 |
| `scored`, `conceded`, `result` | 요청 팀 기준 득점·실점과 `WIN`·`LOSS`. 미확인 점수는 `null` |

종료 이력이 없으면 `200 OK`와 `recentGames: []`를 반환합니다. 지원하지 않는 리그 키, 형식이 잘못된 팀 코드와 `limit`은 `400`입니다. 조회 가능한 순위가 있으나 해당 참가팀을 찾을 수 없으면 `404`, 사용 가능한 순위 적재 행이 없으면 `409`, DB 조회 실패는 `500`입니다.

**Windows 로컬 DB 검수(2026-11-27).** 원주DB의 시즌 성적 `4위·16경기·8승 8패`를 리그 순위와 대조했습니다. 최신 경기 `S49G01N74`는 안양정관장전 `77–82` 패배였고 `limit=5` 응답은 `limit=10`의 첫 다섯 경기와 일치했습니다. 원주DB와 수원KT의 상대·득실점·승패를 기간별 일정 조회와 대조했습니다. Chrome에서 순위표 링크, 5·10경기 전환, 팀·시즌 변경, 모바일 배치를 확인했습니다. 오류 후 재조회·단건·빈 이력은 화면에 모의 응답을 적용해 확인했습니다.

## 등록선수 기본 정보 적재

`POST /api/kbl/players/sync`는 KBL 공식 사이트의 현재 등록선수 명단 전체와 시즌 코드 `49`의 선수 프로필을 조회해 `cc_player`에 저장합니다. 선행 데이터 적재가 없어도 호출할 수 있습니다. [공식 응답 확인 결과](kbl-api-data.md#등록선수-기본-정보-수집-표본)

| 항목 | 값 |
| --- | --- |
| Postman 메서드 | `POST` |
| URL | `http://localhost:9001/api/kbl/players/sync` |
| Params · Body | 입력 없음. Postman Body는 `none` |
| 성공 `200` | `fetchedCount`는 명단에서 읽은 선수 수, `processedCount`는 신규 저장·갱신을 처리한 선수 수 |
| 외부 오류 `502` | 통신 실패, 페이지 건수 불일치, 중복 코드 등은 `detail`에 원인을 반환하고 해당 요청의 저장을 진행하지 않음 |

```json
{
  "fetchedCount": 218,
  "processedCount": 218
}
```

앱을 `docker` 프로필로 다시 시작하면 Flyway `V2`가 적용됩니다. 프로필을 따로 구성한 환경에서는 호출 전에 `V2` 적용 여부를 확인합니다. 선수 수는 등록 명단에 따라 달라집니다. 같은 요청을 다시 보내도 선수 코드별 행과 내부 ID는 유지됩니다. `processedCount`는 신규 등록 수와 갱신 대상 수의 합입니다.

```sql
SELECT COUNT(*) AS player_count, COUNT(DISTINCT code) AS distinct_codes
FROM cc_player;

SELECT id, code, name, birth_date, height, weight
FROM cc_player
WHERE code = '291001';
```

첫 적재를 빈 `cc_player`로 시작했다면 전체 행 수와 고유 선수 코드 수가 응답의 `processedCount`와 일치합니다. 강상재(`291001`)의 생년월일 `19941231`, 신장 `200.00`, 몸무게 `97.00`을 확인하고 재호출 뒤 내부 ID가 같은지 대조합니다. 빈 선택 항목과 원본에서 0으로 제공한 신장·몸무게는 새 선수의 경우 `NULL`로 저장합니다. 이미 저장된 선택 항목은 원본이 결측이면 기존 값을 유지합니다.

2026-11-27에 임시 MariaDB로 공식 명단 적재를 두 번 호출했습니다. 두 요청 모두 218명을 처리했고, 전체 행과 고유 코드는 각각 218개로 유지됐습니다. 기존 강상재 행의 내부 ID를 보존했고, 28명의 신장·몸무게가 `NULL`로 저장됐습니다. 로컬 Postman 시뮬레이션에서 두 번째 선수 프로필에 `503`을 반환하자 적재 API는 `502`와 해당 선수 코드를 반환했고, 기존 선수 데이터는 유지됐습니다.

같은 날 앱 재시작 후 개발 DB에서 Flyway `V2` 적용을 확인했습니다. 두 번 호출한 결과 각각 `200`이었고, 선수 코드와 내부 ID 조합 218개가 모두 유지됐습니다.

## 누락 선수 기본 정보 보완

경기 선수 기록 적재의 `failures`에 선수 기본 정보 누락 코드가 나오면 `POST /api/kbl/players/backfill`로 해당 선수의 공식 프로필을 `cc_player`에 추가합니다. 공식 프로필에서 확인한 한글·영문 이름, 생년월일, 국적, 신장을 저장합니다. 프로필에서 제공하지 않는 선수 구분과 몸무게는 새 행에서 `NULL`로 유지합니다. [KBL 선수 상세 API](kbl-api-data.md#선수-상세-api)

Postman에서 `POST http://localhost:9001/api/kbl/players/backfill`을 선택하고 Body의 `raw` → `JSON`에 아래 배열을 입력합니다.

```json
{
  "playerCodes": ["290553", "291579", "291695", "291881", "291883", "291886"]
}
```

| 응답 | 의미 |
| --- | --- |
| `200` | `requestedCount`는 요청한 코드 수, `processedCount`는 새 프로필을 조회해 저장 대상으로 처리한 수, `existingCount`는 이미 저장돼 조회를 건너뛴 코드 수 |
| `400` | 빈 배열, 잘못된 형식 또는 중복 코드. 각 코드는 6자리 문자열 |
| `404` | KBL에 해당 선수 코드의 공식 프로필이 없음 |
| `502` | KBL 통신 실패, 선수 코드 불일치 또는 프로필 검증 오류 |

모든 누락 코드를 확인한 후 한 트랜잭션에서 저장합니다. 첫 호출은 `requestedCount=6`, `processedCount=6`, `existingCount=0`이며, 재호출은 `processedCount=0`, `existingCount=6`입니다. 기존 선수 행과 내부 ID는 유지됩니다. 보완이 끝나면 [기간별 경기 선수 기록 적재](#기간별-경기-선수-기록-적재)를 `fromDate=2026-10-03`, `toDate=2026-11-26`으로 다시 호출해 `savedCount=targetCount`, `failedCount=0`인지 확인합니다.

2026-11-27에 임시 MariaDB에서 6명을 보완해 선수 224명을 확인했습니다. 재호출 시 이미 적재된 선수 정보와 내부 ID가 유지됐습니다. 프로필이 있는 선수와 없는 선수를 함께 요청하면 `404`를 반환하고 새 행을 저장하지 않았습니다. 같은 임시 DB에서 공식 일정 기준 종료 경기 77건을 다시 적재해 선수 기록 1,848행, 실패 0건을 확인했습니다. 보완한 6명의 경기 기록은 총 40행이었습니다.

같은 날 재시작한 앱과 개발 DB에서 보완 선수 6명의 내부 ID를 재호출로 확인했습니다. 종료 경기 74건, 선수 기록 1,776행을 실패 없이 저장했고 경기·선수 키 중복과 팀 득점 합계 불일치는 모두 0건이었습니다.

## 시즌 팀 로스터 적재

`POST /api/kbl/seasons/S49G01/rosters/sync`는 공식 현재 등록 명단의 `teamCode`와 `playerNo`를 시즌 팀·선수 내부 ID로 연결하고, `cc_season_team_roster`에 저장합니다. 현재 확인된 소속을 추가하며 같은 팀·선수 조합은 복합 PK로 중복을 막습니다. 기존 소속 행과 누적 기록을 보존합니다. [공식 명단 필드](kbl-api-data.md#등록선수-기본-정보-수집-표본)

| 항목 | 값 |
| --- | --- |
| Postman | `POST http://localhost:9001/api/kbl/seasons/S49G01/rosters/sync`, Params 없음, Body `none` |
| 선행 데이터 | `cc_season`, `cc_season_team`, `cc_player`에 해당 시즌·참가팀·선수 코드 필요 |
| 성공 `200` | `teamCount`는 이번 명단의 고유 팀 수, `playerCount`는 이번 명단의 선수 수. 기존 소속 유지분도 처리 건수에 포함 |
| 오류 | 지원하지 않는 시즌 키는 `400`, 선행 데이터 누락은 `409`, 공식 조회·명단 검증 실패는 `502`. 오류 시 해당 요청의 소속을 저장하지 않음 |

```json
{
  "seasonId": "2026-2027",
  "teamCount": 10,
  "playerCount": 218
}
```

```sql
SELECT COUNT(*) AS roster_count, COUNT(DISTINCT r.cc_season_team_id) AS team_count
FROM cc_season_team_roster r
JOIN cc_season_team st ON st.id = r.cc_season_team_id
WHERE st.cc_season_id = '2026-2027';

SELECT st.cc_team_id AS team_code, p.code AS player_code, p.name
FROM cc_season_team_roster r
JOIN cc_season_team st ON st.id = r.cc_season_team_id
JOIN cc_player p ON p.id = r.cc_player_id
WHERE st.cc_season_id = '2026-2027' AND p.code IN ('291001', '291842')
ORDER BY p.code, st.cc_team_id;
```

명단에 변동이 없는 상태에서 재호출하면 행 수가 유지됩니다. 강상재(`291001`)는 원주DB(`16`), 강성욱(`291842`)은 수원KT(`06`)와 연결됩니다. 저장된 이전 소속이 있으면 로스터 전체 행 수는 이번 명단의 `playerCount`보다 많을 수 있습니다. 첫 적재 이후 경기별 선수 기록을 수집할 때 해당 경기의 소속을 확인합니다.

2026-11-27에 공식 명단과 임시 MariaDB로 적재를 확인했습니다. 10개 팀과 선수 218명의 조합을 저장했으며 같은 요청으로 행 수가 유지됐습니다. 검증용 이전 소속 행과 누적 득점을 넣은 후에도 기존 값이 보존됐습니다. 시즌·시즌 참가팀·선수 중 선행 데이터가 빠지면 `409`를 반환했고 로스터 테이블은 비어 있었습니다.

같은 날 재시작한 앱과 개발 DB에서 두 번 호출해 팀·선수 연결 218개가 유지되고 중복이 없는 것을 확인했습니다.

## 기간별 경기 선수 기록 적재

`POST /api/kbl/seasons/S49G01/matches/player-records/sync`는 `cc_season_match`에서 양 끝 날짜를 포함한 한국시간 범위의 종료 경기를 읽고, KBL의 경기별 선수 기록을 `cc_season_match_player_record`에 저장합니다. [공식 기록 필드와 표본](kbl-api-data.md#경기별-선수-기록-적재-표본)

| Postman 항목 | 값 |
| --- | --- |
| 메서드·URL | `POST http://localhost:9001/api/kbl/seasons/S49G01/matches/player-records/sync` |
| Params | 없음 |
| Body | `raw` → `JSON`. `fromDate`, `toDate`를 `YYYY-MM-DD` 형식으로 입력 |

```json
{
  "fromDate": "2026-10-19",
  "toDate": "2026-10-19"
}
```

정상 처리 시 `200`과 `seasonId`, `fromDate`, `toDate`, `targetCount`, `savedCount`, `failedCount`, `failures`를 반환합니다. 건수는 경기 기준입니다. 실패 항목에는 `matchCode`, `status`, `detail`이 들어갑니다. 요청한 기간·시즌 키가 잘못되면 `400`입니다. 적재된 종료 경기가 없으면 세 건수가 모두 `0`입니다.

경기별로 선수의 홈·원정 소속과 개인 득점 합계를 저장된 경기와 대조합니다. 이미 등록된 선수의 당시 소속이 로스터에 없으면 보완하고, 경기·선수 키로 개인 기록을 저장하거나 갱신합니다. 선수 기본 정보가 누락됐거나 저장된 최종점수와 일치하지 않으면 해당 경기의 실패 항목은 `409`입니다. 응답 검증·외부 조회 실패는 `502`, DB 저장 실패는 `500`으로 표시합니다. 각 경기의 소속과 기록은 한 트랜잭션에서 저장하며 실패 뒤에도 다른 경기를 처리합니다.

```sql
SELECT m.match_code, st.cc_team_id AS team_code, p.code AS player_code,
       r.is_starter, r.playing_time_seconds, r.points, r.rebounds, r.assists
FROM cc_season_match_player_record r
JOIN cc_season_match m ON m.id = r.cc_season_match_id
JOIN cc_season_team st ON st.id = r.cc_season_team_id
JOIN cc_player p ON p.id = r.cc_player_id
WHERE m.match_code = 'S49G01N29' AND p.code = '291001';
```

표본 응답의 `targetCount`와 `savedCount`는 `1`, `failedCount`는 `0`입니다. `S49G01N29`는 선수 기록이 24행이며 재호출 후에도 24행을 유지합니다. 강상재는 홈팀 원주DB(`16`)에서 선발로 1,030초를 뛰었고 5득점·4리바운드·5어시스트를 기록했습니다.

2026-11-27에 임시 MariaDB에서 표본 저장과 재호출, 104–78 득점 합계, DB 오류 시 로스터·선수 기록 동시 롤백을 확인했습니다. 10월 3일~11월 26일 공식 일정을 새로 적재한 임시 DB에서는 종료 경기 77건 중 47건의 선수 기록 1,128행이 저장됐습니다. 30건은 현재 등록 명단 기반의 선수 기본 정보 누락으로 `409` 실패 내역에 포함됐습니다. 개발 DB의 대상 경기 수는 그 DB에 적재된 종료 경기 기준입니다.

같은 날 재시작한 앱과 개발 DB에서 표본 적재를 두 번 호출했습니다. 두 요청 모두 `200`이었고 `S49G01N29`의 선수 기록 24행과 득점 합계 104–78, 강상재 표본 값이 일치했습니다.

## 시즌 선수 기록 조회 API

Postman에서 `GET http://localhost:9001/api/kbl/seasons/S49G01/player-records`를 호출합니다. Params와 Body는 비워 둡니다. `recordedGames`는 사용 가능한 선수 기록을 적재한 종료 경기 수입니다. `unverifiedAppearances`는 그중 출전 여부가 미확인인 선수 기록 행 수입니다. 기록이 비어 있는 시즌은 `200`과 빈 `players`를 반환하며, 지원하지 않는 리그 키는 `400`입니다.

`players`의 한 행은 선수 한 명의 시즌 집계입니다. `teams`에 실제 출전한 팀을 모두 담고, `games`와 `totals`에는 여러 팀에서 뛴 기록을 합산합니다. `averages`는 합계를 확인된 출전 경기 수로 나눠 소수점 둘째 자리까지 반환합니다. 출전 경기 중 지표가 미제공인 경기 기록이 있으면 해당 지표의 합계와 평균은 `null`입니다. [화면 동작](dashboard-ui.md#선수-기록)

```json
{
  "code": "291001",
  "name": "강상재",
  "teams": [{"code": "16", "name": "원주DB"}],
  "games": 7,
  "totals": {"points": 50, "rebounds": 37, "assists": 16},
  "averages": {"points": 7.14, "rebounds": 5.29, "assists": 2.29}
}
```

`docker` 프로필로 재시작하면 Flyway `V3`가 출전 컬럼을 추가합니다. 기존 자료의 양수 출전시간과 선발 행은 출전으로 반영하며, 교체 정보가 저장돼 있지 않은 나머지 행은 미확인으로 표시합니다. 이 값을 보완하려면 재시작 후 [기존 기간 적재 API](#기간별-경기-선수-기록-적재)를 `2026-10-03`~`2026-11-26`으로 한 번 더 호출합니다. 검수 시 `recordedGames=74`, `unverifiedAppearances=0`과 위 강상재 값을 대조합니다.

2026-11-27에 개발 DB에서 `V3` 적용 후 74경기를 다시 적재했습니다. 미확인 242행을 보완했고, 실제 출전 1,535행과 미출전 241행이 확인됐습니다. 조회 API는 `recordedGames=74`, `unverifiedAppearances=0`과 선수 150명을 반환했습니다. 강상재의 시즌 합계·평균과 0초에 출전한 선수 `291867`의 경기 수 3→4를 대조했습니다.

앱을 다시 시작한 뒤에도 조회 API는 `200 OK`와 같은 집계를 반환했습니다. Chrome에서 득점·리바운드·어시스트 순위의 150행과 상위 5명 카드가 일치하는지 대조했습니다. 각 부문 1위는 패리스 배스, 아셈 마레이, 허훈이었습니다. 390px에서 표 내부 스크롤, 팀관리 링크와 2025–2026 예시 시즌 전환을 확인했습니다. [선수 기록 화면](dashboard-ui.md#선수-기록)

## 쿼터별 점수 수집과 적재

| 용도 | 로컬 API | 입력 |
| --- | --- | --- |
| 한 경기 | `POST /api/kbl/matches/{matchCode}/quarters/sync` | 경로의 경기 코드 |
| 기간별 | `POST /api/kbl/seasons/{leagueKey}/matches/quarters/sync` | 경로의 `S49G01`과 본문의 `fromDate`, `toDate` |

기간별 요청은 양 끝 날짜를 포함해 이미 DB에 저장된 `FINISHED` 경기를 선택합니다. 각 경기의 KBL 상세 `/match/{matchCode}`에서 홈·원정 `scoreq1`~`scoreq4`를 읽습니다. 연장 점수 `scoreeq`를 더한 합계와 홈·원정팀이 DB 경기 결과와 일치하면, 기존 경기 행의 1~4쿼터 점수 8개를 갱신합니다. 연장 점수는 합계 검증에 사용합니다. [저장 규칙](data-model.md#cc_season_match)

Postman에서는 `POST http://localhost:9001/api/kbl/seasons/S49G01/matches/quarters/sync`에 **No Auth**, **Body → raw → JSON**으로 아래 본문을 보냅니다. 한 경기 저장이 실패해도 다음 경기를 처리합니다. `200 OK` 응답의 `failedCount`와 `failures`에서 실패 내역을 확인합니다.

```json
{
    "fromDate": "2026-10-03",
    "toDate": "2026-11-26"
}
```

**실호출 검수(2026-11-26).** 위 요청은 `200 OK`, `targetCount: 74`, `savedCount: 74`, `failedCount: 0`, `failures: []`를 반환했습니다. MariaDB에서 `S49G01N29`의 홈 `25, 25, 38, 16`과 원정 `21, 13, 18, 26`을 조회해 원본과 대조했습니다. 각 팀의 합계는 최종점수 `104, 78`과 일치했습니다.

## 오늘 예정 경기 조회 API

`GET /api/kbl/matches/today`는 요청 파라미터 없이 개발 DB에 적재된 2026–2027 정규리그(`S49G01`)의 오늘 예정 경기를 조회합니다. 한국시간(`Asia/Seoul`) 기준으로 요청 시각보다 늦고 다음 날 자정보다 이른 시각에 시작하는 `SCHEDULED` 경기 중 경기·시즌·시즌 참가팀·구단이 모두 사용 상태(`Y`)인 경기만 반환합니다. 시작 시각이 미정이거나 조회 시각에 이미 도달한 경기는 제외합니다. 결과는 시작 시각순이며 같은 시각의 경기는 경기 코드순입니다.

일정과 양 팀 정보만 표시하거나, DB에 적재된 일정을 확인할 때 사용합니다. 일정과 승률을 함께 표시하는 화면은 [예측 API](#오늘-예정-경기-예측-api)에서 같은 경기 정보를 받을 수 있습니다.

`KblMatchController`가 요청을 받고, `KblMatchService`가 한국시간 조회 범위와 선행 적재 여부를 확인합니다. `KblMatchRepository`는 `JdbcTemplate`으로 기존 경기·팀 테이블을 읽고, `KblMatchResponse`가 응답 형식을 정의합니다.

### Postman에서 요청 보내기

1. Docker MariaDB를 실행하고, IntelliJ에서 프로젝트 루트를 작업 디렉터리로 지정해 Spring Boot 앱을 `docker` 프로필로 실행합니다. [로컬 DB 운영 안내](../docker/README.md)
2. Postman 데스크톱 앱에서 새 요청을 열고 **GET**과 `http://localhost:9001/api/kbl/matches/today`를 입력합니다. **Params**는 비워 두고 **Body**는 `none`, **Authorization**은 `No Auth`를 선택합니다.
3. **Send**를 누른 뒤 응답의 상태 코드와 **Body**의 JSON을 확인합니다.

PowerShell에서도 같은 요청을 보낼 수 있습니다.

```powershell
Invoke-RestMethod -Method Get -Uri 'http://localhost:9001/api/kbl/matches/today' | ConvertTo-Json -Depth 5
```

### 응답과 검수 기준

| 응답 항목 | 내용 |
| --- | --- |
| `seasonId`, `date`, `queriedAt` | 대상 시즌 ID, 한국시간 조회 날짜, 조회 기준 시각(`+09:00` 포함) |
| `matches[].id`, `matches[].matchCode` | DB 경기 ID, KBL 경기 코드 |
| `matches[].startsAt`, `matches[].status` | 한국시간 예정 시작 일시(`+09:00` 포함), 경기 상태(`SCHEDULED`) |
| `matches[].homeTeam`, `matches[].awayTeam` | 양 팀의 `code`와 `name`. 구단 코드는 `"06"`처럼 문자열로 반환 |

| 검수 상황 | 기대 결과 |
| --- | --- |
| 오늘 시작 전 경기가 있음 | `200 OK`. `seasonId`는 `2026-2027`, `date`는 한국시간 기준 오늘입니다. `queriedAt`과 `startsAt`은 `+09:00`이고, 모든 경기가 `SCHEDULED`이며 `startsAt`은 오늘 중 `queriedAt`보다 뒤입니다. 경기 코드와 양 팀 정보가 적재된 일정과 일치하고, 목록은 시작 시각순 및 경기 코드순으로 정렬됩니다. |
| 같은 경기를 시작 전후에 조회 | 예를 들어 오후 7시 예정 경기는 그 전에 조회하면 목록에 나오고, 오후 7시부터는 제외됩니다. |
| 오늘 남은 예정 경기가 없음 | `200 OK`와 `matches: []`를 반환합니다. 예정 경기가 있어야 하는 날에 빈 배열이 나오면 해당 날짜의 일정 적재 여부를 확인합니다. |
| 사용 가능한 시즌 또는 시즌 참가팀 정보가 없음 | `409 Conflict`를 반환합니다. 응답의 `detail`에서 필요한 수집 API를 확인합니다. |
| DB 연결 또는 조회 실패 | `500 Internal Server Error`를 반환합니다. IntelliJ에서 애플리케이션 로그를 확인합니다. |

**검수 결과.** 11월 25일 Mac 개발 DB와 11월 26일 Postman에서 `200 OK`와 `matches: []`를 확인했습니다. 선행 적재 전에는 시즌·참가팀 누락에 각각 `409`가 반환됐습니다. 백업을 복원한 별도 DB에서는 경기 시작 시각부터 제외되는 동작, `+09:00`, 경기 순서, 팀 코드 `06` 보존과 DB 조회 실패 시 `500` 응답을 확인했습니다.

## 오늘 예정 경기 예측 API

`GET /api/kbl/predictions/today`는 오늘 예정 경기 조회 로직을 재사용하고, 각 경기에 `prediction`을 추가합니다. 대시보드는 이 경로 한 번의 호출로 일정과 예측을 함께 받을 수 있습니다. 요청 파라미터는 없으며 남은 예정 경기가 없으면 `200 OK`와 `matches: []`를 반환합니다.

- **Elo 입력과 계산.** 2026–2027 정규리그의 `FINISHED` 경기 중 `match_datetime`이 한국시간 오늘 00:00보다 앞선 경기의 승패를 시작 시각순·경기 코드순으로 반영합니다. `match_datetime`은 DB의 예정 시작 시각입니다. 초기 Elo는 1500점, 갱신 계수 K는 20이며 홈 이점은 0점입니다. 계산은 요청마다 수행하며, 최신 결과가 필요하면 경기 수집 API로 DB를 먼저 갱신합니다.
- **응답.** `prediction.status`는 `AVAILABLE` 또는 `INSUFFICIENT_DATA`입니다. `homeElo`·`awayElo`, `homeWinPercentage`·`awayWinPercentage`, `reason`을 제공합니다. 표시 승률은 소수점 첫째 자리까지이며 합계는 `100.0`입니다. 같은 Elo는 `50.0`씩, 한 팀이라도 반영할 기록이 없으면 두 승률은 `null`입니다.
- **검수 결과.** 11월 25일 Mac 개발 DB에서 당일 00:00보다 앞선 종료 경기 77건을 조회 SQL로 확인했습니다. 11월 25일과 26일 Postman 호출에서 `200 OK`, 시즌 `2026-2027`, `matches: []`를 확인했습니다. 경기별 `prediction` 응답과 계산값은 실제 정규리그 경기일에 검수합니다.

실제 경기일에 시작 전 Postman으로 예정 경기 응답을 확인하고, 같은 경기의 승률과 근거를 대시보드와 대조합니다. 계산값을 확인할 때는 DB에 적재된 이전 날짜의 종료 경기 승패로 Elo를 별도로 계산합니다. 예를 들어 어제 A팀이 B팀을 이긴 한 경기만 반영하면 오늘 같은 대진의 Elo는 `1510.0` 대 `1490.0`, 승률은 `52.9` 대 `47.1`입니다.

## 초기 승부예측 MVP

**대시보드에서 오늘 열릴 경기의 시작 시간, 두 팀의 예상 승률, 우세 이유를 확인하는 것**이 목표입니다. 첫 적용 범위는 개발 DB에 적재한 2026–2027 정규리그입니다.

| 순서 | 작업 | 현황 및 계획 |
| --- | --- | --- |
| 1 | 오늘 예정 경기 조회 | 구현 및 실호출 검수 완료 |
| 2 | Elo 계산 | 구현 및 빈 목록 실호출 확인. 예정 경기가 있는 응답, 당일 종료 경기 제외, 같은 Elo의 `50.0%`, 승률 합계 `100.0%`, 기록 부족 응답 검수 대기 |
| 3 | 대시보드 연결 | `GET /api/kbl/predictions/today` 연동 완료. 경기 카드와 상세에 시작 시간·예상 승률·Elo·근거를 표시하고 로딩·빈 목록·기록 부족·오류를 구분 |
| 4 | 화면 검수 | 차콜·오렌지·우드 베이지 적용과 빈 목록 화면은 사용자가 확인. 경기 선택·기록 부족·오류와 재조회는 모의 응답으로 확인. 실제 경기 카드·상세 화면은 검수 대기 |

예측 API는 기존 테이블을 읽어 요청마다 승률을 계산합니다. 경기 전에 보여준 승률과 실제 결과를 나중에 비교하려면 예측 당시 승률과 계산 시각을 보관하는 기능이 필요합니다.

선수 기본 정보, 시즌 팀 로스터, 경기별 선수 기록 적재와 누락 선수 보완 API를 구현했습니다. 2026–2027 선수 기록 화면은 출전이 확인된 선수의 시즌 평균을 조회합니다. 연장 차수별 점수 저장은 후속 범위입니다.

## 최근 5시즌 데이터 검증 요약

대상은 **2022–2023~2026–2027시즌의 1군 정규리그**입니다. 플레이오프·챔피언결정전·D리그는 이 전수 검증에서 제외했습니다.

| 시즌 | 시즌 코드 | 기록에 등장하는 선수 | 선수 프로필·요청 시즌 평균 기록 | 기본·고급 팀 통계 |
| --- | --- | ---: | --- | --- |
| 2022–2023 | `41` | 199명 | 전원 조회 성공 | 10개 구단 모두 조회 |
| 2023–2024 | `43` | 200명 | 전원 조회 성공 | 10개 구단 모두 조회 |
| 2024–2025 | `45` | 204명 | 전원 조회 성공 | 10개 구단 모두 조회 |
| 2025–2026 | `47` | 201명 | 전원 조회 성공 | 10개 구단 모두 조회 |
| 2026–2027 진행 중 | `49` | 139명 | 전원 조회 성공 | 10개 구단 모두 조회 |

- 총 943개 선수·시즌 조합이며, 선수를 중복 제거하면 367명입니다.
- 현재 등록선수 187명을 대조하고 이번 시즌 기록에 없는 49명의 프로필도 보완 조회했습니다.
- 그중 45명은 이전 시즌 기록에 이미 포함돼 있습니다. 나머지 4명을 합친 고유 대상은 **371명**입니다.
- 전체 992개 프로필 요청에서 HTTP 오류·빈 프로필·선수 코드 불일치는 없었습니다.
- 시즌 기록에는 출전시간 0인 행도 포함됩니다. 위 숫자는 실제 출장 선수 수나 과거 등록 명단의 완전성을 보장하지 않습니다.
- 모든 개별 경기·모든 사진 파일까지 전수 검증한 것은 아닙니다. 상세 한계는 API 문서에 정리했습니다.

## 규정 변경과 예측 검토안

2026–2027시즌에는 외국선수 최대 2명이 2·3쿼터에 동시 출전할 수 있고, 1·4쿼터에는 1명만 출전할 수 있습니다. 허용 인원과 실제 동시 출전 시간은 구분합니다. [KBL 제공 자료를 인용한 발표](https://www.basketkorea.com/news/newsview.php?ncode=1065593433323957)

과거 기록은 선수 기량·전력 기준을 만드는 데 사용할 수 있지만, 변경 전후 데이터를 같은 조건으로 취급하면 안 됩니다. 최근 5시즌의 수집 가능성을 확인한 것과 실제 모델에 사용할 시즌·가중치를 정하는 것은 별도 결정입니다.

아래 라운드별 운영안은 초기 화면 MVP 이후에 검토할 후보입니다.

| 단계 | 검토 내용 |
| --- | --- |
| 1라운드 | 이번 시즌 기록 수집, 팀 전력의 초기 기준 계산 |
| 2라운드 | 경기 전 정보로 시험 예측, 초기 모델·가중치 검토 |
| 3라운드 | 경기 전 승률을 저장하고 실제 결과와 비교하기 시작 |
| 4~6라운드 | 완료된 경기와 부상·선수 교체 등 확인 가능한 변화를 반영해 갱신 |

10개 팀이 각 라운드에 서로 한 번씩 만나는 편성을 기준으로 1라운드는 45경기, 2라운드 누적은 90경기·팀당 18경기입니다. 이후 같은 날 앞선 경기까지 활용하려면 예측 시각까지 확인된 경기 결과만 입력에 포함합니다.

- 과거 데이터를 제외하고 이번 시즌만으로 시작하는 방안도 검토했습니다. 3라운드 시작이 성능 확보를 보장하는 시점은 아닙니다.
- 초기에는 Elo 등 단순 전력 평가와 소수 변수 모델을 기준으로 비교하는 방안을 권장했습니다. 최종 알고리즘은 미정입니다.
- 1~2라운드의 중요도를 높이더라도 이후 경기 정보를 계속 반영해야 합니다. 학습 가중치·반감기·최근 경기 수는 미정입니다.
- 예측 입력은 해당 경기 직전까지 알 수 있었던 기록만 사용합니다. 실제 출전시간·경기 결과·미래 시즌 누적 기록을 입력하면 안 됩니다.
- 무작위 분할 대신 시간순으로 검증하고, 적중률과 함께 Brier 점수·Log Loss 등 확률 품질도 확인하는 방안을 논의했습니다.
- 숫자 모델이 승률을 계산하고 로컬 LLM은 근거를 설명하는 구성을 권장했습니다. LLM 추가 학습 없이도 설명 기능은 시작할 수 있지만 모델·장비·입력 형식은 미정입니다.

## 현재 테이블의 승부예측 준비 상태

이번 시즌의 승패·점수차·홈원정·경기 일시로 Elo와 최근 팀 성적, 평균 득실점 등을 계산할 수 있습니다. 선수 최근 기록은 경기별 선수 데이터를 적재한 뒤 계산할 수 있습니다. 과거 시즌의 실제 적재 범위와 예측 성능 검증은 다음 단계에서 다룹니다.

| 보완 항목 | 현재 판단 |
| --- | --- |
| 예측 기준 시점·입력 스냅샷 | 당시 알 수 있었던 정보와 계산에 사용한 데이터 범위·변수 버전을 보존할 구조 필요 |
| 예측 결과 이력 | 경기 전 승률·예측 시각·모델 버전·입력 연결을 저장할 구조 필요 |
| 경기별 팀 기록 | 선수 합산에만 의존하지 않고 팀 API의 경기별 슈팅·리바운드·턴오버 등을 보관하는 테이블 제안 |
| 선수 출전 가능 상태 | 단일 `injury` 값으로 과거 부상·회복·출전 불확실성을 재현하기 어려움. 이력·확인 시각·출처 필요 |
| 선수 소속 기간 | `transfer` 값 외에 실제 소속 시작·종료 기간 또는 변경 이력 필요 |
| 수집·정정 이력 | 원본 출처·수집 시각·응답 및 정정 버전 보관 정책 필요 |

위 항목은 **검토안이며 ERD에 추가하지 않았습니다**. 현재 `injury`의 기본값 `FALSE`를 미확인 선수의 정상 출전 근거로 사용하면 안 됩니다. KBL의 부상·당일 출전 정보 제공 여부도 기존 조사에서 미확인입니다.

예측 입력은 최신 시즌 누적값을 그대로 가져오지 않고 과거 경기에서 다시 집계하거나 당시 입력 스냅샷을 사용해야 합니다. 대상 경기의 실제 선발·출전시간·결과는 경기 전 입력으로 사용하지 않습니다. 경기 종료·기록 공개·수집·정정 시점도 시작 일시와 구분해야 하며, 현재 구조는 이 시점 이력을 충분히 보존하지 않습니다.

## 사진 로컬 보관 제안

- 사진은 JAR·소스와 분리된 실행 데이터 폴더에 보관하고 선수 코드로 파일명을 정하는 방식을 권장했습니다.
- DB에는 선수 코드, 파일 상대경로, 원본 URL, 수집 시각을 저장하는 방향입니다.
- 화면은 애플리케이션의 이미지 URL로 접근하고, 다운로드 실패 시 기본 이미지를 표시하는 방안입니다.
- 갱신 실패 시 기존 파일을 유지하고, 수집 사진 폴더는 Git에서 제외합니다.
- 시즌별 사진 보존이 필요하면 수집 시점부터 시즌별 경로를 사용합니다. 현재 사진으로 과거 시즌 사진을 복원할 수는 없습니다.
- 저장 위치·갱신 주기·파일 검증·웹 제공 경로는 아직 확정하거나 구현하지 않았습니다. [Spring 정적 리소스 제공 방식](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/static-resources.html)

## 미확정 사항과 다음 작업 후보

| 항목 | 결정·확인이 필요한 내용 |
| --- | --- |
| 실제 경기일 Elo 검수 | 오늘 예정 경기의 승률·Elo·근거를 응답과 대시보드에서 대조 |
| 이번 시즌 경기 갱신 | 필요한 기간의 일정·결과를 먼저 갱신한 뒤 종료 경기의 쿼터·선수 기록을 적재하고 조회에 반영되는지 확인 |
| 수집 범위 확장 | 선수·로스터·경기별 선수 기록·연장 점수의 수집 시점과 과거 정규리그·플레이오프·D리그의 추가 범위 결정 |
| 저장 구조 | Flyway `V3`까지 적용. Entity 구현, 예측 입력·결과, 팀 경기 기록과 상태·수집 이력의 추가 범위 검토 |
| 수집 운영 | 자동 갱신 주기, 재시도, 원본 응답 보관 정책 결정 |
| 데이터 품질 | 집계 불일치·결측값·필드 의미·고급 지표 산식, 미확인 부상 상태·소속 기간·예측 시점 재현 검토 |
| 이미지 | 저장 경로, 시즌별 보존 여부, 기본 이미지, 갱신 정책 |
| 예측 확장 | 과거 시즌 병행 여부, 추가 변수·가중치, 예측 이력과 성능 평가 기준 |
| LLM | 모델·실행 도구·하드웨어·설명 범위 |
| 이용 조건 | API 접근성 외 이용약관·이미지 보관 및 사용 조건 검토는 완료되지 않음 |
