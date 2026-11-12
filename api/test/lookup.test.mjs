import assert from "node:assert/strict";
import { once } from "node:events";
import test from "node:test";
import { compareProducts, lookup, normalizeBarcode, NUTRIENTS, sameBarcode } from "../src/lookup.mjs";
import { fatSecret, openFoodFacts, usda } from "../src/providers.mjs";
import { createApi } from "../src/server.mjs";

function product(id, values = {}, unit = "g") {
  return {
    product_name: id,
    product_quantity_unit: unit,
    nutriments: Object.fromEntries(NUTRIENTS.map(key => [key, values[key] ?? null])),
    source: { id, name: id, cacheable: true }
  };
}

function provider(id, result) {
  return { id, supports: () => true, lookup: async () => result };
}

test("canonicalizes UPC and preserves exact product identity", () => {
  assert.equal(normalizeBarcode(" 852501006001 "), "0852501006001");
  assert.equal(normalizeBarcode("00000000"), null);
  assert.equal(normalizeBarcode("1234567890a3"), null);
  assert.equal(sameBarcode("0852501006001", "00852501006001"), true);
  assert.equal(sameBarcode("0852501006001", "1852501006001"), false);
});

test("tries a second source when needed and keeps each nutrition table intact", async () => {
  const first = product("first", { "energy-kcal_100g": 10, fat_100g: 0 });
  const second = product("second", { "energy-kcal_100g": 22, proteins_100g: 4, sugars_100g: 1 });
  const result = await lookup({ barcode: "96385074", region: "TR", compare: false },
    [provider("first", first), provider("second", second)]);

  assert.equal(result.product, second);
  assert.equal(result.product.nutriments.fat_100g, null);
  assert.equal(result.alternatives[0].nutriments.fat_100g, 0);
  assert.deepEqual(result.comparisons, [
    { sources: ["second", "first"], kind: "values_differ", fields: ["energy-kcal_100g"] }
  ]);
});

test("a complete table with real zeros skips additional requests unless comparing", async () => {
  const complete = product("first", Object.fromEntries(NUTRIENTS.map(key => [key, 0])));
  let secondRequests = 0;
  const second = { id: "second", supports: () => true, lookup: async () => { secondRequests++; return null; } };
  await lookup({ barcode: "96385074", region: "TR", compare: false }, [provider("first", complete), second]);
  assert.equal(secondRequests, 0);
  await lookup({ barcode: "96385074", region: "TR", compare: true }, [provider("first", complete), second]);
  assert.equal(secondRequests, 1);
});

test("reports unlike bases and ignores small rounding differences", () => {
  const a = product("a", { "energy-kcal_100g": 100, fat_100g: 1 });
  const b = product("b", { "energy-kcal_100g": 101, fat_100g: 1.1 });
  assert.deepEqual(compareProducts([a, b]), []);
  assert.deepEqual(compareProducts([a, { ...b, product_quantity_unit: "ml" }]), [
    { sources: ["a", "b"], kind: "different_basis", fields: [] }
  ]);
});

test("Open Food Facts retains missing and zero separately and includes provenance", async () => {
  const provider = openFoodFacts(async (url, options) => {
    assert.match(url.pathname, /3017620422003$/);
    assert.equal(options.headers["User-Agent"], "fixture");
    return Response.json({ product: { code: "3017620422003", product_name: "Chocolate spread",
      last_modified_t: 1700000000, product_quantity_unit: "g",
      image_nutrition_url: "https://images.openfoodfacts.org/test.jpg",
      nutriments: { fiber_100g: null, sugars_100g: 0, fat_100g: "" } } });
  }, "fixture");

  const record = await provider.lookup("3017620422003", "TR", new AbortController().signal);
  assert.equal(record.nutriments.sugars_100g, 0);
  assert.equal(record.nutriments.fiber_100g, null);
  assert.equal(record.nutriments.fat_100g, null);
  assert.equal(record.source.updated_at, "2023-11-14T22:13:20.000Z");
  assert.equal(record.source.label_url, "https://images.openfoodfacts.org/test.jpg");
});

