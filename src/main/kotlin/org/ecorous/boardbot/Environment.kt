package org.ecorous.boardbot

import dev.kordex.core.utils.env
import org.ecorous.boardbot.types.*

object Environment {
	internal val TEST_SERVER_ID = env("TEST_SERVER").snowflake
	internal val TOKEN = env("TOKEN")
}
