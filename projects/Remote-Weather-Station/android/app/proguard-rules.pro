-keepclassmembernames class io.netty.** { *; }
-keepclassmembers class org.jctools.** { *; }

# HiveMQ references these optional Netty transports, logging adapters, TLS engines,
# and MQTT-over-WebSocket/proxy paths. This app uses standard MQTT over TCP/TLS,
# Android's default TLS provider, and no external logging backend.
-dontwarn io.netty.channel.epoll.**
-dontwarn io.netty.handler.codec.http.**
-dontwarn io.netty.handler.proxy.**
-dontwarn io.netty.internal.tcnative.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.eclipse.jetty.alpn.**
-dontwarn org.eclipse.jetty.npn.**
-dontwarn org.slf4j.**
-dontwarn reactor.blockhound.**
