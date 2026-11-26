package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.CalendarDate
import com.example.data.DailyHabitRepository
import com.example.data.ItemType
import com.example.data.LocalDailyHabitEntity
import com.example.data.LocalHabitCheckInEntity
import com.example.data.LocalItemEntity
import com.example.data.LocalPlannerDatabase
import com.example.data.LocalPlannerRepository
import com.example.data.LocalStepEntity
import com.example.data.PendingSyncOperationEntity
import com.example.data.SyncAction
import com.example.data.SyncEntityType
import com.example.widgets.WidgetBitmapRenderer
import com.example.widgets.WidgetDataLoader
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Room v9 schema, transactional outbox, revision race protection, plan-delete integrity,
 * sign-out wipe isolation, widget habit merge and widget bitmap budget.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncAndDatabaseTest {

    private lateinit var context: Context
    private lateinit var db: LocalPlannerDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LocalPlannerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun itemEntity(
        id: String,
        type: ItemType,
        parentPlanId: String? = null,
        dueDate: String? = "2026-05-10",
        completed: Boolean = false
    ) = LocalItemEntity(
        id = id,
        type = type.name,
        title = "Item $id",
        notes = null,
        completed = completed,
        startDate = null,
        dueDate = dueDate,
        dueTimeMinutes = null,
        startAtUtcMillis = null,
        endAtUtcMillis = null,
        parentPlanId = parentPlanId,
        createdAtMillis = 1L,
        updatedAtMillis = 1L
    )

    @Test
    fun exportedRoomSchema_v9_existsAndMatchesDatabaseVersion() {
        val candidates = listOf(
            File("schemas/com.example.data.LocalPlannerDatabase/9.json"),
            File("app/schemas/com.example.data.LocalPlannerDatabase/9.json")
        )
        val schema = candidates.firstOrNull { it.exists() }
        assertNotNull("Exported Room v9 schema JSON is missing", schema)
        val json = schema!!.readText()
        assertTrue(json.contains("\"version\": 9"))
        assertTrue(json.contains("deleted_tombstones"))
        assertTrue(json.contains("local_daily_habits"))
        assertTrue(json.contains("local_habit_checkins"))
        assertEquals(8, LocalPlannerDatabase.MIGRATION_8_9.startVersion)
        assertEquals(9, LocalPlannerDatabase.MIGRATION_8_9.endVersion)
        assertEquals(8, LocalPlannerDatabase.allMigrations().size)
    }

    @Test
    fun toggleItemCompleted_writesItemAndOutboxAtomically() = runBlocking {
        val dao = db.plannerDao()
        dao.upsertItem(itemEntity("t1", ItemType.TASK))
        val repo = LocalPlannerRepository(dao, db)

        val result = repo.toggleItemCompleted(LocalPlannerRepository.LOCAL_USER_ID, "t1", true)

        assertTrue(result.isSuccess)
        assertTrue(dao.getItemById("t1")!!.completed)
        val ops = dao.getAllPendingSyncOps()
        assertEquals(1, ops.size)
        assertEquals(PendingSyncOperationEntity.keyFor(SyncEntityType.ITEM, "t1"), ops.single().opKey)
        assertEquals(SyncAction.UPSERT, ops.single().action)
    }

    @Test
    fun deletePendingSyncOpIfUnchanged_preservesNewerLocalEdit() = runBlocking {
        val dao = db.plannerDao()
        val first = PendingSyncOperationEntity.upsert(SyncEntityType.ITEM, "x1")
        dao.upsertPendingSyncOp(first)
        // A newer local edit lands while the older revision is being pushed.
        val second = PendingSyncOperationEntity.upsert(SyncEntityType.ITEM, "x1")
        assertTrue(second.revision > first.revision)
        dao.upsertPendingSyncOp(second)

        dao.deletePendingSyncOpIfUnchanged(first.opKey, first.revision)
        assertEquals("Newer revision must survive the stale ack", 1, dao.getPendingSyncCount())

        dao.deletePendingSyncOpIfUnchanged(second.opKey, second.revision)
        assertEquals(0, dao.getPendingSyncCount())
    }

    @Test
    fun deletingPlan_unlinksTasks_queuesOps_andWritesTombstone() = runBlocking {
        val dao = db.plannerDao()
        dao.upsertItem(itemEntity("plan1", ItemType.PLAN))
        dao.upsertItem(itemEntity("child1", ItemType.TASK, parentPlanId = "plan1"))
        dao.upsertStep(
            LocalStepEntity(
                id = "step1", itemId = "plan1", title = "Milestone", completed = false,
                dueDate = null, dueTimeMinutes = null, sortOrder = 0,
                createdAtMillis = 1L, updatedAtMillis = 1L
            )
        )
        val repo = LocalPlannerRepository(dao, db)

        val result = repo.deleteItemCascade(LocalPlannerRepository.LOCAL_USER_ID, "plan1", emptyList(), emptyList())

        assertTrue(result.isSuccess)
        assertNull(dao.getItemById("plan1"))
        val child = dao.getItemById("child1")
        assertNotNull("Linked task must not be deleted with its plan", child)
        assertNull(child!!.parentPlanId)
        assertTrue(dao.getAllSteps().none { it.itemId == "plan1" })

        val ops = dao.getAllPendingSyncOps().associateBy { it.opKey }
        assertEquals(SyncAction.DELETE, ops[PendingSyncOperationEntity.keyFor(SyncEntityType.ITEM, "plan1")]?.action)
        assertEquals(SyncAction.UPSERT, ops[PendingSyncOperationEntity.keyFor(SyncEntityType.ITEM, "child1")]?.action)
        assertEquals(SyncAction.DELETE, ops[PendingSyncOperationEntity.keyFor(SyncEntityType.STEP, "step1")]?.action)
        assertNotNull(dao.getTombstone(PendingSyncOperationEntity.keyFor(SyncEntityType.ITEM, "plan1")))
    }

    @Test
    fun clearAllLocalPlannerData_emptiesEveryUserTable() = runBlocking {
        val dao = db.plannerDao()
        dao.upsertItem(itemEntity("a", ItemType.TASK))
        dao.upsertDailyHabit(LocalDailyHabitEntity("h1", "Water", "WATER", "BLUE", 0, 1L, 1L))
        dao.upsertHabitCheckIn(LocalHabitCheckInEntity("h1|2026-05-10", "h1", "2026-05-10", 1L))
        dao.upsertPendingSyncOp(PendingSyncOperationEntity.upsert(SyncEntityType.ITEM, "a"))

        dao.clearAllLocalPlannerData()

        assertTrue(dao.getAllItems().isEmpty())
        assertTrue(dao.getAllDailyHabits().isEmpty())
        assertTrue(dao.getAllHabitCheckIns().isEmpty())
        assertEquals(0, dao.getPendingSyncCount())
    }

    @Test
    fun wipeAllUserLocalData_removesDbRowsMediaAndScopedPrefs() = runBlocking {
        val shared = LocalPlannerDatabase.getInstance(context).plannerDao()
        shared.upsertItem(itemEntity("leak", ItemType.TASK))
        val photos = File(context.filesDir, "planner_photos").apply { mkdirs() }
        File(photos, "p.jpg").writeText("x")
        val audio = File(context.filesDir, "task_audio_memos").apply { mkdirs() }
        File(audio, "a.m4a").writeText("x")
        val focusPrefs = context.getSharedPreferences("bunny_memo_focus_session_v1", Context.MODE_PRIVATE)
        focusPrefs.edit().putInt("sessions_all", 9).commit()

        LocalPlannerRepository.wipeAllUserLocalData(context)

        assertTrue(shared.getAllItems().isEmpty())
        assertFalse(photos.exists())
        assertFalse(audio.exists())
        assertFalse(focusPrefs.contains("sessions_all"))
    }

    @Test
    fun habitSnapshot_mergesCheckInsAndIgnoresOrphans() {
        val today = CalendarDate(2026, 5, 10)
        val habits = listOf(
            LocalDailyHabitEntity("h1", "Water", "WATER", "BLUE", 0, 1L, 1L),
            LocalDailyHabitEntity("h2", "Read", "BOOK", "SAGE", 1, 1L, 1L)
        )
        val checkIns = listOf(
            LocalHabitCheckInEntity("h1|2026-05-10", "h1", "2026-05-10", 1L),
            LocalHabitCheckInEntity("h1|2026-05-09", "h1", "2026-05-09", 1L),
            LocalHabitCheckInEntity("gone|2026-05-10", "gone", "2026-05-10", 1L)
        )

        val snap = DailyHabitRepository.buildSnapshot(habits, checkIns, today)

        assertEquals(2, snap.totalHabitsCount)
        assertEquals(1, snap.completedTodayCount)
        assertEquals(2, snap.habits.first { it.id == "h1" }.currentStreakDays)
        assertEquals(1, snap.checkInCountByDate[today])
    }

    @Test
    fun widgetDataLoader_coldStart_includesTodayHabitCheckIns() = runBlocking {
        val dao = LocalPlannerDatabase.getInstance(context).plannerDao()
        dao.clearAllLocalPlannerData()
        val todayIso = CalendarDate.today().toIsoString()
        dao.upsertDailyHabit(LocalDailyHabitEntity("wh1", "Stretch", "STRETCH", "BLUE", 0, 1L, 1L))
        dao.upsertHabitCheckIn(LocalHabitCheckInEntity("wh1|$todayIso", "wh1", todayIso, 1L))

        val snapshot = WidgetDataLoader.load(context)

        assertEquals(1, snapshot.dailyHabitSnapshot.totalHabitsCount)
        assertEquals(1, snapshot.dailyHabitSnapshot.completedTodayCount)
        dao.clearAllLocalPlannerData()
    }

    @Test
    fun widgetBitmapBudget_staysUnder380KbAndPreservesAspect() {
        val (w, h) = WidgetBitmapRenderer.scaleToSafeWidgetBudget(1440, 960)
        assertTrue("Bitmap ${w}x$h exceeds budget", w.toLong() * h * 4 <= 380_000L)
        assertEquals(1440.0 / 960.0, w.toDouble() / h.toDouble(), 0.05)

        val small = WidgetBitmapRenderer.scaleToSafeWidgetBudget(200, 100)
        assertEquals(200 to 100, small)
    }
}
