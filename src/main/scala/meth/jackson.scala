package meth

import java.nio.file.Path
import com.fasterxml.jackson.core._
import fs2.{Chunk, Stream, Task}

object jackson {
  val factory = new JsonFactory()

  def tokenStream(jp: JsonParser): Iterator[JsonToken] = new Iterator[JsonToken] {
    var token: JsonToken = null
    def hasNext = {
      token = jp.nextToken
      token != null
    }
    def next = token
  }

  def parseNextMap(json: String, prep: JsonParser => Unit = _ => ()): Map[String, String] = {
    val jp = factory.createParser(json)
    prep(jp)

    case class State(level: Int = 0, field: List[String] = Nil)

    def loop(it: Iterator[JsonToken], state: State, result: Vector[(String, String)]): Vector[(String, String)] =
      if (!it.hasNext) result
      else it.next match {
        case JsonToken.END_OBJECT =>
          if (state.level == 1) result
          else loop(it, state.copy(field = state.field.tail, level = state.level -1), result)
        case JsonToken.START_OBJECT =>
          loop(it, state.copy(level = state.level +1), result)
        case JsonToken.FIELD_NAME =>
          if (state.level > state.field.size) {
            loop(it, state.copy(field = jp.getText :: state.field), result)
          } else {
            loop(it, state.copy(field = jp.getText :: state.field.tail), result)
          }
        case token if token.isScalarValue =>
          loop(it, state, result :+ (state.field.reverse.mkString(".") -> jp.getText))
        case _ =>
          loop(it, state, result)
      }
    loop(tokenStream(jp), State(), Vector()).toMap
  }


  def nextArray(jp: JsonParser): Seq[String] = {
    var result: Vector[String] = Vector.empty
    var token: JsonToken = jp.nextToken
    var start: Boolean = false
    while (token != null) {
      token match {
        case JsonToken.START_ARRAY =>
          start = true
          token = jp.nextToken
        case JsonToken.END_ARRAY =>
          token = null
        case _ if start =>
          result = result :+ jp.getText
          token = jp.nextToken
        case _ =>
          token = jp.nextToken
      }
    }
    result
  }

  def parseFilmlist(file: Path): Stream[Task, Seq[String]] = {
    val jp = factory.createParser(file.toFile)
    Stream.unfoldChunk(jp) { p =>
      val chunk = (for (i <- 1 to 300) yield nextArray(p)).filter(_.nonEmpty)
      Some((Chunk.seq(chunk), p)).filter(_._1.nonEmpty)
    }
  }

}
