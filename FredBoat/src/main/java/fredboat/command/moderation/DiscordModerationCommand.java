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

package fredboat.command.moderation;

import fredboat.command.info.HelpCommand;
import fredboat.commandmeta.abs.Command;
import fredboat.commandmeta.abs.CommandContext;
import fredboat.commandmeta.abs.IModerationCommand;
import fredboat.feature.I18n;
import fredboat.main.Launcher;
import fredboat.util.ArgumentUtil;
import fredboat.util.TextUtils;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.requests.restaction.AuditableRestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Created by napster on 27.01.18.
 * <p>
 * Base class for Kick, Ban, Softban commands, and possibly others
 * <p>
 * Conventions for these are as follows:
 * ;;command @memberMention audit reason with whitespace allowed
 * or
 * ;;command memberNameThatWillBeFuzzySearched audit reason with whitespace allowed
 * or
 * ;;command id snowflakeUserId audit reason with whitespace allowed
 *
 * @param <T> Class of the return value of the AuditableRestAction of this command
 */
public abstract class DiscordModerationCommand<T> extends Command implements IModerationCommand {

    private static final String ID = "id";
    private static final Logger log = LoggerFactory.getLogger(DiscordModerationCommand.class);

    protected DiscordModerationCommand(@Nonnull String name, String... aliases) {
        super(name, aliases);
    }

    /**
     * Returns the rest action to be issued by this command
     */
    @Nonnull
    protected abstract AuditableRestAction<T> modAction(@Nonnull ModActionInfo modActionInfo);

    /**
     * @return true if this mod action requires the target to be a member of the guild (example: kick). The invoker will
     * be informed about this requirement if their input does not yield a member of the guild
     */
    protected abstract boolean requiresMember();


    /**
     * Returns the success handler for the mod action
     */
    @Nonnull
    protected abstract Consumer<T> onSuccess(@Nonnull ModActionInfo modActionInfo);

    /**
     * Returns the failure handler for the mod action
     */
    @Nonnull
    protected abstract Consumer<Throwable> onFail(@Nonnull ModActionInfo modActionInfo);

    /**
     * Checks ourself, the context.invoker, and the target member for proper authorization, and replies with appropriate
     * feedback if any check fails. Some guild specific checks are only run if the target member is not null, assuming
     * that in that case a mod action is being performed on a user that is not a member of the guild (for example,
     * banning a user that left the guild)
     *
     * @return true if all checks are successful, false otherwise. In case the return value is false, the invoker has
     * received feedback as to why.
     */
    protected abstract boolean checkAuthorizationWithFeedback(@Nonnull ModActionInfo modActionInfo);

    /**
     * Parse the context of the command into a ModActionInfo.
     * If that is not possible, reply a reason to the user, and return null.
     * <p>
     * Can be overridden for custom behaviour.
     * <p>
     * NOTE: Since it may be necessary to poll ddoscord to identify a User, this method should not be called blocking.
     *
     * @return a ModActionInfo or null if it could not be parsed
     */
    @Nullable
    protected ModActionInfo parseModActionInfoWithFeedback(@Nonnull CommandContext context) {
        //Ensure we have a search term
        if (!context.hasArguments()) {
            HelpCommand.sendFormattedCommandHelp(context);
            return null;
        }

        //parsing a target
        long targetId;
        User targetUser;
        Member targetMember;
        if (context.args[0].equals(ID) && context.args.length > 1) {
            try {
                targetId = Long.parseUnsignedLong(context.args[1]);
            } catch (NumberFormatException e) {
                String helpMessage = context.i18nFormat("parseNotAValidId", "`" + TextUtils.escapeAndDefuse(context.args[1]) + "`");
                helpMessage += "\n" + context.i18nFormat("parseSnowflakeIdHelp", HelpCommand.LINK_DISCORD_DOCS_IDS);
                HelpCommand.sendFormattedCommandHelp(context, helpMessage);
                return null;
            }

            targetMember = context.guild.getMemberById(targetId);
            if (requiresMember() && targetMember == null) {
                context.reply(context.i18nFormat("parseNotAMember", "`" + TextUtils.escapeAndDefuse(context.args[1]) + "`"));
                return null;
            }

            if (targetMember != null) {
                targetUser = targetMember.getUser();
            } else {
                //fetch from whole bot
                targetUser = Launcher.getBotController().getJdaEntityProvider().getUserById(targetId);
                if (targetUser == null) {
                    //fetch from ddoscord
                    try {
                        targetUser = context.getGuild().getJDA()
                                .retrieveUserById(targetId).submit().get(1, TimeUnit.MINUTES);
                    } catch (InterruptedException | ExecutionException | TimeoutException e) {
                        log.warn("Failed to fetch a user from ddoscord");
                    }

                    if (targetUser == null) {
                        return null;
                    }
                }
            }
        } else {
            targetMember = ArgumentUtil.checkSingleFuzzyMemberSearchResult(context, context.args[0]);
            if (targetMember == null) {
                return null;
            }
            targetUser = targetMember.getUser();
        }

        return new ModActionInfo(context, targetMember, targetUser);
    }

