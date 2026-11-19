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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/kbl/matches")
class KblMatchController {

    private static final Logger LOGGER = LoggerFactory.getLogger(KblMatchController.class);

    private final KblMatchService service;

    /** 경기 조회 경로를 서비스에 연결합니다. */
    KblMatchController(KblMatchService service) {
        this.service = service;
    }

    /** 오늘 예정 경기를 반환하고 시각에 따라 달라지는 응답의 캐시 저장을 막습니다. */
    @GetMapping("/today")
    public ResponseEntity<KblMatchResponse> findTodayMatches() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.findTodayMatches());
    }

    /** 조회에 필요한 선행 적재가 누락되면 수집 방법을 안내합니다. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleQueryError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(exception.getBody());
    }

    /** DB 오류는 서버 로그에 남기고 응답에는 조회 실패 안내를 담습니다. */
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ProblemDetail handleDatabaseError(RuntimeException exception) {
        LOGGER.error("오늘 예정 경기 조회에 실패했습니다.", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "오늘 예정 경기 데이터를 조회하지 못했습니다.");
    }
}
