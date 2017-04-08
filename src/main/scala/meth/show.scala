package meth

import java.time._
import java.time.format._

import meth.format.{Context, Format}
import meth.format.syntax._

object show {
  type Show[A] = A => String

  object Show {
    def fromFormat[A](fmt: Format, data: A => Context): Show[A] =
      a => fmt.format(data(a))
  }

  private val dateF = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
  private val timeF = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
  private val dateTimeF = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL)

  implicit val duration: Show[Duration] = d => {
    @annotation.tailrec
    def loop(dur: Duration, result: List[String]): String =
      result.size match {
        case 0 =>
          val n = dur.toHours
          loop(dur.minusHours(n), "%02d".format(n) :: result)
        case 1 =>
          val n = dur.toMinutes
          loop(dur.minusMinutes(n), "%02d".format(n) :: result)
        case 2 =>
          val n = dur.getSeconds
          ("%02d".format(n) :: result).reverse.mkString(":")
        case _ =>
          ""
      }
    loop(d,Nil)
  }

  implicit val _string: Show[String] = identity

  implicit val _int: Show[Int] = _.toString

  implicit val date: Show[LocalDate] = dateF.format(_)

  implicit val time: Show[LocalTime] = timeF.format(_)

  implicit val instant: Show[Instant] = i => dateTimeF.format(i.atZone(ZoneId.systemDefault))

  object syntax {
    implicit final class ShowSyntax[A](val a: A) extends AnyVal {
      def show(implicit s: Show[A]): String = s(a)
    }

    implicit final class ShowOptSyntax[A](val a: Option[A]) extends AnyVal {
      def showOr(default: => String)(implicit s: Show[A]): String = a.map(s).getOrElse(default)
    }
  }
}
