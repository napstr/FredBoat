/*
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
import fredboat.commandmeta.abs.CommandContext;
import fredboat.feature.metrics.Metrics;
import fredboat.messaging.CentralMessaging;
import fredboat.messaging.internal.Context;
import fredboat.util.DiscordUtil;
import fredboat.util.TextUtils;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.requests.restaction.AuditableRestAction;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * Created by napster on 19.04.17.
 * <p>
 * Ban a user.
 */
public class HardbanCommand extends DiscordModerationCommand<Void> {

    public HardbanCommand(String name, String... aliases) {
        super(name, aliases);
    }

    @Nonnull
    @Override
    protected AuditableRestAction<Void> modAction(@Nonnull ModActionInfo modActionInfo) {
        return modActionInfo.context.guild.getController()
                .ban(modActionInfo.targetUser, 7, modActionInfo.getFormattedReason());
    }

    @Override
    protected boolean requiresMember() {
        return false;
    }

    @Nonnull
    @Override
    protected Consumer<Void> onSuccess(@Nonnull ModActionInfo modActionInfo) {
        String successOutput = modActionInfo.context.i18nFormat("hardbanSuccess",
                modActionInfo.targetUser.getAsMention() + " " + TextUtils.escapeAndDefuse(modActionInfo.targetAsString()))
                + "\n" + TextUtils.escapeAndDefuse(modActionInfo.plainReason);

        return aVoid -> {
            Metrics.successfulRestActions.labels("ban").inc();
            modActionInfo.context.replyWithName(successOutput);
        };
    }

    @Nonnull
    @Override
    protected Consumer<Throwable> onFail(@Nonnull ModActionInfo modActionInfo) {
        String escapedTargetName = TextUtils.escapeAndDefuse(modActionInfo.targetAsString());
        //noinspection Duplicates
        return t -> {
            if (t instanceof IllegalArgumentException) { //banned nonexistent user by id, see GuildController#ban(String, int, String)
                String reply = modActionInfo.context.i18nFormat("parseNotAUser", "`" + modActionInfo.targetUser.getId() + "`");
                reply += "\n" + modActionInfo.context.i18nFormat("parseSnowflakeIdHelp", HelpCommand.LINK_DISCORD_DOCS_IDS);
                modActionInfo.context.reply(reply);
                return;
            }
            CentralMessaging.getJdaRestActionFailureHandler(String.format("Failed to ban user %s in guild %s",
                    escapedTargetName, modActionInfo.context.guild.getId())).accept(t);
            modActionInfo.context.replyWithName(modActionInfo.context.i18nFormat("modBanFail",
                    modActionInfo.targetUser.getAsMention() + " " + escapedTargetName));
        };
    }

    @Override
    protected boolean checkAuthorizationWithFeedback(@Nonnull ModActionInfo modActionInfo) {
        CommandContext context = modActionInfo.context;
        Member target = modActionInfo.targetMember;
        Member mod = context.invoker;

        if (!context.checkInvokerPermissionsWithFeedback(Permission.BAN_MEMBERS)) {
            return false;
        }
        if (!context.checkSelfPermissionsWithFeedback(Permission.BAN_MEMBERS)) {
            return false;
        }
        if (target == null) {
            return true;
        }


        if (mod == target) {
            context.replyWithName(context.i18n("hardbanFailSelf"));
            return false;
        }

        if (target.isOwner()) {
            context.replyWithName(context.i18n("hardbanFailOwner"));
            return false;
        }

        if (target == target.getGuild().getSelfMember()) {
            context.replyWithName(context.i18n("hardbanFailMyself"));
            return false;
        }

        if (DiscordUtil.getHighestRolePosition(mod) <= DiscordUtil.getHighestRolePosition(target) && !mod.isOwner()) {
            context.replyWithName(context.i18nFormat("modFailUserHierarchy", TextUtils.escapeAndDefuse(target.getEffectiveName())));
            return false;
        }

        if (DiscordUtil.getHighestRolePosition(mod.getGuild().getSelfMember()) <= DiscordUtil.getHighestRolePosition(target)) {
            context.replyWithName(context.i18nFormat("modFailBotHierarchy", TextUtils.escapeAndDefuse(target.getEffectiveName())));
            return false;
        }

        return true;
    }


    @Nonnull
    @Override
    public String help(@Nonnull Context context) {
        return "{0}{1} <user> <reason>\n{0}{1} id <userid> <reason>\n#" + context.i18n("helpHardbanCommand");
    }
}

