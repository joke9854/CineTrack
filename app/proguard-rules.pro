-keepattributes Signature,*Annotation*
# Only the TMDB/Simkl/MDBList/Trakt DTOs keep their members. Retrofit's and kotlinx.serialization's
# bundled rules cover the service interfaces and serializers (as for the Floppy client, which has
# no rule of its own); NetworkFactory, the interceptors and the helpers are shrunk normally.
-keep @kotlinx.serialization.Serializable class com.cinetrack.data.remote.** { *; }
-dontwarn org.conscrypt.**
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-keep class com.cinetrack.crash.AcraOutboxSenderFactory { *; }
-keep class com.cinetrack.crash.AcraOutboxSender { *; }