test("USDA chooses an exact barcode in the requested market and maps verified units", async () => {
  const provider = usda(async url => {
    if (url.pathname.endsWith("/search")) {
      return Response.json({ foods: [
        { fdcId: 15, marketCountry: "United States", gtinUpc: "0852501006002" },
        { fdcId: 16, marketCountry: "New Zealand", gtinUpc: "0852501006001" },
        { fdcId: 17, marketCountry: "United States", gtinUpc: "852501006001" }
      ] });
    }
    assert.match(url.pathname, /\/17$/);
    return Response.json({ gtinUpc: "0852501006001", marketCountry: "United States",
      servingSizeUnit: "GRM", modifiedDate: "6/23/2023", foodNutrients: [
        { nutrient: { id: 1008, unitName: "kcal" }, amount: 286 },
        { nutrient: { id: 1093, unitName: "mg" }, amount: 1200 }
      ] });
  }, "fixture");

  assert.equal(provider.supports("0852501006001", "TR"), false);
  const record = await provider.lookup("0852501006001", "US", new AbortController().signal);
  assert.equal(record.nutriments.salt_100g, 3);
  assert.equal(record.nutriments["energy-kcal_100g"], 286);
  assert.equal(record.source.updated_at, "2023-06-23T00:00:00.000Z");
});

test("FatSecret converts a metric serving, shares an OAuth refresh and marks data transient", async () => {
  let tokenRequests = 0;
  const provider = fatSecret(async (url, options) => {
    if (String(url).includes("oauth.fatsecret.com")) {
      tokenRequests++;
      assert.match(options.headers.Authorization, /^Basic /);
      return Response.json({ access_token: "fixture", expires_in: 3600 });
    }
    assert.equal(url.searchParams.get("region"), "TR");
    assert.equal(url.searchParams.get("barcode"), "0000096385074");
    assert.equal(options.headers.Authorization, "Bearer fixture");
    return Response.json({ food: { food_name: "Drink", servings: { serving: {
      metric_serving_amount: "250", metric_serving_unit: "ml", calories: "80", sodium: "20"
    } } } });
  }, { id: "fixture", secret: "fixture", regions: new Set(["TR"]) });

  const [record] = await Promise.all([
    provider.lookup("96385074", "TR", new AbortController().signal),
    provider.lookup("96385074", "TR", new AbortController().signal)
  ]);
  assert.equal(tokenRequests, 1);
  assert.equal(record.source.cacheable, false);
  assert.equal(record.product_quantity_unit, "ml");
  assert.equal(record.nutriments["energy-kcal_100g"], 32);
  assert.equal(record.nutriments.salt_100g, 0.02);
});

test("HTTP contract provides fallback, auth, and distinguishes absence from failure", async t => {
  const providers = [provider("first", null), {
    id: "second", supports: () => true, async lookup(barcode) {
      if (barcode === "96385074") return product("second");
      if (barcode === "3017620422003") throw new Error("temporary failure");
      return null;
    }
  }];
  const server = createApi({ providers, token: "test-token", origins: ["https://example.test"] });
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  t.after(() => server.close());
  const base = "http://127.0.0.1:" + server.address().port;
  const headers = { Authorization: "Bearer test-token", Origin: "https://example.test" };

  const unauthorized = await fetch(base + "/v1/products/96385074");
  assert.equal(unauthorized.status, 401);
  const found = await fetch(base + "/v1/products/96385074", { headers });
  assert.equal(found.status, 200);
  assert.equal(found.headers.get("cache-control"), "no-store");
  assert.equal(found.headers.get("access-control-allow-origin"), "https://example.test");
  assert.deepEqual((await found.json()).sources, [
    { id: "first", status: "not_found" }, { id: "second", status: "found" }
  ]);
  const missing = await fetch(base + "/v1/products/00000001", { headers });
  assert.equal(missing.status, 404);
  const unavailable = await fetch(base + "/v1/products/3017620422003", { headers });
  assert.equal(unavailable.status, 503);
  assert.equal((await unavailable.json()).error, "source_unavailable");
  const spec = await fetch(base + "/v1/openapi.json");
  assert.equal((await spec.json()).paths["/v1/products/{barcode}"].get.operationId, "lookupProduct");
});

test("lookup quota returns a retry delay without blocking health checks", async t => {
  let upstreamCalls = 0;
  const server = createApi({
    requestsPerMinute: 1,
    providers: [{ id: "fixture", supports: () => true, async lookup() {
      upstreamCalls++;
      return null;
    } }]
  });
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  t.after(() => server.close());
  const base = "http://127.0.0.1:" + server.address().port;

  assert.equal((await fetch(base + "/v1/products/96385074")).status, 404);
  const limited = await fetch(base + "/v1/products/3017620422003");
  assert.equal(limited.status, 429);
  assert.ok(Number(limited.headers.get("retry-after")) > 0);
  assert.deepEqual(await limited.json(), { error: "rate_limited" });
  assert.equal((await fetch(base + "/health")).status, 200);
  assert.equal(upstreamCalls, 1);
});
