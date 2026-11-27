package com.basketball.analyze.kbl;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/kbl")
class KblSyncController {

    private static final String PLAYER_BACKFILL_PATH = "/players/backfill";

    private final KblSyncService service;
    private final KblPlayerSyncService playerSyncService;

    /** 수집 경로를 시즌·경기 수집과 선수 수집 서비스에 연결합니다. */
    KblSyncController(KblSyncService service, KblPlayerSyncService playerSyncService) {
        this.service = service;
        this.playerSyncService = playerSyncService;
    }

    /** 대상 시즌의 정보를 수집하고 저장합니다. */
    @PostMapping("/seasons/{leagueKey}/sync")
    public KblSyncService.SyncResult syncSeason(@PathVariable("leagueKey") String leagueKey) {
        return service.syncSeason(leagueKey);
    }

    /** 구단 정보를 수집하고 저장합니다. */
    @PostMapping("/teams/sync")
    public KblSyncService.SyncResult syncTeams() {
        return service.syncTeams();
    }

    /** 현재 등록선수의 기본 정보를 선수 코드로 저장하거나 갱신합니다. */
    @PostMapping("/players/sync")
    public KblSyncService.SyncResult syncPlayers() {
        return playerSyncService.syncPlayers();
    }

    /** 지정한 선수 코드 중 DB에 없는 선수의 공식 프로필을 저장합니다. */
    @PostMapping(PLAYER_BACKFILL_PATH)
    public KblPlayerSyncService.BackfillResult backfillPlayers(@RequestBody PlayerBackfillRequest request) {
        return playerSyncService.backfillPlayers(request.playerCodes());
    }

    /** 등록 명단에 있는 선수와 해당 시즌 참가팀의 소속 관계를 저장합니다. */
    @PostMapping("/seasons/{leagueKey}/rosters/sync")
    public KblSyncService.RosterSyncResult syncRosters(@PathVariable("leagueKey") String leagueKey) {
        return service.syncRosters(leagueKey);
    }

    /** 시즌별 순위를 수집하고 저장합니다. */
    @PostMapping("/seasons/{leagueKey}/standings/sync")
    public KblSyncService.SyncResult syncStandings(@PathVariable("leagueKey") String leagueKey) {
        return service.syncStandings(leagueKey);
    }

    /** 요청 기간의 경기 일정과 결과를 수집하고 저장합니다. */
    @PostMapping("/seasons/{leagueKey}/matches/sync")
    public KblSyncService.SyncResult syncMatches(@PathVariable("leagueKey") String leagueKey, @RequestBody MatchDateRange range) {
        return service.syncMatches(leagueKey, range.fromDate(), range.toDate());
    }

    /** 지정 기간에 적재된 종료 경기의 쿼터 점수를 저장하고 경기별 실패 내역을 반환합니다. */
    @PostMapping("/seasons/{leagueKey}/matches/quarters/sync")
    public KblSyncService.MatchRangeSyncResult syncMatchQuartersInRange(
            @PathVariable("leagueKey") String leagueKey, @RequestBody MatchDateRange range) {
        return service.syncMatchQuartersInRange(leagueKey, range.fromDate(), range.toDate());
    }

    /** 지정 기간에 적재된 종료 경기의 선수 기록과 경기 당시 소속을 저장합니다. */
    @PostMapping("/seasons/{leagueKey}/matches/player-records/sync")
    public KblSyncService.MatchRangeSyncResult syncMatchPlayerRecordsInRange(
            @PathVariable("leagueKey") String leagueKey, @RequestBody MatchDateRange range) {
        return service.syncMatchPlayerRecordsInRange(leagueKey, range.fromDate(), range.toDate());
    }

    /** 이미 적재된 종료 경기 한 건의 홈·원정 1~4쿼터 점수를 수집해 저장합니다. */
    @PostMapping("/matches/{matchCode}/quarters/sync")
    public KblSyncService.QuarterSyncResult syncMatchQuarters(@PathVariable("matchCode") String matchCode) {
        return service.syncMatchQuarters(matchCode);
    }

    /** 요청 검증·선행 적재·외부 조회 오류의 원인을 응답에 담습니다. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleSyncError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(exception.getBody());
    }

    /** JSON 본문을 읽지 못했을 때 요청 경로에 맞는 입력 형식을 안내합니다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleInvalidBody(HttpServletRequest request) {
        String detail = request.getRequestURI().endsWith(PLAYER_BACKFILL_PATH)
                ? "JSON 본문에 playerCodes 배열을 입력해 주세요."
                : "JSON 본문에 fromDate와 toDate를 YYYY-MM-DD 형식으로 입력해 주세요.";
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    record MatchDateRange(String fromDate, String toDate) {}

    record PlayerBackfillRequest(List<?> playerCodes) {}
}
