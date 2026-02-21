# SmartFind ProGuard Rules

# Strip debug and verbose log calls from release builds.
# R8 removes the calls AND the string concatenation building their arguments.
# Log.e(), Log.w(), and Log.i() are preserved for production error reporting.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# Keep BroadcastReceivers
-keep class com.smartfind.app.receiver.SmsReceiver { *; }
-keep class com.smartfind.app.receiver.BootReceiver { *; }
-keep class com.smartfind.app.receiver.NumbersReminderReceiver { *; }
-keep class com.smartfind.app.receiver.SmartFindDeviceAdmin { *; }

# Keep Room database entities (fields for reflection-based column mapping)
-keep class com.smartfind.app.data.AuditEvent { <fields>; }
# Keep Room DAO interfaces (Room generates implementations at compile time)
-keep interface com.smartfind.app.data.AuditEventDao { *; }
# Keep Room database class (needed for Room.databaseBuilder)
-keep class com.smartfind.app.data.SmartFindDatabase { public static ** getInstance(android.content.Context); }

# AndroidX Security (EncryptedSharedPreferences)
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Suppress R8 warnings for javax.annotation classes (used by Guava/Tink)
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
