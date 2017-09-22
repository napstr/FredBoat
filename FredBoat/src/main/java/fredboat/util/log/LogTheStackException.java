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

package fredboat.util.log;

import fredboat.feature.togglz.FeatureFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Created by napster on 04.09.17.
 * <p>
 * This exception exists for the purpose of providing an anchor to the source of a call to queue()
 * NOTE: This does call the "expensive" {@link java.lang.Throwable#fillInStackTrace} method like all instantiations
 * of exceptions do. There is no way around that if we want the original stack trace up to the queue() call in case of
 * an exception happening during it's execution.
 * <p>
 * ===> Only use for debugging purposes <==
 * <p>
 * Make sure to {@link java.lang.Throwable#initCause(Throwable)} on any instance that you create of these.
 */
public class LogTheStackException extends RuntimeException {

    private static final Logger log = LoggerFactory.getLogger(LogTheStackException.class);

    private static final long serialVersionUID = -2900149378359307560L;

    private LogTheStackException() {
        super("Stack that lead to up to this:");
        log.warn("Created a LogTheStackException. Do not use it long term in production as it degrades performance.");
    }


    @Nullable
    public static LogTheStackException createStackTrace() {
        if (FeatureFlags.FULL_STACK_TRACES_FOR_QUEUED_REST_ACTIONS.isActive()) {
            return new LogTheStackException();
        } else {
            return null;
        }
    }

}
