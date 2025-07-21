package io.ybigta.text2sql.infer.cli

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.pattern.color.ANSIConstants
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase

internal class ColorLogConverter : ForegroundCompositeConverterBase<ILoggingEvent>() {
    override fun getForegroundColorCode(event: ILoggingEvent?): String = when (event?.level?.levelInt) {
        Level.ERROR.toInt() -> ANSIConstants.RED_FG
        Level.WARN.toInt() -> ANSIConstants.YELLOW_FG
        Level.INFO.toInt() -> ANSIConstants.GREEN_FG
        Level.DEBUG.toInt() -> ANSIConstants.MAGENTA_FG
        Level.TRACE.toInt() -> ANSIConstants.DEFAULT_FG
        else -> ANSIConstants.DEFAULT_FG
    }
}
