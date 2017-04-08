package meth

import java.time._

import meth.data._

object filter {
  sealed trait Filter {
    import Filter._

    def &&(n: Filter): Filter = (this, n) match {
      case (And(fs), And(os)) => And(fs ++ os)
      case (And(fs), _) => And(fs :+ n)
      case (_, And(os)) => And(os :+ this)
      case _ => And(Seq(this, n))
    }
    def ||(n: Filter): Filter = (this, n) match {
      case (Or(fs), Or(os)) => Or(fs ++ os)
      case (Or(fs), _) => Or(fs :+ n)
      case (_, Or(os)) => Or(os :+ this)
      case _ => Or(Seq(this, n))
    }
    def negate: Filter = this match {
      case Not(f) => f
      case _ => Not(this)
    }
    def unary_! = negate

    def as[A](implicit conv: Interpreter[A]): A = conv(this)
  }

  type Interpreter[A] = Filter => A

  type Predicate = TvShow => Boolean

  object Filter {
    case class FieldContains(field: Field, word: String) extends Filter
    case class DayOfWeekIs(dow: DayOfWeek) extends Filter
    case class FieldIs(field: Field, word: String) extends Filter
    case class LongerThan(min: Int) extends Filter
    case class ShorterThan(min: Int) extends Filter
    case class Not(filter: Filter) extends Filter
    case class Or(filters: Seq[Filter]) extends Filter
    case class And(filters: Seq[Filter]) extends Filter
    case object True extends Filter

    sealed trait Field {
      def is(word: String) = FieldIs(this, word)
      def ===(word: String) = is(word)
      def contains(word: String) = FieldContains(this, word)
      def =%=(word: String) = contains(word)
    }
    case object Station extends Field
    case object Subject extends Field
    case object Title extends Field
    case object Description extends Field
    case object Date extends Field
    case object Time extends Field
    case object New extends Field


    implicit val predicate: Interpreter[Predicate] = _ match {
      case FieldContains(field, word) => field match {
        case Station => _.station.toLowerCase.contains(word.toLowerCase)
        case Subject => _.subject.toLowerCase.contains(word.toLowerCase)
        case Title => _.title.toLowerCase.contains(word.toLowerCase)
        case Description => _.description.toLowerCase.contains(word.toLowerCase)
        case Date => _.date.exists(_.toString contains word)
        case Time => _.time.exists(_.toString contains word)
        case New => _.isNew.toString startsWith word.toLowerCase
      }
      case FieldIs(field, word) => field match {
        case Station => _.station.toLowerCase == word.toLowerCase
        case Subject => _.subject.toLowerCase == word.toLowerCase
        case Title => _.title.toLowerCase == word.toLowerCase
        case Description => _.description.toLowerCase == word.toLowerCase
        case Date => _.date.exists(_.toString == word)
        case Time => _.time.exists(_.toString == word)
        case New => _.isNew == word.toBoolean
      }
      case DayOfWeekIs(dow) => _.date.map(_.getDayOfWeek) == Some(dow)
      case LongerThan(minutes) => _.duration.exists(_.toMinutes > minutes)
      case ShorterThan(minutes) => _.duration.exists(_.toMinutes < minutes)
      case Not(f) => predicate(f).andThen(b => !b)
      case Or(fs) => s => fs.map(predicate).foldLeft(false){ (b, p) => b || p(s) }
      case And(fs) => s => fs.map(predicate).foldLeft(true){ (b, p) => b && p(s) }
      case True => s => true
    }

    def query(q: Seq[String]): Filter =
      if (q.forall(_.trim.isEmpty)) True
      else Parser.parse(q.mkString(" "))
  }

  object Parser {
    import fastparse.all._
    import Filter._

    val fieldNames = Map[String, Field](
      "station" -> Station,
      "subject" -> Subject,
      "title" -> Title,
      "description" -> Description,
      "date" -> Date,
      "time" -> Time,
      "new" -> New
    )

    val field: P[Field] = P(StringIn(fieldNames.keySet.toSeq: _*).!.map(fieldNames))

    def stringEscape(escapeChar: Char, stopChars: Seq[Char]): P[String] = {
      val p = P(escapeChar.toString ~ AnyChar.!)
      val q = P(!CharIn(stopChars :+ escapeChar) ~ AnyChar).rep(1)
      P(p | q.!).rep.map(_.mkString)
    }

    def quotedString(quote: Char, escapeChar: Char = '\\'): P[String] = {
      val str = stringEscape(escapeChar, Seq(quote))
      P(quote.toString ~/ str ~ quote.toString)
    }

    val string = (quotedString('"') | P((!CharIn(" ()<>") ~ AnyChar).rep.!)).filter(_.trim.nonEmpty)

    val fieldContains: P[Filter] = P(field ~ ":" ~ string).map {
      case (f, v) => f =%= v
    }

    val duration: P[Filter] = P(("<"|">").! ~ (CharIn('0' to '9').rep(1).!.map(_.toInt))).map {
      case (comp, v) => if (comp == "<") ShorterThan(v) else LongerThan(v)
    }

    val contains: P[Filter] = P(string.map { v =>
      Title =%= v || Subject =%= v
    })

    val dow: P[Filter] = P("dow:" ~ StringIn("sun", "mon", "tue", "wed", "thu", "fri", "sat").!).map {s =>
      val day = java.time.DayOfWeek.values.find(_.name.startsWith(s.toUpperCase)).get
      DayOfWeekIs(day)
    }

    val simpleFilter: P[Filter] = fieldContains | dow | duration | contains

    def and: P[Filter] = P("(&" ~ " ".rep ~ filter.rep(1, sep = " ") ~ " ".rep ~ ")").map(And(_))

    def or: P[Filter] = P("(|" ~ " ".rep ~ filter.rep(1, sep = " ") ~ " ".rep ~ ")").map(Or(_))

    def not: P[Filter] = P("-" ~ filter).map(f => f.negate)

    def filter: P[Filter] = and | or | not | simpleFilter

    def parse(q: String): Filter = {
      val s = if (!q.trim.startsWith("(")) s"(& ${q.trim} )" else q.trim
      filter.parse(s).fold(
        (p, idx, extra) => sys.error(s"Cannot read query '$q' at index $idx"),
        (f, _) => f
      )
    }
  }
}
