/*
 *
 * MIT License
 *
 * Copyright (c) 2017 Frederik Ar. Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package fredboat.messaging;

import fredboat.feature.I18n;
import fredboat.util.log.LogTheStackException;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.core.requests.Request;
import net.dv8tion.jda.core.requests.Response;
import net.dv8tion.jda.core.requests.RestAction;
import net.dv8tion.jda.core.requests.Route;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Created by napster on 10.09.17.
 * <p>
 * Everything related to sending things out from FredBoat
 */
public class CentralMessaging {

    private static final Logger log = LoggerFactory.getLogger(CentralMessaging.class);


    // ********************************************************************************
    //       Thread local handling and providing of Messages and Embeds builders
    // ********************************************************************************

    //instead of creating millions of MessageBuilder and EmbedBuilder objects we're going to reuse the existing ones, on
    // a per-thread scope
    // this makes sense since the vast majority of message processing in FredBoat is happening in the main JDA threads

    private static ThreadLocal<MessageBuilder> threadLocalMessageBuilder = ThreadLocal.withInitial(MessageBuilder::new);
    private static ThreadLocal<EmbedBuilder> threadLocalEmbedBuilder = ThreadLocal.withInitial(EmbedBuilder::new);

    public static MessageBuilder getClearThreadLocalMessageBuilder() {
        return threadLocalMessageBuilder.get().clear();
    }

    public static EmbedBuilder getClearThreadLocalEmbedBuilder() {
        return threadLocalEmbedBuilder.get()
                .clearFields()
                .setTitle(null)
                .setDescription(null)
                .setTimestamp(null)
                .setColor(null)
                .setThumbnail(null)
                .setAuthor(null, null, null)
                .setFooter(null, null)
                .setImage(null);
    }

    public static Message from(String string) {
        return getClearThreadLocalMessageBuilder().append(string).build();
    }

    public static Message from(MessageEmbed embed) {
        return getClearThreadLocalMessageBuilder().setEmbed(embed).build();
    }


    // ********************************************************************************
    //       Convenience methods that convert input to Message objects and send it
    // ********************************************************************************

    /**
     * Combines the benefits of JDAs RestAction#queue and returning a Future that can be get() if necessary
     *
     * @param channel   The channel that should be messaged
     * @param message   Message to be sent
     * @param onSuccess Optional success handler
     * @param onFail    Optional exception handler
     * @return Future that can be waited on in case the code requires completion. Similar to JDA's RestAction#complete,
     * avoid usage where not absolutely needed.
     */
    public static MessageFuture sendMessage(@Nonnull MessageChannel channel, @Nonnull Message message,
                                            @Nullable Consumer<Message> onSuccess, @Nullable Consumer<Throwable> onFail) {
        return sendMessage0(
                channel,
                message,
                onSuccess,
                onFail
        );
    }

    // Message
    public static MessageFuture sendMessage(@Nonnull MessageChannel channel, @Nonnull Message message,
                                            @Nullable Consumer<Message> onSuccess) {
        return sendMessage0(
                channel,
                message,
                onSuccess,
                null
        );
    }

    // Message
    public static MessageFuture sendMessage(@Nonnull MessageChannel channel, @Nonnull Message message) {
        return sendMessage0(
                channel,
                message,
                null,
                null
        );
    }

    // Embed
    public static MessageFuture sendMessage(@Nonnull MessageChannel channel, @Nonnull MessageEmbed embed,
                                            @Nullable Consumer<Message> onSuccess, @Nullable Consumer<Throwable> onFail) {
        return sendMessage0(
                channel,
                from(embed),
                onSuccess,
                onFail
        );
    }

    // Embed
    public static MessageFuture sendMessage(@Nonnull MessageChannel channel, @Nonnull MessageEmbed embed,
                                            @Nullable Consumer<Message> onSuccess) {
        return sendMessage0(
                channel,
                from(embed),
                onSuccess,
                null
        );
    }

    // Embed
    public static MessageFuture sendMessage(@Nonnull MessageChannel channel, @Nonnull MessageEmbed embed) {
        return sendMessage0(
                channel,
                from(embed),
                null,
                null
        );
    }

