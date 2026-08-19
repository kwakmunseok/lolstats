package com.lolstats.controller;

import com.lolstats.domain.Match;
import com.lolstats.domain.MatchParticipant;
import com.lolstats.domain.Summoner;
import com.lolstats.dto.ChampionStatRow;
import com.lolstats.dto.ChampionStatsResponse;
import com.lolstats.dto.FavoriteResponse;
import com.lolstats.dto.ItemEvent;
import com.lolstats.dto.SearchHistoryResponse;
import com.lolstats.repository.MatchParticipantRepository;
import com.lolstats.repository.MatchRepository;
import com.lolstats.service.ChampionStatsService;
import com.lolstats.service.DataDragonService;
import com.lolstats.service.FavoriteService;
import com.lolstats.service.MatchCollectionQueue;
import com.lolstats.service.MatchService;
import com.lolstats.service.QueueNames;
import com.lolstats.service.SearchHistoryService;
import com.lolstats.service.SummonerService;
import com.lolstats.service.TierEmblems;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

// Server-rendered screens (Task 9). Reuses the same service layer as the JSON API
// controllers instead of having the templates call /api/* over HTTP.
@Controller
public class PageController {

    private static final DateTimeFormatter PLAYED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    private final SummonerService summonerService;
    private final MatchService matchService;
    private final MatchCollectionQueue matchCollectionQueue;
    private final ChampionStatsService championStatsService;
    private final DataDragonService dataDragonService;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final ObjectMapper objectMapper;
    private final FavoriteService favoriteService;
    private final SearchHistoryService searchHistoryService;

    public PageController(
            SummonerService summonerService,
            MatchService matchService,
            MatchCollectionQueue matchCollectionQueue,
            ChampionStatsService championStatsService,
            DataDragonService dataDragonService,
            MatchRepository matchRepository,
            MatchParticipantRepository matchParticipantRepository,
            ObjectMapper objectMapper,
            FavoriteService favoriteService,
            SearchHistoryService searchHistoryService) {
        this.summonerService = summonerService;
        this.matchService = matchService;
        this.matchCollectionQueue = matchCollectionQueue;
        this.championStatsService = championStatsService;
        this.dataDragonService = dataDragonService;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.objectMapper = objectMapper;
        this.favoriteService = favoriteService;
        this.searchHistoryService = searchHistoryService;
    }

