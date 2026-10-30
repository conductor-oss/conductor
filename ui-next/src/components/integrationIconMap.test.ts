import { expect, it } from "vitest";
import { integrationIconMap } from "./integrationIconMap";

it("reuses the existing Firebase brand asset without changing provider identifiers", () => {
  expect(integrationIconMap["firebase-functions"]).toBe("firebase-firestore");
  expect(integrationIconMap["firebase-firestore-v5"]).toBe(
    "firebase-firestore",
  );
});
