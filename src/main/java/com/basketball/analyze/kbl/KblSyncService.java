package com.basketball.analyze.kbl;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.basketball.analyze.kbl.KblSyncRepository.MatchStatus.FINISHED;
import static com.basketball.analyze.kbl.KblSyncRepository.MatchStatus.LIVE;
import static com.basketball.analyze.kbl.KblSyncRepository.MatchStatus.SCHEDULED;

@Service
class KblSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KblSyncService.class);
    private static final String LEAGUE_KEY = "S49G01";
    private static final String SEASON_ID = "2026-2027";
    private static final int GAME_QUERY_DAYS = 31;
    private static final int MAX_REQUEST_DAYS = 366;
    private static final int MIN_DATABASE_YEAR = 1000;
    private static final BigDecimal MAX_GAMES_BEHIND = new BigDecimal("99.9");
    private static final DateTimeFormatter GAME_TIME = DateTimeFormatter.ofPattern("HHmm")
            .withResolverStyle(ResolverStyle.STRICT);

    private final KblClient client;
    private final KblSyncRepository repository;

    /** 외부 조회·검증이 끝난 후에만 저장 트랜잭션을 시작합니다. */
    KblSyncService(KblClient client, KblSyncRepository repository) {
        this.client = client;
        this.repository = repository;
    }

    // 한 로컬 앱에서 수집 요청을 순차 처리해 늦게 도착한 이전 응답이 최신 데이터를 덮지 않게 합니다.

    /** KBL 정규리그 키로 대상 시즌을 찾아 저장합니다. */
    synchronized SyncResult syncSeason(String leagueKey) {
        requireSupportedLeague(leagueKey);
        List<KblClient.Season> seasons = client.fetchSeasons();
        List<KblClient.Season> selected = seasons.stream().filter(season -> leagueKey.equals(season.leagueKey())).toList();
        if (selected.size() != 1 || !SEASON_ID.equals(selected.getFirst().seasonId())) {
            throw invalidResponse("시즌 " + leagueKey + "의 ID를 확인할 수 없습니다.");
        }
        String[] years = selected.getFirst().seasonId().split("-");
        repository.saveSeason(SEASON_ID, years[0], years[1]);
        return new SyncResult(seasons.size(), 1);
    }

    /** 구단 코드와 표시용 구단명을 검증해 저장합니다. */
    synchronized SyncResult syncTeams() {
        List<KblClient.Team> teams = client.fetchTeams();
        if (teams.isEmpty()) {
            throw invalidResponse("구단 목록이 비었습니다.");
        }
        Set<String> codes = new HashSet<>();
        for (KblClient.Team team : teams) {
            validateTeamCode(team.code(), "구단 " + team.code());
            if (!codes.add(team.code()) || team.name() == null || team.name().isBlank() || team.name().length() > 10) {
                throw invalidResponse("구단 " + team.code() + "의 코드가 중복되었거나 이름이 올바르지 않습니다.");
            }
        }
        repository.saveTeams(teams);
        return new SyncResult(teams.size(), teams.size());
    }

    /** 시즌별 순위와 승패·승률·승차의 저장 가능 여부를 검증합니다. */
    synchronized SyncResult syncStandings(String leagueKey) {
        requireSupportedLeague(leagueKey);
        List<KblClient.Standing> standings = client.fetchStandings(leagueKey);
        if (standings.isEmpty()) {
            throw invalidResponse("시즌 " + leagueKey + "의 참가팀 순위가 비었습니다.");
        }
        Set<String> teamCodes = new HashSet<>();
        for (KblClient.Standing standing : standings) {
            String target = "시즌 " + leagueKey + " 구단 " + standing.teamCode();
            validateTeamCode(standing.teamCode(), target);
            if (!teamCodes.add(standing.teamCode())) {
                throw invalidResponse(target + "이(가) 순위 목록에 중복되었습니다.");
            }
            if (!within(standing.rank(), 1, Byte.MAX_VALUE) || !within(standing.wins(), 0, Byte.MAX_VALUE)
                    || !within(standing.losses(), 0, Byte.MAX_VALUE)
                    || !within(standing.winRate(), BigDecimal.ONE) || !within(standing.gamesBehind(), MAX_GAMES_BEHIND)) {
                throw invalidResponse(target + "의 순위 또는 승패·승률·승차 값이 올바르지 않습니다.");
            }
        }
        repository.saveStandings(SEASON_ID, leagueKey, standings);
        return new SyncResult(standings.size(), standings.size());
    }

    /** 경기 목록을 날짜 구간별로 조회해 대상 시즌의 전체 요청 범위를 한 번에 저장합니다. */
    synchronized SyncResult syncMatches(String leagueKey, String from, String to) {
        requireSupportedLeague(leagueKey);
        LocalDate fromDate = parseRequestDate(from);
        LocalDate toDate = parseRequestDate(to);
        long days = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        if (days < 1 || days > MAX_REQUEST_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "경기 조회 기간은 1일 이상 366일 이하여야 합니다.");
        }

        int fetchedCount = 0;
        List<KblSyncRepository.Match> matches = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (LocalDate start = fromDate; !start.isAfter(toDate); start = start.plusDays(GAME_QUERY_DAYS)) {
            LocalDate end = start.plusDays(Math.min(GAME_QUERY_DAYS - 1, ChronoUnit.DAYS.between(start, toDate)));
            List<KblClient.Game> games = client.fetchGames(start, end);
            fetchedCount += games.size();
            for (KblClient.Game game : games) {
                if (game.leagueKey() == null || game.leagueKey().isBlank()) {
                    throw invalidResponse("경기 " + game.code() + "의 정규리그 키가 누락되었습니다.");
                }
                if (leagueKey.equals(game.leagueKey())) {
                    KblSyncRepository.Match match = toMatch(game);
                    if (!codes.add(match.code())) {
                        throw invalidResponse("경기 " + match.code() + "이(가) 일정 목록에 중복되었습니다.");
                    }
                    matches.add(match);
                }
            }
        }
        repository.saveMatches(SEASON_ID, leagueKey, matches);
        return new SyncResult(fetchedCount, matches.size());
    }

    /** 기간 내 종료 경기를 순서대로 수집하고 각 경기의 실패를 기록한 뒤 다음 경기를 처리합니다. */
    synchronized QuarterRangeSyncResult syncMatchQuartersInRange(String leagueKey, String from, String to) {
        requireSupportedLeague(leagueKey);
        LocalDate fromDate = parseRequestDate(from);
        LocalDate toDate = parseRequestDate(to);
        if (fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate는 toDate보다 늦을 수 없습니다.");
        }
        if (fromDate.getYear() < MIN_DATABASE_YEAR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "날짜의 연도는 1000년 이상이어야 합니다.");
        }

        List<String> matchCodes = repository.findFinishedMatchCodes(SEASON_ID, fromDate, toDate);
        List<QuarterSyncFailure> failures = new ArrayList<>();
        for (String matchCode : matchCodes) {
            try {
                syncMatchQuarters(matchCode);
            } catch (ResponseStatusException exception) {
                failures.add(new QuarterSyncFailure(matchCode, exception.getStatusCode().value(), exception.getReason()));
            } catch (DataAccessException | TransactionException exception) {
                LOGGER.error("경기 {}의 쿼터 점수 DB 처리에 실패했습니다.", matchCode, exception);
                failures.add(new QuarterSyncFailure(matchCode, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "쿼터 점수 데이터베이스 처리에 실패했습니다."));
            }
        }
        return new QuarterRangeSyncResult(SEASON_ID, fromDate, toDate, matchCodes.size(),
                matchCodes.size() - failures.size(), failures.size(), List.copyOf(failures));
    }

    /** 적재된 종료 경기의 상세를 검증하고 홈·원정 1~4쿼터의 점수를 저장합니다. */
    synchronized QuarterSyncResult syncMatchQuarters(String matchCode) {
        if (matchCode.length() > 30 || !matchCode.matches(LEAGUE_KEY + "N[0-9]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "경기 코드는 " + LEAGUE_KEY + "N29와 같은 2026–2027 정규리그 경기 코드로 입력해 주세요.");
        }
        if (!repository.hasFinishedMatch(SEASON_ID, matchCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "경기 " + matchCode
                    + "의 종료 결과가 없습니다. 경기 종료 후 경기 수집 API(POST /api/kbl/seasons/"
                    + LEAGUE_KEY + "/matches/sync)를 먼저 호출해 주세요.");
        }

        KblClient.GameDetail detail = client.fetchGameDetail(matchCode);
        KblClient.DetailGame game = detail.game();
        if (game == null || !matchCode.equals(game.code()) || !LEAGUE_KEY.equals(game.leagueKey())
                || !Integer.valueOf(1).equals(game.isStarted()) || !Integer.valueOf(1).equals(game.isEnded())) {
            throw invalidResponse("경기 " + matchCode + "의 경기 코드, 정규리그 또는 종료 상태를 확인할 수 없습니다.");
        }
        validateTeamCode(game.homeTeamCode(), "경기 " + matchCode + " 홈팀");
        validateTeamCode(game.awayTeamCode(), "경기 " + matchCode + " 원정팀");

        KblClient.TeamPeriodRecords records = detail.teamRecords();
        if (records == null || records.home() == null || records.away() == null) {
            throw invalidResponse("경기 " + matchCode + "의 양 팀 쿼터 점수가 누락되었습니다.");
        }
        KblClient.TeamPeriods home = records.home();
        KblClient.TeamPeriods away = records.away();
        List<KblSyncRepository.QuarterScore> quarters = List.of(
                toQuarterScore(matchCode, 1, home.firstQuarter(), away.firstQuarter()),
                toQuarterScore(matchCode, 2, home.secondQuarter(), away.secondQuarter()),
                toQuarterScore(matchCode, 3, home.thirdQuarter(), away.thirdQuarter()),
                toQuarterScore(matchCode, 4, home.fourthQuarter(), away.fourthQuarter()));
        if (home.overtime() == null || away.overtime() == null || home.overtime().size() != away.overtime().size()) {
            throw invalidResponse("경기 " + matchCode + "의 양 팀 연장 점수 배열이 올바르지 않습니다.");
        }
        int homeTotal = addOvertimeScores(matchCode, quarters.stream().mapToInt(KblSyncRepository.QuarterScore::homeScore).sum(),
                home.overtime());
        int awayTotal = addOvertimeScores(matchCode, quarters.stream().mapToInt(KblSyncRepository.QuarterScore::awayScore).sum(),
                away.overtime());
        repository.saveMatchQuarters(SEASON_ID, LEAGUE_KEY, new KblSyncRepository.MatchQuarters(
                matchCode, game.homeTeamCode(), game.awayTeamCode(), homeTotal, awayTotal, quarters));
        return new QuarterSyncResult(matchCode, quarters);
    }

    /** 쿼터 점수가 누락됐거나 DB 저장 범위를 벗어나면 저장 전에 응답을 거절합니다. */
    private KblSyncRepository.QuarterScore toQuarterScore(String matchCode, int quarter, Integer homeScore, Integer awayScore) {
        if (!within(homeScore, 0, Byte.MAX_VALUE) || !within(awayScore, 0, Byte.MAX_VALUE)) {
            throw invalidResponse("경기 " + matchCode + "의 " + quarter + "쿼터 점수가 누락되었거나 올바르지 않습니다.");
        }
        return new KblSyncRepository.QuarterScore(quarter, homeScore, awayScore);
    }

    /** 연장 점수를 검증하고 정규 쿼터 합계에 더해 DB 최종점수와 대조할 값을 계산합니다. */
    private int addOvertimeScores(String matchCode, int regulationScore, List<Integer> overtime) {
        int total = regulationScore;
        for (Integer score : overtime) {
            if (!within(score, 0, Short.MAX_VALUE - total)) {
                throw invalidResponse("경기 " + matchCode + "의 연장 점수가 누락되었거나 올바르지 않습니다.");
            }
            total += score;
        }
        return total;
    }

    /** KBL 플래그로 경기 상태를 정하고 종료 경기의 승리팀을 점수에서 계산합니다. */
    private KblSyncRepository.Match toMatch(KblClient.Game game) {
        String target = "경기 " + game.code();
        validateTeamCode(game.homeTeamCode(), target + " 홈팀");
        validateTeamCode(game.awayTeamCode(), target + " 원정팀");
        if (game.code() == null || game.code().isBlank() || game.code().length() > 30
                || game.homeTeamCode().equals(game.awayTeamCode())) {
            throw invalidResponse(target + "의 경기 코드 또는 홈·원정팀이 올바르지 않습니다.");
        }
        if (!within(game.isStarted(), 0, 1) || !within(game.isEnded(), 0, 1)
                || game.isEnded() > game.isStarted()) {
            throw invalidResponse(target + "의 시작·종료 플래그를 확인할 수 없습니다.");
        }

        LocalDateTime startsAt = parseStartTime(game);
        if (game.isStarted() == 0) {
            return new KblSyncRepository.Match(game.code(), game.homeTeamCode(), game.awayTeamCode(),
                    startsAt, SCHEDULED, null, null, null);
        }

        Integer homeScore = game.homeScore();
        Integer awayScore = game.awayScore();
        if ((homeScore != null && !within(homeScore, 0, Short.MAX_VALUE))
                || (awayScore != null && !within(awayScore, 0, Short.MAX_VALUE))) {
            throw invalidResponse(target + "의 점수가 저장 범위를 벗어났습니다.");
        }
        if (game.isEnded() == 0) {
            return new KblSyncRepository.Match(game.code(), game.homeTeamCode(), game.awayTeamCode(),
                    startsAt, LIVE, null, homeScore, awayScore);
        }
        if (homeScore == null || awayScore == null || homeScore.equals(awayScore)) {
            throw invalidResponse(target + "의 최종점수로 승리팀을 확인할 수 없습니다.");
        }
        String winner = homeScore > awayScore ? "HOME" : "AWAY";
        return new KblSyncRepository.Match(game.code(), game.homeTeamCode(), game.awayTeamCode(),
                startsAt, FINISHED, winner, homeScore, awayScore);
    }

    /** KBL의 한국시간 날짜·시각을 결합하며 미정 일시는 비워 둡니다. */
    private LocalDateTime parseStartTime(KblClient.Game game) {
        try {
            LocalDate date = game.gameDate() == null || game.gameDate().isBlank()
                    ? null : LocalDate.parse(game.gameDate(), DateTimeFormatter.BASIC_ISO_DATE);
            LocalTime time = game.gameStart() == null || game.gameStart().isBlank()
                    ? null : LocalTime.parse(game.gameStart(), GAME_TIME);
            return date == null || time == null ? null : LocalDateTime.of(date, time);
        } catch (DateTimeParseException exception) {
            throw invalidResponse("경기 " + game.code() + "의 예정 일시가 올바르지 않습니다.");
        }
    }

    /** API 요청에 들어온 날짜를 ISO 연월일로 검증합니다. */
    private LocalDate parseRequestDate(String value) {
        if (value != null && value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 날짜입니다: " + value);
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate와 toDate를 YYYY-MM-DD 형식으로 입력해 주세요.");
    }

    /** 첫 수집 대상인 2026–2027 1군 정규리그 키를 확인합니다. */
    private void requireSupportedLeague(String leagueKey) {
        if (!LEAGUE_KEY.equals(leagueKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하는 정규리그 키는 " + LEAGUE_KEY + "입니다.");
        }
    }

    /** 앞자리 0을 유지하면서 구단 코드의 길이와 문자를 확인합니다. */
    private void validateTeamCode(String code, String target) {
        if (code == null || !code.matches("[0-9]{1,3}")) {
            throw invalidResponse(target + "의 구단 코드가 올바르지 않습니다.");
        }
    }

    /** 정수 값의 필수 여부와 저장 범위를 확인합니다. */
    private boolean within(Integer value, int minimum, int maximum) {
        return value != null && value >= minimum && value <= maximum;
    }

    /** 비음수 소수 값의 필수 여부와 저장 범위를 확인합니다. */
    private boolean within(BigDecimal value, BigDecimal maximum) {
        return value != null && value.signum() >= 0 && value.compareTo(maximum) <= 0;
    }

    /** 수집할 수 없는 KBL 응답에 대상과 원인을 표시합니다. */
    private ResponseStatusException invalidResponse(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "KBL 응답 오류. " + detail);
    }

    record SyncResult(int fetchedCount, int processedCount) {}

    /** 저장을 완료한 경기 코드와 1~4쿼터 점수를 반환합니다. */
    record QuarterSyncResult(String matchCode, List<KblSyncRepository.QuarterScore> quarters) {}

    /** 조회 기간과 대상·저장·실패 경기 수를 반환합니다. */
    record QuarterRangeSyncResult(
            String seasonId,
            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate fromDate,
            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate toDate,
            int targetCount,
            int savedCount,
            int failedCount,
            List<QuarterSyncFailure> failures
    ) {}

    /** 실패한 경기와 원인을 식별할 수 있는 상태 코드 및 상세 메시지를 담습니다. */
    record QuarterSyncFailure(String matchCode, int status, String detail) {}
}
