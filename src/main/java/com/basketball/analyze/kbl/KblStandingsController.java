package com.basketball.analyze.kbl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/kbl/seasons/{leagueKey}/standings")
class KblStandingsController {

    private static final Logger LOGGER = LoggerFactory.getLogger(KblStandingsController.class);

    private final KblStandingsService service;

    /** 순위 조회 경로를 서비스에 연결합니다. */
    KblStandingsController(KblStandingsService service) {
        this.service = service;
    }

    /** 정규리그 팀 순위를 반환하고 동기화한 결과를 다시 읽도록 캐시 저장을 막습니다. */
    @GetMapping
    public ResponseEntity<KblStandingsResponse> findStandings(@PathVariable("leagueKey") String leagueKey) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.findStandings(leagueKey));
    }

    /** 리그 키와 선행 적재 오류의 원인을 응답에 담습니다. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleQueryError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(exception.getBody());
    }

    /** DB 오류를 기록하고 순위 조회 실패 안내를 반환합니다. */
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ProblemDetail handleDatabaseError(RuntimeException exception) {
        LOGGER.error("KBL 팀 순위 조회에 실패했습니다.", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "KBL 팀 순위를 조회하지 못했습니다.");
    }
}
