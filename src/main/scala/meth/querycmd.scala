package meth

import scopt.OptionParser
import fs2.{Stream, Task}

import meth.main.Cmd
import meth.filter._
import meth.data._
import meth.show.syntax._
import meth.string.syntax._

object query {

  case class Params(
    detail: Boolean = false,
    skip: Option[Long] = None,
    first: Option[Long] = None,
    query: Seq[String] = Seq.empty)

  object Params {
    implicit val parser = new OptionParser[Params]("query") {
      help("help").text("Prints this help message.")
      opt[Unit]("detail").
        action((_, cfg) => cfg.copy(detail = true)).
        text("Show detailed information about each tv show.")

      opt[Long]("first").
        valueName("<n>").
        action((n, cfg) => cfg.copy(first = Some(n))).
        text("Only select first <n> entries")

      opt[Long]("skip").
        valueName("<n>").
        action((n, cfg) => cfg.copy(skip = Some(n))).
        text("Skip first <n> entries")

      arg[String]("<query>").
        unbounded()
        .optional().
        action((x, cfg) => cfg.copy(query = cfg.query :+ x)).
        text("The query string.")
    }
  }

  val cmd: Cmd = Cmd(Params()) { cfg =>
    implicit val _format =
      if (cfg.detail) TvShow.detailFormat(100)
      else TvShow.onelineFormat
    val separator = string.when(cfg.detail, "\n" + "–".repeat(100)) + "\n"
    movielist.get.
      filter(Filter.query(cfg.query).as[Predicate]).
      through(s => cfg.skip.map(s.drop).getOrElse(s)).
      through(s => cfg.first.map(s.take).getOrElse(s)).
      map(s => s.show).
      intersperse(separator).
      append(Stream("\n")).
      evalMap(s => Task.delay(print(s))).
      run
  }
}
