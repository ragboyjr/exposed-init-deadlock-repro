package repro

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val db = Database.connect("jdbc:h2:mem:repro;DB_CLOSE_DELAY=-1")
    transaction(db) {
        exec("create table item (id int auto_increment primary key, link_token_id int)")
        exec("create table link_token (id int auto_increment primary key, item_id int)")
    }
    // NOTE: the fix; first use from one thread
    if ("warm" in args) listOf(ItemTable, LinkTokenTable).forEach { it.tableName }

    val start = CyclicBarrier(2)
    val threads = listOf<() -> Table>({ ItemTable }, { LinkTokenTable }).map { table ->
        thread(isDaemon = true) {
            start.await()
            transaction(db) { table().selectAll().count() }
        }
    }
    threads.forEach { it.join(5_000) }

    val stuck = threads.filter { it.isAlive }
    stuck.forEach { println("${it.name} stuck at ${it.stackTrace.first()}") }
    println(if (stuck.isEmpty()) "ok" else "DEADLOCK")
    exitProcess(if (stuck.isEmpty()) 0 else 1)
}
