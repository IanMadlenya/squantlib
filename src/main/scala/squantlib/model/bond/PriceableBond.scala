package squantlib.model.bond

import squantlib.util.Date
import squantlib.database.schemadefinitions.{Bond => dbBond}
import squantlib.util.{SimpleCache, FormulaParser}
import squantlib.pricing.model.PricingModel
import squantlib.schedule.call.Callabilities
import squantlib.schedule.payoff.{Payoff, Payoffs}
import squantlib.schedule.{ScheduledPayoffs, CalculationPeriod}
import squantlib.model.market.Market
import squantlib.pricing.model.NoModel
import org.jquantlib.currencies.Currency


trait PriceableBond extends BondModel with Priceable {

  override val db:dbBond
  
  override val scheduledPayoffs:ScheduledPayoffs
  
  override val underlyings:List[String] 
  
  def copy(newdb:dbBond, newSchedule:ScheduledPayoffs, newuls:List[String]):PriceableBond
  
  override def copy:PriceableBond = copy(db, scheduledPayoffs, underlyings)
	
  var defaultModel:(Market, PriceableBond) => Option[PricingModel] = null
  
  var forceModel:Boolean = false
  
  var useCouponAsYield:Boolean = false
  
  var requiresCalibration:Boolean = false
  
  var modelCalibrated:Boolean = false
  
  var _market:Option[Market] = None
  
  var model:Option[PricingModel] = None
		
  override def toString:String = id
  
  /* 
   * Reset model
   */
  override def initializeModel(reCalibrate:Boolean = false):Unit = {
    if (reCalibrate) {calibrationCache.clear; modelCalibrated = false}
    
    model = if (isTerminated == Some(false) && scheduledPayoffs.isPriceable) livePayoffs match {
      case po if !po.isEmpty && !forceModel && po.isFixed => Some(NoModel(po))
      case _ => if (defaultModel == null) None else defaultModel(market.get, this)
    } else None
    
    cache.clear
    if (requiresCalibration && !modelCalibrated) { modelCalibrated = true; calibrateModel}
  }
  
  def reset(newMarket:Market, setter:(Market, BondModel) => Option[PricingModel]) = {
    val recalib = market match {case Some(m) => !m.valuedate.eq(newMarket.valuedate) case None => true}
    _market = Some(newMarket)
    defaultModel = setter
    initializeModel(recalib)
  }
	
  private def disp(name:String, f: => Any) = println(name + (" " * math.max(10 - name.size, 0)) + "\t" + (f match {
	  case null => "null"
	  case s:Option[Any] => s.getOrElse("None")
	  case s => s.toString
	}))
	
	
  def checkPayoffs:List[Boolean] = scheduledPayoffs.map{case (s, p, c) => 
	  p.isPriceable && 
	  c.isPriceable && 
	  (p.variables ++ c.variables).forall(underlyings.contains)}.toList
	  
	
  def transferSettings[T <: PriceableBond](bond:T):Unit = {
    bond.defaultModel = this.defaultModel
    bond.forceModel = this.forceModel
    bond.useCouponAsYield = this.useCouponAsYield
    bond.requiresCalibration = this.requiresCalibration
    bond.modelCalibrated = this.modelCalibrated
    bond._market = this._market
    bond.model = this.model
	calibrationCache.cache.foreach{case (a, b) => bond.calibrationCache.cache.update(a, b)}
  }
	
  /*
  * Creates clone of the same bond with date shifted by given days
  */
  override def dateShifted(shift:Int):PriceableBond = 
    copy(db, scheduledPayoffs.shifted(shift), underlyings)

  /*
   * Creates clone of the same bond with trigger replaced with given triggers.
   */
  def triggerShifted(trig:List[List[Option[Double]]]):PriceableBond = {
    val newtrig = trig.size match {
      case s if s == trigger.size => trig
      case s if s < trigger.size => List.fill(trigger.size - trig.size)(List.empty) ++ trig
	  case s if s > trigger.size => trig takeRight trigger.size
	}
	val newSchedule = ScheduledPayoffs(schedule, payoffs, Callabilities(bermudan, newtrig, underlyings))
	copy(db, newSchedule, underlyings)
  }
  
  def show:Unit = {
	    disp("id", id)
	    disp("currency", currency.code)
	    disp("model", model match { case None => "Not defined" case Some(m) => m.getClass.getName})
	    disp("market", market match { case None => "Not defined" case Some(m) => m.paramset})
	    disp("underlyings", underlyings.mkString(" "))
	    disp("initial", underlyings.map(u => u + " -> " + db.fixingMap.getOrElse(u, "not fixed")).mkString(" "))
	    disp("current", market.collect{case mkt => underlyings.map(u => u + " -> " + mkt.getFixing(u).getOrElse("not fixed")).mkString(" ")}.getOrElse("no market"))
	    disp("termination", earlyTerminationDate.getOrElse("not terminated"))
	    println("Full schedule:")
	    println(scheduledPayoffs.toString)
	  }
	
	
  def showUnderlyingInfo:Unit = {
	  val eventDates:List[Date] = scheduledPayoffs.schedule.eventDates
	  getUnderlyings.foreach{
	    case (k, Some(u)) => println(k); u.show(eventDates)
	    case (k, None) => println(k); println("not found in market or market not calibrated")
	  }
	}  
  
}

