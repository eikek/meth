package meth

import java.time._
import java.time.format._

import scala.util.Try
import meth.show._
import meth.show.syntax._
import meth.string.syntax._

import Predef.{$conforms => _,_}

object data {

  case class Header1(
    createdLocal: String,
    createdUtc: String,
    listVersion: String,
    crawler: String) {

    private val dateF = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm X")
    def created: Instant = ZonedDateTime.parse(createdUtc+" Z", dateF).toInstant
  }

  final class TvShow(private val fields: Seq[String]) {
    require(fields.size == 20, s"fields (${fields.size}) is $fields")

    // (Sender, Thema, Titel, Datum, Zeit, Dauer, Größe [MB],
    // Beschreibung, Url, Website, Url Untertitel, Url RTMP, Url
    // Klein, Url RTMP Klein, Url HD, Url RTMP HD, DatumL, Url
    // History, Geo, neu)

    lazy val station: String = fields(0)
    lazy val subject: String = fields(1)
    lazy val title: String = fields(2)
    lazy val date: Option[LocalDate] = TvShow.parseDate(fields(3))
    lazy val time: Option[LocalTime] = TvShow.parseTime(fields(4))
    lazy val duration: Option[Duration] = TvShow.parseDuration(fields(5))
    lazy val sizeMb: Option[Int] = Try(fields(6).toInt).toOption
    lazy val description: String = fields(7)
    lazy val url: Option[String] = nonEmpty(fields(8))
    lazy val website: Option[String] = nonEmpty(fields(9))
    lazy val subtitleUrl: Option[String] = nonEmpty(fields(10))
    lazy val rtmpUrl: Option[String] = makeUrl(fields(11))
    lazy val smallUrl: Option[String] = makeUrl(fields(12))
    lazy val rtmpSmallUrl: Option[String] = makeUrl(fields(13))
    lazy val hdUrl: Option[String] = makeUrl(fields(14))
    lazy val rtmpHdUrl: Option[String] = makeUrl(fields(15))
    lazy val dateTime: Option[Instant] = Try(Instant.ofEpochSecond(fields(16).toLong)).toOption
    lazy val historyUrl = nonEmpty(fields(17))
    lazy val geo = fields(18)
    lazy val isNew = fields(19).toBoolean

    def bestUrl = hdUrl.orElse(url)
    def lowUrl = smallUrl.orElse(url)

    def setStation(s: String): TvShow =
      if (s == station) this
      else new TvShow(s +: fields.tail)

    private def makeUrl(urlPart: String): Option[String] =
      for {
        base <- url
        part <- nonEmpty(urlPart)
        (offset, file) <- Try {
          val split = part.split('|')
          (split(0).toInt, split(1))
        }.toOption
      } yield base.substring(0, offset) + file

    private def nonEmpty(s: String): Option[String] =
      Option(s).map(_.trim).filter(_.nonEmpty)

    override def toString(): String = fields.toString
    override def equals(other: Any): Boolean = other match {
      case s: TvShow => s.fields equals fields
      case _ => false
    }
    override def hashCode(): Int = fields.hashCode
  }

  object TvShow {
    private val dateF = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val timeF = DateTimeFormatter.ofPattern("HH:mm:ss")

    def normalizeFilename(s: String) = s.toLowerCase
      .replace(" - ", "-")
      .replaceAll("\\s+", "_")
      .replace("ä", "ae").replace("Ä", "Ae")
      .replace("ü", "ue").replace("Ü", "Ue")
      .replace("ö", "oe").replace("Ö", "Oe")
      .replace("ß", "ss")
      .replaceAll("[^a-zA-Z\\-_0-9\\.]", "")
      .replaceAll("^-", "")

    def parseDate(d: String) =
      if (d.trim.isEmpty) None
      else Some(LocalDate.parse(d.trim, dateF))

    def formatDate(ld: LocalDate): String =
      dateF.format(ld)

    def formatTime(lt: LocalTime): String =
      timeF.format(lt)

    def parseTime(t: String) =
      if (t.trim.isEmpty) None
      else Some(LocalTime.parse(t.trim, timeF))

    def parseDuration(d: String): Option[Duration] = {
      val parts = d.split(":")
      if (parts.length == 3) Try(Duration.ZERO.
        plusHours(parts(0).toLong).
        plusMinutes(parts(1).toLong).
        plusSeconds(parts(2).toLong)).toOption
      else None
    }

    def onelineFormat: Show[TvShow] = s => {
      val meta = s"<${s.duration.showOr("-:-:-")}, ${s.dateTime.showOr("")}>"
      val title = s"${string.when(s.subject.nonEmpty, s.subject+": ")}${s.title}"
      s"[${s.station.yellow}] ${title} ${meta.white}"
    }

    def detailFormat(width: Int): Show[TvShow] = s => {
      val w = width - 14
      s"""Station:      ${s.station}
         |Subject:      ${s.subject}
         |Title:        ${s.title}
         |Duration:     ${s.duration.showOr("-:-:-")}
         |Size (MB):    ${s.sizeMb.showOr("")}
         |Description:  ${s.description.wrapLines(w).indentLines2(14)}
         |Homepage:     ${s.website.showOr("")}
         |Aired:        ${s.dateTime.showOr("")}
         |URL:          ${s.url.showOr("")}
         |  Subtitle:   ${s.subtitleUrl.showOr("")}
         |  RTMP:       ${s.rtmpUrl.showOr("")}
         |  Small:      ${s.smallUrl.showOr("")}
         |  RTMP Small: ${s.rtmpSmallUrl.showOr("")}
         |  HD:         ${s.hdUrl.showOr("")}
         |  RTMP HD:    ${s.rtmpHdUrl.showOr("")}
         |  History:    ${s.historyUrl.showOr("")}
         |Region:       ${s.geo}
         |New:          ${s.isNew}""".stripMargin
    }

    def defaultContext(show: TvShow) = format.Context {
      case "station" => Some(show.station)
      case "title" => Some(show.title)
      case "subject" => Some(show.subject)
      case "year" => show.date.map(_.getYear.toString)
      case "month" => show.date.map(_.getMonth.toString)
      case "day" => show.date.map(_.getDayOfMonth.toString)
      case "hour" => show.time.map(_.getHour.toString)
      case "min" => show.time.map(_.getMinute.toString)
      case "date" => show.date.map(_.toString)
      case "time" => show.time.map(_.toString)
      case _ => None
    }

  }
}
