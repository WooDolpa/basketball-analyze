# 로컬 MariaDB 실행·백업·복원

Docker·백업·복원 명령은 `docker/` 폴더에서 실행합니다. 실행 중인 Docker 엔진과 Docker Compose가 필요합니다. 백업·복원 스크립트 `db.ps1`은 Windows 기본 PowerShell 5.1과 PowerShell 7에서 실행할 수 있습니다.

## 시작

1. 저장소 루트에서 PowerShell은 `Set-Location docker`, macOS 셸은 `cd docker`를 실행합니다. 새 PC에서는 `.env.example`을 `.env`로 복사하고 `MARIADB_PASSWORD`와 `MARIADB_ROOT_PASSWORD`에 서로 다른 비밀번호를 입력합니다. Docker Compose와 Spring Boot가 같은 파일을 읽으므로 따옴표 없이 영문·숫자로 된 긴 비밀번호를 사용합니다.
2. 아래 명령으로 컨테이너를 시작합니다. JDBC 접속 주소는 기본 `jdbc:mariadb://127.0.0.1:3307/basketball`입니다. 포트 충돌이 있으면 `.env`의 `MARIADB_PORT`만 변경합니다.

```powershell
docker compose up -d --wait
docker compose ps
```

3. 새 빈 DB를 초기화하려면 저장소 루트로 돌아가 JDK 25로 앱을 실행합니다. 기존 SQL 백업이 있으면 먼저 아래 복원 절차를 진행합니다.

```powershell
Set-Location ..
.\gradlew.bat bootRun --args="--spring.profiles.active=docker"
```

macOS에서 `docker/` 폴더를 기준으로 실행하는 예시입니다. 프로젝트 검수에는 앱 포트 `9001`을 사용했습니다.

```bash
cd ..
JAVA_HOME=$(/usr/libexec/java_home -v 25) sh ./gradlew bootRun \
  --args='--spring.profiles.active=docker --server.address=127.0.0.1 --server.port=9001'
```

IntelliJ에서는 저장소 루트를 작업 디렉터리로 지정하고 활성 프로필에 `docker`를 입력합니다. 활성 프로필 입력란이 없으면 Program arguments에 `--spring.profiles.active=docker`를 추가합니다. 실행 설정에서 기존 원격 DB용 `-DMARIADB_*` 옵션을 빼고 `docker/.env`에서 접속 정보를 읽게 합니다. `host, pipe or local socket must be set to connect socket`이 나오면 활성 프로필과 DB URL을 먼저 확인합니다. 기본 프로필에서 `MARIADB_URL`이 비어 있으면 연결할 호스트가 없습니다.

Flyway는 앱 시작 시 `src/main/resources/db/migration`의 버전 파일을 차례대로 적용합니다. `V1`은 기본 테이블 8개, `V2`는 선수 코드 고유 제약과 내부 ID 시퀀스, `V3`는 경기별 출전 여부를 추가합니다. 적용한 마이그레이션은 보존하고 다음 스키마 변경은 새 버전 파일에 기록합니다. [데이터 저장 설계](../docs/data-model.md)

컨테이너를 종료하려면 `docker compose stop`을 실행합니다. 데이터는 Docker의 `mariadb-data` 볼륨에 유지됩니다.

## 비밀번호 변경과 빈 DB 초기화

- 기존 DB를 유지할 때는 MariaDB의 앱 계정 및 `root` 비밀번호를 먼저 변경하고, `docker/.env`의 `MARIADB_USER`, `MARIADB_PASSWORD`, `MARIADB_ROOT_PASSWORD`를 맞춥니다. 초기화된 볼륨의 계정 정보는 `.env` 수정만으로 갱신되지 않습니다. 앱 계정 이름을 바꿀 때는 새 계정에 `basketball` 스키마 권한을 부여하고, 앱의 새 계정 접속 확인 후 이전 계정을 정리합니다. `docker/`에서 `docker compose up -d --wait`를 실행한 뒤 앱을 다시 시작합니다.
- DB를 새로 만들 때는 빈 볼륨으로 시작하면 `.env`에 입력한 비밀번호로 계정이 생성됩니다. `docker compose down -v`는 해당 프로젝트의 DB 볼륨과 데이터를 삭제합니다. 보존할 데이터가 있다면 먼저 SQL로 백업합니다. 새 볼륨을 만든 뒤 앱을 시작하면 Flyway가 스키마를 적용합니다.

## 백업

쿼터 적재처럼 백업에 담을 작업이 끝난 뒤 실행합니다. DB 테이블 정의·데이터·`flyway_schema_history`를 SQL 파일 하나에 저장합니다. 덤프가 끝날 때까지 Flyway 마이그레이션과 스키마 변경 작업을 기다립니다.

1. Docker 엔진을 시작하고 저장소의 `docker/` 폴더로 이동합니다. `docker compose ps`에서 `mariadb`가 실행 중인지 확인합니다. 중지 상태면 `docker compose up -d --wait`로 시작합니다.
2. 같은 폴더에서 Windows PowerShell을 열고 백업 스크립트를 실행합니다. `RemoteSigned` 실행 정책은 이 명령이 시작하는 프로세스에 적용됩니다.

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\db.ps1 Backup
   ```

   Windows PowerShell에서 이미 로컬 스크립트 실행을 허용한다면 `.\db.ps1 Backup`으로 실행할 수 있습니다. macOS 터미널에서는 `pwsh -NoProfile -File ./db.ps1 Backup`을 실행합니다.

3. `백업 완료.` 메시지에 나온 파일 경로를 확인합니다. 기본 파일명은 `docker/backups/basketball-YYYYMMDD-HHmmss.sql`입니다. PowerShell에서 최신 백업의 경로·크기·수정 시각을 확인합니다.

   ```powershell
   Get-ChildItem ./backups -Filter 'basketball-*.sql' -File |
       Sort-Object LastWriteTime -Descending |
       Select-Object -First 1 FullName, Length, LastWriteTime
   ```

별도 경로를 쓰려면 `Backup` 뒤에 새 SQL 파일 경로를 넘깁니다. `.env`와 기본 백업 폴더는 Git에서 제외됩니다. PC를 옮길 때는 사용할 Git 버전을 새 PC에서 체크아웃하고, SQL 파일을 별도로 복사한 뒤 새 PC의 `.env`를 구성합니다.

2026-11-25 Mac 재적재 직후 만든 `basketball-20261125-232448.sql`은 별도 MariaDB에 복원해 검수했습니다. 현재 Windows 개발 DB의 Flyway `V3` 이력, 쿼터 점수, 선수 224명과 경기별 선수 기록 1,776행을 보관할 때는 위 절차로 현재 DB를 백업합니다. [적재 건수 및 백업 검수 결과](../docs/project-status.md#개발-db-적재와-백업-결과)

## 새 PC에서 복원

1. 새 PC의 `docker/`에서 `.env`를 구성하고 `docker compose up -d --wait`를 실행합니다. 애플리케이션은 복원이 끝난 뒤 시작합니다.
2. 빈 `basketball` DB에 백업을 복원합니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\db.ps1 Restore 'C:\path\to\basketball-20261117-150000.sql'
```

3. 저장소 루트에서 앱을 `docker` 프로필로 실행합니다. 복원된 Flyway 이력을 기준으로 이후 버전만 적용됩니다.

복원 스크립트는 이미 테이블이 있는 DB에서 작업을 중단합니다. 복원 중 오류가 발생해 일부 테이블이 생성됐다면 오류 원인을 해결한 뒤 새 빈 볼륨에서 다시 시도합니다. 기존 데이터를 보존해야 하는 볼륨은 별도로 백업합니다.
