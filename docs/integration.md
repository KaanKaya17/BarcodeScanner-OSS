# Barkod API entegrasyonu

[English quick start](#english-quick-start) · [OpenAPI sözleşmesi](../api/openapi.json)

BarcodeScanner API, aynı HTTP arayüzüyle Android, iOS, web veya sunucu uygulamalarında kullanılabilir. Barkodu metin olarak gönder. Bu sayede baştaki sıfırlar korunur.

Canlı katalog şu anda Open Food Facts üzerinden sorgu yapıyor. API anahtarı olmadan bir barkod sorgusu deneyebilirsin. [Örnek sorgu](https://barcodescanner-catalog.onrender.com/v1/products/3017620422003?region=TR&compare=true)

## Yerel çalıştırma

Node.js 22 veya üzeri gerekir. Ek npm paketi kurulumu gerekmez.

```sh
npm --prefix api start
```

```sh
curl "http://127.0.0.1:8787/v1/products/3017620422003?region=TR&compare=true"
```

`GET /health` servisin ayakta olup olmadığını, `GET /v1/openapi.json` ise OpenAPI sözleşmesini verir. Ürün uç noktası EAN-8, UPC-A, EAN-13 ve GTIN-14 kabul eder. UPC-E kodlarını önce UPC-A'ya genişlet. Varsayılan pazar Türkiye (`TR`).

- `product` önerilen kaydı, `alternatives` aynı barkod için diğer kayıtları, `sources` ise sağlayıcıların sorgu sonucunu taşır.
- İlk kayıt eksikse servis sıradaki uygun kaynağı sorgular. `compare=true` tüm uygun kaynakları sorgular. Servis, ölçü birimi belirli tabloların dolu alanlarını sayıp en dolu tabloyu seçer. Eşitlikte sağlayıcı sırası korunur.
- Besin değerleri her kaydın kendi 100 g veya 100 mL tablosuna aittir. Değerler birbiriyle birleştirilmez. `comparisons`, ortak alanlarda 1 kcal veya 0,1 g üzerindeki farkları ve g/mL baz uyuşmazlıklarını bildirir. Bu eşikler yuvarlama farklarını ayıklamak için kullanılır.
- `null` eksik değeri, `0` bildirilen sıfırı ifade eder. Baz `null` ise g veya mL teyit edilmemiştir. Bu durumda porsiyon hesabı yapma.
- `404` sorgulanan kaynaklarda ürünün bulunmadığını, `503` bir kaynak hatası yüzünden sorgunun tamamlanamadığını belirtir. `200` yanıtı da kısmi sonuç içerebilir. `sources` durumlarını ve her kaydın kaynak bağlantısını göster.

## Sağlayıcılar

- **Open Food Facts** varsayılan olarak çalışır. İsteğe bağlı `OFF_USER_AGENT` ortam değişkenine uygulama adı, sürümü ve iletişim adresi koy. Yeniden kullanımda Open Food Facts atfını ve veri lisansını koru. [Open Food Facts API](https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/)
- **USDA FoodData Central** için `USDA_API_KEY` ayarla. Bağlayıcı yalnız `US` ve `NZ` pazarlarında, barkod ve pazar eşleştiğinde markalı ürün kaydı döndürür. Yerel denemede sınırlı `DEMO_KEY` kullanılabilir. [USDA API kılavuzu](https://fdc.nal.usda.gov/api-guide/)
- **FatSecret** için uygulamana tanımlı Premier barkod ve ilgili pazar erişimi gerekir. `FATSECRET_CLIENT_ID` ve `FATSECRET_CLIENT_SECRET` sunucuda ayarlanır. `FATSECRET_REGIONS` virgülle ayrılmış pazar kodları alır ve varsayılanı `TR`'dir. Kayıtları yalnız yetkilendirilmiş uygulamalara göster ve sağlayıcının koşullarını, atfını ve saklama sınırlarını uygula. API FatSecret kayıtlarına `cacheable: false` ekler. Android bu kayıtları çevrimdışı geçmişe yazmaz. [FatSecret API koşulları](https://platform.fatsecret.com/terms) · [Atıf kılavuzu](https://platform.fatsecret.com/attribution)

## Android'i bağlama

Uygulamada **Katalog sunucusu** menüsünü aç, HTTPS API adresini ve pazar kodunu gir. Adres boşken Android doğrudan Open Food Facts'i kullanır. Uygulamayı dağıtırken aynı adresi ve pazarı derleme varsayılanı olarak da verebilirsin.

```sh
./gradlew :app:assembleRelease -PbarcodeApiBaseUrl=https://api.example.com/ -PbarcodeApiRegion=TR
```

Android emülatöründe yerel API'ye bağlanmak için debug ayarını kullan. Windows PowerShell örneği:

```powershell
.\gradlew.bat :app:assembleDebug "-PbarcodeApiDebugBaseUrl=http://10.0.2.2:8787/" "-PbarcodeApiRegion=TR"
```

Android bağlandığında `compare=true` gönderir. Farklı kaynakları tek tek seçebilir ve varsa ambalaj etiketi fotoğrafını açabilirsin. Kendi girdiğin ambalaj değerleri cihazda kalır ve bu barkodu sonraki açışında öncelikli görünür. Etiket ekranından düzenlenebilir veya silinebilir.

## HTTPS üzerinden yayımlama

Render üzerinde **New → Blueprint** ile bu depoyu seç. [`render.yaml`](../render.yaml) dosyası API testlerini çalıştırır ve `/health` üzerinden servisi doğrular. Dağıtım tamamlandığında HTTPS adresini Android’de **Katalog sunucusu** alanına gir. Ücretsiz planda servis boşta kaldıktan sonra ilk istek Android’in bekleme süresini aşabilir. [Render Blueprint kılavuzu](https://render.com/docs/infrastructure-as-code) · [Ücretsiz plan sınırları](https://render.com/docs/free)

- Ek sağlayıcı anahtarlarını Render’daki **Environment** ekranında tanımla. Türkiye karşılaştırması için FatSecret hesabında `TR` pazarına barkod erişimi ve bu dağıtımı kapsayan izin gerekir. USDA anahtarı yalnız `US` ve `NZ` sorgularında kullanılır.
- Blueprint, bir API örneğinde dakikada ortalama 60 yeni barkod sorgusuna izin verir. `BARCODE_API_REQUESTS_PER_MINUTE` ile değiştirilebilir. Kota dolarsa API, `429` ve `Retry-After` döndürür.
- Canlı doğrulamada `/health` adresini ve `/v1/products/3017620422003?region=TR&compare=true` yanıtındaki `sources` alanını kontrol et. Her sağlayıcıyı desteklediği pazardan, bilinen bir barkodla dene.

## Başka uygulamalardan çağırma

```js
const url = new URL("/v1/products/3017620422003", "https://api.example.com");
url.searchParams.set("region", "TR");
const response = await fetch(url);
const result = await response.json();
if (response.ok) {
  console.log(result.product.source.name, result.product.nutriments);
}
```

Sunucu varsayılan olarak `127.0.0.1:8787` üzerinde dinler. `HOST` ve `PORT` dağıtım ortamında ayarlanabilir. Sunucular arası çağrıları korumak için `BARCODE_API_TOKEN` ayarla ve `Authorization: Bearer ...` gönder. Sağlayıcı anahtarlarını ve sunucu tokenını dağıtılan uygulamalara gömme. Android için dağıtım adresi, mobil istemcilerin erişebileceği HTTPS ağ geçidi olmalıdır. Tarayıcıdan doğrudan erişim için `BARCODE_API_CORS_ORIGINS` ile izin verilen origin adreslerini açıkça belirt. Herkese açık erişimde ağ geçidinde istek kotası uygula.

## English quick start

Run `npm --prefix api start` with Node.js 22+. Call `GET /v1/products/{barcode}?region=TR&compare=true` and import [OpenAPI](../api/openapi.json) into your client tooling. The service returns a primary product, alternative records, provider status, and discrepancies. Use the product's source, update date, and basis when displaying values. Missing values are null. Preserve provider attribution and observe each source's caching rules.

Open Food Facts is available by default. USDA requires `USDA_API_KEY` and a US or NZ lookup. FatSecret requires server-side credentials, licensed applications and enabled markets. See the provider links above. Enter the address in Android under **Catalog server**, or set `-PbarcodeApiBaseUrl=https://api.example.com/` at build time.

For HTTPS hosting, import the repository as a Render Blueprint using [`render.yaml`](../render.yaml). Set provider secrets in the hosting environment, check `/health` and verify the `sources` in a real lookup for each enabled market. The Free plan can take longer to wake than Android waits for its first request. The Blueprint limits new barcode lookups to an average of 60 per minute per instance. [Render Blueprints](https://render.com/docs/infrastructure-as-code) · [Free instance limits](https://render.com/docs/free)
