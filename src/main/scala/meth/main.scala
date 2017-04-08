package meth

import scopt.OptionParser
import fs2.Task

object main {

  type Cmd = Seq[String] => Task[Unit]

  object Cmd {
    def apply[A](init: A)(f: A => Task[Unit])(implicit p: OptionParser[A]): Cmd =
      args => p.parse(args, init) match {
        case Some(cfg) => f(cfg)
        case None => Task.delay(())
      }

    def apply(t: Task[Unit]): Cmd = _ => t
  }

  lazy val cmds = Map(
    "help" -> help,
    "query" -> query.cmd,
    "play" -> play.cmd,
    "download" -> download.cmd,
    "update" -> update.cmd
  )

  def main(args: Array[String]): Unit = {
    val (name, rest) = args.headOption match {
      case Some(c) if cmds contains c => (args(0), args.tail)
      case _ => ("query", args)
    }
    cmds.get(name) match {
      case Some(cmd) => cmd(rest).unsafeRun
      case None => println(s"Command $name not found.")
    }
  }

  lazy val help: Cmd = Cmd(Task.delay {
    Option(getClass.getResource("/README.md")) match {
      case Some(url) =>
        io.Source.fromURL(url).getLines.foreach(println)
        println("\nUsage")
        println("------")
        println("Usage: meth <command> [options]\n")
        println("Commands:")
        cmds.keySet.toList.sorted.foreach(n => println(s"- $n"))
        println("\nOption `--help' displays help to each command")
      case None =>
        val wrap = string.wrapLines(80)_
        println("meth - a cli variant to mediathekview")
        println("")
        println("Usage: meth <command> [options]\n")
        println(wrap("The help file could not be found. Please see "+
          "https://github.com/eikek/meth for information."))
        println("\nCommands:")
        cmds.keySet.toList.sorted.foreach(n => println(s"- $n"))
        println("\nOption `--help' displays help to each command")
    }
  })
}
