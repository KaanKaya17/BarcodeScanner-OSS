import { timingSafeEqual } from "node:crypto";
import { readFileSync } from "node:fs";
import { createServer } from "node:http";
import { pathToFileURL } from "node:url";
import { lookup, normalizeBarcode } from "./lookup.mjs";
import { fatSecret, openFoodFacts, usda } from "./providers.mjs";

const spec = readFileSync(new URL("../openapi.json", import.meta.url), "utf8");

export function buildProviders(env = process.env, fetcher = fetch) {
  if (Boolean(env.FATSECRET_CLIENT_ID) !== Boolean(env.FATSECRET_CLIENT_SECRET)) {
    throw new Error("Provide both FatSecret credentials");
  }
  const regions = new Set((env.FATSECRET_REGIONS ?? "TR").split(",").map(value => value.trim().toUpperCase()));
  return [
    openFoodFacts(fetcher, env.OFF_USER_AGENT || "BarcodeScanner API/1.0 (https://github.com/KaanKaya17/BarcodeScanner-OSS)"),
    fatSecret(fetcher, { id: env.FATSECRET_CLIENT_ID, secret: env.FATSECRET_CLIENT_SECRET, regions }),
    usda(fetcher, env.USDA_API_KEY)
  ];
}

function authorized(request, token) {
  if (!token) return true;
  const supplied = Buffer.from(request.headers.authorization ?? "");
  const expected = Buffer.from("Bearer " + token);
  return supplied.length === expected.length && timingSafeEqual(supplied, expected);
}

function respond(response, status, body) {
  response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff"
  });
  response.end(typeof body === "string" ? body : JSON.stringify(body));
}

export function createApi({ providers = buildProviders(), token, origins = [], requestsPerMinute = 0 } = {}) {
  if (!Number.isSafeInteger(requestsPerMinute) || requestsPerMinute < 0) {
    throw new Error("BARCODE_API_REQUESTS_PER_MINUTE must be a nonnegative integer");
  }
  let active = 0;
  const inflight = new Map();
  const burst = Math.min(10, requestsPerMinute);
  let allowance = burst;
  let lastRefill = performance.now();

  return createServer(async (request, response) => {
    const origin = request.headers.origin;
    response.setHeader("Vary", "Origin");
    if (origins.includes(origin)) response.setHeader("Access-Control-Allow-Origin", origin);
    if (request.method === "OPTIONS" && origins.includes(origin)) {
      response.writeHead(204, {
        "Access-Control-Allow-Methods": "GET, OPTIONS",
        "Access-Control-Allow-Headers": "Authorization"
      });
      response.end();
      return;
    }
    if (request.method !== "GET") {
      response.setHeader("Allow", "GET, OPTIONS");
      respond(response, 405, { error: "method_not_allowed" });
      return;
    }

    let url;
    try {
      url = new URL(request.url, "http://localhost");
    } catch {
      respond(response, 400, { error: "invalid_url" });
      return;
    }
    if (url.pathname === "/health") {
      respond(response, 200, { status: "ok" });
      return;
    }
    if (url.pathname === "/v1/openapi.json") {
      respond(response, 200, spec);
      return;
    }
    const route = /^\/v1\/products\/([^/]+)$/.exec(url.pathname);
    if (!route) {
      respond(response, 404, { error: "route_not_found" });
      return;
    }
    if (!authorized(request, token)) {
      response.setHeader("WWW-Authenticate", "Bearer");
      respond(response, 401, { error: "unauthorized" });
      return;
    }
    const barcode = normalizeBarcode(route[1]);
    const region = (url.searchParams.get("region") ?? "TR").toUpperCase();
    const comparison = url.searchParams.get("compare") ?? "false";
    if (!barcode || !/^[A-Z]{2}$/.test(region) || !["true", "false"].includes(comparison)) {
      respond(response, 400, { error: "invalid_lookup" });
      return;
    }

    const key = [barcode, region, comparison].join(":");
    let pending = inflight.get(key);
    if (!pending) {
      if (active >= 16) {
        response.setHeader("Retry-After", "2");
        respond(response, 429, { error: "server_busy" });
        return;
      }
      if (requestsPerMinute > 0) {
        const now = performance.now();
        allowance = Math.min(burst, allowance + (now - lastRefill) * requestsPerMinute / 60_000);
        lastRefill = now;
        if (allowance < 1) {
          const seconds = Math.ceil((1 - allowance) * 60 / requestsPerMinute);
          response.setHeader("Retry-After", String(seconds));
          respond(response, 429, { error: "rate_limited" });
          return;
        }
        allowance--;
      }
      active++;
      pending = lookup({ barcode, region, compare: comparison === "true" }, providers).finally(() => {
        inflight.delete(key);
        active--;
      });
      inflight.set(key, pending);
    }
    try {
      const result = await pending;
      const unavailable = result.sources.some(source => source.status === "unavailable" || source.status === "rate_limited");
      const status = result.product ? 200 : unavailable ? 503 : 404;
      const error = unavailable ? "source_unavailable" : "product_not_found";
      respond(response, status, result.product ? result : { ...result, error });
    } catch {
      respond(response, 500, { error: "internal_error" });
    }
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const host = process.env.HOST || "127.0.0.1";
  const port = Number(process.env.PORT || "8787");
  const server = createApi({
    token: process.env.BARCODE_API_TOKEN,
    requestsPerMinute: Number(process.env.BARCODE_API_REQUESTS_PER_MINUTE ?? "0"),
    origins: (process.env.BARCODE_API_CORS_ORIGINS ?? "").split(",").map(value => value.trim()).filter(Boolean)
  });
  server.listen(port, host, () => console.log("BarcodeScanner API listening on " + host + ":" + port));
}
