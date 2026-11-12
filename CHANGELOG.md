# Changelog

## 1.3.0

- Refreshed the scanner and product screens with clearer actions, more space and system light and dark themes.
- Updated history, favorites, manual barcode entry, catalog settings and package-label dialogs to Material 3.

## 1.2.1

- Keep barcode-only history entries when a lookup has no result or product details cannot be stored offline. Tap an entry to retry.
- Add a Render Blueprint for HTTPS hosting and an optional API lookup rate limit.

## 1.2.0

- Added a reusable barcode lookup API with OpenAPI and optional USDA FoodData Central and FatSecret providers.
- Added separate source records, update dates, label photo links, and warnings about nutrition discrepancies or incompatible units.
- Added local package-label entry for known and missing products. Personal values stay on the device.
- Added barcode scanning from a selected photo, searchable history and favorites, and product sharing.
- Added in-app catalog server and market settings.

## 1.1.0

- Added English and Turkish with an in-app language selector and system language support.
- Added manual entry, flashlight controls and UPC scanning. The scanner selects a barcode inside the frame and suppresses repeat requests.
- Moved product lookup to Open Food Facts API v3 with distinct messages for connectivity, rate limits, service errors and missing products.
- Preserved missing nutrition values, added portion calculations and linked each result to its source. Product photos and brands are displayed when available.
- Added a 30-product local history and offline access to previously saved products.
- Lowered the minimum Android version to 8.0. Updated CameraX and ML Kit for 16 KB alignment, handled system insets and enabled shrinking for release builds.
- Added unit tests, device UI tests and CI checks for Android builds and native library alignment.
