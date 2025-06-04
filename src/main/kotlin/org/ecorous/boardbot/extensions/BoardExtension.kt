package org.ecorous.boardbot.extensions

import dev.kord.common.entity.Snowflake
import dev.kordex.core.DISCORD_RED
import dev.kordex.core.commands.converters.impl.*
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.extensions.event
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.effectiveName
import dev.kord.core.event.Event
import dev.kord.core.event.message.ReactionAddEvent
import dev.kord.core.event.message.ReactionRemoveEvent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.embed
import dev.kordex.core.commands.Arguments
import dev.kordex.core.components.components
import dev.kordex.core.components.linkButton
import dev.kordex.core.i18n.toKey
import dev.kordex.core.utils.getJumpUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import org.ecorous.boardbot.DatabaseHandler
import org.ecorous.boardbot.logger
import org.ecorous.boardbot.types.*

class BoardExtension : Extension() {
	override val name = "test"

	fun embedTemplate(count: Int, emojiMention: String, originalContent: String, msgImage: String?, authorImage: String?, authorName: String?, jumpUrl: String): EmbedBuilder.() -> Unit = {
		title = "$count $emojiMention"
		description = originalContent
		image = msgImage
		footer {
			icon = authorImage
			text = "Posted by ${authorName ?: "Unknown"}"
		}
	}

	fun Flow<User>.distinct(): Flow<User> = flow {
		val seen = mutableSetOf<Snowflake>()
		collect { value ->
			if (seen.add(value.id)) emit(value)
		}
	}

	suspend fun BoardMessage.updatePostCount(reactionCount: Int, guild: GuildBehavior, originalMessage: Message) {
		val config = DatabaseHandler.getServerConfig(guild.id) ?: run {
			logger.error("No server config found for guild ${guild.id}. Cannot update board message count.")
			return
		}
		val boardChannel = guild.getChannelOfOrNull<TextChannel>(config.channel) ?: run {
			logger.error("No channel found for id ${config.channel}. Cannot update board message count.")
			return
		}
		val emoji = guild.emojis.filter { it.id == config.emoji }.firstOrNull() ?: run {
			logger.error("No emoji found for id ${config.emoji}. Cannot update board message count.")
			return
		}





		DatabaseHandler.updateBoardMessageCount(originalMessage.id, reactionCount)
		boardChannel.getMessage(this.boardMessage).let { boardMsg ->
			boardMsg.edit {
				embed(embedTemplate(
					reactionCount,
					emoji.mention,
					originalMessage.content,
					originalMessage.attachments.firstOrNull()?.let { if (it.isImage) it.url else null },
					originalMessage.author?.avatar?.cdnUrl?.toUrl(),
					originalMessage.author?.effectiveName,
					originalMessage.getJumpUrl()
				))
//				embed {
//					title = "$count ${emoji.mention}s"
//					description = originalMessage.content
//					image = originalMessage.attachments.firstOrNull()?.let { if (it.isImage) it.url else null }
//					footer {
//						icon = originalMessage.author?.avatar?.cdnUrl?.toUrl()
//						text = "Posted by ${originalMessage.author?.effectiveName}"
//					}
//				}
			}
		}
	}

