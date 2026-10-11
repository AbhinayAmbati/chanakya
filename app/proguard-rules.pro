# Chanakya ProGuard / R8 rules.
#
# The app has no reflection-based frameworks beyond Room (which ships its own consumer
# rules), so only a small set of rules is needed here.

# Keep line numbers for readable crash reports, but hide the original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin metadata is required for some runtime reflection used by Compose.
-keep class kotlin.Metadata { *; }
