# kotlinx.serialization keeps generated serializers; the plugin adds most rules,
# these guard the model classes in this app.
-keepclassmembers class com.walzengroup.viennadepart.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.walzengroup.viennadepart.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
