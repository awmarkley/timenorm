package info.bethard.timenorm.formal

import java.time.temporal._
import java.time.{DateTimeException, Duration, LocalDateTime}

import info.bethard.timenorm.field.{ConstantPartialRange, MonthDayPartialRange}

import scala.collection.JavaConverters._

trait TimeExpression {
  def isDefined: Boolean
}

trait Number extends TimeExpression

case class IntNumber(n: Int) extends Number {
  val isDefined = true
}

case class FractionalNumber(number: Int, numerator: Int, denominator: Int) extends Number {
  val isDefined = true
}

case class VagueNumber(description: String) extends Number {
  val isDefined = false
}

trait Modifier extends TimeExpression {
  val isDefined = false
}

object Modifier {

  case object Exact extends Modifier

  case object Approx extends Modifier

  case object LessThan extends Modifier

  case object MoreThan extends Modifier

  case object Start extends Modifier

  case object Mid extends Modifier

  case object End extends Modifier

  case object Fiscal extends Modifier

}

/**
  * An amount of time, expressed as counts of standard time units U = {years, months, etc.}.
  * For example, a week (i.e., weeks -> 1) or three months (i.e., months -> 3). Note that periods
  * are independent of the timeline. For example, given only the period expression 10 weeks, it
  * is impossible to assign time points of the form NNNN-NN-NN NN:NN:NN to its start and end.
  */
trait Period extends TimeExpression with TemporalAmount

case class SimplePeriod(unit: TemporalUnit, n: Number, modifier: Modifier = Modifier.Exact) extends Period {

  val isDefined = n.isDefined

  lazy val number = n match {
    case IntNumber(x) => x
    case n: Number => ???
  }

  override def addTo(temporal: Temporal): Temporal = temporal.plus(number, unit)

  override def get(unit: TemporalUnit): Long = {
    if (unit == this.unit)
      return number
    else
      throw new UnsupportedTemporalTypeException(null)
  }

  override def subtractFrom(temporal: Temporal): Temporal = temporal.minus(number, unit)

  override def getUnits: java.util.List[TemporalUnit] = java.util.Collections.singletonList(unit)
}

object SimplePeriod {

  def apply(unit: TemporalUnit, number: Int): SimplePeriod = {
    SimplePeriod(unit, IntNumber(number), Modifier.Exact)
  }

  def apply(unit: TemporalUnit, number: Int, modifier: Modifier): SimplePeriod = {
    SimplePeriod(unit, IntNumber(number), modifier)
  }
}

case object UnknownPeriod extends Period {

  val isDefined = false

  override def addTo(temporal: Temporal): Temporal = ???

  override def get(unit: TemporalUnit): Long = ???

  override def subtractFrom(temporal: Temporal): Temporal = ???

  override def getUnits: java.util.List[TemporalUnit] = ???
}

case class Sum(periods: Set[Period], modifier: Modifier = Modifier.Exact) extends Period {

  val isDefined = periods.forall(_.isDefined)

  lazy val map = {
    val map = scala.collection.mutable.Map.empty[TemporalUnit, Long]
    for (period <- periods; unit <- period.getUnits.asScala)
      map.get(unit) match {
        case Some(value) => map(unit) += period.get(unit)
        case None => map(unit) = period.get(unit)
      }
    map
  }

  lazy val list = map.keys.toList.sortBy(_.getDuration()).reverse.asJava

  override def addTo(temporal: Temporal): Temporal = map.foldLeft(temporal){
    case (current, (unit, number)) => current.plus(number, unit)
  }

  override def get(unit: TemporalUnit): Long = map.getOrElse(unit, throw new UnsupportedTemporalTypeException(null))

  override def subtractFrom(temporal: Temporal): Temporal = map.foldLeft(temporal){
    case (current, (unit, number)) => current.minus(number, unit)
  }

  override def getUnits: java.util.List[TemporalUnit] = list
}

/**
  * An interval on the timeline, defined by a starting point using the start val (inclusive) and an ending
  * point expressed by the end val (exclusive). For example, the expression \textit{1990} corresponds to the
  * interval [1990-01-01, 1991-01-01).
  */
