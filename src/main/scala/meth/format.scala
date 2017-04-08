package meth

object format {
  sealed trait Pattern
  case class Directive(name: String) extends Pattern
  case class Text(s: String) extends Pattern
  case class Condition(cond: Directive, p: Format) extends Pattern

  type Format = Seq[Pattern]

  object Format {
    def apply(fmt: String): Format = {
      require(fmt.nonEmpty, "fmt must not be empty")
      Parser.format.parse(fmt.trim).fold(
        (p, idx, extra) => sys.error(s"Error parsing format '$fmt' at index $idx"),
        (f, _) => f
      )
    }
  }

  trait Context extends (String => Option[String]) { self =>
    def ++(ctx: Context): Context = Context { k =>
      self(k).orElse(ctx(k))
    }
  }

  object Context {
    val empty: Context = Context(_ => None)
    def apply(f: String => Option[String]): Context = new Context {
      def apply(k: String) = f(k)
    }
    def from(f: PartialFunction[String, String]): Context =
      apply(f.lift)

    def from(map: Map[String, String]): Context = Context(map.get)
  }


  object syntax {
    implicit final class FormatOps(val fmt: Format) extends AnyVal {
      def format(ctx: Context): String =
        fmt.map({
          case Directive(name) => ctx(name) getOrElse ""
          case Text(s) => s
          case Condition(Directive(name), p) =>
            ctx(name).map(v => p.format(ctx)) getOrElse ""
        }).mkString
    }
  }

  object Parser {
    import fastparse.all._

    val ident = P(CharIn(('a' to 'z') ++ ('A' to 'Z') ++ ".").rep(1).!)
    val directive: P[Directive] = P("%[" ~/ ident ~ "]").map(Directive.apply)
    val text: P[Pattern] = P((!("%["|"%]") ~ AnyChar).rep(1).!).map(Text.apply)
    lazy val cond: P[Pattern] = P("%[" ~ ident ~ "|" ~ format ~ "%]").map {
      case (d, f) => Condition(Directive(d), f)
    }

    lazy val format: P[Format] = (cond | directive | text).rep(1)
  }
}