    @GetMapping("/")
    public String main() {
        return "main";
    }

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal Long userId) {
        return userId != null ? "redirect:/" : "login";
    }

    @GetMapping("/signup")
    public String signup(@AuthenticationPrincipal Long userId) {
        return userId != null ? "redirect:/" : "signup";
    }

    // /mypage is a permitAll route (PHASE5_PLAN.md Task 4 - anonymous auth is disabled, so
    // unauthenticated here means userId is simply null, not a 401) - this redirect is the only
    // guard, so a null check before touching favoriteService/searchHistoryService is required,
    // not optional.
    @GetMapping("/mypage")
    public String mypage(@AuthenticationPrincipal Long userId, Model model) {
        if (userId == null) {
            return "redirect:/login";
        }
        model.addAttribute("favorites", favoriteService.list(userId).stream().map(FavoriteResponse::from).toList());
        model.addAttribute("history", searchHistoryService.list(userId).stream().map(SearchHistoryResponse::from).toList());
        return "mypage";
    }

    @GetMapping("/summoners/{gameName}/{tagLine}")
    public String profile(
            @PathVariable String gameName, @PathVariable String tagLine,
            @AuthenticationPrincipal Long userId, Model model) {
        Summoner summoner = summonerService.findOrFetch(gameName, tagLine);
        // Search history (Phase 5 Task 4) has to be recorded here, not just in
        // SummonerController's JSON API - this page route calls summonerService directly and
        // never goes through that controller, so a search made through the actual website (the
        // main page's search form navigates straight here) would otherwise never be recorded.
        // Caught by Playwright verification (Task 5): mypage showed an empty history list after
        // visiting a profile page while logged in, which is what surfaced the gap.
        if (userId != null) {
            searchHistoryService.record(userId, summoner);
        }
        // Same trigger point as SummonerController's API route - response uses whatever's
        // already cached, missing matches go to the background queue (Phase 2 Task 3).
        MatchService.CollectionPlan plan = matchService.planCollection(summoner.getPuuid());
        if (!plan.missingMatchIds().isEmpty()) {
            matchCollectionQueue.enqueue(summoner.getPuuid(), plan.totalCount());
        }

        List<MatchParticipant> own = matchParticipantRepository.findByPuuidAndMatch_QueueTypeIn(
                summoner.getPuuid(), MatchService.RIFT_QUEUE_TYPES,
                PageRequest.of(0, 20, Sort.by("match.gameCreation").descending())).getContent();

        model.addAttribute("summoner", summoner);
        model.addAttribute("tierEmblemUrl", TierEmblems.imageUrl(summoner.getTier()));
        model.addAttribute("profileIconUrl", summoner.getProfileIconId() == null
                ? null : dataDragonService.getProfileIconUrl(summoner.getProfileIconId()));
        model.addAttribute("winRate", winRate(summoner.getWins(), summoner.getLosses()));
        model.addAttribute("matches", own.stream().map(this::toMatchCard).toList());
        // Static notice only (no JS polling yet) - PHASE2_PLAN.md Task 3 스코프는 API 필드까지,
        // 화면 폴링은 별도 태스크로 미룸.
        model.addAttribute("collecting", matchCollectionQueue.isCollecting(summoner.getPuuid()));
        // SSR이라 챔피언 아이콘(ddragon) 붙이기 편함 - 티어 이력은 아이콘이 필요 없어 클라이언트
        // 사이드에서 이미 검증된 /api/summoners/{id}/tier-history를 그대로 fetch(profile.html).
        model.addAttribute("championStats", toChampionStatsView(championStatsService.stats(summoner.getPuuid())));
        // 즐겨찾기 토글 버튼(Phase 5 Task 5) - 비로그인이면 버튼 자체가 안 보이니 false로 충분.
        model.addAttribute("isFavorited", userId != null && favoriteService.isFavorited(userId, summoner.getId()));
        return "profile";
    }

    @GetMapping("/matches/{riotMatchId}")
    public String matchDetail(
            @PathVariable String riotMatchId,
            @RequestParam(required = false) String focusPuuid,
            Model model) {
        Match match = matchRepository.findByRiotMatchId(riotMatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "match not found: " + riotMatchId));
        List<MatchParticipant> participants = matchParticipantRepository.findByMatchIdOrderByIdAsc(match.getId());
        List<ItemEvent> itemEvents = parseItemEvents(match.getItemEventsJson());

        model.addAttribute("riotMatchId", match.getRiotMatchId());
        model.addAttribute("playedAt", PLAYED_AT_FORMAT.format(match.getGameCreation()));
        model.addAttribute("duration", formatDuration(match.getGameDuration()));
        model.addAttribute("queueType", QueueNames.displayName(match.getQueueType()));
        // Riot's response orders participants team100 (first 5) then team200 (last 5), and
        // saveAll()/findByMatchIdOrderByIdAsc preserve that order - that order IS the Timeline
        // participantId (1-based position = participantId). Kept as limit/skip (not
        // subList(0,5)/subList(5,10)) so a match saved with fewer than 10 participants still
        // renders instead of throwing IndexOutOfBoundsException.
        List<ParticipantView> views = toParticipantViews(participants, focusPuuid, itemEvents);
        model.addAttribute("team1", views.stream().limit(5).toList());
        model.addAttribute("team2", views.stream().skip(5).toList());
        return "match-detail";
    }

    // Package-private (not private) so PageControllerTest can exercise the participantId
    // mapping directly - this is the highest-risk logic in the feature (silent mis-attribution
    // of item events to the wrong player if the i+1/ordering assumption ever breaks).
    List<ParticipantView> toParticipantViews(
            List<MatchParticipant> participants, String focusPuuid, List<ItemEvent> itemEvents) {
        return java.util.stream.IntStream.range(0, participants.size())
                .mapToObj(i -> {
                    MatchParticipant p = participants.get(i);
                    int participantId = i + 1;
                    List<ItemEventView> events = p.getPuuid().equals(focusPuuid)
                            ? toItemEventViews(itemEvents, participantId)
                            : List.of();
                    return toParticipantView(p, events);
                })
                .toList();
    }

    private List<ItemEventView> toItemEventViews(List<ItemEvent> itemEvents, int participantId) {
        return itemEvents.stream()
                .filter(e -> e.participantId() == participantId)
                .map(e -> {
                    Optional<DataDragonService.ItemInfo> item = dataDragonService.getItem(e.itemId());
                    return new ItemEventView(
                            item.map(DataDragonService.ItemInfo::imageUrl).orElse(null),
                            item.map(DataDragonService.ItemInfo::name).orElse("?"),
                            "ITEM_SOLD".equals(e.type()),
                            formatDuration((int) (e.timestampMs() / 1000)));
                })
                .toList();
    }

    private List<ItemEvent> parseItemEvents(String itemEventsJson) {
        if (itemEventsJson == null) {
            return List.of();
        }
        return List.of(objectMapper.readValue(itemEventsJson, ItemEvent[].class));
    }

    private MatchCardView toMatchCard(MatchParticipant p) {
        Match match = p.getMatch();
        return new MatchCardView(
                match.getRiotMatchId(),
                PLAYED_AT_FORMAT.format(match.getGameCreation()),
                formatDuration(match.getGameDuration()),
                QueueNames.displayName(match.getQueueType()),
                toParticipantView(p));
    }

    private ParticipantView toParticipantView(MatchParticipant p) {
        return toParticipantView(p, List.of());
    }

    private ParticipantView toParticipantView(MatchParticipant p, List<ItemEventView> itemEvents) {
        String championName = dataDragonService.getChampion(p.getChampionId())
                .map(DataDragonService.ChampionInfo::name).orElse("?");
        String championImageUrl = dataDragonService.getChampion(p.getChampionId())
                .map(DataDragonService.ChampionInfo::imageUrl).orElse(null);

        List<ItemView> items = List.of(objectMapper.readValue(p.getItemsJson(), Integer[].class)).stream()
                .map(id -> id == 0 ? null : dataDragonService.getItem(id)
                        .map(item -> new ItemView(item.imageUrl(), item.name(), item.description()))
                        .orElse(null))
                .toList();

        JsonNode perks = objectMapper.readTree(p.getRunesJson());
        String keystoneIconUrl = dataDragonService.getRuneIconUrl(
                perks.path("styles").path(0).path("selections").path(0).path("perk").asInt(0)).orElse(null);
        String secondaryStyleIconUrl = dataDragonService.getRuneIconUrl(
                perks.path("styles").path(1).path("style").asInt(0)).orElse(null);

        return new ParticipantView(
                p.getGameName(), p.getTagLine(), championName, championImageUrl, p.getTeamPosition(),
                p.getKills(), p.getDeaths(), p.getAssists(), p.getWin(),
                dataDragonService.getSpell(p.getSpell1Id()).map(DataDragonService.SpellInfo::imageUrl).orElse(null),
                dataDragonService.getSpell(p.getSpell2Id()).map(DataDragonService.SpellInfo::imageUrl).orElse(null),
                items, keystoneIconUrl, secondaryStyleIconUrl, itemEvents);
    }

    private String winRate(Integer wins, Integer losses) {
        if (wins == null || losses == null || wins + losses == 0) {
            return "-";
        }
        return "%.0f%%".formatted(100.0 * wins / (wins + losses));
    }

    private ChampionStatsView toChampionStatsView(ChampionStatsResponse stats) {
        List<ChampionStatRowView> rows = stats.perChampion().stream()
                .map(this::toChampionStatRowView)
                .toList();
        return new ChampionStatsView(
                stats.games(), winRatePct(stats.games(), stats.overallWinRate()), stats.recentForm(), rows);
    }

    private ChampionStatRowView toChampionStatRowView(ChampionStatRow row) {
        String championName = dataDragonService.getChampion(row.championId())
                .map(DataDragonService.ChampionInfo::name).orElse("?");
        String championImageUrl = dataDragonService.getChampion(row.championId())
                .map(DataDragonService.ChampionInfo::imageUrl).orElse(null);
        return new ChampionStatRowView(
                championName, championImageUrl, row.games(), winRatePct(row.games(), row.winRate()),
                "%.2f".formatted(row.avgKda()));
    }

    private String winRatePct(int games, double ratio) {
        return games == 0 ? "-" : "%.0f%%".formatted(ratio * 100);
    }

    private String formatDuration(Integer seconds) {
        if (seconds == null) {
            return "-";
        }
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    public record MatchCardView(
            String riotMatchId, String playedAt, String duration, String queueType, ParticipantView player) {
    }

    public record ParticipantView(
            String gameName, String tagLine, String championName, String championImageUrl, String teamPosition,
            Integer kills, Integer deaths, Integer assists, Boolean win,
            String spell1ImageUrl, String spell2ImageUrl, List<ItemView> items,
            String keystoneIconUrl, String secondaryStyleIconUrl, List<ItemEventView> itemEvents) {
    }

    public record ItemEventView(String imageUrl, String name, boolean sold, String timeLabel) {
    }

    public record ItemView(String imageUrl, String name, String description) {
    }

    public record ChampionStatsView(
            int games, String overallWinRate, List<Boolean> recentForm, List<ChampionStatRowView> perChampion) {
    }

    public record ChampionStatRowView(
            String championName, String championImageUrl, int games, String winRate, String avgKda) {
    }
}