trait Interval extends TimeExpression {
  def start: LocalDateTime

  def end: LocalDateTime
}

object Interval {
  def unapply(interval: Interval): Option[(LocalDateTime, LocalDateTime)] = Some(interval.start, interval.end)
}

trait Intervals extends TimeExpression with Seq[Interval] {
  protected def intervals: Seq[Interval]

  override def length: Int = intervals.size

  override def iterator: Iterator[Interval] = intervals.toIterator

  override def apply(idx: Int) = intervals(idx)
}

case object DocumentCreationTime extends Interval {
  val isDefined = false

  def start = ???

  def end = ???
}

case object UnknownInterval extends Interval {
  val isDefined = false

  def start = ???

  def end = ???
}

case class Event(description: String) extends Interval {
  val isDefined = false

  def start = ???

  def end = ???
}

case class SimpleInterval(start: LocalDateTime, end: LocalDateTime) extends Interval {
  val isDefined = true
}

/**
  * A Year represents the interval from the first second of the year (inclusive) to the first second of the
  * next year (exclusive). The optional second parameter allows this to also represent decades (nMissingDigits=1),
  * centuries (nMissingDigits=2), etc.
  */
case class Year(digits: Int, nMissingDigits: Int = 0) extends Interval {
  val isDefined = true
  private val durationInYears = math.pow(10, nMissingDigits).toInt
  lazy val start = LocalDateTime.of(digits * durationInYears, 1, 1, 0, 0, 0, 0)
  lazy val end = start.plusYears(durationInYears)
}

/**
  * YearSuffix creates an interval by taking the year from another interval and replacing the last digits.
  * As with Year, the optional second parameter allows YearSuffix to represent decades (nMissingDigits=1),
  * centuries (nMissingDigits=2), etc.
  */
case class YearSuffix(interval: Interval, lastDigits: Int, nMissingDigits: Int = 0) extends Interval {
  val isDefined = interval.isDefined
  val nSuffixDigits = (math.log10(lastDigits) + 1).toInt
  val divider = math.pow(10, nSuffixDigits + nMissingDigits).toInt
  val multiplier = math.pow(10, nSuffixDigits).toInt
  lazy val Interval(start, end) = Year(interval.start.getYear / divider * multiplier + lastDigits, nMissingDigits)
}

/**
  * Creates an interval of a given Period length centered on a given interval. Formally:
  * This([t1,t2): Interval, Δ: Period) = [ (t1 + t2)/2 - Δ/2, (t1 + t2)/2 + Δ/2 )
  *
  * @param interval interval to center the period upon
  * @param period   period of interest
  */
case class ThisP(interval: Interval, period: Period) extends Interval {
  val isDefined = interval.isDefined && period.isDefined
  lazy val start = {
    val mid = interval.start.plus(Duration.between(interval.start, interval.end).dividedBy(2))
    val halfPeriod = Duration.between(interval.start.minus(period), interval.start).dividedBy(2)
    mid.minus(halfPeriod)
  }
  lazy val end = start.plus(period)
}

trait This extends TimeExpression {
  protected def getIntervals(interval: Interval, repeatingInterval: RepeatingInterval) = {
    // find a start that aligns to the start of the repeating interval's range unit
    val rangeStart = RepeatingInterval.truncate(interval.start, repeatingInterval.range)

    // find an end that aligns to the end of the repeating interval's range unit
    // Note that since Intervals are defined as exclusive of their end, we have to truncate from the nanosecond before
    // the end, or in some corner cases we would truncate to a time after the desired range
    val lastNano = interval.end.minus(Duration.ofNanos(1))
    val rangeEnd = RepeatingInterval.truncate(lastNano, repeatingInterval.range).plus(1, repeatingInterval.range)

    repeatingInterval.following(rangeStart).takeWhile(_.start.isBefore(rangeEnd)).toSeq
  }
}

/**
  * Finds the repeated interval contained within the given interval. The given interval is first expanded and aligned
  * to a unit the size of the repeating interval's range. This results in the proper semantics for something like
  * "this Wednesday", which really means "the Wednesday of this week".
  *
  * @param interval          the interval identifying the boundaries of the container
  * @param repeatingInterval the repeating intervals that should be found within the container
  */
