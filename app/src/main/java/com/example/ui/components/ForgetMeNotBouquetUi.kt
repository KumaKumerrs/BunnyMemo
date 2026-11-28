package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.BunnyFeedbackManager
import com.example.data.AccomplishmentReportEngine
import com.example.data.CalendarDate
import com.example.data.PlannerItem
import com.example.data.PlannerStep
import com.example.ui.theme.BunnyFlowerCrown
import com.example.ui.theme.BunnyFurWhite
import com.example.ui.theme.BunnyOutlineInk
import com.example.ui.theme.CalmSage
import com.example.ui.theme.CalmSageContainer
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ForgetMeNotBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.PollenSoft
import com.example.ui.theme.PollenYellow
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class HandedOutBouquet(
    val year: Int,
    val flowerCount: Int,
    val handedOutAtMillis: Long
)

enum class BouquetTier(
    val label: String,
    val minFlowers: Int,
    val nextTarget: Int?
) {
    EMPTY("Empty Ribbon Wrap", 0, 1),
    FIRST_SPRIG("First Forget-Me-Not Sprig", 1, 10),
    MORNING_POSY("Morning Sky Posy", 10, 30),
    MEADOW_BOUQUET("Lush Meadow Bouquet", 30, 75),
    GRAND_BOUQUET("Grand Annual Bouquet", 75, null);

    companion object {
        fun forCount(count: Int): BouquetTier = when {
            count >= 75 -> GRAND_BOUQUET
            count >= 30 -> MEADOW_BOUQUET
            count >= 10 -> MORNING_POSY
            count >= 1 -> FIRST_SPRIG
            else -> EMPTY
        }
    }
}

/**
 * Persists annual bouquet hand-outs so every completed year's bouquet is gifted and archived.
 */
object BouquetArchiveManager {
    private const val PREFS_NAME = "bunny_memo_bouquet_archive_v1"
    private const val KEY_CLAIMED_YEARS = "claimed_years_csv"
    private const val KEY_YEAR_COUNT_PREFIX = "year_count_"
    private const val KEY_YEAR_TIME_PREFIX = "year_time_"

    private val _archive = MutableStateFlow<List<HandedOutBouquet>>(emptyList())
    val archive: StateFlow<List<HandedOutBouquet>> = _archive.asStateFlow()

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            _archive.value = readArchive(context)
            initialized = true
        }
    }

    fun isYearClaimed(year: Int): Boolean = _archive.value.any { it.year == year }

    fun markYearHandedOut(context: Context, year: Int, flowerCount: Int) {
        initialize(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingYears = prefs.getString(KEY_CLAIMED_YEARS, "").orEmpty()
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toMutableSet()
        existingYears.add(year)
        val now = System.currentTimeMillis()
        prefs.edit()
            .putString(KEY_CLAIMED_YEARS, existingYears.sortedDescending().joinToString(","))
            .putInt("$KEY_YEAR_COUNT_PREFIX$year", flowerCount)
            .putLong("$KEY_YEAR_TIME_PREFIX$year", now)
            .apply()
        _archive.value = readArchive(context)
    }

    private fun readArchive(context: Context): List<HandedOutBouquet> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val years = prefs.getString(KEY_CLAIMED_YEARS, "").orEmpty()
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .sortedDescending()
        return years.map { yr ->
            HandedOutBouquet(
                year = yr,
                flowerCount = prefs.getInt("$KEY_YEAR_COUNT_PREFIX$yr", 0),
                handedOutAtMillis = prefs.getLong("$KEY_YEAR_TIME_PREFIX$yr", 0L)
            )
        }
    }
}

/**
 * Computes total forget-me-not flowers earned in [year] from completed tasks, milestones,
 * plans/events, AND daily habit check-ins so EVERY bloomed flower fills the yearly bouquet!
 */
fun computeYearBouquetFlowerCount(
    year: Int,
    items: List<PlannerItem>,
    steps: List<PlannerStep>,
    habitCheckInCountByDate: Map<CalendarDate, Int>,
    nowMillis: Long
): Int {
    val plannerBlooms = AccomplishmentReportEngine.buildYearEndReport(
        year = year,
        allItems = items,
        allSteps = steps,
        nowMillis = nowMillis
    ).totalBlooms
    val habitBlooms = habitCheckInCountByDate.entries
        .filter { it.key.year == year }
        .sumOf { it.value }
    return plannerBlooms + habitBlooms
}

/**
 * Live growing Forget-Me-Not Bouquet card that fills with a new flower for every task completed
 * and hands out the finished bouquet after every year.
 */
