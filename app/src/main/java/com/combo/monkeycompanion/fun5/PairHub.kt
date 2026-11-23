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
    /**
     * v10.54.1: phones found by a UDP "who's there?" on the local network: works where mDNS (NSD) does not, such as
     * a phone's own hotspot and many home routers. name → (address, TCP port).
     */
    private val udpFound = java.util.concurrent.ConcurrentHashMap<String, Pair<java.net.InetAddress, Int>>()
    const val UDP_PORT = 47821
    /** The inviting phone listens here when it can, so the other phone can also join by typing its address. */
    const val TCP_PORT = 47822
    @Volatile private var udpGen = 0
    private var mcLock: android.net.wifi.WifiManager.MulticastLock? = null
    @Volatile private var udpSock: java.net.DatagramSocket? = null

    private fun allNames(): List<String> = (synchronized(found) { found.keys.toList() } + udpFound.keys).distinct()

    /** This phone's addresses on Wi-Fi or its hotspot (for "type this address on the other phone"). */
    fun myAddresses(): List<String> = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses.toList() }.mapNotNull { it.address as? java.net.Inet4Address }
            .map { it.hostAddress ?: "" }.filter { it.isNotEmpty() && !it.startsWith("169.254") }
    }.getOrDefault(emptyList())

    private fun broadcasts(): List<java.net.InetAddress> = runCatching {
        val l = java.net.NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses.toList() }.mapNotNull { it.broadcast }
        (l + java.net.InetAddress.getByName("255.255.255.255")).distinct()
    }.getOrDefault(listOf(java.net.InetAddress.getByName("255.255.255.255")))

    private fun lockMulticast(ctx: Context) {
        if (mcLock?.isHeld == true) return
        mcLock = runCatching {
            (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager)
                .createMulticastLock("settai-pair").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
    }

    private fun openUdp(bindFixed: Boolean): java.net.DatagramSocket {
        val d = java.net.DatagramSocket(null)
        d.reuseAddress = true; d.broadcast = true; d.soTimeout = 700
        runCatching { d.bind(java.net.InetSocketAddress(if (bindFixed) UDP_PORT else 0)) }
            .onFailure { d.bind(java.net.InetSocketAddress(0)) }
        return d
    }

    private fun sendUdp(d: java.net.DatagramSocket, text: String, to: java.net.InetAddress, port: Int = UDP_PORT) {
        val b = text.toByteArray(); runCatching { d.send(java.net.DatagramPacket(b, b.size, to, port)) }
    }
    /** v10.52.1: the name this phone shows on Wi-Fi, so a search never lists this phone itself. */
    @Volatile private var myService: String? = null
    private fun isMine(n: String): Boolean { val m = myService ?: return false; return n == m || n.startsWith("$m (") }
    /** Tests: the search result filter. */
    fun debugIsMine(myName: String, found: String): Boolean { myService = "Settai-" + PairProtocol.clean(myName); return isMine(found) }

    /** Phone A: become discoverable and show [code]. */
    fun host(ctx: Context, myName: String, myCharacter: String, code: String) {
        stop()
        val s = PairProtocol.Session(myName, myCharacter).also { it.invite(code) }; session = s
        io.execute {
            runCatching {
                val ss = runCatching { ServerSocket(TCP_PORT) }.getOrElse { ServerSocket(0) }; server = ss
                startBeacon(ctx, "Settai-" + PairProtocol.clean(myName), ss.localPort)
                myService = "Settai-" + PairProtocol.clean(myName)
                val info = NsdServiceInfo().apply { serviceName = myService; serviceType = PairProtocol.SERVICE_TYPE; port = ss.localPort }
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

    /** Inviting phone: answer "who's there?" and announce itself every second and a half, until paired or stopped. */
    private fun startBeacon(ctx: Context, name: String, port: Int) {
        lockMulticast(ctx)
        val gen = ++udpGen
        io.execute {
            val d = runCatching { openUdp(bindFixed = true) }.getOrNull() ?: return@execute
            udpSock = d
            val here = "SETTAI1 HERE $name $port"
            var last = 0L
            val buf = ByteArray(256)
            while (gen == udpGen && session?.state != PairProtocol.Session.State.PAIRED) {
                val now = System.currentTimeMillis()
                if (now - last > 1500) { last = now; broadcasts().forEach { sendUdp(d, here, it) } }
                val pk = java.net.DatagramPacket(buf, buf.size)
                val got = runCatching { d.receive(pk); true }.getOrDefault(false)
                if (got && String(pk.data, 0, pk.length).startsWith("SETTAI1 WHO")) sendUdp(d, here, pk.address, pk.port)
            }
            runCatching { d.close() }
        }
    }

    /** Searching phone: ask "who's there?" on every network for 25 seconds and list the answers. */
    private fun startUdpSearch(ctx: Context) {
        lockMulticast(ctx)
        val gen = ++udpGen
        io.execute {
            val d = runCatching { openUdp(bindFixed = true) }.getOrNull() ?: return@execute
            udpSock = d
            val mine = myAddresses().toSet()
            val end = System.currentTimeMillis() + 25_000
            var last = 0L
            val buf = ByteArray(256)
            while (gen == udpGen && System.currentTimeMillis() < end) {
                val now = System.currentTimeMillis()
                if (now - last > 1000) { last = now; broadcasts().forEach { sendUdp(d, "SETTAI1 WHO", it) } }
                val pk = java.net.DatagramPacket(buf, buf.size)
                if (!runCatching { d.receive(pk); true }.getOrDefault(false)) continue
                val p = String(pk.data, 0, pk.length).trim().split(' ')
                if (p.size != 4 || p[0] != "SETTAI1" || p[1] != "HERE") continue
                val name = p[2].take(48); val port = p[3].toIntOrNull() ?: continue
                if (pk.address.hostAddress in mine || isMine(name)) continue
                val isNew = udpFound.put(name, pk.address to port) == null
                if (isNew) { val names = allNames(); fire { it.onFound(names) } }
            }
            runCatching { d.close() }
            if (gen == udpGen && allNames().isEmpty()) fire { it.onStatus("No phone found. Is \"Show a code\" open on the other phone? You can also type its address below.") }
        }
    }

    /** Phone B: connect straight to the address shown on the other phone. */
    fun joinAddress(myName: String, myCharacter: String, address: String, code: String) {
        val s = PairProtocol.Session(myName, myCharacter); session = s
        io.execute {
            runCatching {
                val c = Socket(); c.connect(InetSocketAddress(address.trim(), TCP_PORT), 5000)
                attach(c, inviter = false)
                send(s.join(code))
                fire { it.onStatus("Asking $address to accept…") }
            }.onFailure { e -> fire { it.onStatus("Couldn't reach $address: ${e.message}") } }
        }
    }

    /** Phone B: look for Settais on this Wi-Fi. */
    fun discover(ctx: Context) {
        val nm = ctx.getSystemService(NsdManager::class.java); nsd = nm
        discListener?.let { l -> runCatching { nm.stopServiceDiscovery(l) } }; discListener = null   // a fresh search each tap
        found.clear(); udpFound.clear()
        fire { it.onFound(emptyList()) }
        startUdpSearch(ctx)
        val dl = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) { fire { it.onStatus("Looking for Set-Tai phones on this Wi-Fi…") } }
            override fun onServiceFound(i: NsdServiceInfo) {
                if (!i.serviceType.contains("_settai") || isMine(i.serviceName)) return
                found[i.serviceName] = i; val names = allNames(); fire { it.onFound(names) }
            }
            override fun onServiceLost(i: NsdServiceInfo) { found.remove(i.serviceName); val names = allNames(); fire { it.onFound(names) } }
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) { fire { it.onStatus("Search failed ($e). Try again, or turn Wi-Fi off and on.") } }
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
        }
        discListener = dl
        runCatching { nm.discoverServices(PairProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, dl) }
    }

    /** Phone B: connect to [name] with the code its owner reads out. */
    @Suppress("DEPRECATION")
    fun join(myName: String, myCharacter: String, name: String, code: String) {
        udpFound[name]?.let { (addr, port) ->
            val s = PairProtocol.Session(myName, myCharacter); session = s
            io.execute {
                runCatching {
                    val c = Socket(); c.connect(InetSocketAddress(addr, port), 5000)
                    attach(c, inviter = false)
                    send(s.join(code))
                    fire { it.onStatus("Asking ${name.removePrefix("Settai-")} to accept…") }
                }.onFailure { e -> fire { it.onStatus("Couldn't connect: ${e.message}") } }
            }
            return
        }
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
        udpGen++
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
        sock = null; out = null; server = null; session?.close(); session = null; myService = null
        udpGen++; runCatching { udpSock?.close() }; udpSock = null
        runCatching { mcLock?.takeIf { it.isHeld }?.release() }; mcLock = null
        unregisterNsd()
        io.execute { if (had) runCatching { o?.println(PairProtocol.encode(Msg.Bye)) }; runCatching { sk?.close() }; runCatching { sv?.close() } }
    }
}
