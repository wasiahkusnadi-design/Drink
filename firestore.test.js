const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_seller_1";
const BOB_UID = "bob_buyer_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowPast() {
  return new Date(Date.now() - 2000);
}

function validDrinkPayload(sellerId, status = "ACTIVE") {
  const ts = nowPast();
  return {
    sellerId,
    sellerName: "Kopi Nusantara",
    shopName: "Kedai Teguk",
    name: "Es Kopi Susu Aren",
    description: "Espresso arabika dengan susu oat dan gula aren asli.",
    category: "Kopi",
    imageKey: "coffee",
    price: 24000,
    originalPrice: 28000,
    sizeLabel: "Reguler 350ml",
    calories: 210,
    rating: 4.9,
    soldCount: 0,
    stock: 40,
    status,
    featured: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validCartPayload(drinkId, sellerId) {
  const ts = nowPast();
  return {
    drinkId,
    drinkName: "Es Kopi Susu Aren",
    shopName: "Kedai Teguk",
    sellerId,
    category: "Kopi",
    imageKey: "coffee",
    unitPrice: 24000,
    toppingPrice: 5000,
    quantity: 2,
    sugarLevel: "Less Sugar",
    iceLevel: "Normal",
    topping: "Boba Brown Sugar",
    notes: "Jangan terlalu manis",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validOrderPayload(userId, status = "DIPROSES") {
  const ts = nowPast();
  return {
    userId,
    orderNumber: "SGK-2026-001",
    summaryTitle: "Es Kopi Susu Aren & 1 lainnya",
    totalItems: 2,
    subtotal: 58000,
    deliveryFee: 8000,
    discount: 5000,
    totalAmount: 61000,
    promoCode: "SEGAR5K",
    paymentMethod: "QRIS",
    deliveryAddress: "Jl. Kemang Raya No. 18, Jakarta Selatan",
    status,
    createdAt: ts,
    updatedAt: ts,
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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("drinks").where("status", "==", "ACTIVE").get()
  );
  await assertFails(
    unauthDb.collection("drinks").doc("drink_1").set(validDrinkPayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("User profile: owner can create/read/update; cross-user access denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = nowPast();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      shopName: "Kedai Alice",
      deliveryAddress: "Jl. Sudirman No. 10, Jakarta",
      phone: "08123456789",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Partial update allowed
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      deliveryAddress: "Jl. Thamrin No. 22, Jakarta",
      updatedAt: nowPast(),
    })
  );

  // Cross-user PII read rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      shopName: "Hijacked",
      updatedAt: nowPast(),
    })
  );
});

test("Drinks marketplace: seller creates/updates own drink; buyer lists ACTIVE only", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Alice creates ACTIVE and PAUSED drinks
  await assertSucceeds(
    aliceDb.collection("drinks").doc("drink_active").set(validDrinkPayload(ALICE_UID, "ACTIVE"))
  );
  await assertSucceeds(
    aliceDb.collection("drinks").doc("drink_paused").set(validDrinkPayload(ALICE_UID, "PAUSED"))
  );

  // Reject @DocumentId `id` field injection or ghost field
  await assertFails(
    aliceDb.collection("drinks").doc("drink_bad").set({
      ...validDrinkPayload(ALICE_UID, "ACTIVE"),
      id: "drink_bad",
    })
  );

  // Buyer Bob can query ACTIVE drinks, and Alice can query her own seller drinks
  await assertSucceeds(
    bobDb.collection("drinks").where("status", "==", "ACTIVE").get()
  );
  await assertSucceeds(
    aliceDb.collection("drinks").where("sellerId", "==", ALICE_UID).get()
  );

  // Unfiltered list query must fail
  await assertFails(bobDb.collection("drinks").get());

  // Bob cannot get Alice's PAUSED drink or update/delete Alice's ACTIVE drink
  await assertFails(bobDb.collection("drinks").doc("drink_paused").get());
  await assertFails(
    bobDb.collection("drinks").doc("drink_active").update({
      price: 1000,
      updatedAt: nowPast(),
    })
  );
  await assertFails(bobDb.collection("drinks").doc("drink_active").delete());

  // Seller Alice can pause her own drink
  await assertSucceeds(
    aliceDb.collection("drinks").doc("drink_active").update({
      status: "PAUSED",
      updatedAt: nowPast(),
    })
  );
});

test("Cart & Favorites: strict user subcollection isolation", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("cart").doc("cart_1").set(
      validCartPayload("drink_active", ALICE_UID)
    )
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("cart").doc("cart_1").update({
      quantity: 3,
      updatedAt: nowPast(),
    })
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("cart").get()
  );

  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("favorites").doc("drink_active").set({
      drinkId: "drink_active",
      createdAt: nowPast(),
    })
  );

  // Cross-user cart & favorite access fails
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("cart").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("favorites").get()
  );
});

test("Orders & OrderLineItems: atomic batch creation, valid status transitions, terminal lock", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const orderRef = bobDb.collection("users").doc(BOB_UID).collection("orders").doc("order_1");
  const itemRef = orderRef.collection("items").doc("item_1");

  // Atomic batch write for order + line item
  const batch = bobDb.batch();
  batch.set(orderRef, validOrderPayload(BOB_UID, "DIPROSES"));
  batch.set(itemRef, {
    orderId: "order_1",
    drinkId: "drink_active",
    drinkName: "Es Kopi Susu Aren",
    shopName: "Kedai Teguk",
    imageKey: "coffee",
    unitPrice: 24000,
    toppingPrice: 5000,
    quantity: 2,
    customizationLabel: "Less Sugar, Normal Ice, Boba Brown Sugar",
    createdAt: nowPast(),
  });
  await assertSucceeds(batch.commit());

  // Valid status transitions: DIPROSES -> DIKIRIM -> SELESAI
  await assertSucceeds(
    orderRef.update({ status: "DIKIRIM", updatedAt: nowPast() })
  );
  await assertSucceeds(
    orderRef.update({ status: "SELESAI", updatedAt: nowPast() })
  );

  // Terminal state lock: once SELESAI, cannot transition again
  await assertFails(
    orderRef.update({ status: "DIPROSES", updatedAt: nowPast() })
  );

  // Orphaned item without parent order fails
  const orphanItemRef = bobDb
    .collection("users")
    .doc(BOB_UID)
    .collection("orders")
    .doc("nonexistent_order")
    .collection("items")
    .doc("item_orphan");
  await assertFails(
    orphanItemRef.set({
      orderId: "nonexistent_order",
      drinkId: "drink_active",
      drinkName: "Es Kopi Susu Aren",
      shopName: "Kedai Teguk",
      imageKey: "coffee",
      unitPrice: 24000,
      toppingPrice: 0,
      quantity: 1,
      customizationLabel: "Normal",
      createdAt: nowPast(),
    })
  );
});
