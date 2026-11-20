-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.royalworld.app.MainActivity$RoyalBridge { *; }
-keep class com.royalworld.app.VideoDownloadService { *; }
-keep class com.royalworld.app.RoyalFirebaseMessagingService { *; }
-dontwarn com.google.firebase.**