case class ThisRI(interval: Interval, repeatingInterval: RepeatingInterval) extends Interval with This {
  val isDefined = interval.isDefined && repeatingInterval.isDefined
  lazy val Seq(Interval(start, end)) = getIntervals(interval, repeatingInterval)
}

/**
  * Finds the repeated interval contained within the given interval. The given interval is first expanded and aligned
  * to a unit the size of the repeating interval's range. This results in the proper semantics for something like
  * "this Wednesday", which really means "the Wednesday of this week".
  *
  * @param interval          the interval identifying the boundaries of the container
  * @param repeatingInterval the repeating intervals that should be found within the container
  */
case class ThisRIs(interval: Interval, repeatingInterval: RepeatingInterval)
  extends Intervals with This {
  val isDefined = interval.isDefined && repeatingInterval.isDefined
  lazy val intervals = getIntervals(interval, repeatingInterval)
  // force the case class toString rather than Seq.toString
  override lazy val toString = scala.runtime.ScalaRunTime._toString(this)
}

/**
  * Creates an interval of the given length that ends just before the given interval.
  * Formally: Last([t1,t2): Interval, Δ: Period = [t1 - Δ, t1)
  *
  * @param interval interval to shift from
  * @param period   period to shift the interval by
  */
case class LastP(interval: Interval, period: Period) extends Interval {
  val isDefined = interval.isDefined && period.isDefined
  lazy val start = interval.start.minus(period)
  lazy val end = interval.start
}

trait Last extends TimeExpression {
  protected def getIntervals(interval: Interval, repeatingInterval: RepeatingInterval, number: Int) = {
    repeatingInterval.preceding(interval.start).take(number).toSeq
  }
}

/**
  * Finds the latest repeated interval that appears before the given interval. Formally:
  * Last([t1,t2): Interval, R: RepeatingInterval) = latest of {[t.start,t.end) ∈ R: t.end ≤ t1}
  *
  * @param interval          interval to begin from
  * @param repeatingInterval RI that supplies the appropriate time intervals
  */
case class LastRI(interval: Interval, repeatingInterval: RepeatingInterval) extends Interval with Last {
  val isDefined = interval.isDefined && repeatingInterval.isDefined
  lazy val Seq(Interval(start, end)) = getIntervals(interval, repeatingInterval, 1)
}

/**
  * Finds the n latest repeated intervals that appear before the given interval. Formally:
  * Last([t1,t2): Interval, R: RepeatingInterval, n: Number) = n latest of {[t.start,t.end) ∈ R: t.end ≤ t1}
  *
  * @param interval          interval to begin from
  * @param repeatingInterval RI that supplies the appropriate time intervals
  * @param number            the number of intervals ot take
  */
case class LastRIs(interval: Interval, repeatingInterval: RepeatingInterval, number: Int = 1)
  extends Intervals with Last {
  val isDefined = interval.isDefined && repeatingInterval.isDefined
  lazy val intervals = getIntervals(interval, repeatingInterval, number)
  // force the case class toString rather than Seq.toString
  override lazy val toString = scala.runtime.ScalaRunTime._toString(this)
}

/**
  * Creates an interval of a given length that starts just after the input interval.
  * Formally: Next([t1,t2): Interval, Δ: Period = [t2, t2 + Δ)
  *
  * @param interval interval to shift from
  * @param period   period to shift the interval by
  */
case class NextP(interval: Interval, period: Period) extends Interval {
  val isDefined = interval.isDefined && period.isDefined
  lazy val start = interval.end
  lazy val end = interval.start.plus(period)
}

trait Next extends TimeExpression {
  protected def getIntervals(interval: Interval, repeatingInterval: RepeatingInterval, number: Int) = {
    repeatingInterval.following(interval.end).take(number).toSeq
  }
}

/**
  * Finds the next earliest repeated intervals that appear after the given interval. Formally:
  * Next([t1,t2): Interval, R: RepeatingInterval, n: Number) = n earliest of {[t.start,t.end) ∈ R: t2 ≤ t.start}
  *
  * @param interval          interval to begin from
  * @param repeatingInterval RI that supplies the appropriate time intervals
  */
