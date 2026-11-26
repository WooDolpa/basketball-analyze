package com.basketball.analyze.kbl;

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

@RestController
@RequestMapping("/api/kbl")
class KblSyncController {

    private final KblSyncService service;

    /** 각 수집 경로를 같은 업무 서비스에 연결합니다. */
    KblSyncController(KblSyncService service) {
        this.service = service;
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
    public KblSyncService.QuarterRangeSyncResult syncMatchQuartersInRange(
            @PathVariable("leagueKey") String leagueKey, @RequestBody MatchDateRange range) {
        return service.syncMatchQuartersInRange(leagueKey, range.fromDate(), range.toDate());
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

    /** 기간을 받는 수집 요청의 JSON 본문을 읽지 못했을 때 입력 형식을 안내합니다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleInvalidBody() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "JSON 본문에 fromDate와 toDate를 YYYY-MM-DD 형식으로 입력해 주세요.");
    }

    record MatchDateRange(String fromDate, String toDate) {}
}
