# Zachowaj metody natywne JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

# Zachowaj klasy używane przez JNI callbacki
-keep class com.llamaagent.LlamaEngine { *; }
-keep interface com.llamaagent.LlamaEngine$TokenCallback { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.llamaagent.data.** { *; }
