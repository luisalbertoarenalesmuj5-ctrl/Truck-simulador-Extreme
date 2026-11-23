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
const CHARLIE_UID = "charlie_driver_03";

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
    level: 1,
    claimedMilestoneIds: [],
    selectedTruckId: "nordic_fh540",
    unlockedTruckIds: ["nordic_fh540"],
    paintColorHex: "#1E5BB8",
    liveryStyle: "CLASSIC",
    bullbarInstalled: false,
    roofLightbarInstalled: false,
    aeroSpoilerInstalled: false,
    chromeExhaustInstalled: false,
    truckDamagePercent: 0,
    steeringMode: "WHEEL",
    steeringSensitivity: 1.0,
    cameraMode: "EXTERIOR_2D",
    createdAt: now,
    updatedAt: now,
  };
}

function validLegacyDriverProfilePayload(uid, now = new Date(Date.now() - 5000)) {
  return {
    userId: uid,
    driverName: "Conductor Legacy",
    credits: 25000,
    totalDistanceKm: 0.0,
    deliveriesCompleted: 0,
    xp: 0,
    selectedTruckId: "nordic_fh540",
    unlockedTruckIds: ["nordic_fh540"],
    steeringMode: "WHEEL",
    steeringSensitivity: 1.0,
    cameraMode: "SIDE_2D",
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

function validContractPayload(uid, difficulty = "ALTA", status = "AVAILABLE", now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    title: "PetroRuta ADR • Cisterna Jet-A1",
    companyName: "PetroRuta ADR",
    cargoName: "Queroseno de Aviación Jet-A1",
    cargoTons: 24,
    trailerType: "FUEL_TANKER",
    originCity: "Flagstaff",
    destinationCity: "Gallup",
    routeId: "desert_night",
    distanceKm: 78.0,
    difficulty,
    rewardCredits: 22400,
    rewardXp: 640,
    bonusPerfectCredits: 4000,
    requiredLevel: 2,
    status,
    createdAt: now,
    updatedAt: now,
  };
}

function validConvoyRoomPayload(hostUid, roomCode = "TRK901", privateRoom = false, now = new Date(Date.now() - 4000)) {
  return {
    roomCode,
    roomName: "Convoy Ruta Sur 24H",
    hostId: hostUid,
    hostName: "Carlos Mendoza",
    routeId: "free_roam_world",
    privateRoom,
    status: "OPEN",
    memberUids: [hostUid],
    createdAt: now,
    updatedAt: now,
  };
}

function validConvoyPlayerPayload(uid, now = new Date(Date.now() - 1000)) {
  return {
    userId: uid,
    driverName: "Carlos Mendoza",
    truckId: "valkyrie_s730",
    paintColorHex: "#C22429",
    roadS: 120.5,
    laneOffset: 2.15,
    speedKmh: 78,
    headlightsOn: true,
    hornActive: false,
    online: true,
    updatedAt: now,
  };
}

function validConvoyMessagePayload(uid, now = new Date(Date.now() - 500)) {
  return {
    userId: uid,
    senderName: "Carlos Mendoza",
    text: "Cambio 10-4, salimos por el puente norte!",
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
  await assertFails(unauthDb.collection("convoyRooms").get());
  await assertFails(unauthDb.collection("convoyRooms").doc("CONVOY_TRK901").get());
});

test("Owner can create driver profile and execute all five partial update actions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validDriverProfilePayload(ALICE_UID)));
  await assertSucceeds(profileRef.get());

  // Action 1: Update control / 2D camera settings
  await assertSucceeds(
    profileRef.update({
      steeringMode: "TILT",
      steeringSensitivity: 1.35,
      cameraMode: "CAB_2D",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    profileRef.update({
      cameraMode: "FRONT_2D",
      updatedAt: new Date(Date.now() - 900),
    })
  );

  // Action 2: Unlock and equip a new truck from expanded 8-truck fleet
  await assertSucceeds(
    profileRef.update({
      credits: 10000,
      selectedTruckId: "carrier_trans9",
      unlockedTruckIds: ["nordic_fh540", "carrier_trans9"],
      updatedAt: new Date(Date.now() - 700),
    })
  );

  // Action 3: Complete a delivery and credit career stats + level + damage
  await assertSucceeds(
    profileRef.update({
      credits: 19350,
      totalDistanceKm: 45.0,
      deliveriesCompleted: 1,
      xp: 450,
      level: 1,
      truckDamagePercent: 12,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Action 4: Customize paint, livery & accessories in Workshop
  await assertSucceeds(
    profileRef.update({
      credits: 16850,
      paintColorHex: "#E6730F",
      liveryStyle: "RACING",
      bullbarInstalled: true,
      roofLightbarInstalled: true,
      aeroSpoilerInstalled: true,
      chromeExhaustInstalled: true,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Action 5: Repair truck in Workshop
  await assertSucceeds(
    profileRef.update({
      credits: 16250,
      truckDamagePercent: 0,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Action 6: Profile management & career progression (level, xp, credits, milestones)
  await assertSucceeds(
    profileRef.update({
      driverName: "Carlos Ruta Pro",
      xp: 700,
      level: 2,
      credits: 17750,
      claimedMilestoneIds: ["starter_license"],
      steeringMode: "WHEEL",
      steeringSensitivity: 1.15,
      cameraMode: "CAB_2D",
      updatedAt: new Date(Date.now() - 50),
    })
  );
});

test("Legacy driver profile without workshop or level fields can be created and updated smoothly", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validLegacyDriverProfilePayload(ALICE_UID)));
  await assertSucceeds(
    profileRef.update({
      paintColorHex: "#1E5BB8",
      liveryStyle: "BOLT",
      bullbarInstalled: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertSucceeds(
    profileRef.update({
      driverName: "Conductor Actualizado",
      level: 1,
      xp: 250,
      credits: 26500,
      claimedMilestoneIds: ["starter_license"],
      updatedAt: new Date(Date.now() - 50),
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
    profileRef.update({
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

  // Invalid level bounds or XP/level regression blocked
  await assertFails(
    profileRef.update({
      level: 0,
      xp: 100,
      credits: 25000,
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    profileRef.update({
      level: 501,
      xp: 100,
      credits: 25000,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Invalid truck ID, invalid livery, or negative credits blocked
  await assertFails(
    profileRef.update({
      selectedTruckId: "hacked_tank",
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    profileRef.update({
      liveryStyle: "HACKED_WRAP",
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

  // Out-of-bounds damage blocked
  await assertFails(
    profileRef.update({
      credits: 24000,
      truckDamagePercent: 120,
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

test("Convoy rooms: public listing, private invite get, member join/leave, telemetry & CB chat", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const publicRoomRef = aliceDb.collection("convoyRooms").doc("CONVOY_PUB001");
  const privateRoomRef = aliceDb.collection("convoyRooms").doc("CONVOY_PRV777");

  // Create public and private rooms as Alice
  await assertSucceeds(publicRoomRef.set(validConvoyRoomPayload(ALICE_UID, "PUB001", false)));
  await assertSucceeds(privateRoomRef.set(validConvoyRoomPayload(ALICE_UID, "PRV777", true)));

  // Bob can query public rooms only, cannot list all rooms including private ones
  await assertSucceeds(
    bobDb.collection("convoyRooms").where("privateRoom", "==", false).get()
  );
  await assertFails(bobDb.collection("convoyRooms").get());

  // Bob cannot read players or messages in private room before joining
  await assertFails(
    bobDb.collection("convoyRooms").doc("CONVOY_PRV777").collection("players").get()
  );
  await assertFails(
    bobDb.collection("convoyRooms").doc("CONVOY_PRV777").collection("messages").get()
  );

  // Bob can fetch private room by exact deterministic code ID to join, and add himself to memberUids
  await assertSucceeds(bobDb.collection("convoyRooms").doc("CONVOY_PRV777").get());
  await assertSucceeds(
    bobDb.collection("convoyRooms").doc("CONVOY_PRV777").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Now Bob is a member: can publish his own player telemetry and read/write CB chat
  await assertSucceeds(
    bobDb
      .collection("convoyRooms")
      .doc("CONVOY_PRV777")
      .collection("players")
      .doc(BOB_UID)
      .set(validConvoyPlayerPayload(BOB_UID))
  );
  await assertSucceeds(
    bobDb
      .collection("convoyRooms")
      .doc("CONVOY_PRV777")
      .collection("players")
      .doc(BOB_UID)
      .update({
        roadS: 245.2,
        speedKmh: 88,
        hornActive: true,
        updatedAt: new Date(Date.now() - 300),
      })
  );
  await assertSucceeds(
    bobDb
      .collection("convoyRooms")
      .doc("CONVOY_PRV777")
      .collection("messages")
      .doc("msg_001")
      .set(validConvoyMessagePayload(BOB_UID))
  );

  // Bob can read convoy players & messages, but CANNOT spoof Alice's player doc or delete/edit chat
  await assertSucceeds(
    bobDb.collection("convoyRooms").doc("CONVOY_PRV777").collection("players").get()
  );
  await assertSucceeds(
    bobDb.collection("convoyRooms").doc("CONVOY_PRV777").collection("messages").get()
  );
  await assertFails(
    bobDb
      .collection("convoyRooms")
      .doc("CONVOY_PRV777")
      .collection("players")
      .doc(ALICE_UID)
      .set(validConvoyPlayerPayload(ALICE_UID))
  );
  await assertFails(
    bobDb
      .collection("convoyRooms")
      .doc("CONVOY_PRV777")
      .collection("messages")
      .doc("msg_001")
      .update({ text: "editado" })
  );
  await assertFails(
    bobDb
      .collection("convoyRooms")
      .doc("CONVOY_PRV777")
      .collection("messages")
      .doc("msg_001")
      .delete()
  );

  // Bob cannot change host-only room status or routeId, nor delete the room
  await assertFails(
    bobDb.collection("convoyRooms").doc("CONVOY_PRV777").update({
      status: "DRIVING",
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(bobDb.collection("convoyRooms").doc("CONVOY_PRV777").delete());

  // Charlie (non-member) still blocked from reading private room subcollections
  await assertFails(
    charlieDb.collection("convoyRooms").doc("CONVOY_PRV777").collection("players").get()
  );

  // Alice (host) can change room status to DRIVING and update routeId
  await assertSucceeds(
    privateRoomRef.update({
      status: "DRIVING",
      routeId: "pacific_coast",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Delivery contracts: owner can generate, accept, complete, and terminal-lock; cross-user and invalid writes fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const aliceProfile = aliceDb.collection("users").doc(ALICE_UID);
  const aliceContracts = aliceProfile.collection("contracts");
  const contractRef = aliceContracts.doc("contract_01");

  await assertSucceeds(aliceProfile.set(validDriverProfilePayload(ALICE_UID)));
  await assertSucceeds(contractRef.set(validContractPayload(ALICE_UID)));

  // Owner can list contracts ordered by createdAt
  await assertSucceeds(aliceContracts.orderBy("createdAt", "desc").get());

  // Unauthenticated & cross-user access denied
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("contracts").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("contracts").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("contracts").doc("contract_02").set(validContractPayload(BOB_UID))
  );

  // Invalid difficulty or ghost field rejected
  await assertFails(
    aliceContracts.doc("contract_bad").set({
      ...validContractPayload(ALICE_UID),
      difficulty: "IMPOSIBLE",
    })
  );

  // Cannot create directly as COMPLETED
  await assertFails(
    aliceContracts.doc("contract_done").set(validContractPayload(ALICE_UID, "FACIL", "COMPLETED"))
  );

  // Accept contract (AVAILABLE -> ACTIVE) and store activeContractId on profile
  await assertSucceeds(
    contractRef.update({
      status: "ACTIVE",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    aliceProfile.update({
      activeContractId: "contract_01",
      updatedAt: new Date(Date.now() - 900),
    })
  );

  // Cannot tamper rewardCredits during status transition
  await assertFails(
    contractRef.update({
      status: "COMPLETED",
      rewardCredits: 499999,
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Complete contract (ACTIVE -> COMPLETED) and credit profile stats
  await assertSucceeds(
    contractRef.update({
      status: "COMPLETED",
      updatedAt: new Date(Date.now() - 700),
    })
  );
  await assertSucceeds(
    aliceProfile.update({
      credits: 51400,
      totalDistanceKm: 78.0,
      deliveriesCompleted: 1,
      contractsCompleted: 1,
      activeContractId: "",
      xp: 790,
      level: 2,
      truckDamagePercent: 0,
      updatedAt: new Date(Date.now() - 600),
    })
  );

  // Terminal state lock: COMPLETED contract cannot be mutated again
  await assertFails(
    contractRef.update({
      status: "AVAILABLE",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
