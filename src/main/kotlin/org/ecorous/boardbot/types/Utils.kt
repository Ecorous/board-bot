package org.ecorous.boardbot.types

import dev.kord.common.entity.Snowflake

val Snowflake.long: Long
	get() = this.value.toLong()

val Long.snowflake: Snowflake
	get() = Snowflake(this)

val String.snowflake: Snowflake
	get() = this.toLong().snowflake

fun String.getEmojiId(): Snowflake {
	// example input: <:emoji:123456789012345678>
	return this.substringAfter(":").substringBefore(">").substringAfter(":").snowflake
}
