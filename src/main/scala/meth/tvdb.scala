package meth

import java.time.{Duration, Instant}
import java.nio.file.{Files}
import scalaj.http._
import fs2.Task

object tvdb {
  import settings._

  case class TvDbConfig(
    apiKey: String,
    userKey: String,
    username: String,
    baseUrl: String,
    tokenLifetime: Duration,
    acceptLanguage: String)

  val config = settings.load[TvDbConfig]("meth.thetvdb")

  def newToken: Task[String] =  {
    val f = settings.main.directory.resolve("thetvdb.token")
    val fromFile: Task[Option[String]] = Task.delay {
      lazy val notExpired = Files.getLastModifiedTime(f).
        toInstant.
        plus(config.tokenLifetime).
        isAfter(Instant.now)

      if (Files.exists(f) && notExpired) Some(new String(Files.readAllBytes(f)))
      else None
    }
    val fetchNew = Task.delay {
      val res = Http(s"${config.baseUrl}/login")
        .postData(s"""{"apikey": "${config.apiKey}", "username": "${config.username}", "userkey": "${config.userKey}"}""")
        .header("content-type", "application/json")
        .asString
        .body
      Files.write(f, res.getBytes)
      res
    }
    fromFile.
      flatMap(opt => opt.map(Task.now).getOrElse(fetchNew)).
      map(read.parseMap).
      map(_.apply("token"))
  }

  def queryEpisodes(token: String, seriesId: String, params: (String, String)*): Task[Map[String, String]] = Task.delay {
    val url = s"${config.baseUrl}/series/$seriesId/episodes/query"
    val req = Http(url)
      .header("Accept-Language", config.acceptLanguage)
      .header("Authorization", s"Bearer ${token}")
    val json = params.foldLeft(req)({ case (r, (k, v)) => r.param(k, v) })
      .asString
      .body
    read.parseResult(json)
  }

  def searchEpisodes(seriesId: String, params: (String, String)*) = {
    for {
      token <- newToken
      resp <- queryEpisodes(token, seriesId, params: _*)
    } yield resp
  }

  object read {
    def parseMap(json: String): Map[String, String] =
      jackson.parseNextMap(json)

    def parseResult(json: String): Map[String, String] = {
      jackson.parseNextMap(json).
        filter({ case (k, v) => k.startsWith("data.") && v.trim.nonEmpty }).
        map(t => (t._1.replace("data.", "tvdb."), t._2))
    }
  }
}
