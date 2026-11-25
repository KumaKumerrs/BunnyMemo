const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_bb_123";
const BOB_UID = "bob_maker_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

// --- 1. Unauthenticated access denied across all collections ---

test("Unauthenticated user cannot read or write profiles, items, steps, or reminders", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = pastDate();

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      dedicationText: "For you, bb.",
      hasSeenDedication: false,
      defaultReminderHour: 9,
      defaultReminderMinute: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("items").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("steps").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("reminders").get()
  );
});

// --- 2. Owner profile & editable dedication ---

test("Owner can create and update dedication profile; cross-user and ghost fields denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      dedicationText:
        "For you, bb. I made this little planner so you have a place for all the things you look forward to. Love you.",
      hasSeenDedication: false,
      defaultReminderHour: 9,
      defaultReminderMinute: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update marking dedication seen and editing text
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      dedicationText: "For you, bb. Always.",
      hasSeenDedication: true,
      updatedAt: pastDate(1000),
    })
  );

  // Maker/other user has zero read or write access
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      hasSeenDedication: false,
      updatedAt: pastDate(500),
    })
  );

  // Shadow update with ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Tampering with immutable createdAt rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(200),
      updatedAt: pastDate(200),
    })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      hasSeenDedication: true,
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

// --- 3. Planner items (Plan, Task, Event) ---

