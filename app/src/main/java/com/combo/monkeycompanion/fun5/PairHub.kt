package com.combo.monkeycompanion.fun5

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import com.combo.monkeycompanion.v5.PairProtocol
import com.combo.monkeycompanion.v5.PairProtocol.Msg
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * v5 two phones: NSD (DNS-SD on the same Wi-Fi) + one local TCP socket. No Google Play Services, no internet.
 * Discoverable only while the "Two phones" screen is open on the inviting phone; pairing needs the code + Accept.
 * Only [PairProtocol] events travel. (Bluetooth LE fallback: not implemented.)
 */
object PairHub {
    interface Listener {
        fun onStatus(text: String) {}
        fun onFound(names: List<String>) {}
        /** Inviter: the other phone typed the right code: ask the user. */
        fun onAsk(peerName: String, peerCharacter: String) {}
        fun onPaired(peerName: String, peerCharacter: String) {}
        fun onEvent(ev: PairProtocol.Event, side: PairProtocol.Side?) {}
        fun onClosed() {}
    }
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newCachedThreadPool()
    private val listeners = LinkedHashSet<Listener>()
    fun add(l: Listener) { listeners.add(l) }
    fun remove(l: Listener) { listeners.remove(l) }
    private fun fire(f: (Listener) -> Unit) { main.post { listeners.toList().forEach(f) } }

    var session: PairProtocol.Session? = null; private set
    val paired get() = session?.state == PairProtocol.Session.State.PAIRED
    val peerName get() = session?.peerName
    val peerCharacter get() = session?.peerCharacter

    private var server: ServerSocket? = null
    private var sock: Socket? = null
    private var out: PrintWriter? = null
    private var nsd: NsdManager? = null
    private var regListener: NsdManager.RegistrationListener? = null
    private var discListener: NsdManager.DiscoveryListener? = null
    private val found = LinkedHashMap<String, NsdServiceInfo>()

    /** Phone A: become discoverable and show [code]. */
    fun host(ctx: Context, myName: String, myCharacter: String, code: String) {
        stop()
        val s = PairProtocol.Session(myName, myCharacter).also { it.invite(code) }; session = s
        io.execute {
            runCatching {
                val ss = ServerSocket(0); server = ss
                val info = NsdServiceInfo().apply { serviceName = "Settai-" + PairProtocol.clean(myName); serviceType = PairProtocol.SERVICE_TYPE; port = ss.localPort }
                val nm = ctx.getSystemService(NsdManager::class.java); nsd = nm
                val rl = object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(i: NsdServiceInfo) { fire { it.onStatus("Waiting for the other phone… code $code") } }
                    override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) { fire { it.onStatus("Couldn't go visible on Wi-Fi ($e)") } }
                    override fun onServiceUnregistered(i: NsdServiceInfo) {}
                    override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
                }
                regListener = rl
                main.post { runCatching { nm.registerService(info, NsdManager.PROTOCOL_DNS_SD, rl) } }
                val c = ss.accept(); ss.close(); server = null
                attach(c, inviter = true)
            }.onFailure { if (session != null) fire { it.onStatus("Stopped") } }
        }
    }

    /** Phone B: look for Settais on this Wi-Fi. */
    fun discover(ctx: Context) {
        val nm = ctx.getSystemService(NsdManager::class.java); nsd = nm
        found.clear()
        val dl = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) { fire { it.onStatus("Looking for Settais on this Wi-Fi…") } }
            override fun onServiceFound(i: NsdServiceInfo) {
                if (!i.serviceType.contains("_settai")) return
                found[i.serviceName] = i; val names = found.keys.toList(); fire { it.onFound(names) }
            }
            override fun onServiceLost(i: NsdServiceInfo) { found.remove(i.serviceName); val names = found.keys.toList(); fire { it.onFound(names) } }
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) { fire { it.onStatus("Search failed ($e)") } }
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
        }
        discListener = dl
        runCatching { nm.discoverServices(PairProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, dl) }
    }

    /** Phone B: connect to [name] with the code its owner reads out. */
    @Suppress("DEPRECATION")
    fun join(myName: String, myCharacter: String, name: String, code: String) {
        val info = found[name] ?: return fire { it.onStatus("That phone went away") }
        val nm = nsd ?: return
        val s = PairProtocol.Session(myName, myCharacter); session = s
        nm.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(i: NsdServiceInfo, e: Int) { fire { it.onStatus("Couldn't reach it ($e)") } }
            override fun onServiceResolved(i: NsdServiceInfo) {
                io.execute {
                    runCatching {
                        val c = Socket(); c.connect(InetSocketAddress(i.host, i.port), 5000)
                        attach(c, inviter = false)
                        send(s.join(code))
                        fire { it.onStatus("Asking ${name.removePrefix("Settai-")} to accept…") }
                    }.onFailure { e -> fire { it.onStatus("Couldn't connect: ${e.message}") } }
                }
            }
        })
    }

    private fun attach(c: Socket, inviter: Boolean) {
        sock = c; c.soTimeout = 0
        out = PrintWriter(c.getOutputStream(), true)
        io.execute {
            val rd = BufferedReader(InputStreamReader(c.getInputStream()))
            runCatching {
                while (true) {
                    val line = rd.readLine() ?: break
                    val m = PairProtocol.decode(line) ?: continue
                    val s = session ?: break
                    when (m) {
                        is Msg.Hello -> if (inviter && s.checkHello(m)) fire { it.onAsk(m.name, m.characterId) } else { send(Msg.Reject); break }
                        is Msg.Accept, Msg.Reject -> if (!inviter) { s.onAnswer(m); if (s.state == PairProtocol.Session.State.PAIRED) fire { it.onPaired(s.peerName!!, s.peerCharacter!!) } else break }
                        is Msg.Ev -> if (s.state == PairProtocol.Session.State.PAIRED) fire { it.onEvent(m.event, m.side) }
                        Msg.Bye -> break
                    }
                }
            }
            closeSocket(); fire { it.onClosed() }
        }
    }

    /** Inviter's answer. */
    fun answer(accept: Boolean) {
        val s = session ?: return
        if (accept) { send(s.accept()); fire { it.onPaired(s.peerName ?: "friend", s.peerCharacter ?: "monkey") } } else { send(s.reject()); val sk = sock; io.execute { Thread.sleep(300); runCatching { sk?.close() } } }
        unregisterNsd()
    }

    fun sendEvent(ev: PairProtocol.Event, side: PairProtocol.Side? = null): Boolean {
        val s = session ?: return false
        if (!s.allowEvent(android.os.SystemClock.elapsedRealtime())) return false
        send(Msg.Ev(ev, side)); return true
    }

    private fun send(m: Msg) { val o = out ?: return; io.execute { runCatching { o.println(PairProtocol.encode(m)) } } }

    private fun closeSocket() { runCatching { sock?.close() }; sock = null; out = null; session?.close() }
    private fun unregisterNsd() {
        regListener?.let { l -> runCatching { nsd?.unregisterService(l) } }; regListener = null
        discListener?.let { l -> runCatching { nsd?.stopServiceDiscovery(l) } }; discListener = null
    }

    fun stop() {
        val o = out; val sk = sock; val sv = server; val had = session != null
        sock = null; out = null; server = null; session?.close(); session = null
        unregisterNsd()
        io.execute { if (had) runCatching { o?.println(PairProtocol.encode(Msg.Bye)) }; runCatching { sk?.close() }; runCatching { sv?.close() } }
    }
}
