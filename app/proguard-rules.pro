# sherpa-onnx reads and creates these classes from native code (JNI) by their
# field and class names, so they must survive shrinking and obfuscation.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Called from native code by name and signature: invoke(IIJ)Ljava/lang/Integer;
-keep class app.fenek.engine.DiarizationProgress {
    public java.lang.Integer invoke(int, int, long);
}

# kotlinx.serialization: keep generated serializers of the app's @Serializable classes.
-keepclassmembers @kotlinx.serialization.Serializable class app.fenek.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class app.fenek.** {
    kotlinx.serialization.KSerializer serializer(...);
}
