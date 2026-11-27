package com.basketball.analyze.kbl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.util.List;

import static com.basketball.analyze.kbl.KblStandingsResponse.Result.LOSS;
import static com.basketball.analyze.kbl.KblStandingsResponse.Result.WIN;

@Service
class KblTeamService {

    private static final String LEAGUE_KEY = "S49G01";
    private static final String SEASON_ID = "2026-2027";
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final KblStandingsRepository standingsRepository;
    private final KblMatchRepository matchRepository;

    /** 팀의 시즌 성적과 종료 경기 기록을 읽는 저장소를 연결합니다. */
    KblTeamService(KblStandingsRepository standingsRepository, KblMatchRepository matchRepository) {
        this.standingsRepository = standingsRepository;
        this.matchRepository = matchRepository;
    }

    /** 입력을 검증하고 같은 DB 트랜잭션에서 시즌 성적과 최근 경기 결과를 조회합니다. */
    @Transactional(readOnly = true)
    public KblTeamResponse findTeam(String leagueKey, String teamCode, String requestedLimit) {
        if (!LEAGUE_KEY.equals(leagueKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하는 정규리그 키는 " + LEAGUE_KEY + "입니다.");
        }
        if (!teamCode.matches("[0-9]{1,3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "팀 코드는 1~3자리 숫자로 입력해 주세요.");
        }
        int limit = parseLimit(requestedLimit);
        List<KblStandingsRepository.StandingRow> standings = standingsRepository.findStandings(SEASON_ID);
        if (standings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "조회 가능한 순위 데이터가 없습니다. 시즌·구단 적재 후 POST /api/kbl/seasons/"
                            + LEAGUE_KEY + "/standings/sync를 호출해 주세요.");
        }
        KblStandingsRepository.StandingRow row = standings.stream()
                .filter(standing -> teamCode.equals(standing.teamCode()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "시즌 " + SEASON_ID + "의 참가팀 " + teamCode + "을(를) 찾을 수 없습니다."));
        var standing = new KblTeamResponse.SeasonStanding(row.rank(), row.wins() + row.losses(), row.wins(),
                row.losses(), row.winRate(), row.gamesBehind());
        List<KblTeamResponse.Game> recentGames = matchRepository.findRecentTeamMatches(SEASON_ID, teamCode, limit)
                .stream().map(match -> toTeamGame(match, teamCode)).toList();
        return new KblTeamResponse(SEASON_ID, new KblMatchResponse.Team(row.teamCode(), row.teamName()), standing, recentGames);
    }

    /** 생략 시 최대 열 경기로 조회하고 허용된 경기 수 문자열만 받습니다. */
    private int parseLimit(String requestedLimit) {
        return switch (requestedLimit == null ? "10" : requestedLimit) {
            case "5" -> 5;
            case "10" -> 10;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit은 5 또는 10으로 입력해 주세요.");
        };
    }

    /** 홈·원정 위치에 따라 상대팀과 득실점을 요청 팀 관점으로 변환합니다. */
    private KblTeamResponse.Game toTeamGame(KblMatchRepository.TeamMatchRow row, String teamCode) {
        KblMatchRepository.MatchRow match = row.match();
        boolean home = teamCode.equals(match.homeTeamCode());
        KblMatchResponse.Team opponent = home
                ? new KblMatchResponse.Team(match.awayTeamCode(), match.awayTeamName())
                : new KblMatchResponse.Team(match.homeTeamCode(), match.homeTeamName());
        return new KblTeamResponse.Game(match.matchCode(), match.startsAt().atZone(KOREA_ZONE).toOffsetDateTime(),
                opponent, home ? match.homeScore() : match.awayScore(), home ? match.awayScore() : match.homeScore(),
                home == row.homeWon() ? WIN : LOSS);
    }
}
