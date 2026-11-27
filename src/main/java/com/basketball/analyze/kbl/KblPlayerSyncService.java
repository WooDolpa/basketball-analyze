package com.basketball.analyze.kbl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
class KblPlayerSyncService {

    private static final String PROFILE_SEASON_CODE = "49";
    private static final BigDecimal MAX_MEASUREMENT = new BigDecimal("999.99");
    private static final Set<String> DIVISIONS = Set.of("0", "1", "2");

    private final KblPlayerClient client;
    private final KblPlayerRepository repository;

    /** 공식 선수 목록을 읽는 클라이언트와 선수 기본 정보 저장소를 연결합니다. */
    KblPlayerSyncService(KblPlayerClient client, KblPlayerRepository repository) {
        this.client = client;
        this.repository = repository;
    }

    /** 모든 페이지와 프로필을 확인한 뒤 선수 기본 정보를 한 번에 저장합니다. */
    synchronized KblSyncService.SyncResult syncPlayers() {
        List<KblPlayerClient.RegisteredPlayer> registered = client.fetchRegisteredPlayers();
        List<KblPlayerRepository.Player> players = registered.stream().map(this::toPlayer).toList();
        repository.savePlayers(players);
        return new KblSyncService.SyncResult(registered.size(), players.size());
    }

    /** 누락 선수만 공식 프로필로 조회하고 전체 검증을 마친 뒤 신규 행을 저장합니다. */
    synchronized BackfillResult backfillPlayers(List<?> requestedCodes) {
        List<String> codes = validateBackfillCodes(requestedCodes);
        Set<String> existingCodes = repository.findExistingCodes();
        List<KblPlayerRepository.Player> players = codes.stream()
                .filter(code -> !existingCodes.contains(code))
                .map(this::toBackfillPlayer)
                .toList();
        if (!players.isEmpty()) {
            repository.insertMissingPlayers(players);
        }
        return new BackfillResult(codes.size(), players.size(), codes.size() - players.size());
    }

    /** 외부 조회 전에 비어 있는 요청, 6자리 문자열 형식과 코드 중복을 검증합니다. */
    private List<String> validateBackfillCodes(List<?> requestedCodes) {
        if (requestedCodes == null || requestedCodes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "playerCodes 배열에 선수 코드를 한 개 이상 입력해 주세요.");
        }
        Set<String> seen = new HashSet<>();
        List<String> codes = new ArrayList<>(requestedCodes.size());
        for (Object value : requestedCodes) {
            if (!(value instanceof String code) || !code.matches("[0-9]{6}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "선수 코드는 숫자 6자리 문자열로 입력해 주세요.");
            }
            if (!seen.add(code)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "선수 코드 " + code + "이(가) 중복되었습니다.");
            }
            codes.add(code);
        }
        return codes;
    }

    /** 누락 선수는 프로필에서 확인할 수 있는 기본 정보만 사용합니다. */
    private KblPlayerRepository.Player toBackfillPlayer(String code) {
        KblPlayerClient.PlayerProfile profile = findProfile(code);
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "KBL 선수 " + code + "의 공식 프로필이 없습니다.");
        }
        String name = text(profile.name(), 10, "이름", code);
        if (name == null) {
            throw invalidResponse("선수 " + code + "의 프로필에 이름이 없습니다.");
        }
        return new KblPlayerRepository.Player(code, name, text(profile.englishName(), 30, "영문 이름", code),
                birthDate(profile.birthDate(), code), text(profile.nationality(), 10, "국적", code),
                null, measurement(profile.height(), "신장", code), null);
    }

    /** 목록과 같은 선수의 프로필을 결합하고 결측치를 DB용 NULL로 변환합니다. */
    private KblPlayerRepository.Player toPlayer(KblPlayerClient.RegisteredPlayer row) {
        String code = row.code();
        String name = text(row.name(), 10, "이름", code);
        String englishName = text(row.englishName(), 30, "영문 이름", code);
        String division = text(row.division(), 1, "선수 구분", code);
        if (name == null || (division != null && !DIVISIONS.contains(division))) {
            throw invalidResponse("선수 " + code + "의 이름 또는 구분이 올바르지 않습니다.");
        }

        KblPlayerClient.PlayerProfile profile = findProfile(code);
        BigDecimal height = measurement(row.height(), "신장", code);
        BigDecimal weight = measurement(row.weight(), "몸무게", code);
        String birthDate = null;
        String nationality = null;
        if (profile != null) {
            birthDate = birthDate(profile.birthDate(), code);
            nationality = text(profile.nationality(), 10, "국적", code);
            if (height == null) {
                height = measurement(profile.height(), "신장", code);
            }
        }
        return new KblPlayerRepository.Player(code, name, englishName, birthDate, nationality, division, height, weight);
    }

    /** 공식 프로필이 요청한 선수와 일치하는지 확인하고 빈 프로필을 구분합니다. */
    private KblPlayerClient.PlayerProfile findProfile(String code) {
        List<KblPlayerClient.PlayerProfile> profiles = client.fetchProfile(PROFILE_SEASON_CODE, code).profiles();
        if (profiles == null || profiles.size() > 1
                || (!profiles.isEmpty() && (profiles.getFirst() == null || !code.equals(profiles.getFirst().code())))) {
            throw invalidResponse("선수 " + code + "의 프로필 식별자가 올바르지 않습니다.");
        }
        return profiles.isEmpty() ? null : profiles.getFirst();
    }

    /** 공백을 제거하고 DB 컬럼을 넘는 문자열에는 선수 코드와 필드명을 표시합니다. */
    private String text(String value, int maxLength, String field, String code) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.codePointCount(0, trimmed.length()) > maxLength) {
            throw invalidResponse("선수 " + code + "의 " + field + " 길이가 올바르지 않습니다.");
        }
        return trimmed;
    }

    /** 생년월일이 제공되면 실제 달력 날짜인지 확인하고 YYYYMMDD를 보존합니다. */
    private String birthDate(String value, String code) {
        String date = text(value, 8, "생년월일", code);
        if (date == null) {
            return null;
        }
        try {
            LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
            return date;
        } catch (DateTimeParseException exception) {
            throw invalidResponse("선수 " + code + "의 생년월일이 올바르지 않습니다.");
        }
    }

    /** 신장·몸무게는 0을 결측으로 보고 DECIMAL(5,2)에 들어가는 양수만 받습니다. */
    private BigDecimal measurement(BigDecimal value, String field, String code) {
        if (value == null || value.signum() == 0) {
            return null;
        }
        if (value.signum() < 0 || value.compareTo(MAX_MEASUREMENT) > 0 || value.stripTrailingZeros().scale() > 2) {
            throw invalidResponse("선수 " + code + "의 " + field + " 값이 올바르지 않습니다.");
        }
        return value;
    }

    /** 유효하지 않은 원본 응답의 이유를 수집 API에 반환합니다. */
    private ResponseStatusException invalidResponse(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "KBL " + reason);
    }

    /** 요청 선수 수와 신규 저장 대상으로 처리하거나 기존 행을 유지한 수를 반환합니다. */
    record BackfillResult(int requestedCount, int processedCount, int existingCount) {}

}
