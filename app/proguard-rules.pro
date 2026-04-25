-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Navigation safe args
-keepnames class * extends android.os.Parcelable
-keepnames class * extends java.io.Serializable
