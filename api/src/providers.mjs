import { NUTRIENTS, nonnegative, sameBarcode } from "./lookup.mjs";

const OFF_FIELDS = [
  "code", "product_name", "product_name_en", "product_name_tr", "brands", "image_front_small_url",
  "image_nutrition_url", "product_quantity_unit", "last_modified_t", "nutriments"
];
const USDA_MARKETS = { US: "United States", NZ: "New Zealand" };
const USDA_NUTRIENTS = [
  [1008, "kcal", 1], [1003, "g", 1], [1004, "g", 1], [1005, "g", 1],
  [2000, "g", 1], [1079, "g", 1], [1093, "mg", 0.0025]
];
const FATSECRET_NUTRIENTS = ["calories", "protein", "fat", "carbohydrate", "sugar", "fiber", "sodium"];

function text(value) {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function https(value, hosts) {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && hosts.includes(url.hostname) ? url.href : null;
  } catch {
    return null;
  }
}

function unit(value) {
  const normalized = text(value)?.toLowerCase();
  if (["ml", "mlt"].includes(normalized)) return "ml";
  if (["g", "grm"].includes(normalized)) return "g";
  return null;
}

function isoDate(value) {
  const match = typeof value === "string" && /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(value);
  const parsed = typeof value === "number" ? value : Date.parse(match
    ? match[3] + "-" + match[1].padStart(2, "0") + "-" + match[2].padStart(2, "0") : value);
  const date = new Date(parsed);
  return Number.isFinite(date.getTime()) ? date.toISOString() : null;
}

function nutrientValues(read) {
  return Object.fromEntries(NUTRIENTS.map((key, index) => [key, nonnegative(read(key, index))]));
}

async function json(fetcher, url, signal, init = {}, missing = false) {
  const response = await fetcher(url, {
    ...init,
    signal: AbortSignal.any([signal, AbortSignal.timeout(4_000)]),
    redirect: "error"
  });
  if (missing && response.status === 404) return null;
  if (!response.ok) {
    const error = new Error("Provider request failed");
    error.status = response.status;
    throw error;
  }
  return response.json();
}

export function openFoodFacts(fetcher, userAgent) {
  return {
    id: "open_food_facts",
    supports: () => true,
    async lookup(barcode, region, signal) {
      const url = new URL("https://world.openfoodfacts.org/api/v3/product/" + barcode);
      url.searchParams.set("fields", OFF_FIELDS.join(","));
      const result = await json(fetcher, url, signal, { headers: { "User-Agent": userAgent } }, true);
      if (!result || result.result?.id === "product_not_found") return null;
      const item = result.product;
      if (!item || !sameBarcode(String(item.code ?? ""), barcode)) throw new Error("Unexpected product code");

      const imageHosts = ["images.openfoodfacts.org", "static.openfoodfacts.org"];
      const timestamp = nonnegative(item.last_modified_t);
      const quantityUnit = unit(item.product_quantity_unit);
      return {
        product_name: text(item.product_name), product_name_en: text(item.product_name_en),
        product_name_tr: text(item.product_name_tr), brands: text(item.brands),
        image_front_small_url: https(item.image_front_small_url, imageHosts),
        product_quantity_unit: quantityUnit,
        nutriments: nutrientValues(key => item.nutriments?.[key]),
        source: {
          id: "open_food_facts", name: "Open Food Facts",
          url: "https://world.openfoodfacts.org/product/" + item.code,
          label_url: https(item.image_nutrition_url, imageHosts),
          updated_at: timestamp ? isoDate(timestamp * 1000) : null,
          market: null, cacheable: true
        }
      };
    }
  };
}