@Composable
fun YearBouquetCard(
    year: Int,
    flowerCount: Int,
    today: CalendarDate,
    dedicationText: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val feedback = remember(context) { BunnyFeedbackManager.getInstance(context) }
    LaunchedEffect(context) { BouquetArchiveManager.initialize(context) }
    val archive by BouquetArchiveManager.archive.collectAsStateWithLifecycle()

    val isPastOrYearEnd = year < today.year || (year == today.year && today.month == 12 && today.day >= 25)
    val isClaimed = archive.any { it.year == year }
    val tier = BouquetTier.forCount(flowerCount)
    val daysLeftInYear = remember(year, today) {
        if (year > today.year) 365
        else if (year < today.year) 0
        else today.daysUntil(CalendarDate(year, 12, 31)).coerceAtLeast(0)
    }

    var showCeremony by remember { mutableStateOf(false) }

    ModernPanel(
        shape = MaterialTheme.shapes.large,
        elevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("year_bouquet_card_$year")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            PollenSoft.copy(alpha = 0.25f),
                            PureWhite
                        )
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = SoftIceSurface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, SubtleBorder.copy(alpha = 0.8f)),
                    modifier = Modifier.size(112.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ForgetMeNotBouquetIllustration(
                            flowerCount = flowerCount,
                            isRibbonGifted = isClaimed,
                            modifier = Modifier.size(104.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ModernPillBadge(
                            text = "$year Bouquet",
                            containerColor = SkyBlue.copy(alpha = 0.65f),
                            contentColor = DeepBlue
                        )
                        if (isClaimed) {
                            ModernPillBadge(
                                text = "Gifted",
                                containerColor = CalmSageContainer,
                                contentColor = CalmSage
                            )
                        }
                    }

                    Text(
                        text = if (flowerCount == 1) "1 bloom in your bouquet" else "$flowerCount blooms in your bouquet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MidnightNavy
                    )
                    Text(
                        text = tier.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = DeepBlue
                    )
                    Text(
                        text = when {
                            isClaimed -> "Handed out by your bunny as a $year keepsake!"
                            isPastOrYearEnd && flowerCount > 0 -> "Your $year bouquet is ready to be handed out!"
                            daysLeftInYear > 0 -> "Every finished task adds a flower · Hand-out in $daysLeftInYear ${if (daysLeftInYear == 1) "day" else "days"}"
                            else -> "Complete a task or daily habit to plant your first stem"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedNavy
                    )
                }
            }

            // Progress bar to next bouquet tier
            val nextTarget = tier.nextTarget
            if (nextTarget != null) {
                val span = (nextTarget - tier.minFlowers).coerceAtLeast(1)
                val progress = ((flowerCount - tier.minFlowers).toFloat() / span.toFloat()).coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = ForgetMeNotBlue,
                        trackColor = SubtleBorder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$flowerCount / $nextTarget flowers",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedNavy
                        )
                        Text(
                            text = "Next tier",
                            style = MaterialTheme.typography.labelSmall,
                            color = DeepBlue
                        )
                    }
                }
            }

            // Hand-Out CTA (Automatic ready state at year-end, or preview anytime!)
            if (isPastOrYearEnd && !isClaimed && flowerCount > 0) {
                Button(
                    onClick = {
                        feedback.onCelebrateBloom()
                        showCeremony = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepBlue,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("receive_year_bouquet_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CardGiftcard,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Receive Your $year Bouquet!")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        feedback.onPopOpen()
                        showCeremony = true
                    },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, DeepBlue.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preview_year_bouquet_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CardGiftcard,
                        contentDescription = null,
                        tint = DeepBlue,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isClaimed) "Revisit Your $year Bouquet Keepsake" else "Preview Bouquet Keepsake",
                        color = DeepBlue,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    if (showCeremony) {
        BouquetHandoutCeremonyDialog(
            year = year,
            flowerCount = flowerCount,
            dedicationText = dedicationText,
            isOfficialYearEnd = isPastOrYearEnd,
            alreadyClaimed = isClaimed,
            onClaimBouquet = {
                if (isPastOrYearEnd) {
                    feedback.onCelebrateBloom()
                    BouquetArchiveManager.markYearHandedOut(context, year, flowerCount)
                }
                showCeremony = false
            },
            onDismiss = { showCeremony = false }
        )
    }
}

/**
 * Keepsake shelf of all past annual bouquets handed out by Bunny Memo.
 */