case class NextRI(interval: Interval, repeatingInterval: RepeatingInterval) extends Interval with Next {
  val isDefined = interval.isDefined && repeatingInterval.isDefined
  lazy val Seq(Interval(start, end)) = getIntervals(interval, repeatingInterval, 1)
}

/**
  * Finds the n earliest repeated intervals that appear after the given interval. Formally:
  * Next([t1,t2): Interval, R: RepeatingInterval, n: Number) = n earliest of {[t.start,t.end) ∈ R: t2 ≤ t.start}
  *
  * @param interval          interval to begin from
  * @param repeatingInterval RI that supplies the appropriate time intervals
  * @param number            the number of repeated intervals to take
  */
case class NextRIs(interval: Interval, repeatingInterval: RepeatingInterval, number: Int = 1)
  extends Intervals with Next {
  val isDefined = interval.isDefined && repeatingInterval.isDefined
  lazy val intervals = getIntervals(interval, repeatingInterval, number)
  // force the case class toString rather than Seq.toString
  override lazy val toString = scala.runtime.ScalaRunTime._toString(this)
}

/**
  * Shifts the input interval earlier by a given period length. Formally:
  * Before([t1,t2): Interval, Δ: Period) = [t1 - Δ, t2 - Δ)
  *
  * @param interval interval to shift from
  * @param period   period to shift the interval by
  */
case class BeforeP(interval: Interval, period: Period) extends Interval {
  val isDefined = interval.isDefined && period.isDefined
  lazy val start = interval.start.minus(period)
  lazy val end = interval.end.minus(period)
}

/**
  * Finds the Nth latest repeated interval before the input interval. Formally:
  * Before([t1,t2): Interval, R: RepeatingInterval, n: Number): Interval =
  * Nth latest interval {[t.start, t.end) ∈ R: t.end ≤ t1}
  *
  * @param interval          interval to begin from
  * @param repeatingInterval RI that supplies the appropriate time intervals
  * @param number            the number of intervals to skip
  */
case class BeforeRI(interval: Interval, repeatingInterval: RepeatingInterval, number: Int = 1)
  extends Interval {
  val isDefined = interval.isDefined && repeatingInterval.isDefined
  lazy val Interval(start, end) = repeatingInterval.preceding(interval.start).drop(number - 1).next
}

/**
  * Shifts the input interval later by a given period length.
  * Formally: After([t1,t2): Interval, Δ: Period) = [t1 +  Δ, t2 +  Δ)
  *
  * @param interval interval to shift from
  * @param period   period to shift the interval by
  */
case class AfterP(interval: Interval, period: Period) extends Interval {
  val isDefined = interval.isDefined && period.isDefined
  lazy val start = interval.start.plus(period)
  lazy val end = interval.end.plus(period)
}

/**
  * Finds the Nth earliest repeated interval after the input interval. Formally:
  * After([t1,t2): Interval, R: RepeatingInterval, n: Number): Interval =
  * Nth earliest interval {[t.start, t.end) ∈ R: t2 ≤ t.start}
  *
  * @param interval          interval to start from
  * @param repeatingInterval repeating intervals to search over
  */
case class AfterRI(interval: Interval, repeatingInterval: RepeatingInterval, number: Int = 1)
  extends Interval {
  val isDefined = interval.isDefined && repeatingInterval.isDefined
  lazy val Interval(start, end) = repeatingInterval.following(interval.end).drop(number - 1).next
}

/**
  * Finds the interval between two input intervals. Formally:
  * Between([t1,t2): startInterval,[t3,t4): endInterval): Interval = [t2,t3)
  *
  * @param startInterval first interval
  * @param endInterval   second interval
  */
case class Between(startInterval: Interval, endInterval: Interval) extends Interval {
  val isDefined = startInterval.isDefined && endInterval.isDefined
  lazy val start = startInterval.end
  lazy val end = endInterval.start
}

/**
  * Creates an interval that is the nth repetition of the period following the start of the interval.
  * Formally: NthFromStart([t1,t2): Interval, Δ: Period, n: N): Interval = [t1+Δ*(n-1), t1+Δ*n)
  *
  * @param interval the interval to begin from
  * @param number   the number of repetitions of the period to add
  * @param period   the period to scale by
  */