    // String
    public static MessageFuture sendMessage(@Nonnull MessageChannel channel, @Nonnull String content,
                                            @Nullable Consumer<Message> onSuccess, @Nullable Consumer<Throwable> onFail) {
        return sendMessage0(
                channel,
                from(content),
                onSuccess,
                onFail
        );
    }

    // String
    public static MessageFuture sendMessage(@Nonnull MessageChannel channel, @Nonnull String content,
                                            @Nullable Consumer<Message> onSuccess) {
        return sendMessage0(
                channel,
                from(content),
                onSuccess,
                null
        );
    }

    // String
    public static MessageFuture sendMessage(@Nonnull MessageChannel channel, @Nonnull String content) {
        return sendMessage0(
                channel,
                from(content),
                null,
                null
        );
    }

    // for the adventurers among us
    public static void sendShardlessMessage(long channelId, Message msg) {
        sendShardlessMessage(msg.getJDA(), channelId, msg.getRawContent());
    }

    // for the adventurers among us
    public static void sendShardlessMessage(JDA jda, long channelId, String content) {
        JSONObject body = new JSONObject();
        body.put("content", content);
        LogTheStackException stackTrace = LogTheStackException.createStackTrace();
        new RestAction<Void>(jda, Route.Messages.SEND_MESSAGE.compile(Long.toString(channelId)), body) {
            @Override
            protected void handleResponse(Response response, Request<Void> request) {
                if (response.isOk())
                    request.onSuccess(null);
                else
                    request.onFailure(response);
            }
        }.queue(null, t -> stackTraceOnFail(t, stackTrace));
    }

    // ********************************************************************************
    //                            File sending methods
    // ********************************************************************************

    /**
     * Combines the benefits of JDAs RestAction#queue and returning a Future that can be get() if necessary
     *
     * @param channel   The channel that should be messaged
     * @param file      File to be sent
     * @param message   Optional message
     * @param onSuccess Optional success handler
     * @param onFail    Optional exception handler
     * @return Future that can be waited on in case the code requires completion. Similar to JDA's RestAction#complete,
     * avoid usage where not absolutely needed.
     */
    public static MessageFuture sendFile(@Nonnull MessageChannel channel, @Nonnull File file, @Nullable Message message,
                                         @Nullable Consumer<Message> onSuccess, @Nullable Consumer<Throwable> onFail) {
        return sendFile0(
                channel,
                file,
                message,
                onSuccess,
                onFail
        );
    }

    public static MessageFuture sendFile(@Nonnull MessageChannel channel, @Nonnull File file, @Nullable Message message,
                                         @Nullable Consumer<Message> onSuccess) {
        return sendFile0(
                channel,
                file,
                message,
                onSuccess,
                null
        );
    }

    public static MessageFuture sendFile(@Nonnull MessageChannel channel, @Nonnull File file, @Nullable Message message) {
        return sendFile0(
                channel,
                file,
                message,
                null,
                null
        );
    }

    public static MessageFuture sendFile(@Nonnull MessageChannel channel, @Nonnull File file,
                                         @Nullable Consumer<Message> onSuccess, @Nullable Consumer<Throwable> onFail) {
        return sendFile0(
                channel,
                file,
                null,
                onSuccess,
                onFail
        );
    }

    public static MessageFuture sendFile(@Nonnull MessageChannel channel, @Nonnull File file,
                                         @Nullable Consumer<Message> onSuccess) {
        return sendFile0(
                channel,
                file,
                null,
                onSuccess,
                null
        );
    }

    public static MessageFuture sendFile(@Nonnull MessageChannel channel, @Nonnull File file) {
        return sendFile0(
                channel,
                file,
                null,
                null,
                null
        );
    }


    // ********************************************************************************
    //                            Message editing methods
    // ********************************************************************************

