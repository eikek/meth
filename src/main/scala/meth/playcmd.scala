package meth

import java.nio.file.Paths
import scala.sys.process._
import fs2.Task
import scopt.OptionParser
import pureconfig._
import pureconfig.generic.auto._

import meth.data._
import meth.filter._
import meth.settings._
import meth.main.Cmd

object play {

  case class Config(program: String, options: Seq[String])

  lazy val config = loadConfig[Config]("meth.play-cmd").get

  case class Params(
    first: Option[Long] = None,
    skip: Option[Long] = None,
    query: Seq[String] = Seq.empty)

  object Params {
    implicit val parser = new OptionParser[Params]("download") {
      help("help").text("Prints this help message")

      opt[Long]("first").
        valueName("<n>").
        action((n, cfg) => cfg.copy(first = Some(n))).
        text("Only select first <n> entries")

      opt[Long]("skip").
        valueName("<n>").
        action((n, cfg) => cfg.copy(skip = Some(n))).
        text("Skip first <n> entries")

      arg[String]("<query>").
        unbounded().
        action((x, cfg) => cfg.copy(query = cfg.query :+ x)).
        text("The query string.")
    }
  }

  def play(show: TvShow): Task[Unit] = Task.delay {
    show.bestUrl match {
      case Some(url) =>
        println(s"Playing '${show.title}'")
        val cmd = Seq(config.program) ++ config.options.map(s => if (s == "%[url]") url else s)
        println(s"Using command: ${cmd.mkString(" ")}")
        Process(cmd, Some(Paths.get("").toAbsolutePath.toFile)).!!
      case None =>
        println(s"No url available for '${show.title}'")
    }
  }

  val cmd: Cmd = Cmd(Params()) { cfg =>
    movielist.get.
      filter(Filter.query(cfg.query).as[Predicate]).
      through(s => cfg.skip.map(s.drop).getOrElse(s)).
      through(s => cfg.first.map(s.take).getOrElse(s)).
      evalMap(play).
      run
  }

}
