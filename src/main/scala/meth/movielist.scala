package meth

import java.nio.file.{Files, Path}
import java.time._
import java.time.format._
import org.tukaani.xz.XZInputStream

import fs2.{Pipe, Stream, Task}
import scalaj.http._

import meth.data._
import meth.string.syntax._

object movielist {
  private val lastmodFormat = DateTimeFormatter.RFC_1123_DATE_TIME
  private lazy val cfg = settings.main
  private lazy val listUrl = cfg.listUrl.unsafeRun

  def get: Stream[Task, TvShow] = {
    val prepare =
      if (cfg.autoDownload || !Files.exists(cfg.movieFile)) Stream.eval(getCurrentFile).drain
      else Stream.empty

    val post =
      if (cfg.autoDownload) Stream.empty
      else Stream.eval(checkCurrentFile).drain

    /** Copy the station from previous show, if current one is empty */
    def moveStation: Pipe[Task, TvShow, TvShow] = {
      s => s.mapAccumulate("")({
        case (_, show) if show.station.nonEmpty => (show.station, show)
        case (s, show) => (s, show.setStation(s))
      }).map(_._2)
    }

    prepare ++ jackson.parseFilmlist(cfg.movieFile).
      drop(2). // skip headers
      map(x => new TvShow(x)).
      through(moveStation) ++ post
  }

  def getCurrentFile: Task[Path] =
    for {
      currentXz <- isListCurrent(cfg.movieFileXz)
      file <- currentXz match {
        case true if Files.exists(cfg.movieFile) =>
          Task.now(cfg.movieFile)
        case true => unpackFile(cfg.movieFileXz, cfg.movieFile)
        case false =>  downloadList(cfg.movieFileXz).flatMap(f => unpackFile(f, cfg.movieFile))
      }
    } yield file


  def checkCurrentFile: Task[Unit] =
    isListCurrent(cfg.movieFileXz).flatMap {
      case true if Files.exists(cfg.movieFile) => Task.now(())
      case true => unpackFile(cfg.movieFileXz, cfg.movieFile).map(_ => ())
      case false if Files.exists(cfg.movieFile) => Task.delay {
        print("\nInfo: ".cyan)
        println("There is a newer filmlist version available.")
        println("Use `update' command to get the new file or set `meth.auto-download' to true.")
      }
      case false => Task.delay {
        print("\nWarn: ".red)
        println("There is no filmlist file. Will download now.")
      }
    }

  def isListCurrent(target: Path): Task[Boolean] = Task.delay {
    def isCurrent(resp: HttpResponse[_]): Boolean = {
      val listDateTime = getHeader.map(_.created).unsafeRun.plusSeconds(10 * 60)
      val lastmod = resp.header("Last-Modified").map(lastmodFormat.parse).map(Instant.from)
      val size = resp.header("Content-Length")
        (lastmod, size, Files.exists(target)) match {
        case (Some(ts), Some(len), true) =>
          len.toLong == Files.size(target) && ts.isBefore(listDateTime)
        case _ => false
      }
    }

    Files.exists(target) && isCurrent(Http(listUrl).
      option(HttpOptions.followRedirects(true)).method("HEAD").asString)
  }

  def getHeader: Task[Header1] =
    jackson.parseFilmlist(cfg.movieFile).
      take(1).
      map(seq => Header1(seq(0), seq(1), seq(2), seq(3))).
      runLast.
      map(_.get)

  def downloadList(target: Path): Task[Path] = Task.delay {
    print("Downloading movie list… ")
    Files.deleteIfExists(target)
    Http(listUrl).option(HttpOptions.followRedirects(true)).execute(in => {
      Files.copy(in, target)
    })
    println("ok")
    target
  }

  def unpackFile(infile: Path, outfile: Path): Task[Path] = Task.delay {
    print("Unpacking movie list file… ")
    val in = new XZInputStream(Files.newInputStream(infile))
    Files.deleteIfExists(outfile)
    Files.copy(in, outfile)
    println("ok")
    outfile
  }

  def getUnrecognizedEntries: Stream[Task, Seq[String]] =
    jackson.parseFilmlist(settings.main.movieFile).
      drop(2). // skip headers
      filter(_.size != 20)

}
