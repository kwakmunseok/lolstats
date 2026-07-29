package com.lolstats.controller;

import com.lolstats.domain.Match;
import com.lolstats.domain.MatchParticipant;
import com.lolstats.domain.Summoner;
import com.lolstats.repository.MatchParticipantRepository;
import com.lolstats.repository.MatchRepository;
import com.lolstats.service.DataDragonService;
import com.lolstats.service.MatchService;
import com.lolstats.service.SummonerService;
import com.lolstats.service.TierEmblems;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Server-rendered screens (Task 9). Reuses the same service layer as the JSON API
// controllers instead of having the templates call /api/* over HTTP.
@Controller
public class PageController {

    private static final DateTimeFormatter PLAYED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    private final SummonerService summonerService;
    private final MatchService matchService;
    private final DataDragonService dataDragonService;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final ObjectMapper objectMapper;

    public PageController(
            SummonerService summonerService,
            MatchService matchService,
            DataDragonService dataDragonService,
            MatchRepository matchRepository,
            MatchParticipantRepository matchParticipantRepository,
            ObjectMapper objectMapper) {
        this.summonerService = summonerService;
        this.matchService = matchService;
        this.dataDragonService = dataDragonService;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/")
    public String main() {
        return "main";
    }

    @GetMapping("/summoners/{gameName}/{tagLine}")
    public String profile(@PathVariable String gameName, @PathVariable String tagLine, Model model) {
        Summoner summoner = summonerService.findOrFetch(gameName, tagLine);
        // Sync, best-effort collection - same trigger point as SummonerController's API route.
        matchService.collectRecentMatches(summoner.getPuuid());

        List<MatchParticipant> own = matchParticipantRepository.findByPuuid(
                summoner.getPuuid(), PageRequest.of(0, 20, Sort.by("match.gameCreation").descending())).getContent();

        model.addAttribute("summoner", summoner);
        model.addAttribute("tierEmblemUrl", TierEmblems.imageUrl(summoner.getTier()));
        model.addAttribute("profileIconUrl", summoner.getProfileIconId() == null
                ? null : dataDragonService.getProfileIconUrl(summoner.getProfileIconId()));
        model.addAttribute("winRate", winRate(summoner.getWins(), summoner.getLosses()));
        model.addAttribute("matches", own.stream().map(this::toMatchCard).toList());
        return "profile";
    }

    @GetMapping("/matches/{riotMatchId}")
    public String matchDetail(@PathVariable String riotMatchId, Model model) {
        Match match = matchRepository.findByRiotMatchId(riotMatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "match not found: " + riotMatchId));
        List<MatchParticipant> participants = matchParticipantRepository.findByMatchId(match.getId());

        model.addAttribute("riotMatchId", match.getRiotMatchId());
        model.addAttribute("playedAt", PLAYED_AT_FORMAT.format(match.getGameCreation()));
        model.addAttribute("duration", formatDuration(match.getGameDuration()));
        model.addAttribute("queueType", match.getQueueType());
        // A fixed 5-vs-5 split assumed Summoner's Rift; Arena (queue 1750) has 16-18
        // participants in 2-player teams, so a single win/loss-colored list is the one
        // layout that's correct for every queue without persisting a team id.
        model.addAttribute("participants", participants.stream().map(this::toParticipantView).toList());
        return "match-detail";
    }

    private MatchCardView toMatchCard(MatchParticipant p) {
        Match match = p.getMatch();
        return new MatchCardView(
                match.getRiotMatchId(),
                PLAYED_AT_FORMAT.format(match.getGameCreation()),
                formatDuration(match.getGameDuration()),
                match.getQueueType(),
                toParticipantView(p));
    }

    private ParticipantView toParticipantView(MatchParticipant p) {
        String championName = dataDragonService.getChampion(p.getChampionId())
                .map(DataDragonService.ChampionInfo::name).orElse("?");
        String championImageUrl = dataDragonService.getChampion(p.getChampionId())
                .map(DataDragonService.ChampionInfo::imageUrl).orElse(null);

        List<String> itemImageUrls = List.of(objectMapper.readValue(p.getItemsJson(), Integer[].class)).stream()
                .map(id -> id == 0 ? null : dataDragonService.getItem(id).map(DataDragonService.ItemInfo::imageUrl).orElse(null))
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
                itemImageUrls, keystoneIconUrl, secondaryStyleIconUrl);
    }

    private String winRate(Integer wins, Integer losses) {
        if (wins == null || losses == null || wins + losses == 0) {
            return "-";
        }
        return "%.0f%%".formatted(100.0 * wins / (wins + losses));
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
            String spell1ImageUrl, String spell2ImageUrl, List<String> itemImageUrls,
            String keystoneIconUrl, String secondaryStyleIconUrl) {
    }
}
