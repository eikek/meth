package meth

import java.nio.file.{Files, Path, Paths}
import java.time.Duration

import fs2.Task
import pureconfig._
import pureconfig.error._
import pureconfig.ConvertHelpers._
import pureconfig.generic.auto._
import pureconfig.generic.ProductHint

object settings {
  private def setConfigFile(): Unit = {
    val f = Paths.get(System.getProperty("user.home"), ".config", "meth", "meth.conf")
    if (Files.exists(f) && System.getProperty("config.file") == null) {
      System.setProperty("config.file", f.toString)
    }
  }
  setConfigFile()

  lazy val main: Settings = loadConfig[Settings]("meth").get

  implicit class EitherOps[A](e: Either[ConfigReaderFailures, A]) {
    def get: A = e match {
      case Right(c) => c
      case Left(err) =>
        err.toList.foreach(e => println(e))
        sys.error("Reading configuraion failed.")
    }
  }

  implicit def hint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, KebabCase))

  implicit val pathConvert: ConfigReader[Path] = ConfigReader.fromString[Path](catchReadError(s =>
    if (s.isEmpty) Paths.get(".").toAbsolutePath
    else Paths.get(s)
  ))

  implicit val durationConvert: ConfigReader[Duration] = {
    val dc = implicitly[ConfigReader[scala.concurrent.duration.Duration]]
    dc.map(sd => Duration.ofNanos(sd.toNanos))
  }


  case class Settings(directory: Path, currentListXml: String, fallbackListUrl: String, autoDownload: Boolean) {
    if (!Files.exists(directory)) {
      Files.createDirectories(directory)
    }
    require(Files.isWritable(directory), s"Directory ${directory} is not writeable")

    val movieFileXz = directory.resolve("filmlist.xz")
    val movieFile = directory.resolve("filmlist")

    /** Choose a url for downloading the `filmlist` file. */
    val listUrl: Task[String] = Task.delay {
     fallbackListUrl
    }
  }
}
