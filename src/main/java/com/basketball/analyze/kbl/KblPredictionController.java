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
@RequestMapping("/api/kbl/predictions")
class KblPredictionController {

    private static final Logger LOGGER = LoggerFactory.getLogger(KblPredictionController.class);

    private final KblPredictionService service;

    /** 예측 조회 경로를 Elo 계산 서비스에 연결합니다. */
    KblPredictionController(KblPredictionService service) {
        this.service = service;
    }

    /** 오늘 예정 경기의 예측을 반환하고 시각에 따라 바뀌는 응답의 캐시 저장을 막습니다. */
    @GetMapping("/today")
    public ResponseEntity<KblPredictionResponse> findTodayPredictions() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.findTodayPredictions());
    }

    /** 선행 적재 누락 등 기존 경기 조회에서 전달된 오류를 반환합니다. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleQueryError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(exception.getBody());
    }

    /** DB 오류를 로그에 남기고 예측 데이터를 조회할 수 없다는 응답을 반환합니다. */
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ProblemDetail handleDatabaseError(RuntimeException exception) {
        LOGGER.error("오늘 예정 경기 예측 조회에 실패했습니다.", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "오늘 예정 경기 예측 데이터를 조회하지 못했습니다.");
    }
}
