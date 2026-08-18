# samsung-health-data-api-1.1.0.aar ships its own consumer ProGuard rules (proguard.txt),
# which AGP applies automatically - no Samsung-specific rules are needed here.

# Gson serializes our own DTOs in HealthRecords.kt reflectively.
-keep class com.example.shealthpoc.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
