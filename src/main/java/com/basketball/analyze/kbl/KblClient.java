package com.basketball.analyze.kbl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

@Component
class KblClient {

    private final RestClient client;

    /** 모든 KBL 조회에 동일한 요청 헤더와 연결·응답 제한 시간을 적용합니다. */
    KblClient(@Value("${kbl.api.base-url:https://api.kbl.or.kr}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> {
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                    headers.set("Channel", "WEB");
                    headers.set("TeamCode", "XX");
                    headers.set("lang", "ko");
                    headers.set("X-Requested-With", "XMLHttpRequest");
                    headers.set("Referer", "https://www.kbl.or.kr/");
                })
                .build();
    }

    /** KBL 시즌 목록을 조회합니다. */
    List<Season> fetchSeasons() {
        return fetch("시즌 목록", uri -> uri.path("/season/list").build(), new ParameterizedTypeReference<>() {});
    }

    /** KBL 1군 구단 목록을 조회합니다. */
    List<Team> fetchTeams() {
        return fetch("구단 목록", uri -> uri.path("/common/teamList")
                .queryParam("kblYn", "N")
                .queryParam("sangumuYn", "N")
                .build(), new ParameterizedTypeReference<>() {});
    }

    /** 정규리그 키로 시즌 참가팀과 순위를 조회합니다. */
    List<Standing> fetchStandings(String leagueKey) {
        return fetch("시즌별 순위", uri -> uri.path("/league/rank/{leagueKey}").build(leagueKey),
                new ParameterizedTypeReference<>() {});
    }

    /** 지정한 날짜 구간의 1군 정규리그 일정을 양 끝 날짜를 포함해 조회합니다. */
    List<Game> fetchGames(LocalDate fromDate, LocalDate toDate) {
        return fetch("경기 일정 " + fromDate + "~" + toDate, uri -> uri.path("/match/list")
                .queryParam("fromDate", fromDate.format(DateTimeFormatter.BASIC_ISO_DATE))
                .queryParam("toDate", toDate.format(DateTimeFormatter.BASIC_ISO_DATE))
                .queryParam("tcodeList", "all")
                .queryParam("seasonCategory", "R")
                .queryParam("seasonGrade", 1)
                .build(), new ParameterizedTypeReference<>() {});
    }

    /** 경기 코드 한 건의 상세 정보와 홈·원정 쿼터 점수를 읽습니다. */
    GameDetail fetchGameDetail(String matchCode) {
        GameDetail detail = request("경기 " + matchCode + " 상세",
                uri -> uri.path("/match/{matchCode}").build(matchCode), new ParameterizedTypeReference<>() {});
        if (detail == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "KBL 경기 " + matchCode + " 상세 응답이 비었습니다.");
        }
        return detail;
    }

    /** KBL 목록 응답에 배열과 유효한 행이 들어 있는지 확인합니다. */
    private <T> List<T> fetch(String description, Function<UriBuilder, URI> uri, ParameterizedTypeReference<List<T>> type) {
        List<T> rows = request(description, uri, type);
        if (rows == null || rows.stream().anyMatch(row -> row == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "KBL " + description + " 응답 배열이 올바르지 않습니다.");
        }
        return rows;
    }

    /** 공통 KBL 요청을 보내고 통신·역직렬화 실패를 502로 전달합니다. */
    private <T> T request(String description, Function<UriBuilder, URI> uri, ParameterizedTypeReference<T> type) {
        try {
            return client.get().uri(uri).retrieve().body(type);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "KBL " + description + " 조회에 실패했습니다.", exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Season(@JsonProperty("glkey") String leagueKey, @JsonProperty("seasonName") String seasonId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Team(@JsonProperty("teamCode") String code, @JsonProperty("teamName") String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Standing(
            @JsonProperty("teamCode") String teamCode,
            Integer rank,
            @JsonProperty("TWin") Integer wins,
            @JsonProperty("TLoss") Integer losses,
            BigDecimal winRate,
            @JsonProperty("winDiff") BigDecimal gamesBehind
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Game(
            @JsonProperty("glkey") String leagueKey,
            @JsonProperty("gmkey") String code,
            @JsonProperty("tcodeH") String homeTeamCode,
            @JsonProperty("tcodeA") String awayTeamCode,
            String gameDate,
            String gameStart,
            Integer isStarted,
            Integer isEnded,
            @JsonProperty("scoreH") Integer homeScore,
            @JsonProperty("scoreA") Integer awayScore
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GameDetail(DetailGame game, @JsonProperty("teamrecords") TeamPeriodRecords teamRecords) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DetailGame(
            @JsonProperty("glkey") String leagueKey,
            @JsonProperty("gmkey") String code,
            @JsonProperty("tcodeH") String homeTeamCode,
            @JsonProperty("tcodeA") String awayTeamCode,
            Integer isStarted,
            Integer isEnded
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TeamPeriodRecords(TeamPeriods home, TeamPeriods away) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TeamPeriods(
            @JsonProperty("scoreq1") Integer firstQuarter,
            @JsonProperty("scoreq2") Integer secondQuarter,
            @JsonProperty("scoreq3") Integer thirdQuarter,
            @JsonProperty("scoreq4") Integer fourthQuarter,
            @JsonProperty("scoreeq") List<Integer> overtime
    ) {}
}
