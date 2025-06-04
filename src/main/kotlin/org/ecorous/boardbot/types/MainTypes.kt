package org.ecorous.boardbot.types

import dev.kord.common.entity.Snowflake

data class ServerConfig(val server: Snowflake, val channel: Snowflake, val emoji: Snowflake, val threshold: Int)

data class BoardMessage(val boardMessage: Snowflake, val channel: Snowflake, val message: Snowflake, val server: Snowflake, val user: Snowflake, val count: Int)