case class NthFromStartP(interval: Interval, number: Int, period: Period) extends Interval {
  val isDefined = interval.isDefined && period.isDefined
  lazy val start = Iterator.fill(number - 1)(period).foldLeft(interval.start)(_ plus _)
  lazy val end = start.plus(period)
}

/**
  * Selects the Nth subinterval of a RepeatingInterval, counting from the start of another Interval. Formally:
  * NthFromStart([t1,t2): Interval, n: Number, R: RepeatingInterval): Interval
  * = Nth of {[t.start, t.end) ∈ R : t1 ≤ t.start ∧ t.end ≤ t2}
  *
  * @param interval          interval to start from
  * @param number            number indicating which item should be selected
  * @param repeatingInterval repeating intervals to select from
  */
case class NthFromStartRI(interval: Interval, number: Int, repeatingInterval: RepeatingInterval) extends Interval {
  val isDefined = interval.isDefined && repeatingInterval.isDefined
  lazy val Interval(start, end) = repeatingInterval.following(interval.start).drop(number - 1).next match {
    case result if result.end.isBefore(interval.end) => result
    case _ => ???
  }
}

trait RepeatingInterval extends TimeExpression {
  def preceding(ldt: LocalDateTime): Iterator[Interval]

  def following(ldt: LocalDateTime): Iterator[Interval]

  val base: TemporalUnit
  val range: TemporalUnit
}

private[formal] object RepeatingInterval {
  def truncate(ldt: LocalDateTime, tUnit: TemporalUnit): LocalDateTime = tUnit match {
    case ChronoUnit.CENTURIES => LocalDateTime.of(ldt.getYear / 100 * 100, 1, 1, 0, 0)
    case ChronoUnit.DECADES => LocalDateTime.of(ldt.getYear / 10 * 10, 1, 1, 0, 0)
    case ChronoUnit.YEARS => ldt.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS)
    case ChronoUnit.MONTHS => ldt.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS)
    case ChronoUnit.WEEKS =>
      ldt.withDayOfYear(ldt.getDayOfYear - ldt.getDayOfWeek.getValue).truncatedTo(ChronoUnit.DAYS)
    case range: MonthDayPartialRange => ldt.`with`(range.first).truncatedTo(ChronoUnit.DAYS)
    case range: ConstantPartialRange => ldt.`with`(range.field, range.first).truncatedTo(range.field.getBaseUnit)
    case _ => ldt.truncatedTo(tUnit)
  }
}

case class RepeatingUnit(unit: TemporalUnit, modifier: Modifier = Modifier.Exact) extends RepeatingInterval {
  val isDefined = true
  override val base = unit
  override val range = unit

  override def preceding(ldt: LocalDateTime): Iterator[Interval] = {
    var end = RepeatingInterval.truncate(ldt, unit).plus(1, unit)
    var start = end.minus(1, unit)

    Iterator.continually {
      end = start
      start = start.minus(1, unit)
      SimpleInterval(start, end)
    }
  }

  override def following(ldt: LocalDateTime): Iterator[Interval] = {
    val truncated = RepeatingInterval.truncate(ldt, unit)
    var end = if (truncated.isBefore(ldt)) truncated.plus(1, unit) else truncated
    var start = end.minus(1, unit)


    Iterator.continually {
      start = end
      end = start.plus(1, unit)
      SimpleInterval(start, end)
    }
  }
}

