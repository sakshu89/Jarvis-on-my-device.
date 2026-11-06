# Tink / security-crypto reference optional annotation classes
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-keep class androidx.security.crypto.** { *; }

# On-device LLM runtime (MediaPipe) uses JNI + protobuf reflection
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.proto.**
-dontwarn com.google.protobuf.**

# Vosk wake word + JNA
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn java.awt.**