	suspend fun Event.reactionUpdate() {
		if (this !is ReactionAddEvent && this !is ReactionRemoveEvent) return
		var message = (if (this is ReactionAddEvent) this.message else (this as ReactionRemoveEvent).message).asMessageOrNull() ?: return
		val keepMsg = message // keep a reference to the original message, because we might need it later in case it's a board message
		val guild = (if (this is ReactionAddEvent) this.guild else (this as ReactionRemoveEvent).guild) ?: return
		val config = DatabaseHandler.getServerConfig(guild.id) ?: return
		val boardChannel = guild.getChannelOfOrNull<TextChannel>(config.channel) ?: return
		val emoji = guild.emojis.filter { it.id == config.emoji }.firstOrNull() ?: return
		val reactionEmoji = ReactionEmoji.from(emoji)
		var reactors = message.getReactors(reactionEmoji)

		val originalMessageId = DatabaseHandler.getOriginalMessageOrNull(message.id)
		val isBoardMessage = originalMessageId != null
		if (originalMessageId != null) {
			// if the message is a board message, we need to get the original message
			val tmp = boardChannel.getMessageOrNull(originalMessageId) ?: run {
				logger.error("Original message for ${message.id} not found. Deleting board message.")
				message.delete()
				return
			}
			message = tmp
			reactors = message.getReactors(reactionEmoji) // get the reactors for the original message
		}
		val boardMessage = DatabaseHandler.getBoardMessageOrNull(message.id)
		val boardReactions = mutableListOf<User>()
		if (isBoardMessage) {
			keepMsg.getReactors(reactionEmoji).collect { r ->
				if (r.id == message.author?.id) return@collect // don't add the author of the original message to the board reactions
				boardReactions.add(r)
			}
		} else if (boardMessage != null) {
			val msg = boardChannel.getMessage(boardMessage.boardMessage)
			msg.getReactors(reactionEmoji).collect { r ->
				if (r.id == message.author?.id) return@collect // don't add the author of the original message to the board reactions
				boardReactions.add(r)
			}
		}
		val reactions = (reactors.filter { it.id != message.author?.id }.distinct().toList() + boardReactions).distinct()
		val numOfReactions = reactions.count()



		if (numOfReactions < config.threshold) {
			if (boardMessage != null) {
				boardChannel.getMessage(boardMessage.boardMessage).delete()
				DatabaseHandler.deleteBoardMessage(boardMessage.boardMessage)
				logger.info("Deleted board message for ${message.id} due to reaction count dropping below threshold.")
			}
			return
		} else {
			boardMessage?.updatePostCount(numOfReactions, guild, message)
		}
		if (boardMessage != null) return // if we already have a board message, we don't need to do anything else
		logger.info("No board message found for ${message.id}. Creating a new one.")
		val bM = boardChannel.createMessage {
			embed(embedTemplate(
				numOfReactions,
				emoji.mention,
				message.content,
				message.attachments.firstOrNull()?.let { if (it.isImage) it.url else null },
				message.author?.avatar?.cdnUrl?.toUrl(),
				message.author?.effectiveName,
				message.getJumpUrl()
			))

			if (message.embeds.isNotEmpty()) {
				message.embeds.forEach { e ->
					embed {
						title = e.title
						description = e.description
						color = e.color
						image = e.image?.url
						footer {
							icon = e.footer?.iconUrl
							text = e.footer?.text ?: ""
						}
						if (e.fields.isNotEmpty()) {
							e.fields.forEach { field ->
								field(field.name, field.inline == true) { field.value }
							}
						}
					}
				}
			}

			components {
				linkButton {
					label = "Jump".toKey()
					url = message.getJumpUrl()
				}
			}
		}
		message.author ?: run {
			logger.error("Message author is null")
			return
		}
		DatabaseHandler.addBoardMessage(
			bM.id,
			message.channelId,
			message.id,
			guild.id,
			message.author!!.id,
			numOfReactions
		)
	}

