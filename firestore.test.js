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

  // Create linked Task with priority tag
  await assertSucceeds(
    itemsRef.doc("task_card").set({
      userId: ALICE_UID,
      type: "TASK",
      title: "Pick up the card",
      completed: false,
      dueDate: "2026-11-26",
      parentPlanId: "plan_scrapbook",
      priority: "HIGH",
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

  // Quick priority tag partial update
  await assertSucceeds(
    itemsRef.doc("task_card").update({
      priority: "LOW",
      updatedAt: pastDate(750),
    })
  );

  // Reject invalid priority enum on update
  await assertFails(
    itemsRef.doc("task_card").update({
      priority: "URGENT",
      updatedAt: pastDate(600),
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

  // Reject invalid priority enum on create
  await assertFails(
    itemsRef.doc("bad_priority").set({
      userId: ALICE_UID,
      type: "TASK",
      title: "Invalid priority",
      completed: false,
      priority: "CRITICAL",
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

// --- 6. Task categories, milestone effort minutes, and shared friend calendar invites ---

test("Items support category labels and steps support timeSpentMinutes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userDoc = aliceDb.collection("users").doc(ALICE_UID);
  const created = pastDate(10000);

  await assertSucceeds(
    userDoc.collection("items").doc("plan_cat").set({
      userId: ALICE_UID,
      type: "PLAN",
      title: "Health reset week",
      completed: false,
      priority: "HIGH",
      category: "HEALTH",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial category update succeeds
  await assertSucceeds(
    userDoc.collection("items").doc("plan_cat").update({
      category: "WORK",
      updatedAt: pastDate(2000),
    })
  );

  // Invalid category enum denied
  await assertFails(
    userDoc.collection("items").doc("plan_cat").update({
      category: "FINANCE",
      updatedAt: pastDate(1500),
    })
  );

  // Step with timeSpentMinutes succeeds
  await assertSucceeds(
    userDoc.collection("steps").doc("step_effort").set({
      userId: ALICE_UID,
      itemId: "plan_cat",
      title: "Outline milestones",
      completed: false,
      sortOrder: 0,
      timeSpentMinutes: 30,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial effort logging succeeds
  await assertSucceeds(
    userDoc.collection("steps").doc("step_effort").update({
      timeSpentMinutes: 60,
      updatedAt: pastDate(1000),
    })
  );

  // Negative effort minutes denied
  await assertFails(
    userDoc.collection("steps").doc("step_effort").update({
      timeSpentMinutes: -10,
      updatedAt: pastDate(500),
    })
  );
});

test("Shared calendar invites and replies enforce creator/verified-invitee isolation and valid RSVP updates", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, {
      email: "alice@example.com",
      email_verified: true,
    })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, {
      email: "bob@example.com",
      email_verified: true,
    })
    .firestore();
  const unverifiedBobDb = testEnv
    .authenticatedContext(BOB_UID, {
      email: "bob@example.com",
      email_verified: false,
    })
    .firestore();
  const carolDb = testEnv
    .authenticatedContext("carol_789", {
      email: "carol@example.com",
      email_verified: true,
    })
    .firestore();

  const created = pastDate(10000);
  const inviteId = "inv_coffee";

  // Owner creates invite for bob@example.com
  await assertSucceeds(
    aliceDb.collection("calendarInvites").doc(inviteId).set({
      ownerUid: ALICE_UID,
      ownerName: "Alice",
      ownerEmail: "alice@example.com",
      inviteeEmail: "bob@example.com",
      plannedDate: "2026-11-28",
      scheduleTimeLabel: "3:00 PM",
      title: "Matcha study session",
      notes: "Corner cafe table",
      itemType: "EVENT",
      category: "STUDY",
      rsvpStatus: "PENDING",
      replyCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Owner can list own invites; verified invitee Bob can list invites addressed to bob@example.com
  await assertSucceeds(
    aliceDb
      .collection("calendarInvites")
      .where("ownerUid", "==", ALICE_UID)
      .get()
  );
  await assertSucceeds(
    bobDb
      .collection("calendarInvites")
      .where("inviteeEmail", "==", "bob@example.com")
      .get()
  );

  // Unverified email Bob and unrelated Carol cannot read or list Alice->Bob invite
  await assertFails(
    unverifiedBobDb.collection("calendarInvites").doc(inviteId).get()
  );
  await assertFails(
    unverifiedBobDb
      .collection("calendarInvites")
      .where("inviteeEmail", "==", "bob@example.com")
      .get()
  );
  await assertFails(carolDb.collection("calendarInvites").doc(inviteId).get());

  // Verified invitee Bob batches a reply + RSVP status update on planned date
  const bobInviteRef = bobDb.collection("calendarInvites").doc(inviteId);
  const bobReplyRef = bobInviteRef.collection("replies").doc("rep_bob_1");
  const bobBatch = bobDb.batch();
  bobBatch.set(bobReplyRef, {
    inviteId,
    ownerUid: ALICE_UID,
    inviteeEmail: "bob@example.com",
    authorUid: BOB_UID,
    authorName: "Bob",
    authorEmail: "bob@example.com",
    plannedDate: "2026-11-28",
    rsvpStatus: "GOING",
    message: "See you at 3 PM!",
    createdAt: pastDate(2000),
    updatedAt: pastDate(2000),
  });
  bobBatch.update(bobInviteRef, {
    inviteeUid: BOB_UID,
    rsvpStatus: "GOING",
    replyCount: 1,
    lastReplyText: "See you at 3 PM!",
    lastReplyAuthorName: "Bob",
    lastReplyAtMillis: Date.now() - 2000,
    updatedAt: pastDate(2000),
  });
  await assertSucceeds(bobBatch.commit());

  // Both Alice and Bob can list replies on the planned date; Carol cannot
  await assertSucceeds(
    aliceDb.collection("calendarInvites").doc(inviteId).collection("replies").get()
  );
  await assertSucceeds(
    bobDb.collection("calendarInvites").doc(inviteId).collection("replies").get()
  );
  await assertFails(
    carolDb.collection("calendarInvites").doc(inviteId).collection("replies").get()
  );

  // Invitee cannot tamper with immutable inviteeEmail or ownerUid
  await assertFails(
    bobInviteRef.update({
      ownerUid: BOB_UID,
      updatedAt: pastDate(500),
    })
  );

  // Owner can reply back without changing invitee RSVP
  const aliceInviteRef = aliceDb.collection("calendarInvites").doc(inviteId);
  const aliceReplyRef = aliceInviteRef.collection("replies").doc("rep_alice_2");
  const aliceBatch = aliceDb.batch();
  aliceBatch.set(aliceReplyRef, {
    inviteId,
    ownerUid: ALICE_UID,
    inviteeEmail: "bob@example.com",
    authorUid: ALICE_UID,
    authorName: "Alice",
    authorEmail: "alice@example.com",
    plannedDate: "2026-11-28",
    rsvpStatus: "GOING",
    message: "Yay, bringing stickers too!",
    createdAt: pastDate(1000),
    updatedAt: pastDate(1000),
  });
  aliceBatch.update(aliceInviteRef, {
    replyCount: 2,
    lastReplyText: "Yay, bringing stickers too!",
    lastReplyAuthorName: "Alice",
    lastReplyAtMillis: Date.now() - 1000,
    updatedAt: pastDate(1000),
  });
  await assertSucceeds(aliceBatch.commit());

  // Invitee cannot delete invite; owner can batch-delete replies + invite
  await assertFails(bobInviteRef.delete());
  const delBatch = aliceDb.batch();
  delBatch.delete(aliceReplyRef);
  delBatch.delete(aliceInviteRef.collection("replies").doc("rep_bob_1"));
  delBatch.delete(aliceInviteRef);
  await assertSucceeds(delBatch.commit());
});

