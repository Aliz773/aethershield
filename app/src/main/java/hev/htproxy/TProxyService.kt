package hev.htproxy

/**
 * JNI bridge to libhev-socks5-tunnel.so.
 * The package and class name MUST be exactly hev.htproxy.TProxyService, because that is the
 * default name compiled into hev-socks5-tunnel's JNI_OnLoad (PKGNAME/CLSNAME in hev-jni.c).
 */
object TProxyService {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    external fun TProxyStartService(configPath: String, fd: Int): Boolean
    external fun TProxyStopService(): Boolean
    external fun TProxyIsRunning(): Boolean
    external fun TProxyGetStats(): LongArray
}