	override suspend fun setup() {
		event<ReactionAddEvent> {
			check { !event.user.asUser().isBot }
			action {
				event.reactionUpdate()
//				val message = event.message
//				val guild = event.guild ?: return@action
//				val config = DatabaseHandler.getServerConfig(guild.id) ?: return@action
//				val channel = guild.getChannelOf<TextChannel>(config.channel) ?: return@action
//				// get guild emojis, find the one with the same id as the one in the config
//				val emoji = guild.emojis.filter { it.id == config.emoji }.firstOrNull() ?: return@action
//				val reactionEmoji = ReactionEmoji.from(emoji)
//				val reactions = message.getReactors(reactionEmoji)
//				var numOfReactions = reactions.count()
//				if (reactions.filter { it.id == message.id }.count() > 0) {
//					numOfReactions -= 1 // remove the author's reaction - they dont count
//				}
//
//
//				if (numOfReactions >= config.threshold) {
//					val message = message.asMessage() // shadow because icba
//					// need to check if the message is already in the board
//					val boardMessage = DatabaseHandler.getBoardMessageOrNull(message.id)
//					boardMessage?.updatePostCount(numOfReactions, guild, message)
//					if (boardMessage != null) {
//						logger.info("Board message already exists for ${message.id}. Updated post count.")
//						return@action
//					}

//					if (boardMessage != null) {
//						// update the count
//						DatabaseHandler.updateBoardMessageCount(message.id, numOfReactions)
//						channel.getMessage(boardMessage.boardMessage).let { msg ->
//							msg.edit {
//								embed {
//									title = "$numOfReactions ${emoji.mention}s"
//									description = message.content
//									image = message.attachments.firstOrNull()?.let { if (it.isImage) it.url else null}
//									footer {
//										icon = message.author?.avatar?.cdnUrl?.toUrl()
//										text = "Posted by ${message.author?.effectiveName}"
//									}
//								}
//							}
//						}
//						return@action
//					}
//					logger.info("Posting to board")
//					logger.info("Message: ${message.content}")
//					logger.info("Reactions: $numOfReactions")
//					logger.info("Message ID: ${message.id}")
//					logger.info("Message Author: ${message.author?.mention}")
//					logger.info("Message Author ID: ${message.author?.id}")
//					logger.info("Message Effective Name: ${message.author?.effectiveName}")
//					logger.info("Message Author Avatar: ${message.author?.avatar?.cdnUrl}")
//					logger.info("Message Attachments: ${message.attachments}")
//					logger.info("Message: $message")
//
//					val bM = channel.createMessage {
//						embed(embedTemplate(
//							numOfReactions,
//							emoji.mention,
//							message.content,
//							message.attachments.firstOrNull()?.let { if (it.isImage) it.url else null },
//							message.author?.avatar?.cdnUrl?.toUrl(),
//							message.author?.effectiveName,
//							message.getJumpUrl()
//						))
//
//						components {
//							linkButton {
//								label = "Jump".toKey()
//								url = message.getJumpUrl()
//							}
//						}
//					}
//					if (message.author == null) {
//						logger.error("Message author is null")
//						return@action
//					}
//					DatabaseHandler.addBoardMessage(bM.id, message.channelId, message.id, guild.id, message.author!!.id, numOfReactions) // why is !! needed here. we checked for null above
//				}
			}
		}
		event<ReactionRemoveEvent> {
			check { !event.user.asUser().isBot }
			action {
				event.reactionUpdate()
//				val message = event.message
//				val guild = event.guild ?: return@action
//				val config = DatabaseHandler.getServerConfig(guild.id) ?: return@action
//				val channel = guild.getChannelOf<TextChannel>(config.channel) ?: return@action
//				val emoji = guild.emojis.filter { it.id == config.emoji }.firstOrNull() ?: return@action
//				val reactionEmoji = ReactionEmoji.from(emoji)
//				val reactions = message.getReactors(reactionEmoji)
//				var numOfReactions = reactions.count()
//				if (reactions.filter { it.id == message.id }.count() > 0) {
//					numOfReactions -= 1 // remove the author's reaction - they dont count
//				}
//				if (numOfReactions < config.threshold) {
//					val boardMessage = DatabaseHandler.getBoardMessageOrNull(message.id)
//					if (boardMessage != null) {
//						channel.getMessage(boardMessage.boardMessage).delete()
//						DatabaseHandler.deleteBoardMessage(boardMessage.boardMessage)
//					}
//				} else {
//					val message = message.asMessage()
//					val boardMessage = DatabaseHandler.getBoardMessageOrNull(message.id)
//				}
			}
		}
		ephemeralSlashCommand(::ServerConfigSetup) {

			//guild(Environment.TEST_SERVER_ID)
			name = "setup".toKey()
			description = "Set up the server's board configuration".toKey()
			check {
				allowInDms = false
				passIf { event.interaction.user.id.value.toLong() == 604653220341743618 } // my id is 604653220341743618
				failIf { event.interaction.user.id.value.toLong() != 604653220341743618 } // my id is 604653220341743618
			}
			action {
				val channel = arguments.boardChannel
				val emoji = arguments.boardEmoji
				val threshold = arguments.threshold
				val guild = this.guild!!
				if (emoji.mention.length < 10) {
					respond {
						embed {
							color = DISCORD_RED
							title = "Invalid Emoji"
							description = "Tip: cannot use system emojis due to discord being fucky, you have to use a custom guild emoji"
						}
					}
					return@action
				}
				DatabaseHandler.updateServerConfig(guild.id, channel.id, emoji.mention.getEmojiId(), threshold)

				respond {
					embed {
						title = "Server Configuration"
						description = "Updated server configuration"
						field {
							name = "Channel"
							value = channel.mention
						}
						field {
							name = "Emoji"
							value = "${emoji.mention} (${emoji.mention.getEmojiId()})"
						}
						field {
							name = "Threshold"
							value = threshold.toString()
						}
					}
				}
			}
		}
	}

	inner class ServerConfigSetup : Arguments() {
		val boardChannel by channel {
			name = "channel".toKey()
			description = "The channel to use for the board".toKey()
		}
		val boardEmoji by emoji {
			name = "emoji".toKey()
			description = "The emoji to use for the board".toKey()
		}
		val threshold by int {
			name = "threshold".toKey()
			description = "The number of reactions required to post to the board".toKey()
		}
	}
}
