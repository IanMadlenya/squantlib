package squantlib.montecarlo.payoff

import scala.collection.JavaConversions._
import org.codehaus.jackson.JsonNode
import org.codehaus.jackson.map.ObjectMapper

import DisplayUtils._
import JsonUtils._

/**
 * Interprets JSON formula specification for a fixed leg.
 * JSON format: {type:"fixed", description:XXX, payoff:double}
 * Natual format: 0.035 or "3.5%"
 */
case class FixedPayoff(val payoff:Option[Double], val description:String = null) extends Payoff {
  
	val variables:Set[String] = Set.empty
	 
	override def price(fixings:Map[String, Double]) = payoff
	override def price(fixing:Double)(implicit d:DummyImplicit) = payoff
	override def price = payoff
	
	override def toString = description textOr (payoff match {
	  case Some(v) => v.asPercent + " fixed"
	  case None => "not defined"
	})
	
}


object FixedPayoff {
  
	def apply(formula:String):FixedPayoff = 
	  if (formula.parseDouble.isDefined) FixedPayoff(formula.parseDouble)
	  else FixedPayoff(formula.parseJsonDouble("payoff"), formula.parseJsonString("description"))
	
	def apply(payoff:Double):FixedPayoff = FixedPayoff(Some(payoff))

}