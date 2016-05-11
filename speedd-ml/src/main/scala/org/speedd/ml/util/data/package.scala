package org.speedd.ml.util

import java.text.SimpleDateFormat

package object data {

  /**
    * Translates a date-time value into a unix time-stamp (in seconds) according to a
    * given date format for the date-times and a second short date-format in case some
    * date-time values are shorter (e.g. seconds are missing). Moreover, the unix
    * time-stamp can be shifted by a specified offset, translated into a time-stamp
    * relative to a given starting time-stamp and/or rounded to seconds (0, 15, 30, 45).
    *
    * @param dateTime a date-time string value
    * @param dateFormat a date format
    * @param dateFormatShort a short date format
    * @param offset an offset used to shift the produced time-stamp
    * @param startTs a starting time-stamp used to find the relative time-distance
    * @param round if true the date-time is rounded into seconds (0, 15, 30, 45)
    *
    * @return a unix time-stamp
    */
  def ts2UnixTS(dateTime: String, dateFormat: String, dateFormatShort: String,
                offset: Int = 0, startTs: Long = 0, round: Boolean = false) = {

    val validSeconds = Vector(0, 15, 30, 45)
    val components = dateTime.split(":")

    // Step 1. Round date to seconds 0, 15, 30 or 45
    val roundedDateTime =
      if (round && dateTime.trim.length == 19 && !validSeconds.contains(components.last.toInt)) {
        val replacement = validSeconds.minBy(v => math.abs(v - components.last.toInt))
        var result = StringBuilder.newBuilder
        for (i <- 0 until components.length - 1)
          result ++= components(i) + ":"
        result ++= replacement.toString
        result.result()
      }
      else dateTime

    // Step 2. Format date. In case not full date is available use short date format
    val simpleDateFormat =
      new SimpleDateFormat(
        if (roundedDateTime.trim.length == 19) dateFormat
        else dateFormatShort
      )

    // Step 3. Get unix time-stamp in seconds. If an offset is specified also shift the seconds by that offset
    val seconds = (simpleDateFormat.parse(roundedDateTime).getTime / 1000) + offset

    // Step 4. Return the result
    if (startTs > 0 && round)
      (startTs + ((seconds - startTs) / 15)).toInt
    else seconds.toInt
  }

}