    /**
     * Combines the benefits of JDAs RestAction#queue and returning a Future that can be get() if necessary
     *
     * @param oldMessage The message to be edited
     * @param newMessage The message to be set
     * @param onSuccess  Optional success handler
     * @param onFail     Optional exception handler
     * @return Future that can be waited on in case the code requires completion. Similar to JDA's RestAction#complete,
     * avoid usage where not absolutely needed.
     */
    public static MessageFuture editMessage(@Nonnull Message oldMessage, @Nonnull Message newMessage,
                                            @Nullable Consumer<Message> onSuccess, @Nullable Consumer<Throwable> onFail) {
        return editMessage0(
                oldMessage.getChannel(),
                oldMessage.getIdLong(),
                newMessage,
                onSuccess,
                onFail
        );
    }

    public static MessageFuture editMessage(@Nonnull Message oldMessage, @Nonnull Message newMessage) {
        return editMessage0(
                oldMessage.getChannel(),
                oldMessage.getIdLong(),
                newMessage,
                null,
                null
        );
    }

    public static MessageFuture editMessage(@Nonnull Message oldMessage, @Nonnull String newContent) {
        return editMessage0(
                oldMessage.getChannel(),
                oldMessage.getIdLong(),
                from(newContent),
                null,
                null
        );
    }


    public static MessageFuture editMessage(@Nonnull MessageChannel channel, long oldMessageId, @Nonnull Message newMessage,
                                            @Nullable Consumer<Message> onSuccess, @Nullable Consumer<Throwable> onFail) {
        return editMessage0(
                channel,
                oldMessageId,
                newMessage,
                onSuccess,
                onFail
        );
    }

    public static MessageFuture editMessage(@Nonnull MessageChannel channel, long oldMessageId, @Nonnull Message newMessage) {
        return editMessage0(
                channel,
                oldMessageId,
                newMessage,
                null,
                null
        );
    }

    public static MessageFuture editMessage(@Nonnull MessageChannel channel, long oldMessageId, @Nonnull String newContent) {
        return editMessage0(
                channel,
                oldMessageId,
                from(newContent),
                null,
                null
        );
    }

    // ********************************************************************************
    //                   Miscellaneous messaging related methods
    // ********************************************************************************

    public static void sendTyping(MessageChannel channel) {
        try {
            LogTheStackException stackTrace = LogTheStackException.createStackTrace();
            channel.sendTyping().queue(null, t -> stackTraceOnFail(t, stackTrace));
        } catch (InsufficientPermissionException e) {
            handleInsufficientPermissionsException(channel, e);
        }
    }

    //messages must all be from the same channel
    public static void deleteMessages(Collection<Message> messages) {
        LogTheStackException stackTrace = LogTheStackException.createStackTrace();
        if (!messages.isEmpty()) {
            MessageChannel channel = messages.iterator().next().getChannel();
            if (channel instanceof TextChannel) {
                try {
                    ((TextChannel) channel).deleteMessages(messages).queue(null, t -> stackTraceOnFail(t, stackTrace));
                } catch (InsufficientPermissionException e) {
                    handleInsufficientPermissionsException(channel, e);
                }
            } else {
                messages.forEach(m -> deleteMessageById(channel, m.getIdLong()));
            }
        }
    }

    public static void deleteMessage(Message message) {
        deleteMessageById(message.getChannel(), message.getIdLong());
    }

    public static void deleteMessageById(MessageChannel channel, long messageId) {
        LogTheStackException stackTrace = LogTheStackException.createStackTrace();
        try {
            channel.deleteMessageById(messageId).queue(null, t -> stackTraceOnFail(t, stackTrace));
        } catch (InsufficientPermissionException e) {
            handleInsufficientPermissionsException(channel, e);
        }
    }

    public static EmbedBuilder addFooter(EmbedBuilder eb, Member author) {
        return eb.setFooter(author.getEffectiveName(), author.getUser().getAvatarUrl());
    }


    // ********************************************************************************
    //                           Class internal methods
    // ********************************************************************************

