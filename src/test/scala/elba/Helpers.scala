package elba

import java.util.concurrent.atomic.AtomicInteger
import io.gatling.core.Predef._
import io.gatling.core.session.Expression

import io.gatling.core.structure.{Execs, StructureBuilder, ChainBuilder}
import io.gatling.core.validation.Success

object Helpers {
  val userNumberAttributeKey = "user_number"
  val incrementalId = new AtomicInteger

  implicit class EnhancedStructureBuilder[B <: io.gatling.core.structure.StructureBuilder[B]](sb: StructureBuilder[B]) {
    /**
      * Gives an execution chain a session attribute, "user_number".
      */
    def withUserNumber: B = sb.exec(session => session.set(userNumberAttributeKey, incrementalId.getAndIncrement.toString))

    /**
      * Allows you to create a side-effect which uses the session.
      * TODO: Probably better to make this function just a "log(X) since that's what I really wanted"
      */
    def sideEffect( f: Session => Any ): B = {
      sb.exec( session => {
        f(session)
        // TODO: This is godawful. There must be a better way
        session.set("blah", "blah")
      })
    }
  }

  /**
    * Allows a string to be passed anywhere that an expression is required. Literally.
    * Without running it through the expression evaluator.
    */
  implicit class EnhancedString(str: String) {
    def literally: Expression[String] = session => Success(str)
  }
}