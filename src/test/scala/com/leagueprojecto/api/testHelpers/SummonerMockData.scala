package com.leagueprojecto.api.testHelpers

import com.analyzedgg.api.domain.Summoner
import com.analyzedgg.api.domain.riot.RiotSummoner

/**
  * Created by RemcoW on 5-1-2017.
  */
object SummonerMockData {
  val testSummoner = Summoner(123123123, "Wagglez", 100, 1434315156000L, 30)
  val testRiotSummoner = RiotSummoner(testSummoner)
}
