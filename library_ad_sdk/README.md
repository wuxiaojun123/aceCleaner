# library_ad_sdk

Reusable Android advertising module containing the ad mediation implementation,
AdMob/TopOn adapters, UMP consent handling, Firebase Remote Config support,
configuration parsing, full-screen ads, banners, and native-ad rendering.

## Consumer setup

1. Include the module and add `implementation(project(":library_ad_sdk"))`.
2. Add the repositories used by TopOn, Pangle, and Mintegral (see this project's
   `settings.gradle.kts`).
3. Set the product's AdMob app ID in the application module:

   ```kotlin
   android.defaultConfig.manifestPlaceholders["admobAppId"] =
       "ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy"
   ```

4. Initialize from `Application.onCreate()`:

   ```kotlin
   AdSdkManager.initialize(this)
   ```

The default bundled configuration is `library_ad_sdk/src/main/res/raw/spaces.json`.
A product can provide its own JSON without editing the library:

```kotlin
AdSdkManager.initialize(
    context = this,
    bundledConfigResId = R.raw.product_ad_spaces,
    remoteConfigKey = "abi_config_raw",
    debug = BuildConfig.DEBUG
)
```

Pass `remoteConfigKey = null` to disable Firebase Remote Config. When remote
configuration is enabled, the consuming application must apply the Google
Services plugin and provide its own `google-services.json`.

All placements are addressed by the `id` values in the selected JSON. Use
`AdSdkManager.preload`, `loadAndShow`, `showLoaded`, `loadAndShowBanner`, and
`loadAndShowNative` from product-facing code or wrap them in a product facade.
