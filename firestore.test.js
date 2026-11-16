const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

test("Unauthenticated user: cannot read cloudSaves or mpRooms", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("cloudSaves").get()
  );
  await assertFails(
    unauthDb.collection("mpRooms").where("status", "in", ["WAITING", "ACTIVE"]).get()
  );
});

test("Authenticated user: cannot read another user's private or cloudSaves", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("private")
      .doc("account")
      .set({
        ownerId: BOB_UID,
        email: "bob@test.com",
        mobileStorageLimitMb: 8192,
        createdAt: new Date(),
        updatedAt: new Date(),
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("private")
      .doc("account")
      .get()
  );
});

test("Authenticated user: can query open mpRooms with status filter, denied without filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("mpRooms").where("status", "in", ["WAITING", "ACTIVE"]).get()
  );
  await assertFails(aliceDb.collection("mpRooms").get());
});
