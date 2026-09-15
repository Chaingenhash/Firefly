# kotlinx.serialization keeps its generated serializers via @Serializable companions;
# R8 cannot see they are used and would otherwise strip them, which surfaces only at
# runtime as a SerializationException when the stored data is read back.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.chaingenhash.firefly.domain.** {
    *** Companion;
}
-keepclasseswithmembers class dev.chaingenhash.firefly.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.chaingenhash.firefly.domain.**$$serializer { *; }
