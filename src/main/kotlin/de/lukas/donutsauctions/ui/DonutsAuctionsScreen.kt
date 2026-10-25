package de.lukas.donutsauctions.ui

import de.lukas.donutsauctions.DonutsAuctionsClient
import de.lukas.donutsauctions.config.DonutsAuctionsConfigScreenFactory
import de.lukas.donutsauctions.model.DealCandidate
import de.lukas.donutsauctions.model.ItemPercentileStats
import de.lukas.donutsauctions.model.ItemPriceStats
import de.lukas.donutsauctions.model.ItemSearchHit
import de.lukas.donutsauctions.model.ItemTrendSeries
import de.lukas.donutsauctions.model.ListingPreview
import de.lukas.donutsauctions.model.RepositoryStatus
import de.lukas.donutsauctions.model.SellerStatsRow
import de.lukas.donutsauctions.model.TrendSeriesPoint
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.KeyEvent
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class DonutsAuctionsScreen : Screen(Component.literal("DonutsAuctions")) {

    private enum class ViewMode { SEARCH, DEALS }
    private enum class SortMode { BEST_DEAL, MIN_PRICE, SAMPLES, RECENT, NAME }
    private enum class FilterMode { ALL, WATCHLIST }
    private enum class DetailTab { OVERVIEW, TRENDS, SELLERS }
    private enum class TrendWindow(val label: String, val hours: Int, val bucketMinutes: Int) {
        H25("25h", 25, 60),
        D7("7d", 7 * 24, 360),
        D30("30d", 30 * 24, 1440)
    }
    private enum class EmptyState { NONE, NO_DATA, NO_MATCHES, BACKEND_UNAVAILABLE }

    private data class ResultRow(
        val itemId: String,
        val itemSignature: String,
        val displayName: String,
        val minUnitPrice: Double,
        val sampleCount: Int,
        val lastSeenAt: Long,
        val dealScore: Double?
    )

    private data class LayoutMetrics(
        val topControlsBottomY: Int,
        val statusBannerTopY: Int,
        val statusBannerBottomY: Int,
        val statusTextY: Int,
        val contentTopY: Int,
        val footerReservedHeight: Int,
        val devToolsReservedHeight: Int,
        val contentBottomY: Int,
        val showRepairLine: Boolean
    )

    private lateinit var searchField: EditBox
    private lateinit var searchButton: Button
    private lateinit var modeButton: Button
    private lateinit var refreshButton: Button
    private lateinit var configButton: Button

    private lateinit var sortButton: Button
    private lateinit var filterButton: Button
    private lateinit var lookbackButton: Button
    private lateinit var stackButton: Button

    private lateinit var tabOverviewButton: Button
    private lateinit var tabTrendsButton: Button
    private lateinit var tabSellersButton: Button
    private lateinit var watchButton: Button
    private lateinit var trend25hButton: Button
    private lateinit var trend7dButton: Button
    private lateinit var trend30dButton: Button

    private lateinit var devToggleButton: Button
    private lateinit var exportCsvButton: Button
    private lateinit var exportJsonButton: Button
    private lateinit var rebuildButton: Button
    private lateinit var vacuumButton: Button

    private val stackOptions = listOf(1, 16, 64)
    private var stackIndex = 0
    private var lookbackDays = 30

    private var viewMode = ViewMode.SEARCH
    private var sortMode = SortMode.MIN_PRICE
    private var filterMode = FilterMode.ALL
    private var detailTab = DetailTab.OVERVIEW
    private var devModeEnabled = false

    private var rows: List<ResultRow> = emptyList()
    private var selectedRow: ResultRow? = null
    private var selectedStats: ItemPriceStats? = null
    private var selectedListings: List<ListingPreview> = emptyList()
    private var selectedTrendSeries: ItemTrendSeries? = null
    private var selectedPercentiles: ItemPercentileStats? = null
    private var selectedSellers: List<SellerStatsRow> = emptyList()
    private var watchlistKeys: Set<String> = emptySet()
    private var trendWindow: TrendWindow = TrendWindow.H25

    private var statusLine = "Ready"
    private var emptyState = EmptyState.NONE
    private var resultScroll = 0

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private val rowHeight: Int
        get() = DonutsAuctionsScreenLayoutMath.computeRowHeight(font.lineHeight)

    override fun init() {
        val compact = isCompactLayout()
        val margin = 20
        val gap = 6
        val rowAY = 34
        val rowBY = 60

        val searchWidth = if (compact) 180 else 280
        searchField = EditBox(font, margin, rowAY, searchWidth, 20, Component.literal("Search"))
        searchField.setMaxLength(120)

        var x = margin + searchWidth + gap
        searchButton = Button.builder(Component.literal("Search")) { performSearch() }
            .bounds(x, rowAY, if (compact) 78 else 88, 20)
            .build()
        x += if (compact) 84 else 94

        modeButton = Button.builder(Component.literal("")) { cycleViewMode() }
            .bounds(x, rowAY, if (compact) 154 else 172, 20)
            .build()
        x += if (compact) 160 else 178

        refreshButton = Button.builder(Component.literal("Refresh")) { refreshData() }
            .bounds(x, rowAY, if (compact) 92 else 100, 20)
            .build()

        configButton = Button.builder(Component.literal(if (compact) "Config" else "Mod Config")) {
            minecraft?.gui?.setScreen(DonutsAuctionsConfigScreenFactory.create(this))
        }.bounds(width - if (compact) 96 else 118, rowAY, if (compact) 76 else 98, 20).build()

        sortButton = Button.builder(Component.literal("")) { cycleSortMode() }
            .bounds(margin, rowBY, if (compact) 128 else 138, 20)
            .build()
        filterButton = Button.builder(Component.literal("")) { cycleFilterMode() }
            .bounds(margin + if (compact) 134 else 144, rowBY, if (compact) 126 else 134, 20)
            .build()
        lookbackButton = Button.builder(Component.literal("")) { toggleLookback() }
            .bounds(margin + if (compact) 266 else 284, rowBY, if (compact) 128 else 138, 20)
            .build()
        stackButton = Button.builder(Component.literal("")) { cycleStackSize() }
            .bounds(margin + if (compact) 400 else 428, rowBY, if (compact) 120 else 128, 20)
            .build()

        val details = detailsPanelBounds()
        tabOverviewButton = Button.builder(Component.literal("Overview")) { switchTab(DetailTab.OVERVIEW) }
            .bounds(details.left + 8, details.top + 4, 74, 16)
            .build()
        tabTrendsButton = Button.builder(Component.literal("Trends")) { switchTab(DetailTab.TRENDS) }
            .bounds(details.left + 86, details.top + 4, 62, 16)
            .build()
        tabSellersButton = Button.builder(Component.literal("Sellers")) { switchTab(DetailTab.SELLERS) }
            .bounds(details.left + 152, details.top + 4, 62, 16)
            .build()
        watchButton = Button.builder(Component.literal("☆ Watch")) { toggleWatchSelected() }
            .bounds(details.right - 94, details.top + 4, 86, 16)
            .build()
        trend25hButton = Button.builder(Component.literal("25h")) { switchTrendWindow(TrendWindow.H25) }
            .bounds(details.left + 220, details.top + 4, 38, 16)
            .build()
        trend7dButton = Button.builder(Component.literal("7d")) { switchTrendWindow(TrendWindow.D7) }
            .bounds(details.left + 262, details.top + 4, 34, 16)
            .build()
        trend30dButton = Button.builder(Component.literal("30d")) { switchTrendWindow(TrendWindow.D30) }
            .bounds(details.left + 300, details.top + 4, 42, 16)
            .build()

        devToggleButton = Button.builder(Component.literal("Dev")) { toggleDevMode() }
            .bounds(width - 58, height - 26, 42, 16)
            .build()

        exportCsvButton = Button.builder(Component.literal("CSV")) { exportCsv() }
            .bounds(width - 262, height - 26, 46, 16)
            .build()
        exportJsonButton = Button.builder(Component.literal("JSON")) { exportJson() }
            .bounds(width - 212, height - 26, 52, 16)
            .build()
        rebuildButton = Button.builder(Component.literal("Reindex")) { rebuildIndexes() }
            .bounds(width - 156, height - 26, 64, 16)
            .build()
        vacuumButton = Button.builder(Component.literal("Vacuum")) { vacuumDb() }
            .bounds(width - 88, height - 26, 64, 16)
            .build()

        addRenderableWidget(searchField)
        addRenderableWidget(searchButton)
        addRenderableWidget(modeButton)
        addRenderableWidget(refreshButton)
        addRenderableWidget(configButton)

        addRenderableWidget(sortButton)
        addRenderableWidget(filterButton)
        addRenderableWidget(lookbackButton)
        addRenderableWidget(stackButton)

        addRenderableWidget(tabOverviewButton)
        addRenderableWidget(tabTrendsButton)
        addRenderableWidget(tabSellersButton)
        addRenderableWidget(watchButton)
        addRenderableWidget(trend25hButton)
        addRenderableWidget(trend7dButton)
        addRenderableWidget(trend30dButton)

        addRenderableWidget(devToggleButton)
        addRenderableWidget(exportCsvButton)
        addRenderableWidget(exportJsonButton)
        addRenderableWidget(rebuildButton)
        addRenderableWidget(vacuumButton)

        updateFromConfig()
        updateButtonLabels()
        updateControlState()
        setInitialFocus(searchField)
        performSearch()
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val layout = computeLayoutMetrics()

        context.fill(0, 0, width, height, 0x88000000.toInt())
        context.text(font, title, 20, 14, color(0xFFFFFF), true)
        drawStatusBanner(context, layout)
        context.text(font, Component.literal(statusLine), 20, layout.statusTextY, color(0xD0D0D0), false)

        drawResultsPanel(context, mouseX, mouseY, layout)
        drawDetailPanel(context, layout)
        drawFooter(context, layout)
        super.extractRenderState(context, mouseX, mouseY, delta)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val keyCode = input.key()
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            performSearch()
            return true
        }
        return super.keyPressed(input)
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (super.mouseClicked(click, doubled)) {
            return true
        }

        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false
        }

        val panel = resultsPanelBounds()
        val rowsTop = panel.top + 20
        val visibleRows = ((panel.height - 24) / rowHeight).coerceAtLeast(1)

        if (click.x() >= panel.left && click.x() <= panel.right && click.y() >= rowsTop && click.y() <= panel.bottom) {
            val row = ((click.y() - rowsTop) / rowHeight).toInt()
            val absoluteIndex = resultScroll + row
            if (absoluteIndex in rows.indices && row < visibleRows) {
                selectRow(absoluteIndex)
                return true
            }
        }

        return false
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val panel = resultsPanelBounds()
        if (mouseX < panel.left || mouseX > panel.right || mouseY < panel.top || mouseY > panel.bottom) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }

        val visibleRows = ((panel.height - 24) / rowHeight).coerceAtLeast(1)
        val maxScroll = (rows.size - visibleRows).coerceAtLeast(0)
        if (maxScroll == 0) {
            return true
        }

        val direction = if (verticalAmount > 0) -1 else if (verticalAmount < 0) 1 else 0
        if (direction != 0) {
            resultScroll = (resultScroll + direction).coerceIn(0, maxScroll)
        }

        return true
    }

    override fun isPauseScreen(): Boolean = false

    private fun performSearch() {
        val health = DonutsAuctionsClient.repositoryHealthSnapshot()
        watchlistKeys = DonutsAuctionsClient.listWatchlist().map { keyOf(it.itemId, it.itemSignature) }.toSet()

        rows = when (viewMode) {
            ViewMode.SEARCH -> buildSearchRows()
            ViewMode.DEALS -> buildDealRows()
        }

        rows = applySort(rows)
        if (filterMode == FilterMode.WATCHLIST) {
            rows = rows.filter { isWatched(it.itemId, it.itemSignature) }
        }

        resultScroll = 0
        when {
            health.status == RepositoryStatus.ERROR -> {
                emptyState = EmptyState.BACKEND_UNAVAILABLE
                selectedRow = null
                clearDetailData()
                statusLine = health.message
                updateControlState()
                return
            }
            rows.isEmpty() -> {
                emptyState = if (searchField.value.trim().isBlank() && viewMode == ViewMode.SEARCH) {
                    EmptyState.NO_DATA
                } else {
                    EmptyState.NO_MATCHES
                }
                selectedRow = null
                clearDetailData()
                statusLine = when (emptyState) {
                    EmptyState.NO_DATA -> "No data in DB for current mode/window"
                    EmptyState.NO_MATCHES -> "No matches for '${searchField.value.trim()}'"
                    else -> "No data"
                }
                updateControlState()
                return
            }
            else -> {
                emptyState = EmptyState.NONE
            }
        }

        selectRow(0)
        statusLine = when (viewMode) {
            ViewMode.SEARCH -> "Found ${rows.size} local item variants"
            ViewMode.DEALS -> "Found ${rows.size} deal candidates"
        }
        updateControlState()
    }

    private fun refreshData() {
        val selectedKey = selectedRow?.let { keyOf(it.itemId, it.itemSignature) }
        performSearch()
        if (selectedKey != null) {
            val idx = rows.indexOfFirst { keyOf(it.itemId, it.itemSignature) == selectedKey }
            if (idx >= 0) {
                selectRow(idx)
            }
        }
    }

    private fun buildSearchRows(): List<ResultRow> {
        val query = searchField.value.trim()
        val lookback = if (lookbackDays == 0) 3650 else lookbackDays
        return DonutsAuctionsClient.searchItems(query, lookback, 250)
            .map { it.toRow() }
    }

    private fun buildDealRows(): List<ResultRow> {
        val config = DonutsAuctionsClient.configManager.config
        val deals = DonutsAuctionsClient.findBestDeals(
            lookbackDays = if (lookbackDays == 0) 30 else lookbackDays,
            minDiscountPct = config.minDiscountPct,
            minSamples = config.minSamples,
            watchlistOnly = filterMode == FilterMode.WATCHLIST,
            limit = 250
        )

        val query = searchField.value.trim().lowercase()
        return deals
            .filter { deal ->
                query.isBlank() || deal.displayName.lowercase().contains(query) || shortItemId(deal.itemId).contains(query)
            }
            .map { it.toRow() }
    }

    private fun applySort(input: List<ResultRow>): List<ResultRow> {
        return when (sortMode) {
            SortMode.BEST_DEAL -> input.sortedWith(
                compareByDescending<ResultRow> { it.dealScore ?: Double.NEGATIVE_INFINITY }
                    .thenBy { it.minUnitPrice }
            )
            SortMode.MIN_PRICE -> input.sortedBy { it.minUnitPrice }
            SortMode.SAMPLES -> input.sortedByDescending { it.sampleCount }
            SortMode.RECENT -> input.sortedByDescending { it.lastSeenAt }
            SortMode.NAME -> input.sortedBy { it.displayName.lowercase() }
        }
    }

    private fun selectRow(index: Int) {
        if (index !in rows.indices) return
        selectedRow = rows[index]

        val selected = selectedRow ?: return
        val lookback = if (lookbackDays == 0) 3650 else lookbackDays
        val trendLookback = if (lookbackDays == 0) 30 else lookbackDays

        selectedStats = DonutsAuctionsClient.getItemStats(selected.itemId, selected.itemSignature, lookback, currentStackSize())
        selectedListings = DonutsAuctionsClient.getLowestListings(selected.itemId, selected.itemSignature, lookback, 15)
        selectedTrendSeries = DonutsAuctionsClient.getTrendSeries(
            selected.itemId,
            selected.itemSignature,
            trendWindow.hours,
            trendWindow.bucketMinutes
        )
        selectedPercentiles = DonutsAuctionsClient.getPercentileStats(selected.itemId, selected.itemSignature, trendLookback)
        selectedSellers = DonutsAuctionsClient.getSellerStats(selected.itemId, selected.itemSignature, trendLookback, 30)

        updateControlState()
    }

    private fun clearDetailData() {
        selectedStats = null
        selectedListings = emptyList()
        selectedTrendSeries = null
        selectedPercentiles = null
        selectedSellers = emptyList()
    }

    private fun cycleViewMode() {
        viewMode = if (viewMode == ViewMode.SEARCH) ViewMode.DEALS else ViewMode.SEARCH
        performSearch()
        updateButtonLabels()
    }

    private fun cycleSortMode() {
        val values = SortMode.entries
        sortMode = values[(values.indexOf(sortMode) + 1) % values.size]
        rows = applySort(rows)
        updateButtonLabels()
    }

    private fun cycleFilterMode() {
        filterMode = if (filterMode == FilterMode.ALL) FilterMode.WATCHLIST else FilterMode.ALL
        performSearch()
        updateButtonLabels()
    }

    private fun toggleLookback() {
        lookbackDays = if (lookbackDays == 30) 0 else 30
        performSearch()
        updateButtonLabels()
    }

    private fun cycleStackSize() {
        stackIndex = (stackIndex + 1) % stackOptions.size
        val selected = selectedRow
        if (selected != null) {
            val lookback = if (lookbackDays == 0) 3650 else lookbackDays
            selectedStats = DonutsAuctionsClient.getItemStats(selected.itemId, selected.itemSignature, lookback, currentStackSize())
        }
        updateButtonLabels()
    }

    private fun switchTab(tab: DetailTab) {
        detailTab = tab
        updateButtonLabels()
        updateControlState()
    }

    private fun switchTrendWindow(window: TrendWindow) {
        trendWindow = window
        val selected = selectedRow
        if (selected != null) {
            selectedTrendSeries = DonutsAuctionsClient.getTrendSeries(
                selected.itemId,
                selected.itemSignature,
                trendWindow.hours,
                trendWindow.bucketMinutes
            )
        }
        updateButtonLabels()
        updateControlState()
    }

    private fun toggleWatchSelected() {
        val selected = selectedRow ?: return
        if (!DonutsAuctionsClient.isRepositoryWritable()) {
            statusLine = "Watchlist is disabled while DB is read-only"
            updateControlState()
            return
        }
        val watched = isWatched(selected.itemId, selected.itemSignature)
        DonutsAuctionsClient.setWatchlist(selected.itemId, selected.itemSignature, selected.displayName, !watched)
        watchlistKeys = DonutsAuctionsClient.listWatchlist().map { keyOf(it.itemId, it.itemSignature) }.toSet()
        if (filterMode == FilterMode.WATCHLIST) {
            performSearch()
        } else {
            updateControlState()
        }
    }

    private fun toggleDevMode() {
        devModeEnabled = !devModeEnabled
        DonutsAuctionsClient.setDevModeEnabled(devModeEnabled)
        updateButtonLabels()
        updateControlState()
    }

    private fun exportCsv() {
        val selected = selectedRow ?: run {
            statusLine = "Select an item first"
            return
        }
        val lookback = if (lookbackDays == 0) 3650 else lookbackDays
        val path = DonutsAuctionsClient.exportItemHistoryCsv(selected.itemId, selected.itemSignature, lookback)
        statusLine = if (path != null) {
            "CSV exported: ${compactPath(path)}"
        } else {
            "CSV export skipped: no rows"
        }
    }

    private fun exportJson() {
        val selected = selectedRow ?: run {
            statusLine = "Select an item first"
            return
        }
        val lookback = if (lookbackDays == 0) 3650 else lookbackDays
        val path = DonutsAuctionsClient.exportItemSummaryJson(selected.itemId, selected.itemSignature, lookback)
        statusLine = if (path != null) {
            "JSON exported: ${compactPath(path)}"
        } else {
            "JSON export skipped: no rows"
        }
    }

    private fun rebuildIndexes() {
        statusLine = DonutsAuctionsClient.rebuildIndexes()
    }

    private fun vacuumDb() {
        statusLine = DonutsAuctionsClient.vacuumDatabase()
    }

    private fun updateButtonLabels() {
        modeButton.message = Component.literal("Mode: ${if (viewMode == ViewMode.SEARCH) "Search" else "Deals"} (*)")
        sortButton.message = Component.literal("Sort: ${sortModeShort(sortMode)} (*)")
        filterButton.message = Component.literal("Filter: ${if (filterMode == FilterMode.ALL) "All" else "Watch"} (*)")
        lookbackButton.message = Component.literal("Window: ${if (lookbackDays == 0) "All" else "30d"} (*)")
        stackButton.message = Component.literal("Stack: x${currentStackSize()} (*)")

        tabOverviewButton.message = Component.literal(if (detailTab == DetailTab.OVERVIEW) "[Overview]" else "Overview")
        tabTrendsButton.message = Component.literal(if (detailTab == DetailTab.TRENDS) "[Trends]" else "Trends")
        tabSellersButton.message = Component.literal(if (detailTab == DetailTab.SELLERS) "[Sellers]" else "Sellers")
        trend25hButton.message = Component.literal(if (trendWindow == TrendWindow.H25) "[25h]" else "25h")
        trend7dButton.message = Component.literal(if (trendWindow == TrendWindow.D7) "[7d]" else "7d")
        trend30dButton.message = Component.literal(if (trendWindow == TrendWindow.D30) "[30d]" else "30d")

        devToggleButton.message = Component.literal("Dev")
    }

    private fun updateControlState() {
        val writable = DonutsAuctionsClient.isRepositoryWritable()
        val hasSelection = selectedRow != null

        sortButton.visible = true
        filterButton.visible = true
        lookbackButton.visible = true
        stackButton.visible = hasSelection

        val watchVisible = detailTab != DetailTab.TRENDS
        watchButton.visible = watchVisible
        watchButton.active = watchVisible && hasSelection && writable
        watchButton.message = when {
            !hasSelection -> Component.literal("☆ Watch")
            !writable -> Component.literal("☆ Watch")
            isWatched(selectedRow!!.itemId, selectedRow!!.itemSignature) -> Component.literal("★ Watched")
            else -> Component.literal("☆ Watch")
        }

        val trendButtonsVisible = detailTab == DetailTab.TRENDS
        trend25hButton.visible = trendButtonsVisible
        trend7dButton.visible = trendButtonsVisible
        trend30dButton.visible = trendButtonsVisible
        trend25hButton.active = trendButtonsVisible
        trend7dButton.active = trendButtonsVisible
        trend30dButton.active = trendButtonsVisible

        // Dev mode is controlled via Mod Config only.
        devToggleButton.visible = false
        devToggleButton.active = false

        val devVisible = devModeEnabled
        exportCsvButton.visible = devVisible
        exportJsonButton.visible = devVisible
        rebuildButton.visible = devVisible
        vacuumButton.visible = devVisible

        exportCsvButton.active = devVisible && hasSelection
        exportJsonButton.active = devVisible && hasSelection
        rebuildButton.active = devVisible && writable
        vacuumButton.active = devVisible && writable
    }

    private fun updateFromConfig() {
        val config = DonutsAuctionsClient.configManager.config
        devModeEnabled = config.devModeEnabled
    }

    private fun drawStatusBanner(context: GuiGraphicsExtractor, layout: LayoutMetrics) {
        val health = DonutsAuctionsClient.repositoryHealthSnapshot()

        val (fillColor, title, textColor, message) = when (health.status) {
            RepositoryStatus.OK -> Quad(0x66314D31, "DB OK", 0xB7FFB7, "Writable. Capture enabled.")
            RepositoryStatus.READ_ONLY -> Quad(0x66533E1D, "DB Read-only", 0xFFD37F, health.message.ifBlank { "Write operations disabled." })
            RepositoryStatus.ERROR -> Quad(0x664D1E1E, "DB Error", 0xFF9E9E, health.message.ifBlank { "Backend unavailable." })
        }

        context.fill(20, layout.statusBannerTopY, width - 20, layout.statusBannerBottomY, fillColor)
        context.fill(20, layout.statusBannerTopY, width - 20, layout.statusBannerTopY + 1, 0x88FFFFFF.toInt())

        context.text(font, Component.literal(title), 26, layout.statusBannerTopY + 6, color(textColor), false)
        drawClampedText(
            context,
            message,
            106,
            layout.statusBannerTopY + 6,
            (width - 20) - 106 - 8,
            color(0xD8D8D8)
        )
    }

    private fun drawResultsPanel(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, layout: LayoutMetrics) {
        val panel = resultsPanelBounds(layout)
        drawPanel(context, panel.left, panel.top, panel.right, panel.bottom)
        context.text(font, Component.literal("Results"), panel.left + 6, panel.top + 6, color(0xFFFFFF), false)

        if (panel.height < rowHeight + 28) {
            context.text(font, Component.literal("Window too small for results"), panel.left + 6, panel.top + 24, color(0xB8B8B8), false)
            return
        }

        if (emptyState != EmptyState.NONE) {
            val text = when (emptyState) {
                EmptyState.NO_DATA -> "No data in local DB yet"
                EmptyState.NO_MATCHES -> "No matches for this query"
                EmptyState.BACKEND_UNAVAILABLE -> "Backend unavailable (see DB status above)"
                EmptyState.NONE -> ""
            }
            context.text(font, Component.literal(text), panel.left + 6, panel.top + 26, color(0xB8B8B8), false)
            return
        }

        val rowsTop = panel.top + 20
        val visibleRows = ((panel.height - 24) / rowHeight).coerceAtLeast(1)
        val endExclusive = (resultScroll + visibleRows).coerceAtMost(rows.size)

        withScissor(context, panel.left + 2, rowsTop, panel.right - 2, panel.bottom - 14) {
            for ((row, idx) in (resultScroll until endExclusive).withIndex()) {
                val y = rowsTop + (row * rowHeight)
                val item = rows[idx]
                val selected = selectedRow?.let { keyOf(it.itemId, it.itemSignature) == keyOf(item.itemId, item.itemSignature) } ?: false

                val bg = when {
                    selected -> 0x66486EA8
                    mouseX in panel.left..panel.right && mouseY in y..(y + rowHeight - 2) -> 0x332A2A2A
                    else -> 0x22141414
                }
                context.fill(panel.left + 2, y, panel.right - 2, y + rowHeight - 2, bg)

                val iconY = y + ((rowHeight - 16) / 2).coerceAtLeast(0)
                : Boolean {
        return contentWidth >= 430
    }
}
