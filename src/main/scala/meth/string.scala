package meth

import Console._

object string {

  def black(s: String): String = BLACK + s + RESET
  def blue(s: String): String = BLUE + s + RESET
  def cyan(s: String): String = CYAN + s + RESET
  def green(s: String): String = GREEN + s + RESET
  def magenta(s: String): String = MAGENTA + s + RESET
  def red(s: String): String = RED + s + RESET
  def white(s: String): String = WHITE + s + RESET
  def yellow(s: String): String = YELLOW + s + RESET

  def reversed(s: String): String = REVERSED + s + RESET
  def bold(s: String): String = BOLD + s + RESET
  def underlined(s: String): String = UNDERLINED + s + RESET

  private val ansiCodes = List(BLACK, BLUE, CYAN, GREEN,
    MAGENTA, RED, WHITE, YELLOW, REVERSED, BOLD, UNDERLINED,
    RESET)

  /** Length of `s` without counting ansi color codes */
  def length(s: String): Int = {
    ansiCodes.foldLeft(s)((r, c) => r.replace(c, "")).length
  }

  def when(p: Boolean, s: => String): String =
    if (p) s else ""

  @annotation.tailrec
  def repeat(n: Int, s: String, target: String = ""): String =
    if (n <= 0) target
    else repeat(n -1, s, target + s)

  def spaces(n: Int, target: String = ""): String =
    repeat(n, " ", target)

  def wrapLines(len: Int)(text: String): String = {
    def index(idx: (String, Int) => Int): String => Int = s =>
      idx(s, '\n') match { //wrap at newline if present
        case -1 => idx(s, ' ') // otherwise wrap at spaces
        case n => n
      }
    val searchBack: String => Int = index(_.lastIndexOf(_, len))
    val searchForward: String => Int = index(_.indexOf(_))
    @scala.annotation.tailrec
    def loop(start: Int, sepIndex: String => Int, result: StringBuilder): String =
      text.substring(start) match {
        case s if s.length() <= len => (result append s).toString
        case s => sepIndex(s) match {
          case -1 =>
            if (sepIndex eq searchForward) (result append s).toString
            else loop(start, searchForward, result)
          case n =>
            loop(start + n + 1, searchBack, result append s.substring(0, n) append '\n')
        }
      }

    loop(0, searchBack, new StringBuilder)
  }

  def prefixLines(text: String, target: String): String =
    target.split("\n").
      map(l => text + l).
      mkString("\n")

  def prefixLines2(text: String, target: String): String =
    target.indexOf('\n') match {
      case -1 => target
      case i =>
        target.substring(0, i) + prefixLines(text, target.substring(i))
    }


  object syntax {
    implicit final class StringFormatOps(val s: String) extends AnyVal {
      def wrapLines(n: Int) = string.wrapLines(n)(s)
      def when(b: Boolean) = string.when(b, s)
      def repeat(n: Int) = string.repeat(n, s)
      def prefixLines(text: String) = string.prefixLines(text, s)
      def prefixLines2(text: String) = string.prefixLines2(text, s)
      def indentLines(n: Int) = string.prefixLines(string.spaces(n), s)
      def indentLines2(n: Int) = string.prefixLines2(string.spaces(n), s)
      def underlined = string.underlined(s)
      def red = string.red(s)
      def yellow = string.yellow(s)
      def green = string.green(s)
      def blue = string.blue(s)
      def cyan = string.cyan(s)
      def magenta = string.magenta(s)
      def white = string.white(s)
    }
  }
}