test("Owner can create, list, edit, toggle, and delete Plans, Tasks, and Events", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const itemsRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("items");
  const created = pastDate(10000);

  // Empty subcollection list succeeds
  await assertSucceeds(itemsRef.get());

  // Create Plan (mirrors Kotlin map without @DocumentId 'id')
  await assertSucceeds(
    itemsRef.doc("plan_scrapbook").set({
      userId: ALICE_UID,
      type: "PLAN",
      title: "Make a weekend scrapbook",
      notes: "Collect autumn photos",
      completed: false,
      startDate: "2026-11-25",
      dueDate: "2026-11-29",
      dueTimeMinutes: 1020,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create linked Task
  await assertSucceeds(
    itemsRef.doc("task_card").set({
      userId: ALICE_UID,
      type: "TASK",
      title: "Pick up the card",
      completed: false,
      dueDate: "2026-11-26",
      parentPlanId: "plan_scrapbook",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create timed Event (UTC epoch millis)
  await assertSucceeds(
    itemsRef.doc("event_dinner").set({
      userId: ALICE_UID,
      type: "EVENT",
      title: "Birthday dinner",
      completed: false,
      startAtUtcMillis: Date.now() + 86400000,
      endAtUtcMillis: Date.now() + 93600000,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Toggle completion partial update
  await assertSucceeds(
    itemsRef.doc("task_card").update({
      completed: true,
      updatedAt: pastDate(1000),
    })
  );

  // Reschedule item partial update
  await assertSucceeds(
    itemsRef.doc("plan_scrapbook").update({
      dueDate: "2026-12-01",
      updatedAt: pastDate(500),
    })
  );

  // Reject '@DocumentId' id field inside payload
  await assertFails(
    itemsRef.doc("bad_item").set({
      id: "bad_item",
      userId: ALICE_UID,
      type: "TASK",
      title: "Has forbidden id field",
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject invalid enum type
  await assertFails(
    itemsRef.doc("bad_type").set({
      userId: ALICE_UID,
      type: "NOTE",
      title: "Invalid type",
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject cross-user read, list, and write
  const bobItemsRef = bobDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("items");
  await assertFails(bobItemsRef.get());
  await assertFails(bobItemsRef.doc("plan_scrapbook").get());
  await assertFails(bobItemsRef.doc("plan_scrapbook").delete());

  // Owner can delete
  await assertSucceeds(itemsRef.doc("task_card").delete());
});

// --- 4. Milestones / Steps & Reminders with parent existence guard ---

test("Steps and reminders require existing parent item, support atomic batch create, and enforce ownership", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const userDoc = aliceDb.collection("users").doc(ALICE_UID);
  const created = pastDate(10000);

  // Orphaned step without parent item is rejected
  await assertFails(
    userDoc.collection("steps").doc("step_orphan").set({
      userId: ALICE_UID,
      itemId: "nonexistent_plan",
      title: "Orphan step",
      completed: false,
      sortOrder: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Atomic batch create: plan + step + progress check-in reminder succeeds via existsAfter
  const batch = aliceDb.batch();
  batch.set(userDoc.collection("items").doc("plan_1"), {
    userId: ALICE_UID,
    type: "PLAN",
    title: "Make a weekend scrapbook",
    completed: false,
    startDate: "2026-11-25",
    dueDate: "2026-11-29",
    createdAt: created,
    updatedAt: created,
  });
  batch.set(userDoc.collection("steps").doc("step_1"), {
    userId: ALICE_UID,
    itemId: "plan_1",
    title: "Choose photos",
    completed: false,
    dueDate: "2026-11-26",
    sortOrder: 0,
    createdAt: created,
    updatedAt: created,
  });
  batch.set(userDoc.collection("reminders").doc("rem_1"), {
    userId: ALICE_UID,
    itemId: "plan_1",
    kind: "PROGRESS_CHECKIN",
    offsetMinutes: 1440,
    localHour: 17,
    localMinute: 0,
    scheduledAtUtcMillis: Date.now() + 86400000,
    occurrenceKey: "plan_1_PROGRESS_CHECKIN_1440",
    dismissed: false,
    createdAt: created,
    updatedAt: created,
  });
  await assertSucceeds(batch.commit());

  // Toggle step completion
  await assertSucceeds(
    userDoc.collection("steps").doc("step_1").update({
      completed: true,
      updatedAt: pastDate(1000),
    })
  );

  // Snooze single reminder occurrence
  await assertSucceeds(
    userDoc.collection("reminders").doc("rem_1").update({
      snoozedUntilUtcMillis: Date.now() + 3600000,
      dismissed: false,
      updatedAt: pastDate(1000),
    })
  );

  // Cannot reassign step itemId
  await assertFails(
    userDoc.collection("steps").doc("step_1").update({
      itemId: "other_plan",
      updatedAt: pastDate(500),
    })
  );

  // Cross-user access to steps & reminders denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("steps").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("reminders").get()
  );
});

// --- 5. Class Schedules ---

test("Owner can create, list, update, and delete semester class schedules; invalid payloads and cross-user access denied", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const schedulesRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("classSchedules");
  const created = pastDate(10000);

  // Unauthenticated denied
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("classSchedules").get()
  );

  // Empty list succeeds for owner
  await assertSucceeds(schedulesRef.get());

  // Valid weekly class schedule succeeds
  await assertSucceeds(
    schedulesRef.doc("cls_math").set({
      userId: ALICE_UID,
      subject: "Calculus II",
      room: "Hall B · Room 204",
      repeatMode: "WEEKLY",
      daysOfWeek: [1, 3, 5],
      dayOfMonth: 25,
      startTimeMinutes: 540,
      endTimeMinutes: 630,
      semesterStartDate: "2026-11-25",
      semesterMonths: 4,
      notifyEnabled: true,
      notifyOffsetMinutes: 15,
      colorKey: "SKY",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid monthly day-of-month class schedule succeeds (omitting optional room)
  await assertSucceeds(
    schedulesRef.doc("cls_lab").set({
      userId: ALICE_UID,
      subject: "Biology Seminar",
      repeatMode: "MONTHLY_DAY",
      daysOfWeek: [],
      dayOfMonth: 15,
      startTimeMinutes: 840,
      endTimeMinutes: 960,
      semesterStartDate: "2026-11-15",
      semesterMonths: 5,
      notifyEnabled: true,
      notifyOffsetMinutes: 60,
      colorKey: "SAGE",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update succeeds
  await assertSucceeds(
    schedulesRef.doc("cls_math").update({
      semesterMonths: 5,
      notifyOffsetMinutes: 30,
      updatedAt: pastDate(1000),
    })
  );

  // Out-of-bounds semesterMonths rejected
  await assertFails(
    schedulesRef.doc("cls_bad_months").set({
      userId: ALICE_UID,
      subject: "Too Long",
      repeatMode: "WEEKLY",
      daysOfWeek: [1],
      dayOfMonth: 1,
      startTimeMinutes: 540,
      endTimeMinutes: 600,
      semesterStartDate: "2026-11-25",
      semesterMonths: 24,
      notifyEnabled: false,
      notifyOffsetMinutes: 15,
      colorKey: "SKY",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Invalid repeatMode rejected
  await assertFails(
    schedulesRef.doc("cls_bad_mode").set({
      userId: ALICE_UID,
      subject: "Bad Mode",
      repeatMode: "DAILY",
      daysOfWeek: [1],
      dayOfMonth: 1,
      startTimeMinutes: 540,
      endTimeMinutes: 600,
      semesterStartDate: "2026-11-25",
      semesterMonths: 4,
      notifyEnabled: false,
      notifyOffsetMinutes: 15,
      colorKey: "SKY",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cross-user read/delete denied
  const bobRef = bobDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("classSchedules");
  await assertFails(bobRef.get());
  await assertFails(bobRef.doc("cls_math").get());
  await assertFails(bobRef.doc("cls_math").delete());

  // Owner delete succeeds
  await assertSucceeds(schedulesRef.doc("cls_lab").delete());
});
