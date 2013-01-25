package squantlib.payoff

import scala.collection.JavaConversions._
import org.codehaus.jackson.JsonNode
import org.codehaus.jackson.map.ObjectMapper
import squantlib.util.DisplayUtils._
import squantlib.util.JsonUtils._
import squantlib.util.FormulaParser
import java.util.{Map => JavaMap}
import squantlib.util.VariableInfo

/**
 * Interprets JSON formula specification for sum of linear formulas with discrete range.
 * JSON format:
 *  {type:"putdi", variable:[String], trigger:[Double], strike:[Double], description:String}, 
 * No strike is considered as no low boundary
 */
case class PutDIPayoff(putVariables:List[String], trigger:List[Double], strike:List[Double], amount:Double = 1.0, description:String = null) 
extends Payoff {
  
	val variables = putVariables.toSet
  
	def getFixings(fixings:Map[String, Double]):Option[List[Double]] = 
	  if (variables.toSet subsetOf fixings.keySet) 
	    Some((0 to putVariables.size - 1).toList.map(i => fixings(putVariables(i))))
	  else None
	    
	override def price(fixings:Map[String, Double]) = 
	  getFixings(fixings) match {
	    case None => Double.NaN
	    case Some(fixValues) => 
	      if (fixValues.corresponds(trigger) {_ >= _}) amount
	      else amount * math.min(1.00, (fixValues, strike).zipped.map((v, k) => v/k).min)
	  }
	  
	override def price(fixing:Double)(implicit d:DummyImplicit) =
	  if (variables.size != 1) Double.NaN
	  else if (fixing >= trigger.head) amount
	  else amount * math.min(1.00, fixing / strike.head)
	
	override def toString =
	  amount.asPercent + " [" + trigger.mkString(",") + "] " + amount.asPercent + " x Min([" + variables.mkString(",") + "] / [" + strike.mkString(",") + "]"
	
	override def price = Double.NaN
	
	override def display(isRedemption:Boolean):String = {
 	  val varnames = putVariables.map(VariableInfo.namejpn)
	  val strikeMap = (putVariables, strike).zipped.map{case (v, k) => (VariableInfo.namejpn(v), VariableInfo.displayValue(v, k))}
	  val triggerMap = (putVariables, trigger).zipped.map{case (v, t) => (VariableInfo.namejpn(v), VariableInfo.displayValue(v, t))}
	  val multiple = variables.size > 1
	  
	  if (isRedemption){
	    List("最終参照日の" + varnames.mkString("、") + "によって、下記の金額で償還されます。", 
	        "・" + (if(multiple) "全ての参照指数" else varnames.head) + "がノックイン価格を上回っている場合 ： 額面 " + amount.asPercent,
	        "・" + (if(multiple) "いずれかの参照指数" else varnames.head) + "がノックイン価格を下回っている場合 ： ",
	        "  " + strikeMap.map{case (v, k) => "額面 x " + v + " / " + k}.mkString("、") + (if(multiple) "の低いほう" else ""),
	        "",
	        "ノックイン価格 ： " + triggerMap.map{case (v, k) => v + " ＝ " + k}.mkString("、"))
	        .mkString(sys.props("line.separator"))
	  }

	  else {
	    List("利率決定日の" + varnames.mkString("、") + "によって決定されます。", 
	        "・" + (if(multiple) "全ての参照指数" else varnames.head) + "がノックイン価格を上回っている場合 ： " + amount.asPercent + " (年率）",
	        "・" + (if(multiple) "いずれかの参照指数" else varnames.head) + "がノックイン価格を下回っている場合 ： ",
	        "  " + strikeMap.map{case (v, k) => amount.asPercent + " x " + v + " / " + k}.mkString("、") + (if(multiple) "の低いほう" else "")  + " (年率）",
	        "",
	        "ノックイン価格 ： " + triggerMap.map{case (v, k) => v + " ＝ " + k}.mkString("、"))
	        .mkString(sys.props("line.separator"))
	  }
	}
	
	override def jsonString = {
	  
	  val infoMap:JavaMap[String, Any] = Map(
	      "type" -> "putdi", 
	      "variable" -> putVariables.toArray, 
	      "trigger" -> trigger.toArray, 
	      "strike" -> strike.toArray, 
	      "description" -> description)
	  
	  (new ObjectMapper).writeValueAsString(infoMap)	  
	}	
	
}

object PutDIPayoff {
  
	def apply(node:String):PutDIPayoff = {
	  val variable:List[String] = node.jsonNode("variable") match {
	    case Some(n) if n isArray => n.map(s => s.asText).toList
	    case Some(n) if n isTextual => List(n.asText)
	    case _ => List.empty
	  }
	  
	  val trigger:List[Double] = node.jsonNode("trigger") match {
	    case Some(n) if n isArray => n.map(s => s.parseJsonDouble.getOrElse(Double.NaN)).toList
	    case Some(n) if n isDouble => List(n.parseJsonDouble.getOrElse(Double.NaN))
	    case _ => List.empty
	  }

	  val strike:List[Double] = node.jsonNode("strike") match {
	    case Some(n) if n isArray => n.map(s => s.parseJsonDouble.getOrElse(Double.NaN)).toList
	    case Some(n) if n isDouble => List(n.parseJsonDouble.getOrElse(Double.NaN))
	    case _ => List.empty
	  }
	  
	  val amount:Double = node.jsonNode("amount") match {
	    case Some(n) if n isDouble => n.parseJsonDouble.getOrElse(Double.NaN)
	    case _ => 1.0
	  }
	  
	  val description:String = node.parseJsonString("description")
	  PutDIPayoff(variable, trigger, strike, amount, description)
	}
  
}

