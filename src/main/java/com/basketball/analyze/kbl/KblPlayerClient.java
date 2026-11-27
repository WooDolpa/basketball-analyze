package com.basketball.analyze.kbl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Component
class KblPlayerClient {

    private static final int PAGE_SIZE = 20;
    private static final int MAX_REGISTERED_PLAYERS = 2000;

    private final RestClient client;

    /** KBL 공식 사이트의 선수 API에 필요한 헤더와 제한 시간을 설정합니다. */
    KblPlayerClient(@Value("${kbl.player-api.base-url:https://kbl-api.sports2i.com/api/v1}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> {
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                    headers.setOrigin("https://www.kbl.or.kr");
                    headers.set("Referer", "https://www.kbl.or.kr/");
                })
                .build();
    }

    /** 전체 건수와 페이지별 행 수, 선수 코드 중복을 검사해 등록 명단을 구성합니다. */
    List<RegisteredPlayer> fetchRegisteredPlayers() {
        PlayerPage first = fetchRegisteredPlayerPage(1);
        Integer total = first.totalCount();
        if (total == null || total < 1 || total > MAX_REGISTERED_PLAYERS) {
            throw invalidRosterResponse("등록선수 총건수가 올바르지 않습니다.");
        }

        List<RegisteredPlayer> players = new ArrayList<>(total);
        Set<String> codes = new HashSet<>();
        int pageCount = Math.ceilDiv(total, PAGE_SIZE);
        for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
            PlayerPage page = pageNumber == 1 ? first : fetchRegisteredPlayerPage(pageNumber);
            int expected = Math.min(PAGE_SIZE, total - players.size());
            if (!total.equals(page.totalCount()) || page.players() == null || page.players().size() != expected) {
                throw invalidRosterResponse("등록선수 목록 " + pageNumber + "페이지의 건수가 올바르지 않습니다.");
            }
            for (RegisteredPlayer row : page.players()) {
                if (row == null || row.code() == null || !row.code().matches("[0-9]{6}")) {
                    throw invalidRosterResponse("등록선수 목록 " + pageNumber + "페이지에 올바르지 않은 선수 코드가 있습니다.");
                }
                if (!codes.add(row.code())) {
                    throw invalidRosterResponse("등록선수 목록에 선수 코드 " + row.code() + "이(가) 중복되었습니다.");
                }
                players.add(row);
            }
        }
        return players;
    }

    /** 현재 등록선수 목록에서 지정한 페이지를 읽습니다. */
    private PlayerPage fetchRegisteredPlayerPage(int pageNumber) {
        return fetch("등록선수 목록 " + pageNumber + "페이지", uri -> uri.path("/players")
                .queryParam("regSc", "Y")
                .queryParam("pageNo", pageNumber)
                .queryParam("listCn", PAGE_SIZE)
                .build(), PlayerPage.class);
    }

    /** 지정한 시즌 코드와 선수 코드에 해당하는 공식 프로필을 읽습니다. */
    ProfileResponse fetchProfile(String seasonCode, String playerCode) {
        return fetch("선수 " + playerCode + " 프로필", uri -> uri.path("/players/profile/{seasonCode}/{playerCode}")
                .build(seasonCode, playerCode), ProfileResponse.class);
    }

    /** 빈 응답과 통신·역직렬화 실패를 외부 조회 오류로 전달합니다. */
    private <T> T fetch(String target, Function<UriBuilder, URI> uri, Class<T> type) {
        try {
            T body = client.get().uri(uri).retrieve().body(type);
            if (body == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "KBL " + target + " 응답이 비었습니다.");
            }
            return body;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "KBL " + target + " 조회에 실패했습니다.", exception);
        }
    }

    /** 사용할 수 없는 명단 응답의 원인을 수집 API에 전달합니다. */
    private ResponseStatusException invalidRosterResponse(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "KBL " + detail);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PlayerPage(@JsonProperty("totalCn") Integer totalCount,
                              @JsonProperty("playerList") List<RegisteredPlayer> players) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RegisteredPlayer(
            @JsonProperty("playerNo") String code,
            @JsonProperty("kname") String name,
            @JsonProperty("ename") String englishName,
            String teamCode,
            @JsonProperty("playerFlagCode2") String division,
            @JsonProperty("pHeight") BigDecimal height,
            @JsonProperty("pWeight") BigDecimal weight
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProfileResponse(@JsonProperty("playerInfo") List<PlayerProfile> profiles) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PlayerProfile(
            @JsonProperty("playerNo") String code,
            @JsonProperty("kName") String name,
            @JsonProperty("eName") String englishName,
            @JsonProperty("birthday") String birthDate,
            @JsonProperty("country") String nationality,
            @JsonProperty("pHeight") BigDecimal height
    ) {}

}
