package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.victoryscreen.RankingType
import com.unciv.ui.screens.victoryscreen.VictoryScreen
import com.unciv.ui.screens.victoryscreen.VictoryScreenCivGroup

/**
 *  A small always-visible leaderboard on the [WorldScreen], listing the major civilizations by score
 *  so the player can see at a glance who is ahead.
 *
 *  - Respects the game's "Show Civilization Stats" / "Show Rankings" / "Hide other civilization stats" options,
 *    exactly like the Rankings tab of the [VictoryScreen].
 *  - Civilizations the viewer hasn't met yet are shown as [Constants.unknownNationName].
 *  - Clicking the header collapses / expands it (remembered in the settings),
 *    clicking a row opens the full Rankings tab.
 */
internal class LeaderboardTable(private val worldScreen: WorldScreen) : Table(BaseScreen.skin) {
    private companion object {
        /** How many top civilizations are listed - the viewing civ is always added below if it isn't among them */
        const val maxEntries = 5
        const val iconSize = 24f
        const val fontSize = 18
    }

    private val header = Table(skin)
    private val rows = Table(skin)

    /** Identifies the content currently built, so an [update] that changes nothing keeps it */
    private var contentHash = 0

    private class Entry(val civ: Civilization, val rank: Int, val score: Int)

    init {
        background = BaseScreen.skinStrings.getUiBackground("WorldScreen/Leaderboard",
            BaseScreen.skinStrings.roundedEdgeRectangleShape,
            BaseScreen.skinStrings.skinConfig.baseColor.darken(0.5f))
        pad(8f)
        defaults().left()

        header.add(ImageGetter.getImage("OtherIcons/Score").apply { color = Color.FIREBRICK })
            .size(iconSize).padRight(6f)
        header.add("Score".toLabel(fontSize = fontSize))
        // Registered once - see the note in TutorialTaskTable about stacking listeners
        header.onClick {
            GUI.getSettings().leaderboardExpanded = !GUI.getSettings().leaderboardExpanded
            GUI.getSettings().save()
            contentHash = 0
            worldScreen.shouldUpdate = true
        }
        rows.onClick { openRankings() }

        add(header).row()
        add(rows).growX()
    }

    /** Rebuilds the list if the scores changed, then positions it below the tech/policy buttons.
     *  Safe to call as often as [WorldScreen] updates. */
    fun update() {
        val viewingCiv = worldScreen.selectedGameView.civView.getCiv()
        if (!isAllowed(viewingCiv)) {
            isVisible = false
            return
        }
        isVisible = true

        val expanded = GUI.getSettings().leaderboardExpanded
        val entries = if (expanded) getEntries(viewingCiv) else emptyList()
        val newHash = entries.fold(expanded.hashCode()) { hash, entry ->
            31 * hash + (entry.civ.civName.hashCode() xor entry.score xor (if (viewingCiv.knows(entry.civ)) 1 else 0))
        }
        if (newHash != contentHash) {
            contentHash = newHash
            rebuildRows(entries, viewingCiv)
        }

        pack()
        // Below the Tech/Policy/Diplomacy buttons - or below the chat button when that one is shown
        val anchor = if (worldScreen.chatButton.isVisible) worldScreen.chatButton
            else worldScreen.techPolicyAndDiplomacy
        setPosition(10f, anchor.y - height - 10f)
    }

    private fun isAllowed(viewingCiv: Civilization): Boolean {
        val gameInfo = worldScreen.gameInfo
        val parameters = gameInfo.gameParameters
        return viewingCiv.isSpectator
            || gameInfo.victoryData != null
            || (parameters.showCivilizationStats == true && parameters.showRankings)
    }

    private fun getEntries(viewingCiv: Civilization): List<Entry> {
        val gameInfo = worldScreen.gameInfo
        val ranked = gameInfo.civilizations.asSequence()
            .filter { it.isMajorCiv && !it.isDefeated() }
            .filter { VictoryScreen.canViewCivStats(gameInfo, viewingCiv, it) }
            .map { it to it.getStatForRanking(RankingType.Score) }
            .sortedWith(compareByDescending<Pair<Civilization, Int>> { it.second }.thenBy { it.first.civName })
            .mapIndexed { index, (civ, score) -> Entry(civ, index + 1, score) }
            .toList()

        val top = ranked.take(maxEntries)
        if (top.any { it.civ == viewingCiv }) return top
        val own = ranked.firstOrNull { it.civ == viewingCiv } ?: return top
        return top + own
    }

    private fun rebuildRows(entries: List<Entry>, viewingCiv: Civilization) {
        rows.clearChildren()
        if (entries.isEmpty()) return
        rows.padTop(6f)
        rows.defaults().padTop(2f).padBottom(2f)

        var previousRank = 0
        for (entry in entries) {
            // Visual gap when the viewing civ is appended below the top list
            if (entry.rank > previousRank + 1)
                rows.add("…".toLabel(Color.LIGHT_GRAY, fontSize)).colspan(4).center().row()
            previousRank = entry.rank

            val isViewer = entry.civ == viewingCiv
            val isKnown = isViewer || viewingCiv.isSpectator || viewingCiv.knows(entry.civ)
            val textColor = if (isViewer) Color.GOLD else Color.WHITE

            rows.add("${entry.rank}.".toLabel(textColor, fontSize)).right().padRight(6f)
            val (icon, _, _) = VictoryScreenCivGroup.getCivImageAndColors(entry.civ, viewingCiv,
                VictoryScreenCivGroup.DefeatedPlayerStyle.GREYED_OUT)
            rows.add(icon).size(iconSize).padRight(6f)
            val name = if (isKnown) entry.civ.civName else Constants.unknownNationName
            rows.add(name.toLabel(textColor, fontSize, hideIcons = true)).left().padRight(12f)
            rows.add(entry.score.toString().toLabel(textColor, fontSize, Align.right)).right().row()
        }
    }

    private fun openRankings() {
        worldScreen.game.pushScreen { VictoryScreen(worldScreen).apply { selectRankingsPage() } }
    }
}
