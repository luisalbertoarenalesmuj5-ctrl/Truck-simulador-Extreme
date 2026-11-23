const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_driver_01";
const BOB_UID = "bob_driver_02";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validDriverProfilePayload(uid, now = new Date(Date.now() - 5000)) {
  return {
    userId: uid,
    driverName: "Carlos Mendoza",
    credits: 25000,
    totalDistanceKm: 0.0,
    deliveriesCompleted: 0,
    xp: 0,
    selectedTruckId: "nordic_fh540",
    unlockedTruckIds: ["nordic_fh540"],
    steeringMode: "WHEEL",
    steeringSensitivity: 1.0,
    cameraMode: "CHASE",
    createdAt: now,
    updatedAt: now,
  };
}

function validDeliveryPayload(uid, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    routeId: "alpine_pass",
    routeName: "Paso Alpino — Maquinaria Pesada",
    cargoName: "Excavadora CAT 18t",
    truckId: "nordic_fh540",
    distanceKm: 45.0,
    earnedCredits: 9350,
    damagePercent: 0,
    durationSeconds: 185,
    perfectDelivery: true,
    createdAt: now,
  };
}

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

test("Unauthenticated user cannot read or write driver profiles or deliveries", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validDriverProfilePayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("deliveries").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("deliveries")
      .doc("del_1")
      .set(validDeliveryPayload(ALICE_UID))
  );
});

test("Owner can create driver profile and execute all three partial update actions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validDriverProfilePayload(ALICE_UID)));
  await assertSucceeds(profileRef.get());

  // Action 1: Update control / camera settings
  await assertSucceeds(
    profileRef.update({
      steeringMode: "TILT",
      steeringSensitivity: 1.35,
      cameraMode: "CAB",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Action 2: Unlock and equip a new truck
  await assertSucceeds(
    profileRef.update({
      credits: 10000,
      selectedTruckId: "valkyrie_s730",
      unlockedTruckIds: ["nordic_fh540", "valkyrie_s730"],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Action 3: Complete a delivery and credit career stats
  await assertSucceeds(
    profileRef.update({
      credits: 19350,
      totalDistanceKm: 45.0,
      deliveriesCompleted: 1,
      xp: 450,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Rejects profile spoofing, ghost fields, immutable createdAt tamper, and invalid bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validDriverProfilePayload(ALICE_UID)));

  // Cross-user read/write blocked
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ credits: 999999 }));

  // Ghost field blocked
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Immutable createdAt mutation blocked
  await assertFails(
    profileRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Invalid truck ID or negative credits blocked
  await assertFails(
    profileRef.update({
      selectedTruckId: "hacked_tank",
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    profileRef.update({
      credits: -50,
      totalDistanceKm: 10.0,
      deliveriesCompleted: 1,
      xp: 100,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Future timestamp blocked
  await assertFails(
    profileRef.update({
      steeringMode: "BUTTONS",
      updatedAt: new Date(Date.now() + 120000),
    })
  );
});

test("Owner can create and list delivery records; updates and cross-user reads fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDeliveries = aliceDb.collection("users").doc(ALICE_UID).collection("deliveries");

  await assertSucceeds(aliceDeliveries.doc("del_001").set(validDeliveryPayload(ALICE_UID)));
  await assertSucceeds(aliceDeliveries.orderBy("createdAt", "desc").get());

  // Immutable audit log: updates rejected
  await assertFails(aliceDeliveries.doc("del_001").update({ earnedCredits: 999999 }));

  // Cross-user list and create rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("deliveries").get());
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("deliveries")
      .doc("del_002")
      .set(validDeliveryPayload(BOB_UID))
  );
});
