package meth

import java.nio.file.{Files, Path}
import java.time.Duration
import javax.xml.stream._
import java.io.StringReader
import com.typesafe.config.ConfigValue

import fs2.Task
import scalaj.http._
import pureconfig._, pureconfig.error._

object settings {

  lazy val main: Settings = load[Settings]("meth")

  def load[A](name: String)(implicit conv: ConfigConvert[A]): A =
    loadConfig[A](name) match {
      case Right(c) => c
      case Left(err) =>
        err.toList.foreach(e => println(e))
        sys.error("Reading configuraion failed.")
    }


  private implicit def hint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, KebabCase))

  implicit def durationConvert: ConfigConvert[Duration] = {
    val dc = implicitly[ConfigConvert[scala.concurrent.duration.Duration]]
    new ConfigConvert[Duration] {
      def from(v: ConfigValue): Either[ConfigReaderFailures, Duration] =
        dc.from(v).map(fd => Duration.ofNanos(fd.toNanos))

      def to(d: Duration): ConfigValue =
        dc.to(scala.concurrent.duration.Duration.fromNanos(d.toNanos))
    }
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
      val reader = XMLInputFactory.newInstance.createXMLStreamReader(
        new StringReader(Http(currentListXml).method("GET").asString.body))
      var result: List[(Int, String)] = Nil
      var stack: List[String] = Nil
      while (reader.hasNext) {
        reader.next() match {
          case XMLStreamConstants.END_ELEMENT =>
            if (reader.getLocalName == "Prio") {
              result = (stack.head.toInt, stack.tail.head) :: result
              stack = stack.tail.tail
            }

          case XMLStreamConstants.CHARACTERS  =>
            if (reader.getText.trim.nonEmpty) {
              stack = reader.getText :: stack
            }
          case _ =>
        }
      }

      (stack.map((0, _)) ++ result).
        map(_._2).
        find(url => Http(url).method("HEAD").asString.isSuccess).
        getOrElse(fallbackListUrl)
    }
  }
}
