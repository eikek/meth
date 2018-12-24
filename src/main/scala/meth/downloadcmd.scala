package meth

import java.nio.file.{Files, Path}
import java.nio.file.StandardOpenOption.{APPEND, CREATE}
import java.nio.charset.StandardCharsets
import java.io.File
import scala.sys.process._
import fs2.{concurrent, Sink, Stream, Strategy, Task}
import scopt.OptionParser
import pureconfig._
import pureconfig.generic.auto._

import meth.data._
import meth.filter._
import meth.format.{Context, Format}
import meth.format.syntax._
import meth.string.syntax._
import meth.settings._
import meth.main.Cmd

object download {

  implicit def readPath: scopt.Read[Path] =
    scopt.Read.reads(s => java.nio.file.Paths.get(s))

  case class Config(program: String, options: Seq[String], defaultTarget: Path, parallel: Int, downloadLogFile: Path, skipSeen: Boolean)

  lazy val config = loadConfig[Config]("meth.download-cmd").get
  lazy val defaultFormat = Format("%[subject|%[subject]-%]%[title]-%[date]")

  case class Params(
    target: Path = config.defaultTarget
      , first: Option[Long] = None
      , skip: Option[Long] = None
      , parallel: Option[Int] = None
      , pattern: Option[String] = None
      , tvdbSeriesId: Option[String] = None
      , tvdbFirstAired: Boolean = false
      , query: Seq[String] = Seq.empty
      , normalize: Boolean = true
      , downloadLog: Path = config.downloadLogFile
      , skipSeen: Boolean = config.skipSeen) {

    lazy val tvdbEnabled = tvdbSeriesId.isDefined && tvdbFirstAired
  }

  object Params {
    implicit val parser = new OptionParser[Params]("download") {
      head("Download all tv shows from a given query.\n")
      help("help").text("Prints this help message")
      opt[File]("target").
        valueName("<directory>").
        action((f, cfg) => cfg.copy(target = f.toPath)).
        text("The target directory, default is cwd");

      opt[Long]("first").
        valueName("<n>").
        action((n, cfg) => cfg.copy(first = Some(n))).
        text("Only select first <n> entries")

      opt[Long]("skip").
        valueName("<n>").
        action((n, cfg) => cfg.copy(skip = Some(n))).
        text("Skip first <n> entries")

      opt[Int]("parallel").
        valueName("<n>").
        action((n, cfg) => cfg.copy(parallel = Some(n))).
        text("Run downloads in parallel. Default is in config file which is initially 1.".
          wrapLines(60).indentLines2(27)).
        validate(n => if (n <= 0) failure("n must be > 0") else success)

      opt[String]("pattern").
        action((p, cfg) => cfg.copy(pattern = Some(p))).
        text("A pattern for generating the output file")

      opt[String]("tvdb-seriesid").
        action((id, cfg) => cfg.copy(tvdbSeriesId = Some(id))).
        text(("This is required for searching thetvdb.com. It is the series that is to be "+
          "searched for a episode.").
          wrapLines(60).indentLines2(27))

      opt[Unit]("tvdb-firstaired").
        action((_, cfg) => cfg.copy(tvdbFirstAired = true)).
        text(("Search thetvdb.com for a tv show using the date as `firstAired' query and add "+
          "its properties to the set of pattern variables. This only applies if a `pattern' "+
          "is used and allows to use data like episodeNumber etc for generating the file name. "+
          "Currently this is the only supported search operation.").
          wrapLines(60).indentLines2(27))

      opt[Path]("download-log").
        action((p, cfg) => cfg.copy(downloadLog = p.toAbsolutePath)).
        valueName("<file>").
        text("Use a different download log file than the default (specified in config file).".
          wrapLines(60).indentLines2(27))

      opt[Boolean]("skip-seen").
        action((f, cfg) => cfg.copy(skipSeen = f)).
        valueName("true|false").
        text("Whether to skip already downloaded urls according to the download log file. Default is true.".
          wrapLines(60).indentLines2(27))

      opt[Boolean]("normalize").
        action((f, cfg) => cfg.copy(normalize = f)).
        valueName("true|false").
        text("Whether to normalize the target filename to contain ascii characters only. Default is true.".
          wrapLines(60).indentLines2(27))

      arg[String]("<query>").
        unbounded().
        action((x, cfg) => cfg.copy(query = cfg.query :+ x)).
        text("The query string.")

      checkConfig { cfg =>
        if (cfg.tvdbSeriesId.isEmpty && cfg.tvdbFirstAired) failure("A tvdb series-id is required")
        else success
      }
    }
  }