@Composable
fun BouquetKeepsakeShelf(
    dedicationText: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(context) { BouquetArchiveManager.initialize(context) }
    val archive by BouquetArchiveManager.archive.collectAsStateWithLifecycle()
    var inspectedBouquet by remember { mutableStateOf<HandedOutBouquet?>(null) }

    if (archive.isEmpty()) return

    ModernPanel(
        shape = MaterialTheme.shapes.large,
        elevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("bouquet_keepsake_shelf")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CardGiftcard,
                    contentDescription = null,
                    tint = DeepBlue,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = "Handed-Out Yearly Bouquets",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MidnightNavy
                )
            }
            archive.forEach { entry ->
                Surface(
                    onClick = { inspectedBouquet = entry },
                    shape = MaterialTheme.shapes.medium,
                    color = SoftIceSurface,
                    border = BorderStroke(0.75.dp, SubtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ForgetMeNotBouquetIllustration(
                            flowerCount = entry.flowerCount,
                            isRibbonGifted = true,
                            modifier = Modifier.size(54.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${entry.year} Annual Bouquet",
                                style = MaterialTheme.typography.titleSmall,
                                color = MidnightNavy
                            )
                            Text(
                                text = "${entry.flowerCount} forget-me-nots bloomed · ${BouquetTier.forCount(entry.flowerCount).label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedNavy
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Received",
                            tint = CalmSage,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    inspectedBouquet?.let { entry ->
        BouquetHandoutCeremonyDialog(
            year = entry.year,
            flowerCount = entry.flowerCount,
            dedicationText = dedicationText,
            isOfficialYearEnd = true,
            alreadyClaimed = true,
            onClaimBouquet = { inspectedBouquet = null },
            onDismiss = { inspectedBouquet = null }
        )
    }
}

/**
 * Special animated ceremony dialog where the Bunny presents and hands out the completed
 * yearly bouquet wrapped in stationery paper & golden ribbon.
 */
@Composable
fun BouquetHandoutCeremonyDialog(
    year: Int,
    flowerCount: Int,
    dedicationText: String,
    isOfficialYearEnd: Boolean,
    alreadyClaimed: Boolean,
    onClaimBouquet: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tier = BouquetTier.forCount(flowerCount)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE80C1728))
                .padding(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = PureWhite,
                border = BorderStroke(1.5.dp, PollenYellow.copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bouquet_handout_ceremony_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    SkyBlue.copy(alpha = 0.30f),
                                    PollenSoft.copy(alpha = 0.32f),
                                    PureWhite
                                )
                            )
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = PollenSoft,
                        border = BorderStroke(1.dp, PollenYellow)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = DeepBlue,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (isOfficialYearEnd) "$year YEAR-END BOUQUET HAND-OUT" else "$year BOUQUET PREVIEW",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MidnightNavy
                            )
                        }
                    }

                    // Bunny presenting the wrapped Forget-Me-Not Bouquet
                    Box(
                        modifier = Modifier.size(216.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ForgetMeNotBouquetIllustration(
                            flowerCount = flowerCount,
                            isRibbonGifted = isOfficialYearEnd || alreadyClaimed,
                            modifier = Modifier.size(204.dp)
                        )
                        Surface(
                            shape = CircleShape,
                            color = PureWhite,
                            shadowElevation = 3.dp,
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(66.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                SmallBunnyIllustration(
                                    pose = BunnyPose.CELEBRATING,
                                    size = 54.dp,
                                    contentDesc = "Bunny handing out your yearly bouquet"
                                )
                            }
                        }
                    }

                    Text(
                        text = if (isOfficialYearEnd) {
                            "A Bouquet for Your $year!"
                        } else {
                            "Your $year Bouquet ($flowerCount Blooms)"
                        },
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MidnightNavy,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "${tier.label} · Every single forget-me-not represents a task, milestone, or daily habit you saw through.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedNavy,
                        textAlign = TextAlign.Center
                    )

                    TonalInsetPanel(
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "“$dedicationText”",
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                            color = MidnightNavy,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }

                    if (!alreadyClaimed && isOfficialYearEnd) {
                        Button(
                            onClick = onClaimBouquet,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DeepBlue,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("claim_bouquet_confirm_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CardGiftcard,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Accept & Save $year Bouquet")
                        }
                    } else if (!alreadyClaimed && !isOfficialYearEnd) {
                        Text(
                            text = "Your bouquet keeps growing all year — official hand-out unlocks Dec 25.",
                            style = MaterialTheme.typography.labelSmall,
                            color = DeepBlue,
                            textAlign = TextAlign.Center
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val text = "My $year Bunny Memo Forget-Me-Not Bouquet has $flowerCount bloomed flowers (${tier.label})! Every bloom marks a task or daily habit completed."
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "My $year Forget-Me-Not Bouquet")
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(shareIntent, "Share bouquet keepsake"))
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Close", color = MidnightNavy)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Botanical Compose Canvas bouquet that physically fills flower-by-flower for every bloomed task.
 * Uses a golden-angle phyllotaxis spiral so 1 flower looks like a delicate sprig, 12 looks like a
 * posy, and 60+ blooms overflow into a lush forget-me-not bouquet wrapped in linen paper & ribbon.
 */
@Composable
fun ForgetMeNotBouquetIllustration(
    flowerCount: Int,
    isRibbonGifted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    val animatedBloomPopState = animateFloatAsState(
        targetValue = if (flowerCount > 0) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bouquet_pop"
    )
    val swayState = if (reducedMotion) {
        remember { mutableStateOf(0f) }
    } else {
        val infinite = rememberInfiniteTransition(label = "bouquet_breeze")
        infinite.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bouquet_sway"
        )
    }
    val backWrap = remember { Path() }
    val frontWrap = remember { Path() }

    Canvas(modifier = modifier) {
        val s = size.minDimension
        if (s <= 6f) return@Canvas
        val cx = size.width / 2f
        val cy = size.height / 2f
        val swayPx = swayState.value * s * 0.012f
        val animatedBloomPop = animatedBloomPopState.value
        val strokeW = (s * 0.018f).coerceIn(1.3f, 3.2f)
        val outline = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Back stationery wrapping cone (soft linen cream)
        backWrap.reset()
        backWrap.moveTo(cx - s * 0.34f, cy - s * 0.04f)
        backWrap.lineTo(cx, cy - s * 0.24f)
        backWrap.lineTo(cx + s * 0.34f, cy - s * 0.04f)
        backWrap.lineTo(cx + s * 0.10f, cy + s * 0.43f)
        backWrap.lineTo(cx - s * 0.10f, cy + s * 0.43f)
        backWrap.close()
        drawPath(backWrap, color = Color(0xFFF3EFE6))
        drawPath(backWrap, color = BunnyOutlineInk.copy(alpha = 0.32f), style = outline)

        // Greenery leaves behind the blossoms (grow fuller with flowerCount)
        val leafCount = when {
            flowerCount == 0 -> 2
            flowerCount < 6 -> 4
            flowerCount < 20 -> 6
            else -> 9
        }
        val domeCenterY = cy - s * 0.10f
        for (i in 0 until leafCount) {
            val t = if (leafCount == 1) 0f else (i.toFloat() / (leafCount - 1).toFloat()) - 0.5f
            val leafAngle = (-90.0 + t * 125.0) * PI / 180.0
            val dist = s * (0.24f + (i % 2) * 0.05f)
            val lx = cx + swayPx + (dist * cos(leafAngle)).toFloat()
            val ly = domeCenterY + (dist * sin(leafAngle)).toFloat()
            drawOval(
                color = if (i % 2 == 0) CalmSage.copy(alpha = 0.72f) else Color(0xFF83B899),
                topLeft = Offset(lx - s * 0.06f, ly - s * 0.04f),
                size = Size(s * 0.12f, s * 0.08f)
            )
        }

        // Stems converging to ribbon knot
        val visibleFlowers = flowerCount.coerceIn(0, 64)
        val knotY = cy + s * 0.20f
        val stemDrawCount = visibleFlowers.coerceAtMost(14)
        for (i in 0 until stemDrawCount) {
            val spread = if (stemDrawCount <= 1) 0f else ((i.toFloat() / (stemDrawCount - 1)) - 0.5f)
            drawLine(
                color = CalmSage.copy(alpha = 0.85f),
                start = Offset(cx + spread * s * 0.06f, cy + s * 0.38f),
                end = Offset(cx + swayPx + spread * s * 0.36f, domeCenterY - s * 0.06f),
                strokeWidth = strokeW * 1.15f,
                cap = StrokeCap.Round
            )
        }

        // Render every bloomed Forget-Me-Not in a lush golden-angle dome
        if (visibleFlowers == 0) {
            // Single waiting bud so empty bouquet still looks inviting
            drawCircle(
                color = SkyBlue.copy(alpha = 0.65f),
                radius = s * 0.06f,
                center = Offset(cx + swayPx, domeCenterY)
            )
            drawCircle(
                color = BunnyOutlineInk.copy(alpha = 0.35f),
                radius = s * 0.06f,
                center = Offset(cx + swayPx, domeCenterY),
                style = outline
            )
        } else {
            val goldenAngle = 2.3999632 // radians (~137.5 deg)
            val maxRadius = s * (0.14f + (visibleFlowers.coerceAtMost(36) / 36f) * 0.19f)
            val baseFlowerR = when {
                visibleFlowers <= 5 -> s * 0.085f
                visibleFlowers <= 18 -> s * 0.068f
                visibleFlowers <= 36 -> s * 0.056f
                else -> s * 0.047f
            } * animatedBloomPop

            val palette = listOf(
                Color(0xFF7CB6F9),
                Color(0xFF5FA4F6),
                Color(0xFF9AC9FD),
                Color(0xFF6B9FF2)
            )

            for (i in 0 until visibleFlowers) {
                val frac = if (visibleFlowers == 1) 0f else sqrt((i + 0.5f) / visibleFlowers.toFloat())
                val angle = i * goldenAngle
                val fx = cx + swayPx + (frac * maxRadius * 1.12f * cos(angle)).toFloat()
                val fy = domeCenterY + (frac * maxRadius * 0.82f * sin(angle)).toFloat() - s * 0.03f
                val fRadius = baseFlowerR * (0.90f + ((i * 7) % 5) * 0.045f)
                drawBouquetForgetMeNot(
                    cx = fx,
                    cy = fy,
                    radius = fRadius,
                    petalColor = palette[i % palette.size]
                )
            }
        }

        // Front pastel wrapper flaps cradling the stems
        frontWrap.reset()
        frontWrap.moveTo(cx - s * 0.28f, cy + s * 0.04f)
        frontWrap.lineTo(cx, cy + s * 0.16f)
        frontWrap.lineTo(cx + s * 0.28f, cy + s * 0.04f)
        frontWrap.lineTo(cx + s * 0.11f, cy + s * 0.43f)
        frontWrap.lineTo(cx - s * 0.11f, cy + s * 0.43f)
        frontWrap.close()
        drawPath(frontWrap, color = Color(0xFFFCFAF4))
        drawPath(frontWrap, color = BunnyOutlineInk.copy(alpha = 0.30f), style = outline)

        // Ribbon band & bow (glows warm gold when gifted or high tier!)
        val ribbonColor = if (isRibbonGifted || flowerCount >= 30) PollenYellow else ForgetMeNotBlue
        drawLine(
            color = ribbonColor,
            start = Offset(cx - s * 0.14f, knotY),
            end = Offset(cx + s * 0.14f, knotY),
            strokeWidth = s * 0.048f,
            cap = StrokeCap.Round
        )
        // Left & right ribbon bow loops
        drawOval(
            color = ribbonColor,
            topLeft = Offset(cx - s * 0.15f, knotY - s * 0.045f),
            size = Size(s * 0.13f, s * 0.085f)
        )
        drawOval(
            color = ribbonColor,
            topLeft = Offset(cx + s * 0.02f, knotY - s * 0.045f),
            size = Size(s * 0.13f, s * 0.085f)
        )
        // Ribbon tails
        drawLine(
            color = ribbonColor,
            start = Offset(cx - s * 0.02f, knotY + s * 0.01f),
            end = Offset(cx - s * 0.09f, knotY + s * 0.14f),
            strokeWidth = s * 0.030f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = ribbonColor,
            start = Offset(cx + s * 0.02f, knotY + s * 0.01f),
            end = Offset(cx + s * 0.09f, knotY + s * 0.14f),
            strokeWidth = s * 0.030f,
            cap = StrokeCap.Round
        )
        // Center bow knot
        drawCircle(
            color = if (isRibbonGifted) Color(0xFFE7B83C) else DeepBlue,
            radius = s * 0.032f,
            center = Offset(cx, knotY)
        )
    }
}

private fun DrawScope.drawBouquetForgetMeNot(
    cx: Float,
    cy: Float,
    radius: Float,
    petalColor: Color
) {
    val petalDist = radius * 0.48f
    val petalR = radius * 0.40f
    for (i in 0 until 5) {
        val rad = (-90.0 + i * 72.0) * PI / 180.0
        val px = cx + (petalDist * cos(rad)).toFloat()
        val py = cy + (petalDist * sin(rad)).toFloat()
        drawCircle(petalColor, radius = petalR, center = Offset(px, py))
    }
    drawCircle(BunnyFurWhite, radius = radius * 0.30f, center = Offset(cx, cy))
    drawCircle(PollenYellow, radius = radius * 0.21f, center = Offset(cx, cy))
}