    //class internal message sending method
    private static MessageFuture sendMessage0(@Nonnull MessageChannel channel, @Nonnull Message message,
                                              @Nullable Consumer<Message> onSuccess, @Nullable Consumer<Throwable> onFail) {
        if (channel == null) {
            throw new IllegalArgumentException("Channel is null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message is null");
        }

        MessageFuture result = new MessageFuture();
        Consumer<Message> successWrapper = getDefaultSuccessWrapper(result, onSuccess);
        Consumer<Throwable> failureWrapper = getDefaultFailureWrapper(result, onFail, LogTheStackException.createStackTrace());

        try {
            channel.sendMessage(message).queue(successWrapper, failureWrapper);
        } catch (InsufficientPermissionException e) {
            failureWrapper.accept(e);
            //do not call CentralMessaging#handleInsufficientPermissionsException() from here as that will result in a loop
            log.warn("Could not send message to channel {} due to missing permission {}", channel.getIdLong(), e.getPermission().getName(), e);
        }
        return result;
    }

    //class internal file sending method
    private static MessageFuture sendFile0(@Nonnull MessageChannel channel, @Nonnull File file, @Nullable Message message,
                                           @Nullable Consumer<Message> onSuccess, @Nullable Consumer<Throwable> onFail) {
        if (channel == null) {
            throw new IllegalArgumentException("Channel is null");
        }
        if (file == null) {
            throw new IllegalArgumentException("File is null");
        }

        MessageFuture result = new MessageFuture();
        Consumer<Message> successWrapper = getDefaultSuccessWrapper(result, onSuccess);
        Consumer<Throwable> failureWrapper = getDefaultFailureWrapper(result, onFail, LogTheStackException.createStackTrace());

        try {
            channel.sendFile(file, message).queue(successWrapper, failureWrapper);
        } catch (InsufficientPermissionException e) {
            failureWrapper.accept(e);
            handleInsufficientPermissionsException(channel, e);
        }
        return result;
    }

    //class internal editing method
    private static MessageFuture editMessage0(@Nonnull MessageChannel channel, long oldMessageId, @Nonnull Message newMessage,
                                              @Nullable Consumer<Message> onSuccess, @Nullable Consumer<Throwable> onFail) {
        if (channel == null) {
            throw new IllegalArgumentException("Channel is null");
        }
        if (newMessage == null) {
            throw new IllegalArgumentException("New message is null");
        }

        MessageFuture result = new MessageFuture();
        Consumer<Message> successWrapper = getDefaultSuccessWrapper(result, onSuccess);
        Consumer<Throwable> failureWrapper = getDefaultFailureWrapper(result, onFail, LogTheStackException.createStackTrace());

        try {
            channel.editMessageById(oldMessageId, newMessage).queue(successWrapper, failureWrapper);
        } catch (InsufficientPermissionException e) {
            failureWrapper.accept(e);
            handleInsufficientPermissionsException(channel, e);
        }
        return result;
    }

    private static void handleInsufficientPermissionsException(MessageChannel channel, InsufficientPermissionException e) {
        final ResourceBundle i18n;
        if (channel instanceof TextChannel) {
            i18n = I18n.get(((TextChannel) channel).getGuild());
        } else {
            i18n = I18n.DEFAULT.getProps();
        }
        sendMessage(channel, i18n.getString("permissionMissingBot") + " " + e.getPermission().getName());
    }

    private static <T> Consumer<T> getDefaultSuccessWrapper(@Nonnull CompletableFuture<T> result,
                                                            @Nullable Consumer<T> onSuccess) {
        return m -> {
            result.complete(m);
            if (onSuccess != null) {
                onSuccess.accept(m);
            }
        };
    }

    //wraps a consumer of throwables (used in queue()s) to complete a future and optionally show a proper stacktrace
    //NOTE: the throwble sent to this consumer may not have an initialized cause
    private static Consumer<Throwable> getDefaultFailureWrapper(@Nonnull CompletableFuture result,
                                                                @Nullable Consumer<Throwable> wrapThisOne,
                                                                @Nullable LogTheStackException stackTrace) {
        return t -> {
            stackTraceOnFail(t, stackTrace);
            result.completeExceptionally(t);
            if (wrapThisOne != null) {
                wrapThisOne.accept(t);
            }
        };
    }

    //link two exceptions together. make sure the passed Throwable t has no initialized cause
    private static void stackTraceOnFail(@Nonnull Throwable t, @Nullable LogTheStackException ex) {
        if (ex != null) {
            ex.initCause(t);
            log.error("queue() threw exception:", ex);
        } else {
            log.error("queue() threw exception:", t);
        }
    }
}