  def normalize(s: String) = s.toLowerCase
    .replace(" - ", "-")
    .replaceAll("\\s+", "_")
    .replace("ä", "ae").replace("Ä", "Ae")
    .replace("ü", "ue").replace("Ü", "Ue")
    .replace("ö", "oe").replace("Ö", "Oe")
    .replace("ß", "ss")
    .replaceAll("[^a-zA-Z\\-_0-9\\./]", "")
    .replaceAll("^-", "")

  def urlAlreadySeen(log: Path, url: String): Boolean =
    if (!Files.exists(log)) false
    else scala.io.Source.fromFile(log.toFile).getLines.toSet.contains(url)

  def addLog(log: Path, url: String): Unit = {
    if (!urlAlreadySeen(log, url)) {
      try {
        val bw = Files.newBufferedWriter(log, StandardCharsets.UTF_8, APPEND, CREATE)
        bw.write(url + "\n")
        bw.close
      } catch {
        case e: Exception =>
          Console.err.println(s"Could not append to download log: ${e.getMessage}")
      }
    }
  }

  /** Download a show using curl into `target' directory. */
  def downloadExtern(downloadLog: Path, skipSeen: Boolean, outFile: TvShow => Path)(show: TvShow): Task[Unit] = Task.delay {
    val out = outFile(show)
    if (Files.exists(out)) println(s"$out already exists")
    else show.bestUrl match {
      case Some(url) =>
        if (skipSeen && urlAlreadySeen(downloadLog, url)) {
          println(s"${show.title} ($url) already downloaded according to log.")
        } else {
          Files.createDirectories(out.getParent)
          println(s"Download '${show.title}' to $out …")
          val cmd = Seq(config.program) ++ config.options.map {
            case "%[url]" => url
            case "%[outfile]" => out.toString
            case "%[outdir]" => out.getParent.toString
            case s => s
          }

          Process(cmd, Some(out.getParent.toFile)).!!
          addLog(downloadLog, url)
        }
      case None =>
        println(s"No url available for '${show.title}'")
    }
  }

  def tvdbContext(params: Params, show: TvShow) = {
    if (params.tvdbEnabled) {
      val qp = Seq("firstAired" -> show.date.get.toString)
      Context.from(tvdb.searchEpisodes(params.tvdbSeriesId.get, qp: _*).unsafeRun)
    } else {
      Context.empty
    }
  }

  val cmd: Cmd = Cmd(Params()) { cfg =>
    Files.createDirectories(cfg.downloadLog.getParent)

    val parallel = cfg.parallel.getOrElse(config.parallel)
    val shows = movielist.get.
      filter(Filter.query(cfg.query).as[Predicate]).
      through(s => cfg.skip.map(s.drop).getOrElse(s)).
      through(s => cfg.first.map(s.take).getOrElse(s))

    val format = cfg.pattern.map(Format.apply).getOrElse(defaultFormat)
    val outFile: TvShow => Path = { show =>
      val ctx = TvShow.defaultContext(show) ++ tvdbContext(cfg, show)
      val fileName = (if (cfg.normalize) normalize(format.format(ctx)) else format.format(ctx)) +".mp4"
      cfg.target.toAbsolutePath.resolve(fileName)
    }

    val sink: Sink[Task, TvShow] = _.evalMap(downloadExtern(cfg.downloadLog, cfg.skipSeen, outFile))

    if (parallel <= 1) {
      (shows to sink).run
    } else {
      println(s"Downloading $parallel concurrently")
      implicit val S = Strategy.fromFixedDaemonPool(parallel)
      concurrent.join(parallel)(shows.map(Stream.emit).map(_.to(sink))).run
    }
  }
}
