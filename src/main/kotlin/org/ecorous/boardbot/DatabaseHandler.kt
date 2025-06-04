package org.ecorous.boardbot

import dev.kord.common.entity.Snowflake
import org.ecorous.boardbot.types.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseHandler {
	lateinit var db: Database
		private set

	fun init(): Boolean {
		try {
			// we're using postgresql
			db = Database.connect(
				url = "jdbc:postgresql://localhost:5432/boardbot",
				driver = "org.postgresql.Driver",
				user = "postgres", // these are just
				password = "example" // example credentials
			)
			transaction(db) {
				SchemaUtils.create(BoardMessagesTable, ServerConfigTable)
			}
			return true
		} catch (e: Exception) {
			e.printStackTrace()
			return false
		}
	}

	fun updateServerConfig(config: ServerConfig) {
		updateServerConfig(config.server, config.channel, config.emoji, config.threshold)
	}

	fun updateServerConfig(server: Snowflake, channel: Snowflake, emoji: Snowflake, threshold: Int): ServerConfig {
		transaction(db) {
			if (ServerConfigTable.selectAll().where { ServerConfigTable.server eq server.long }.count() > 0) {
				ServerConfigTable.update({ ServerConfigTable.server eq server.long }) {
					it[ServerConfigTable.channel] = channel.long
					it[ServerConfigTable.emoji] = emoji.long
				}
				return@transaction
			}
			ServerConfigTable.insert {
				it[ServerConfigTable.server] = server.long
				it[ServerConfigTable.channel] = channel.long
				it[ServerConfigTable.emoji] = emoji.long
				it[ServerConfigTable.threshold] = threshold
			}
		}
		return ServerConfig(server, channel, emoji, threshold)
	}

	fun getServerConfig(server: Snowflake): ServerConfig? {
		return transaction(db) {
			ServerConfigTable.selectAll().where { ServerConfigTable.server eq server.long }.singleOrNull()?.let {
				ServerConfig(
					Snowflake(it[ServerConfigTable.server]),
					Snowflake(it[ServerConfigTable.channel]),
					it[ServerConfigTable.emoji].snowflake,
					it[ServerConfigTable.threshold]
				)
			}
		}
	}

	fun getServerConfig(server: Long): ServerConfig? {
		return getServerConfig(server.snowflake)
	}

	fun getOriginalMessageOrNull(boardMessage: Snowflake): Snowflake? {
		return transaction(db) {
			BoardMessagesTable.selectAll().where { BoardMessagesTable.boardMessage eq boardMessage.long }.singleOrNull()?.let {
				Snowflake(it[BoardMessagesTable.message])
			}
		}
	}

	fun getBoardMessageOrNull(originalMessage: Snowflake): BoardMessage? {
		return transaction(db) {
			BoardMessagesTable.selectAll().where { BoardMessagesTable.message eq originalMessage.long }.singleOrNull()?.let {
				BoardMessage(
					it[BoardMessagesTable.boardMessage].snowflake,
					it[BoardMessagesTable.channel].snowflake,
					it[BoardMessagesTable.message].snowflake,
					it[BoardMessagesTable.server].snowflake,
					it[BoardMessagesTable.user].snowflake,
					it[BoardMessagesTable.count]
				)
			}
		}
	}
	fun updateBoardMessageCount(originalMessage: Snowflake, count: Int) {
		transaction(db) {
			BoardMessagesTable.update({ BoardMessagesTable.message eq originalMessage.long }) {
				it[BoardMessagesTable.count] = count
			}
		}
	}

	fun addBoardMessage(boardMessage: Snowflake, channel: Snowflake, message: Snowflake, server: Snowflake, user: Snowflake, count: Int) {
		transaction(db) {
			BoardMessagesTable.insert {
				it[BoardMessagesTable.boardMessage] = boardMessage.long
				it[BoardMessagesTable.channel] = channel.long
				it[BoardMessagesTable.message] = message.long
				it[BoardMessagesTable.server] = server.long
				it[BoardMessagesTable.user] = user.long
				it[BoardMessagesTable.count] = count
			}
		}
	}

	fun deleteBoardMessage(boardMessage: Snowflake) {
		transaction(db) {
			BoardMessagesTable.deleteWhere { BoardMessagesTable.boardMessage eq boardMessage.long }
		}
	}

}
