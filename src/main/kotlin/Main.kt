package repro

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import java.sql.DriverManager
import java.util.concurrent.CyclicBarrier
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

private const val URL = "jdbc:h2:mem:repro;DB_CLOSE_DELAY=-1"

// NOTE: raw DDL on purpose; SchemaUtils.create would initialise every table object on this thread and hide the bug.
private val ddl = listOf(
    "create table app_user (id int auto_increment primary key, guid varchar(64) not null)",
    "create table link_token (id int auto_increment primary key, user_id int not null, token varchar(128) not null, item_id int)",
    "create table item (id int auto_increment primary key, user_id int not null, " +
        (1..60).joinToString("") { "col_$it varchar(64), " } + "link_token_id int)",
    "create table account (id int auto_increment primary key, item_id int not null, name varchar(128) not null)",
)

/**
 * One cold start: three transactions run in parallel, each the first to touch a different table object.
 * `warm` initialises the objects on the main thread first (the fix in yf-user-data-systems#1057).
 */
@Suppress("DEPRECATION")
fun main(args: Array<String>) {
    val warm = "warm" in args
    DriverManager.getConnection(URL).use { conn -> conn.createStatement().use { st -> ddl.forEach(st::execute) } }
    val database = Database.connect(URL)
    if (warm) listOf(ItemTable, LinkTokenTable, AccountTable).forEach { it.tableName }

    val queries: List<() -> Table> = listOf({ ItemTable }, { LinkTokenTable }, { AccountTable })
    val dispatcher = Dispatchers.IO.limitedParallelism(queries.size)
    val start = CyclicBarrier(queries.size)

    // NOTE: not children of runBlocking, otherwise the timeout would wait forever for the hung transactions to finish
    val transactions = queries.map { table ->
        CoroutineScope(dispatcher).async {
            newSuspendedTransaction(dispatcher, database) {
                start.await()
                table().selectAll().count()
            }
        }
    }
    val counts = runBlocking { withTimeoutOrNull(5.seconds) { transactions.awaitAll() } }

    if (counts != null) {
        println("ok (warm=$warm): $counts")
        exitProcess(0)
    }
    println("DEADLOCK (warm=$warm): transactions hung on table class init")
    Thread.getAllStackTraces()
        .filterKeys { it.name.startsWith("DefaultDispatcher") }
        .filterValues { trace -> trace.any { it.className.startsWith("repro.") } }
        .forEach { (thread, trace) ->
            println("\n\"${thread.name}\" ${thread.state}")
            trace.takeWhile { !it.className.startsWith("kotlinx.coroutines") }.forEach { println("    at $it") }
        }
    exitProcess(1)
}
