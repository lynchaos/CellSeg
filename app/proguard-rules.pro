# Add project specific ProGuard rules here.

# ── Kotlin ───────────────────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**

# ── Timber: strip all logging in release ────────────────────────────────────
-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ── Hilt ─────────────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

# ── Room ─────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# ── Moshi codegen (retain @JsonClass annotated models) ──────────────────────
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class **JsonAdapter { *; }
-keep class **JsonAdapter$* { *; }
-dontwarn com.squareup.moshi.**

# ── Retrofit + OkHttp ────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep interface retrofit2.** { *; }

# ── Protobuf ─────────────────────────────────────────────────────────────────
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# Generated proto message classes (AppSettingsProto, SegmentationParamsProto, etc.)
# R8 must not rename or remove these — DataStore serializer accesses them by name.
-keep class uk.yaylali.cellseg.data.datastore.proto.** { *; }

# ── ONNX Runtime ─────────────────────────────────────────────────────────────
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ── CameraX ─────────────────────────────────────────────────────────────────
-keep class androidx.camera.** { *; }

# ── Security Crypto ──────────────────────────────────────────────────────────
-keep class androidx.security.crypto.** { *; }

# ── Tink (used by Security Crypto) — errorprone annotations are compile-time only ──
-dontwarn com.google.errorprone.annotations.**

# ── Tiff library ─────────────────────────────────────────────────────────────
-keep class com.waynejo.androidndkgif.** { *; }
-dontwarn com.waynejo.**
