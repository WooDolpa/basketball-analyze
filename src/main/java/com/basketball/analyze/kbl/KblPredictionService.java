package com.basketball.analyze.kbl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.basketball.analyze.kbl.KblPredictionResponse.PredictionStatus.AVAILABLE;
import static com.basketball.analyze.kbl.KblPredictionResponse.PredictionStatus.INSUFFICIENT_DATA;

@Service
class KblPredictionService {

    private static final double INITIAL_ELO = 1500.0;
    private static final double K_FACTOR = 20.0;
    private static final double ELO_SCALE = 400.0;
    private static final BigDecimal FULL_PERCENTAGE = new BigDecimal("100.0");

    private final KblMatchService matchService;
    private final KblMatchRepository repository;

    /** 오늘 예정 경기와 Elo 계산용 종료 경기의 조회를 연결합니다. */
    KblPredictionService(KblMatchService matchService, KblMatchRepository repository) {
        this.matchService = matchService;
        this.repository = repository;
    }

    /** 같은 한국시간 조회 기준으로 오늘 경기를 조회하고 이전 날짜의 결과만 예측에 반영합니다. */
    @Transactional(readOnly = true)
    public KblPredictionResponse findTodayPredictions() {
        KblMatchResponse schedule = matchService.findTodayMatches();
        if (schedule.matches().isEmpty()) {
            return new KblPredictionResponse(schedule.seasonId(), schedule.date(), schedule.queriedAt(), List.of());
        }

        Map<String, Double> ratings = calculateRatings(repository.findFinishedMatchesBefore(
                schedule.seasonId(), schedule.date().atStartOfDay()));
        List<KblPredictionResponse.Match> matches = schedule.matches().stream()
                .map(match -> new KblPredictionResponse.Match(match.id(), match.matchCode(), match.startsAt(),
                        match.status(), match.homeTeam(), match.awayTeam(),
                        predict(ratings.get(match.homeTeam().code()), ratings.get(match.awayTeam().code()))))
                .toList();
        return new KblPredictionResponse(schedule.seasonId(), schedule.date(), schedule.queriedAt(), matches);
    }

    /** 시간순으로 승패를 반영해 양 팀의 Elo를 같은 점수만큼 반대 방향으로 갱신합니다. */
    private Map<String, Double> calculateRatings(List<KblMatchRepository.FinishedMatchRow> matches) {
        Map<String, Double> ratings = new HashMap<>();
        for (KblMatchRepository.FinishedMatchRow match : matches) {
            double homeElo = ratings.getOrDefault(match.homeTeamCode(), INITIAL_ELO);
            double awayElo = ratings.getOrDefault(match.awayTeamCode(), INITIAL_ELO);
            double homeResult = match.homeWon() ? 1.0 : 0.0;
            double change = K_FACTOR * (homeResult - homeWinProbability(homeElo, awayElo));
            ratings.put(match.homeTeamCode(), homeElo + change);
            ratings.put(match.awayTeamCode(), awayElo - change);
        }
        return ratings;
    }

    /** 홈 이점을 적용하지 않고 두 팀 Elo 차이만으로 홈팀의 승리 확률을 계산합니다. */
    private double homeWinProbability(double homeElo, double awayElo) {
        return 1.0 / (1.0 + Math.pow(10.0, (awayElo - homeElo) / ELO_SCALE));
    }

    /** 비교할 기록이 없으면 승률을 비워 두고, 계산할 수 있으면 표시 합계를 100%로 맞춥니다. */
    private KblPredictionResponse.Prediction predict(Double homeElo, Double awayElo) {
        BigDecimal displayedHome = homeElo == null ? null : roundForDisplay(homeElo);
        BigDecimal displayedAway = awayElo == null ? null : roundForDisplay(awayElo);
        if (homeElo == null || awayElo == null) {
            return new KblPredictionResponse.Prediction(INSUFFICIENT_DATA, displayedHome, displayedAway,
                    null, null, "한 팀 이상이 반영할 종료 경기 기록이 없어 승률을 계산할 수 없습니다.");
        }

        BigDecimal homePercentage = roundForDisplay(homeWinProbability(homeElo, awayElo) * 100.0);
        BigDecimal awayPercentage = FULL_PERCENTAGE.subtract(homePercentage);
        return new KblPredictionResponse.Prediction(AVAILABLE, displayedHome, displayedAway,
                homePercentage, awayPercentage, describeEloDifference(displayedHome.subtract(displayedAway)));
    }

    /** 표시용 Elo 차이로 우세한 팀과 점수 차이를 설명합니다. */
    private String describeEloDifference(BigDecimal difference) {
        if (difference.signum() == 0) {
            return "표시 기준으로 두 팀의 Elo가 같습니다.";
        }
        String leadingSide = difference.signum() > 0 ? "홈팀" : "원정팀";
        return leadingSide + " Elo가 " + difference.abs().toPlainString() + "점 높아 우세합니다.";
    }

    /** 내부 계산의 정밀도를 유지하고 응답에 표시할 값만 소수점 첫째 자리로 반올림합니다. */
    private BigDecimal roundForDisplay(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
