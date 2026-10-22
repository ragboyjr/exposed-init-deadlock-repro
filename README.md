# Exposed table class-init deadlock

Two Exposed tables that reference each other deadlock when two threads use them for the first time concurrently.

```kotlin
object ItemTable : IntIdTable("item") {
    val linkTokenId = optReference("link_token_id", LinkTokenTable)
}

object LinkTokenTable : IntIdTable("link_token") {
    val itemId = optReference("item_id", ItemTable)
}
```

On a cold JVM, one thread queries `ItemTable` while another queries `LinkTokenTable`. Both hang forever; no exception, no log line, no timeout. From `jstack`:

```
"Thread-0" RUNNABLE
    at repro.ItemTable.<clinit>
    - waiting on the Class initialization monitor for repro.LinkTokenTable
"Thread-1" RUNNABLE
    at repro.LinkTokenTable.<clinit>
    - waiting on the Class initialization monitor for repro.ItemTable
```

Tested with Exposed 1.6.0, Kotlin 2.4, JDK 21, H2. Deadlocks on every cold run.

## Run

Needs JDK 21.

```sh
./repro.sh            # 20 cold JVMs; stops at the first deadlock (exit 1)
./repro.sh 20 warm    # same, with the workaround applied; all print "ok"
```

After a build (`./gradlew installDist`), `./app` runs a single attempt.

`src/main/kotlin/plain/PlainKotlin.kt` shows the same deadlock with plain Kotlin objects and no Exposed:

```sh
java -cp "build/install/exposed-init-deadlock-repro/lib/*" plain.PlainKotlinKt
```

## Cause

This is JVM class initialisation, not an Exposed bug.

- A Kotlin `object` body runs in the class's static initialiser, the first time any thread touches the object.
- While a class is initialising, the JVM makes every other thread that touches it wait. The initialising thread itself may re-enter and sees the half-built object.
- `optReference("item_id", ItemTable)` reads `ItemTable` inside `LinkTokenTable`'s initialiser, and vice versa, so the two initialisers depend on each other.
- Thread A starts `ItemTable` and needs `LinkTokenTable`; thread B starts `LinkTokenTable` and needs `ItemTable`. Each waits for the other.

Exposed makes the overlap likely: the first table touched on a cold JVM loads and initialises many Exposed classes, which keeps the window open long enough for the second thread to arrive. With instant initialisers (the plain Kotlin version without its sleep) the race rarely hits.

The JVM's deadlock detection (`ThreadMXBean.findDeadlockedThreads`) does not report class-init waits, and blocked threads ignore interrupts and coroutine cancellation.

## Workarounds

- **Initialise up front.** Touch every table on one thread before running parallel transactions; `warm` does this with `listOf(ItemTable, LinkTokenTable).forEach { it.tableName }`. Single-threaded re-entry is allowed, so the cycle resolves.
- **Break the cycle.** Declare one side as a plain column (`integer("item_id").nullable()`) and keep that foreign key only in migrations. Exposed no longer knows about that constraint.

`SchemaUtils.create(...)` on startup also initialises every table on one thread, which is why many apps never see this.

## Detection

SpotBugs' `IC_INIT_CIRCULARITY` check flags the cycle from bytecode, including Kotlin objects:

```
IC: Initialization circularity between repro.ItemTable and repro.LinkTokenTable
```

## Related

- [querydsl#2334](https://github.com/querydsl/querydsl/issues/2334): the same deadlock in QueryDSL's generated Q-classes
- [jOOQ#8226](https://github.com/jOOQ/jOOQ/issues/8226): the same class of deadlock in jOOQ's generated code
- [Error Prone ClassInitializationDeadlock](https://errorprone.info/bugpattern/ClassInitializationDeadlock)
- [JLS §12.4.2, the class initialisation procedure](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.4.2)
