package plain

import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread
import kotlin.system.exitProcess

// The same cycle as ItemTable <-> LinkTokenTable, no Exposed. The sleep stands in for Exposed's column setup.
object Item {
    init { Thread.sleep(SETUP_MS) }
    val linkToken = LinkToken
}

object LinkToken {
    init { Thread.sleep(SETUP_MS) }
    val item = Item
}

private val SETUP_MS = System.getProperty("setupMs", "100").toLong()

fun main() {
    val start = CyclicBarrier(2)
    val threads = listOf<() -> Any>({ Item }, { LinkToken }).map { touch ->
        thread(isDaemon = true) {
            start.await()
            touch()
        }
    }
    threads.forEach { it.join(2_000) }

    val stuck = threads.filter { it.isAlive }
    stuck.forEach { println("${it.name} stuck at ${it.stackTrace.first()}") }
    println(if (stuck.isEmpty()) "ok" else "DEADLOCK")
    exitProcess(if (stuck.isEmpty()) 0 else 1)
}
