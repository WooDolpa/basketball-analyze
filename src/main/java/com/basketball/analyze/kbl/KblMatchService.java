package com.basketball.analyze.kbl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
class KblMatchService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final String SEASON_ID = "2026-2027";
    private static final String LEAGUE_KEY = "S49G01";
    private static final int MIN_DATABASE_YEAR = 1000;
    private static final LocalTime LAST_DATABASE_TIME = LocalTime.MAX.truncatedTo(ChronoUnit.MICROS);

    private final KblMatchRepository repository;

    /** 경기 일정과 오늘 예정 경기 조회에 사용할 저장소를 연결합니다. */
    KblMatchService(KblMatchRepository repository) {
        this.repository = repository;
    }

    /** 요청 날짜를 검증하고 한국시간 시작일과 종료일을 모두 포함해 시즌 일정을 조회합니다. */
    @Transactional(readOnly = true)
    public KblScheduleResponse findMatches(String from, String to) {
        LocalDate fromDate = parseScheduleDate(from);
        LocalDate toDate = parseScheduleDate(to);
        if (fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate는 toDate보다 늦을 수 없습니다.");
        }
        requireSeasonData();

        List<KblScheduleResponse.Match> matches = repository.findMatches(
                        SEASON_ID, fromDate.atStartOfDay(), toDate.atTime(LAST_DATABASE_TIME))
                .stream().map(this::toScheduleMatch).toList();
        return new KblScheduleResponse(SEASON_ID, fromDate, toDate, matches);
    }

    /** 한국시간 기준으로 조회 시각을 한 번 정해 오늘 남은 예정 경기를 조회합니다. */
    @Transactional(readOnly = true)
    public KblMatchResponse findTodayMatches() {
        OffsetDateTime queriedAt = OffsetDateTime.now(KOREA_ZONE).withNano(0);
        LocalDate today = queriedAt.toLocalDate();
        requireSeasonData();

        List<KblMatchResponse.Match> matches = repository.findScheduledMatches(
                        SEASON_ID, queriedAt.toLocalDateTime(), today.plusDays(1).atStartOfDay())
                .stream().map(this::toResponseMatch).toList();
        return new KblMatchResponse(SEASON_ID, today, queriedAt, matches);
    }

    /** 요청 값을 DB가 다룰 수 있는 YYYY-MM-DD 형식의 실제 날짜로 변환합니다. */
    private LocalDate parseScheduleDate(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fromDate와 toDate를 YYYY-MM-DD 형식으로 입력해 주세요.");
        }
        try {
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < MIN_DATABASE_YEAR) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "날짜의 연도는 1000년 이상이어야 합니다.");
            }
            return date;
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 날짜입니다: " + value);
        }
    }

    /** 시즌과 참가팀의 사용 가능한 데이터가 있는지 확인합니다. */
    private void requireSeasonData() {
        String seasonSyncPath = "POST /api/kbl/seasons/" + LEAGUE_KEY;
        if (!repository.hasActiveSeason(SEASON_ID)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "시즌 " + SEASON_ID
                    + " 데이터를 사용할 수 없습니다. 시즌 정보 API(" + seasonSyncPath + "/sync)를 확인해 주세요.");
        }
        if (!repository.hasActiveSeasonTeam(SEASON_ID)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "시즌 " + SEASON_ID
                    + "의 참가팀 데이터가 없습니다. 구단 정보 API(POST /api/kbl/teams/sync)와 시즌별 순위 API("
                    + seasonSyncPath + "/standings/sync)를 확인해 주세요.");
        }
    }

    /** 기간별 일정에 홈·원정 쿼터 점수를 1~4쿼터 순으로 추가하고 미확인 점수는 null로 유지합니다. */
    private KblScheduleResponse.Match toScheduleMatch(KblMatchRepository.ScheduleMatchRow row) {
        KblMatchRepository.MatchRow matchRow = row.match();
        KblMatchResponse.Match match = toResponseMatch(matchRow);
        List<KblScheduleResponse.QuarterScore> quarters = List.of(
                new KblScheduleResponse.QuarterScore(1, row.homeFirstQuarterScore(), row.awayFirstQuarterScore()),
                new KblScheduleResponse.QuarterScore(2, row.homeSecondQuarterScore(), row.awaySecondQuarterScore()),
                new KblScheduleResponse.QuarterScore(3, row.homeThirdQuarterScore(), row.awayThirdQuarterScore()),
                new KblScheduleResponse.QuarterScore(4, row.homeFourthQuarterScore(), row.awayFourthQuarterScore()));
        return new KblScheduleResponse.Match(match.id(), match.matchCode(), match.startsAt(), match.status(),
                match.homeTeam(), match.awayTeam(), matchRow.homeScore(), matchRow.awayScore(), quarters);
    }

    /** DB에 저장한 한국시간 경기 일시에 UTC 오프셋을 붙여 응답을 구성합니다. */
    private KblMatchResponse.Match toResponseMatch(KblMatchRepository.MatchRow row) {
        return new KblMatchResponse.Match(row.id(), row.matchCode(),
                row.startsAt().atZone(KOREA_ZONE).toOffsetDateTime(), row.status(),
                new KblMatchResponse.Team(row.homeTeamCode(), row.homeTeamName()),
                new KblMatchResponse.Team(row.awayTeamCode(), row.awayTeamName()));
    }
}
