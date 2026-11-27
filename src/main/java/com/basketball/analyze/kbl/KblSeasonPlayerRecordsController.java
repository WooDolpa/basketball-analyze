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
@RequestMapping("/api/kbl/seasons/{leagueKey}/player-records")
class KblSeasonPlayerRecordsController {

    private static final Logger LOGGER = LoggerFactory.getLogger(KblSeasonPlayerRecordsController.class);

    private final KblSeasonPlayerRecordsService service;

    /** 선수 시즌 집계 조회 경로를 서비스에 연결합니다. */
    KblSeasonPlayerRecordsController(KblSeasonPlayerRecordsService service) {
        this.service = service;
    }

    /** 적재된 종료 경기에서 계산한 선수별 합계와 경기당 평균을 반환합니다. */
    @GetMapping
    public ResponseEntity<KblSeasonPlayerRecordsResponse> findPlayerRecords(@PathVariable("leagueKey") String leagueKey) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.findPlayerRecords(leagueKey));
    }

    /** 지원하지 않는 정규리그 키의 원인을 응답에 담습니다. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleQueryError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(exception.getBody());
    }

    /** DB 실패를 기록하고 선수 기록 조회 오류를 반환합니다. */
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ProblemDetail handleDatabaseError(RuntimeException exception) {
        LOGGER.error("KBL 선수 기록을 조회하지 못했습니다.", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "KBL 선수 기록을 조회하지 못했습니다.");
    }
}
