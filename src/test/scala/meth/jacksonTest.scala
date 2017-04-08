package meth

import org.scalatest._

class jacksonTest extends FlatSpec with Matchers {

  "parseMap" should "work for simple maps" in {
    val m = meth.jackson.parseNextMap("""{"a": 1, "z": {"k":1}, "b": "c"}""")
    m should be (Map("a" -> "1", "z.k" -> "1", "b" -> "c"))
  }
}
