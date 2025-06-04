package org.ecorous.boardbot.types

import org.jetbrains.exposed.sql.Table

// we are implementing a starboard-like bot

object ServerConfigTable : Table() {
	val server = long("server") // Discord server ID (Snowflake). This is the server that the board is in
	val channel = long("channel") // Channel ID (Snowflake). This is the channel that the board will post to
	val emoji = long("emoji") // Emoji name. This is the emoji that will be reacted to the original message
	val threshold = integer("threshold") // Number of reactions required to post to the board. This is the number of reactions required to post to the board

	override val primaryKey = PrimaryKey(server)
}

object BoardMessagesTable : Table() {
	val boardMessage = long("boardMessage") // Board message ID (Snowflake). This is the message that will be posted to the board
	val channel = long("channel") // Channel ID (Snowflake). This is the channel that the original message was posted in
	val message = long("message") // Message ID (Snowflake). This is the original message that was reacted to
	val server = long("server") // Discord server ID (Snowflake). This is the server that the original message was posted in
	val user = long("user") // User ID (Snowflake). This is the user that posted the original message
	val count = integer("count") // Number of reactions. This is the number of reactions on the original message

	override val primaryKey = PrimaryKey(boardMessage)
}
