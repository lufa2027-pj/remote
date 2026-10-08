# Keep cryptographic security providers and protocol models
-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class com.vu.tvremote.protocol.** { *; }
-keep class com.vu.tvremote.data.** { *; }
