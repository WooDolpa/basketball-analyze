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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/kbl/seasons/{leagueKey}/teams/{teamCode}")
class KblTeamController {

    private static final Logger LOGGER = LoggerFactory.getLogger(KblTeamController.class);

    private final KblTeamService service;

    /** 팀 상세 조회 경로를 서비스에 연결합니다. */
    KblTeamController(KblTeamService service) {
        this.service = service;
    }

    /** 팀의 시즌 성적과 최근 종료 경기를 반환합니다. */
    @GetMapping
    public ResponseEntity<KblTeamResponse> findTeam(@PathVariable("leagueKey") String leagueKey,
                                                    @PathVariable("teamCode") String teamCode,
                                                    @RequestParam(name = "limit", required = false) String limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.findTeam(leagueKey, teamCode, limit));
    }

    /** 요청 검증과 팀·시즌 적재 데이터 조회 오류를 응답에 담습니다. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleQueryError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(exception.getBody());
    }

    /** DB 오류를 로그에 기록하고 화면에 표시할 조회 실패 문구를 반환합니다. */
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ProblemDetail handleDatabaseError(RuntimeException exception) {
        LOGGER.error("KBL 팀 상세 조회에 실패했습니다.", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "KBL 팀 상세를 조회하지 못했습니다.");
    }
}
