# Keep line numbers and source file names for readable crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# Modbus TCP client, packet parsing, registers, models, and repositories
-keep class com.huaweitv.solar.data.modbus.** { *; }
-keep class com.huaweitv.solar.data.model.** { *; }
-keep class com.huaweitv.solar.data.repository.** { *; }

# ViewModel reflection instantiation
-keep class com.huaweitv.solar.ui.viewmodel.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Jetpack Compose & Compose for TV
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
-keepclassmembers class * {
    @androidx.compose.runtime.Stable *;
}
-keepclassmembers class * {
    @androidx.compose.runtime.Immutable *;
}
-dontwarn androidx.compose.**
-dontwarn androidx.tv.**

# DataStore & Protobuf
-keep class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { *; }
-dontwarn androidx.datastore.**

# Kotlin & Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlin.Metadata { *; }