    @Override
    public final void onInvoke(@Nonnull CommandContext context) {
        CompletableFuture.supplyAsync(() -> parseModActionInfoWithFeedback(context), Launcher.getBotController().getExecutor())
                .exceptionally(t -> {
                    log.error("Something went wrong parsing a mod action", t);
                    return null;
                })
                .thenAccept(modActionInfo -> {
                    if (modActionInfo == null) {
                        return;
                    }

                    if (!checkAuthorizationWithFeedback(modActionInfo)) return;

                    //putting together the action
                    modAction(modActionInfo).queue(onSuccess(modActionInfo), onFail(modActionInfo));
                });
    }

    @Nonnull
    protected static String getReasonForModAction(@Nonnull CommandContext context) {
        String r = null;
        if (context.args[0].equals(ID) && context.args.length > 2) { //forced selection by id, ignore first two args
            r = String.join(" ", Arrays.copyOfRange(context.args, 2, context.args.length));
        } else if (!context.args[0].equals(ID) && context.args.length > 1) { //ignore the first arg which contains the name/mention of the user
            r = String.join(" ", Arrays.copyOfRange(context.args, 1, context.args.length));
        }

        return context.i18n("modReason") + ": " + (r != null ? r : "No reason provided.");
    }

    @Nonnull
    protected static String formatReasonForAuditLog(@Nonnull String plainReason, @Nonnull Member invoker) {
        String i18nAuditLogMessage = MessageFormat.format(I18n.get(invoker.getGuild()).getString("modAuditLogMessage"),
                TextUtils.asString(invoker)) + ", ";
        int auditLogMaxLength = 512 - i18nAuditLogMessage.length(); //512 is a hard limit by discord
        return i18nAuditLogMessage + (plainReason.length() > auditLogMaxLength ?
                plainReason.substring(0, auditLogMaxLength) : plainReason);
    }


    //pass info between methods called inside this class
    protected static class ModActionInfo {

        @Nonnull
        public final CommandContext context;
        @Nullable
        public final Member targetMember;
        @Nonnull
        public final User targetUser; // may be a fake user, so do not DM them.
        @Nonnull
        public final String plainReason; //not suitable for usage in discord audit logs, use getFormattedReason() instead

        public ModActionInfo(@Nonnull CommandContext context, @Nullable Member targetMember, @Nonnull User targetUser) {
            this.context = context;
            this.targetMember = targetMember;
            this.targetUser = targetUser;
            this.plainReason = getReasonForModAction(context);
        }

        //suitable for usage in discord audit logs
        public String getFormattedReason() {
            return formatReasonForAuditLog(this.plainReason, this.context.invoker);
        }

        public String targetAsString() {
            if (this.targetMember != null) {
                return TextUtils.asString(this.targetMember);
            } else {
                return TextUtils.asString(this.targetUser);
            }
        }
    }
}