export function usda(fetcher, apiKey) {
  async function request(path, params, signal) {
    const url = new URL("https://api.nal.usda.gov/fdc/v1/" + path);
    url.search = new URLSearchParams({ api_key: apiKey, ...params });
    return json(fetcher, url, signal);
  }

  return {
    id: "usda_fdc",
    supports: (barcode, region) => Boolean(apiKey && USDA_MARKETS[region]),
    async lookup(barcode, region, signal) {
      const market = USDA_MARKETS[region];
      const codes = [barcode];
      if (barcode.length === 13 && barcode.startsWith("0")) codes.push(barcode.slice(1));
      let match;
      for (const query of codes) {
        const result = await request("foods/search", { query, dataType: "Branded", pageSize: "50" }, signal);
        if (!Array.isArray(result.foods)) throw new Error("Unexpected FoodData Central response");
        match = result.foods.filter(item => item.marketCountry === market &&
            sameBarcode(item.gtinUpc, barcode) && Number.isSafeInteger(item.fdcId))
          .sort((a, b) => String(b.publishedDate ?? "").localeCompare(String(a.publishedDate ?? "")) ||
            b.fdcId - a.fdcId)[0];
        if (match) break;
      }
      if (!match) return null;

      const food = await request("food/" + match.fdcId, {}, signal);
      if (!sameBarcode(food.gtinUpc, barcode) || food.marketCountry !== market) {
        throw new Error("Unexpected FoodData Central detail");
      }
      const basis = unit(food.servingSizeUnit);
      const values = nutrientValues((key, index) => {
        const [id, expectedUnit, factor] = USDA_NUTRIENTS[index];
        const entry = food.foodNutrients?.find(item =>
          item.nutrient?.id === id && item.nutrient.unitName?.toLowerCase() === expectedUnit);
        const amount = nonnegative(entry?.amount);
        return amount == null ? null : amount * factor;
      });

      return {
        product_name: text(food.description), brands: text(food.brandName) ?? text(food.brandOwner),
        product_quantity_unit: basis, nutriments: basis ? values : null,
        source: {
          id: "usda_fdc", name: "USDA FoodData Central", market: region, cacheable: true,
          url: "https://fdc.nal.usda.gov/food-details/" + match.fdcId + "/nutrients",
          updated_at: isoDate(food.modifiedDate) ?? isoDate(food.publicationDate)
        }
      };
    }
  };
}

export function fatSecret(fetcher, { id, secret, regions }) {
  let token;
  let refresh;

  async function getToken(signal) {
    if (token?.expires > Date.now() + 30_000) return token.value;
    if (!refresh) {
      refresh = json(fetcher, "https://oauth.fatsecret.com/connect/token", signal, {
        method: "POST",
        headers: {
          Authorization: "Basic " + Buffer.from(id + ":" + secret).toString("base64"),
          "Content-Type": "application/x-www-form-urlencoded"
        },
        body: new URLSearchParams({ grant_type: "client_credentials", scope: "premier barcode localization" })
      }).then(result => {
        if (!text(result.access_token) || !(nonnegative(result.expires_in) > 0)) {
          throw new Error("Unexpected token response");
        }
        token = { value: result.access_token, expires: Date.now() + Number(result.expires_in) * 1000 };
        return token.value;
      }).finally(() => { refresh = null; });
    }
    return refresh;
  }

  return {
    id: "fatsecret",
    supports: (barcode, region) => Boolean(id && secret && barcode.length <= 13 && regions.has(region)),
    async lookup(barcode, region, signal) {
      const url = new URL("https://platform.fatsecret.com/rest/food/barcode/find-by-id/v3");
      url.search = new URLSearchParams({ barcode: barcode.padStart(13, "0"), region, format: "json" });
      const accessToken = await getToken(signal);
      let result;
      try {
        result = await json(fetcher, url, signal, { headers: { Authorization: "Bearer " + accessToken } });
      } catch (error) {
        if (error.status === 401) token = null;
        throw error;
      }
      const errorCode = Number(result.error?.code);
      if (errorCode === 211) return null;
      if (errorCode === 13) token = null;
      if (result.error || !result.food) throw new Error("Unexpected FatSecret response");

      const food = result.food;
      const servings = food.servings?.serving;
      const metric = (Array.isArray(servings) ? servings : [servings]).filter(serving =>
        unit(serving?.metric_serving_unit) && nonnegative(serving.metric_serving_amount) > 0);
      const standard = metric.find(serving => Number(serving.metric_serving_amount) === 100);
      const chosen = standard ?? (new Set(metric.map(serving => unit(serving.metric_serving_unit))).size === 1
        ? metric[0] : null);
      const basis = unit(chosen?.metric_serving_unit);
      return {
        product_name: text(food.food_name), brands: text(food.brand_name),
        product_quantity_unit: basis,
        nutriments: chosen ? nutrientValues((key, index) => {
          const amount = nonnegative(chosen[FATSECRET_NUTRIENTS[index]]);
          const factor = index === 6 ? 0.0025 : 1;
          return amount == null ? null : amount * factor * 100 / Number(chosen.metric_serving_amount);
        }) : null,
        source: {
          id: "fatsecret", name: "FatSecret", market: region, cacheable: false, updated_at: null,
          url: https(food.food_url, ["foods.fatsecret.com", "www.fatsecret.com", "www.fatsecret.com.tr"])
            ?? "https://platform.fatsecret.com",
          attribution_url: "https://platform.fatsecret.com",
          terms_url: "https://platform.fatsecret.com/terms"
        }
      };
    }
  };
}
