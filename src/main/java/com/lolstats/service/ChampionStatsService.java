package com.lolstats.service;

import com.lolstats.domain.MatchParticipant;
import com.lolstats.dto.ChampionStatRow;
import com.lolstats.dto.ChampionStatsResponse;
import com.lolstats.repository.MatchParticipantRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Real-time aggregation over MATCH_PARTICIPANTS - no batch table (PROJECT_PLAN.md §6: even a
// heavily crawled puuid tops out in the low hundreds of rows, live-verified, so a GROUP
// BY-equivalent in-memory pass is effectively instant).
@Service
public class ChampionStatsService {

    private final MatchParticipantRepository matchParticipantRepository;

    public ChampionStatsService(MatchParticipantRepository matchParticipantRepository) {
        this.matchParticipantRepository = matchParticipantRepository;
    }

    public ChampionStatsResponse stats(String puuid) {
        List<MatchParticipant> matches = matchParticipantRepository
                .findByPuuidAndMatch_QueueTypeInOrderByMatch_GameCreationDesc(puuid, MatchService.RIFT_QUEUE_TYPES);

        int games = matches.size();
        long wins = matches.stream().filter(MatchParticipant::getWin).count();
        double overallWinRate = games == 0 ? 0.0 : (double) wins / games;
        List<Boolean> recentForm = matches.stream().map(MatchParticipant::getWin).toList();

        Map<Integer, List<MatchParticipant>> byChampion = matches.stream()
                .collect(Collectors.groupingBy(MatchParticipant::getChampionId, LinkedHashMap::new, Collectors.toList()));

        List<ChampionStatRow> perChampion = byChampion.entrySet().stream()
                .map(e -> toRow(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(ChampionStatRow::games).reversed())
                .toList();

        return new ChampionStatsResponse(games, overallWinRate, recentForm, perChampion);
    }

    private ChampionStatRow toRow(Integer championId, List<MatchParticipant> championMatches) {
        int championGames = championMatches.size();
        long championWins = championMatches.stream().filter(MatchParticipant::getWin).count();
        double winRate = (double) championWins / championGames;

        int sumKills = championMatches.stream().mapToInt(MatchParticipant::getKills).sum();
        int sumDeaths = championMatches.stream().mapToInt(MatchParticipant::getDeaths).sum();
        int sumAssists = championMatches.stream().mapToInt(MatchParticipant::getAssists).sum();
        // Standard aggregate KDA is (ΣK+ΣA)/ΣD, not an average of per-game ratios - a single
        // 0-death outlier game would otherwise blow up a per-game average. ΣD == 0 ("Perfect")
        // falls back to ΣK+ΣA instead of dividing by zero.
        double avgKda = sumDeaths == 0 ? sumKills + sumAssists : (double) (sumKills + sumAssists) / sumDeaths;

        return new ChampionStatRow(championId, championGames, (int) championWins, winRate, avgKda);
    }
}
