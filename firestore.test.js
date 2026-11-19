const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated user: denied all reads and writes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("rooms").where("isPublic", "==", true).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Rogue",
      coins: 999,
      unlockedSkins: ["CYBER_CYAN"],
      equippedSkin: "CYBER_CYAN",
      equippedModel: "CYBER_DRONE",
      bestPercent: 0,
      totalKills: 0,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("UserProfile: owner create/update succeed; cross-user & ghost-field writes fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      coins: 50,
      unlockedSkins: ["CYBER_CYAN"],
      equippedSkin: "CYBER_CYAN",
      equippedModel: "CYBER_DRONE",
      bestPercent: 12.5,
      totalKills: 3,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Partial update on coins & skin succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      coins: 150,
      unlockedSkins: ["CYBER_CYAN", "WATERMELON"],
      equippedSkin: "WATERMELON",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user read/write fails
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ coins: 0 }));

  // Ghost field & future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("MatchRoom & RoomPlayer: room lifecycle, player sync, and query enforcement", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  // Alice creates room
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_1").set({
      hostId: ALICE_UID,
      roomCode: "PZ42A1",
      status: "playing",
      playerCount: 1,
      memberUids: [ALICE_UID],
      isPublic: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob queries public rooms (aligned with rules)
  await assertSucceeds(
    bobDb.collection("rooms").where("isPublic", "==", true).where("status", "==", "playing").get()
  );

  // Unfiltered rooms query fails
  await assertFails(bobDb.collection("rooms").get());

  // Bob joins room
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_1").update({
      memberUids: [ALICE_UID, BOB_UID],
      playerCount: 2,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob writes his RoomPlayer state
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_1").collection("players").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      slotIndex: 1,
      x: 4.5,
      z: -8.2,
      yaw: 1.2,
      speed: 11.2,
      alive: true,
      kills: 1,
      territoryPct: 8.4,
      skinId: "WATERMELON",
      modelType: "CYBER_DRONE",
      trailFlat: [1.0, 2.0, 3.0, 4.0],
      bombActive: true,
      bombX: 2.0,
      bombZ: 3.0,
      bombTimer: 2.5,
      roomOpen: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob cannot spoof Alice's player document
  await assertFails(
    bobDb.collection("rooms").doc("room_1").collection("players").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Spoof",
      slotIndex: 0,
      x: 0,
      z: 0,
      yaw: 0,
      speed: 11,
      alive: true,
      kills: 0,
      territoryPct: 0,
      skinId: "CYBER_CYAN",
      modelType: "CYBER_DRONE",
      trailFlat: [],
      bombActive: false,
      bombX: 0,
      bombZ: 0,
      bombTimer: 0,
      roomOpen: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Alice lists players in room_1 with aligned filter
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_1").collection("players").where("roomOpen", "==", true).get()
  );
});
