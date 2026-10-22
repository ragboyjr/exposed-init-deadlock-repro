package repro

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

// Same shape as linked-accounts.plaid: item <-> link_token reference each other, account -> item.

object UserTable : IntIdTable("app_user") {
    val guid = varchar("guid", 64)
}

object ItemTable : IntIdTable("item") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)

    // NOTE: the real plaid_item has ~60 columns; padding widens the window in which the cycle can interleave.
    val padding = (1..60).map { varchar("col_$it", 64).nullable() }

    val linkTokenId = optReference("link_token_id", LinkTokenTable, onDelete = ReferenceOption.SET_NULL)
}

object LinkTokenTable : IntIdTable("link_token") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val token = varchar("token", 128)
    val itemId = optReference("item_id", ItemTable, onDelete = ReferenceOption.CASCADE)
}

object AccountTable : IntIdTable("account") {
    val itemId = reference("item_id", ItemTable, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 128)
}
