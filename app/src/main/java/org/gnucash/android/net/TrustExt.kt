package org.gnucash.android.net

import org.apache.commons.httpclient.params.HttpConnectionParams
import org.apache.commons.httpclient.protocol.Protocol
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory
import java.net.InetAddress
import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class TrustAllCertificatesManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // Trust all client certificates
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // Trust all server certificates
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return arrayOf() // No accepted issuers
    }
}

fun configureHttpClientToTrustAllCertificates() {
    try {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(TrustAllCertificatesManager()), null)
        SSLContext.setDefault(sslContext)

        val socketFactory = object : ProtocolSocketFactory {
            override fun createSocket(
                host: String?,
                port: Int,
                localAddress: InetAddress?,
                localPort: Int
            ): Socket {
                return sslContext.socketFactory.createSocket(
                    host,
                    port,
                    localAddress,
                    localPort
                )
            }

            override fun createSocket(
                host: String?,
                port: Int,
                localAddress: InetAddress?,
                localPort: Int,
                params: HttpConnectionParams?
            ): Socket {
                return sslContext.socketFactory.createSocket(
                    host,
                    port,
                    localAddress,
                    localPort
                )
            }

            override fun createSocket(host: String?, port: Int): Socket {
                return sslContext.socketFactory.createSocket(host, port)
            }
        }

        Protocol.registerProtocol("https", Protocol("https", socketFactory, 443))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