case class RepeatingField(field: TemporalField, value: Long, modifier: Modifier = Modifier.Exact) extends RepeatingInterval {
  val isDefined = true
  override val base = field.getBaseUnit
  override val range = field.getRangeUnit

  override def preceding(ldt: LocalDateTime): Iterator[Interval] = {
    var start = RepeatingInterval.truncate(ldt.`with`(field, value), field.getBaseUnit)

    if (!start.isAfter(ldt))
      start = start.plus(1, field.getRangeUnit)

    Iterator.continually {
      start = start.minus(1, field.getRangeUnit)

      while (start.get(field) != value) {
        start = start.minus(1, field.getRangeUnit)

        try
          start = start.`with`(field, value)
        catch {
          case dte: DateTimeException =>
        }
      }

      SimpleInterval(start, start.plus(1, field.getBaseUnit))
    }
  }

  override def following(ldt: LocalDateTime): Iterator[Interval] = {
    var start = RepeatingInterval.truncate(ldt `with`(field, value), field.getBaseUnit)

    if (!start.isBefore(ldt))
      start = start.minus(1, field.getRangeUnit)

    Iterator.continually {
      start = start.plus(1, field.getRangeUnit)

      while (start.get(field) != value) {
        start = start.plus(1, field.getRangeUnit)

        try
          start = start.`with`(field, value)
        catch {
          case dte: DateTimeException =>
        }
      }

      SimpleInterval(start, start.plus(1, field.getBaseUnit))
    }
  }
}

case class Union(repeatingIntervals: Set[RepeatingInterval]) extends RepeatingInterval {
  val isDefined = repeatingIntervals.forall(_.isDefined)
  override val base = repeatingIntervals.minBy(_.base.getDuration).base
  override val range = repeatingIntervals.maxBy(_.range.getDuration).range

  implicit val ordering = Ordering.Tuple2(Ordering.fromLessThan[LocalDateTime](_ isAfter _), Ordering[Duration].reverse)

  override def preceding(ldt: LocalDateTime): Iterator[Interval] = {
    val iterators = repeatingIntervals.map(_.preceding(ldt).buffered).toList

    Iterator.continually {
      iterators.minBy { iterator =>
        val interval = iterator.head
        (interval.end, Duration.between(interval.start, interval.end))
      }.next
    }
  }

  override def following(ldt: LocalDateTime): Iterator[Interval] = {
    val iterators = repeatingIntervals.map(_.following(ldt).buffered).toList

    Iterator.continually {
      iterators.maxBy { iterator =>
        val interval = iterator.head
        (interval.start, Duration.between(interval.start, interval.end))
      }.next
    }
  }
}

case class Intersection(repeatingIntervals: Set[RepeatingInterval]) extends RepeatingInterval {
  val isDefined = repeatingIntervals.forall(_.isDefined)
  override val base = repeatingIntervals.minBy(_.base.getDuration).base
  override val range = repeatingIntervals.maxBy(_.range.getDuration).range

  private val sortedRepeatingIntervals = repeatingIntervals.toList
    .sortBy(ri => (ri.range.getDuration, ri.base.getDuration)).reverse

  override def preceding(ldt: LocalDateTime): Iterator[Interval] = {
    var startPoint = sortedRepeatingIntervals.head.preceding(ldt).next.end
    val iterators = sortedRepeatingIntervals.map(_.preceding(startPoint).buffered)

    Iterator.continually {
      startPoint = startPoint.minus(1, range)
      val firstInterval = iterators.head.next
      val othersAfterStart = iterators.tail.map(it => it.takeWhile(_ => it.head.start isAfter startPoint).toList)

      othersAfterStart.iterator.foldLeft(List(firstInterval)) {
        (intersectedIntervals, newIntervals) => newIntervals.filter(isContainedInOneOf(_, intersectedIntervals))
      }
    }.flatten
  }

  override def following(ldt: LocalDateTime): Iterator[Interval] = {
    var startPoint = sortedRepeatingIntervals.head.following(ldt).next.start
    val iterators = sortedRepeatingIntervals.map(_.following(startPoint).buffered)

    Iterator.continually {
      startPoint = startPoint.plus(1, range)
      val firstInterval = iterators.head.next
      val othersBeforeStart = iterators.tail.map(it => it.takeWhile(_ => it.head.end isBefore startPoint).toList)

      othersBeforeStart.iterator.foldLeft(List(firstInterval)) {
        (intersectedIntervals, newIntervals) => newIntervals.filter(isContainedInOneOf(_, intersectedIntervals))
      }
    }.flatten
  }

  private def isContainedInOneOf(interval: Interval, intervals: Iterable[Interval]): Boolean = {
    intervals.exists(i => !(interval.start isBefore i.start) && !(interval.end isAfter i.end))
  }
}

case class TimeZone(name: String) extends TimeExpression {
  val isDefined = false
}

