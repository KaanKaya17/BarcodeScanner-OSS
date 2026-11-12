export const NUTRIENTS = [
  "energy-kcal_100g", "proteins_100g", "fat_100g", "carbohydrates_100g",
  "sugars_100g", "fiber_100g", "salt_100g"
];

export function normalizeBarcode(raw) {
  if (typeof raw !== "string") return null;
  const code = raw.trim();
  if (!/^(?:[0-9]{8}|[0-9]{12,14})$/.test(code) || /^0+$/.test(code)) return null;
  return code.length === 12 ? "0" + code : code;
}

export function sameBarcode(a, b) {
  const left = normalizeBarcode(a);
  const right = normalizeBarcode(b);
  return left !== null && right !== null && left.padStart(14, "0") === right.padStart(14, "0");
}

export function nonnegative(value) {
  if (typeof value !== "number" && (typeof value !== "string" || value.trim() === "")) return null;
  const number = Number(value);
  return Number.isFinite(number) && number >= 0 ? number : null;
}

function available(product) {
  if (!product?.nutriments || !product.product_quantity_unit) return 0;
  return NUTRIENTS.filter(key => product.nutriments[key] != null).length;
}

export function compareProducts(products) {
  const comparisons = [];
  for (const [index, first] of products.entries()) {
    for (const second of products.slice(index + 1)) {
      const firstUnit = first.product_quantity_unit;
      const secondUnit = second.product_quantity_unit;
      if (!available(first) || !available(second)) continue;
      const differentBasis = firstUnit !== secondUnit;
      const fields = differentBasis ? [] : NUTRIENTS.filter(key => {
        const a = first.nutriments[key];
        const b = second.nutriments[key];
        const tolerance = key === "energy-kcal_100g" ? 1 : 0.1;
        return a != null && b != null && Math.abs(a - b) > tolerance + 1e-9;
      });
      if (differentBasis || fields.length) {
        comparisons.push({
          sources: [first.source.id, second.source.id],
          kind: differentBasis ? "different_basis" : "values_differ",
          fields
        });
      }
    }
  }
  return comparisons;
}

export async function lookup({ barcode, region, compare }, providers) {
  const products = [];
  const sources = [];
  const signal = AbortSignal.timeout(12_000);

  for (const provider of providers) {
    if (!provider.supports(barcode, region)) {
      sources.push({ id: provider.id, status: "not_applicable" });
      continue;
    }
    if (!compare && products.some(product => available(product) === NUTRIENTS.length)) {
      sources.push({ id: provider.id, status: "skipped" });
      continue;
    }
    try {
      const product = await provider.lookup(barcode, region, signal);
      if (product) products.push(product);
      sources.push({ id: provider.id, status: product ? "found" : "not_found" });
    } catch (error) {
      sources.push({ id: provider.id, status: error?.status === 429 ? "rate_limited" : "unavailable" });
    }
  }

  // Choose one complete label at a time. For equal coverage, keep provider order.
  products.sort((a, b) => available(b) - available(a));
  return {
    barcode, region, product: products[0] ?? null, alternatives: products.slice(1),
    comparisons: compareProducts(products), sources
  };
}
