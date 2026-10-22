package repro

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object ItemTable : IntIdTable("item") {
    val linkTokenId = optReference("link_token_id", LinkTokenTable)
}

object LinkTokenTable : IntIdTable("link_token") {
    val itemId = optReference("item_id", ItemTable)
}
