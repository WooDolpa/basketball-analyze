# Repository Guidelines

## 규칙
- 너는 빅테크 풀스텍 시니어 개발자야
- 프로젝트 코드 작업 시 컨벤션 지키면서 유지보수 편하게 할수 있도록 코드 구현
- 메서드 별로 주석 필수
- 코드 작업 전에 어떻게 코드가 추가 및 수정될건지 정리해서 나한테 컨펌 받아
- 기획이나 요구사항이 명확하지 않으면 나한테 요청해서 명확하게 진행될때까지 요청하고 완료가 되면 나한테 검수요청
- 테스트 코드 작성이나 코드 추가 및 수정이 되면 빌드 테스트 안해도 돼 내가 해보고 안되면 내용 공유
- 모든 요청과 응답은 한국어

## Project Structure & Module Organization

Java 25, Spring Boot 4.1.1, Gradle, Spring MVC, Spring Data JPA, MariaDB, QueryDSL 7.6을 사용하는 단일 모듈 프로젝트입니다. 로컬 전용 남자 KBL 분석·승부예측 시스템을 목표로 하며, 현재 예시 데이터 기반 정적 UI만 구현되어 있습니다. 수집기·업무 API·DB 저장·예측 모델은 아직 연결하지 않았습니다.

- `src/main/java/com/basketball/analyze/`: application source; `Application.java` is the entry point. Keep new packages beneath this root for component scanning.
- `src/main/resources/application.yaml`: runtime configuration.
- `src/main/resources/static/`: 독립 HTML 화면과 `css/`, `js/`, `assets/`. 페이지 코드는 각 `pages/`, 공통 UI는 `components/`, 예시 데이터는 `js/data/demo-data.js`에 둡니다.
- `templates/`: 현재 사용하지 않습니다.
- `src/test/java/`: intended test location; not created yet.
- `build/generated/querydsl/`: generated Q types. Never edit generated files or commit `build/`.
- `docs/project-status.md`: 확정 범위·구현 현황·예측 검토안. `docs/kbl-api-data.md`: API 항목·샘플·검증 범위. `docs/dashboard-ui.md`: 화면 구성과 실행 방법.

메뉴는 대시보드, 경기 일정, 팀 > 팀관리, 리그 순위, 선수 기록입니다. 페이지 정보와 메뉴 계층은 `js/components/ui.js`의 `PAGES`, `NAVIGATION`에서 관리하고 공통 동작은 `app-shell.js`에 둡니다.

## Build, Test, and Development Commands

Install JDK 25 and use the checked-in Gradle wrapper. From the repository root in PowerShell:

```powershell
.\gradlew.bat clean build  # Compile, run configured tests, and package
.\gradlew.bat test         # Run tests once test support is configured
.\gradlew.bat bootRun      # Start locally on port 9000
.\gradlew.bat bootJar      # Create the executable JAR under build/libs/
```

On macOS/Linux, use `./gradlew` instead. QueryDSL generation runs during Java compilation.

작업 규칙에 따라 빌드·테스트는 자동 실행하지 않아도 됩니다. 실제 수행한 확인만 결과에 기록합니다. UI는 별도의 프런트엔드 빌드 도구 없이 Spring Boot 정적 리소스로 제공됩니다.

## Coding Style & Naming Conventions

Use four-space indentation for Java and Gradle, and two spaces for YAML. Follow existing brace placement and explicit imports. Use lowercase packages, PascalCase classes, camelCase methods and fields, and UPPER_SNAKE_CASE constants. Use descriptive role suffixes such as `GameController`, `GameService`, and `GameRepository`. No formatter or linter is currently configured; keep formatting consistent with surrounding code.

새 메서드와 함수에는 역할을 설명하는 주석을 작성합니다. 프런트엔드는 기존 ES 모듈 구조와 네 칸 들여쓰기를 따르고 HTML·CSS·JS의 역할을 분리합니다.

## Testing Guidelines

There are currently no tests, test dependencies, or coverage thresholds. When adding tests, configure a Spring Boot test starter and JUnit Jupiter with `useJUnitPlatform()` in Gradle. Mirror production packages under `src/test/java` and name classes `*Test`. Cover business rules and failure paths; database integration tests require an isolated test database. A successful build without test sources does not demonstrate behavioral coverage.

## Commit & Pull Request Guidelines

History contains only brief setup messages (`first commit`, `기본설정`), with no established Conventional Commits policy. Write concise, action-oriented commit subjects. Pull requests should explain the behavior changed, reference applicable issues, list verification commands and results, and describe configuration or schema changes. Include screenshots when changing visible UI.

## Security & Configuration Tips

Set `MARIADB_URL` to `localhost:3306/basketball` or the actual host, port, and database; the YAML already supplies `jdbc:mariadb://`. Also provide `MARIADB_USERNAME` and `MARIADB_PASSWORD` through the process environment or IDE run configuration. Never commit credentials. Provision schema changes explicitly because `ddl-auto` is `none`. No `compose.yaml` is checked in despite the configuration comment.

API 조사 수치의 기준일은 2026-10-26입니다. 최근 5시즌은 진행 중인 2026–2027시즌을 포함한 2022–2023~2026–2027 정규리그이며, 조사 결과를 이미 구현된 수집 기능으로 표현하지 않습니다. 선수·팀 코드는 문자열로 보존하고, 최신 프로필의 소속팀을 과거 시즌 소속으로 사용하지 않습니다.
