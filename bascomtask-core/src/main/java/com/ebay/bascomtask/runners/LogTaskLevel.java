/*-**********************************************************************
 Copyright 2018 eBay Inc.
 Author/Developer: Brendan McCarthy

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 **************************************************************************/
package com.ebay.bascomtask.runners;

import org.slf4j.Logger;

/**
 * Provides missing log levels for slf4j.
 *
 * @author Brendan McCarthy
 */
public enum LogTaskLevel {
    INFO {
        public boolean isEnabled(Logger logger) {
            return logger.isInfoEnabled();
        }

        public void write(Logger logger, String msg) {
            logger.info(msg);
        }

        public void write(Logger logger, String format, Object arg) {
            logger.info(format, arg);
        }

        public void write(Logger logger, String format, Object arg1, Object arg2) {
            logger.info(format, arg1, arg2);
        }

        public void write(Logger logger, String format, Object... arguments) {
            logger.info(format, arguments);
        }

        public void write(Logger logger, String msg, Throwable t) {
            logger.info(msg, t);
        }
    },
    ERROR {
        public boolean isEnabled(Logger logger) {
            return logger.isErrorEnabled();
        }

        public void write(Logger logger, String msg) {
            logger.error(msg);
        }

        public void write(Logger logger, String format, Object arg) {
            logger.error(format, arg);
        }

        public void write(Logger logger, String format, Object arg1, Object arg2) {
            logger.error(format, arg1, arg2);
        }

        public void write(Logger logger, String format, Object... arguments) {
            logger.error(format, arguments);
        }

        public void write(Logger logger, String msg, Throwable t) {
            logger.error(msg, t);
        }
    },
    WARN {
        public boolean isEnabled(Logger logger) {
            return logger.isWarnEnabled();
        }

        public void write(Logger logger, String msg) {
            logger.warn(msg);
        }

        public void write(Logger logger, String format, Object arg) {
            logger.warn(format, arg);
        }

        public void write(Logger logger, String format, Object arg1, Object arg2) {
            logger.warn(format, arg1, arg2);
        }

        public void write(Logger logger, String format, Object... arguments) {
            logger.warn(format, arguments);
        }

        public void write(Logger logger, String msg, Throwable t) {
            logger.warn(msg, t);
        }
    },
    TRACE {
        public boolean isEnabled(Logger logger) {
            return logger.isTraceEnabled();
        }

        public void write(Logger logger, String msg) {
            logger.trace(msg);
        }

        public void write(Logger logger, String format, Object arg) {
            logger.trace(format, arg);
        }

        public void write(Logger logger, String format, Object arg1, Object arg2) {
            logger.trace(format, arg1, arg2);
        }

        public void write(Logger logger, String format, Object... arguments) {
            logger.trace(format, arguments);
        }

        public void write(Logger logger, String msg, Throwable t) {
            logger.trace(msg, t);
        }
    },
    DEBUG {
        public boolean isEnabled(Logger logger) {
            return logger.isDebugEnabled();
        }

        public void write(Logger logger, String msg) {
            logger.debug(msg);
        }

        public void write(Logger logger, String format, Object arg) {
            logger.debug(format, arg);
        }

        public void write(Logger logger, String format, Object arg1, Object arg2) {
            logger.debug(format, arg1, arg2);
        }

        public void write(Logger logger, String format, Object... arguments) {
            logger.debug(format, arguments);
        }

        public void write(Logger logger, String msg, Throwable t) {
            logger.debug(msg, t);
        }
    };

    public abstract boolean isEnabled(Logger logger);

    public abstract void write(Logger logger, String msg);

    public abstract void write(Logger logger, String format, Object arg);

    public abstract void write(Logger logger, String format, Object arg1, Object arg2);

    public abstract void write(Logger logger, String format, Object... arguments);

    public abstract void write(Logger logger, String msg, Throwable t);
}
