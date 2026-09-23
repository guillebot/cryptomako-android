# Cryptomator cryptolib (ServiceLoader + reflection)
-keep class org.cryptomator.cryptolib.** { *; }
-keep class org.cryptomator.siv.** { *; }
-keep class org.cryptomator.cryptolib.v1.CryptorProviderImpl { *; }
-keep class org.cryptomator.cryptolib.v2.CryptorProviderImpl { *; }
-keep class org.cryptomator.cryptolib.common.** { *; }

# Keep AGPL / About strings referenced from code
-keepclassmembers class net.gschimmel.cryptomako.R$string { *; }

# WorkManager
-dontwarn androidx.work.**

# cryptolib shaded BouncyCastle references JNDI / EdEC classes absent on Android
-dontwarn javax.naming.**
-dontwarn org.cryptomator.cryptolib.shaded.bouncycastle.jcajce.provider.asymmetric.edec.**
# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn javax.naming.NamingEnumeration
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.directory.DirContext
-dontwarn javax.naming.directory.InitialDirContext
-dontwarn javax.naming.directory.SearchControls
-dontwarn javax.naming.directory.SearchResult
-dontwarn org.cryptomator.cryptolib.shaded.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi$Ed25519
-dontwarn org.cryptomator.cryptolib.shaded.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi$Ed448
-dontwarn org.cryptomator.cryptolib.shaded.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi$X25519
-dontwarn org.cryptomator.cryptolib.shaded.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi$X448
-dontwarn org.cryptomator.cryptolib.shaded.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi