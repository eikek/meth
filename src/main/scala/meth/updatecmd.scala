package meth

import scopt.OptionParser

import meth.main.Cmd

object update {

  case class Params()

  object Params {
    implicit val parser = new OptionParser[Params]("update") {
      head("Checks if a new filmlist is available and download it.\n")
      help("help").text("Displays this help message")
    }
  }

  val cmd = Cmd(Params()) { cfg =>
    movielist.getCurrentFile.map(_ => ())
  }
}
