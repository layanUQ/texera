package edu.uci.ics.texera.workflow.operators.difference

import edu.uci.ics.amber.engine.common.InputExhausted
import edu.uci.ics.amber.engine.common.virtualidentity.{LayerIdentity, LinkIdentity}
import edu.uci.ics.texera.workflow.common.tuple.Tuple
import edu.uci.ics.texera.workflow.common.tuple.schema.{Attribute, AttributeType, Schema}
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.Random

class DifferenceOpExecSpec extends AnyFlatSpec with BeforeAndAfter {
  var linkID1: LinkIdentity = _
  var linkID2: LinkIdentity = _
  var opExec: DifferenceOpExec = _
  var counter: Int = 0

  def layerID(): LayerIdentity = {
    counter += 1
    LayerIdentity("" + counter, "" + counter, "" + counter)
  }

  def linkID(): LinkIdentity = LinkIdentity(layerID(), layerID())

  def tuple(): Tuple = {
    counter += 1
    val schema = Schema
      .newBuilder()
      .add(
        new Attribute("field1", AttributeType.STRING),
        new Attribute("field2", AttributeType.INTEGER),
        new Attribute("field3", AttributeType.BOOLEAN)
      )
      .build()
    Tuple
      .newBuilder(schema)
      .addSequentially(Array("hello", Int.box(counter), Boolean.box(true)))
      .build()
  }

  before {
    linkID1 = linkID()
    linkID2 = linkID()
    opExec = new DifferenceOpExec(linkID2)
  }

  it should "open" in {

    opExec.open()

  }

  it should "work with basic two input streams with no duplicates" in {
    opExec.open()
    counter = 0
    val commonTuples = (1 to 10).map(_ => tuple()).toList

    (0 to 7).map(i => {
      opExec.processTexeraTuple(Left(commonTuples(i)), linkID1)
    })
    assert(opExec.processTexeraTuple(Right(InputExhausted()), linkID1).isEmpty)

    (5 to 9).map(i => {
      opExec.processTexeraTuple(Left(commonTuples(i)), linkID2)
    })

    val outputTuples: Set[Tuple] = opExec.processTexeraTuple(Right(InputExhausted()), linkID2).toSet
    assert(
      outputTuples.equals(commonTuples.slice(0, 5).toSet)
    )

    opExec.close()
  }

  it should "raise IllegalArgumentException when intersect with more than two input upstreams" in {

    opExec.open()
    counter = 0
    val commonTuples = (1 to 10).map(_ => tuple()).toList
    assertThrows[IllegalArgumentException] {
      (1 to 100).map(_ => {
        opExec.processTexeraTuple(Left(tuple()), linkID())
        opExec.processTexeraTuple(Left(commonTuples(Random.nextInt(commonTuples.size))), linkID())
      })

      val outputTuples: Set[Tuple] = opExec.processTexeraTuple(Right(InputExhausted()), null).toSet
      assert(outputTuples.size <= 10)
      assert(outputTuples.subsetOf(commonTuples.toSet))
      outputTuples.foreach(tuple => assert(tuple.getField[Int]("field2") <= 10))
      opExec.close()
    }
  }

  it should "work with one empty input upstream after a data stream" in {
    opExec.open()
    counter = 0
    val commonTuples = (1 to 10).map(_ => tuple()).toList

    (0 to 9).map(i => {
      opExec.processTexeraTuple(Left(commonTuples(i)), linkID1)
    })
    assert(opExec.processTexeraTuple(Right(InputExhausted()), linkID1).isEmpty)

    val outputTuples: Set[Tuple] = opExec.processTexeraTuple(Right(InputExhausted()), linkID2).toSet
    assert(outputTuples.equals(commonTuples.toSet))
    opExec.close()
  }

  it should "work with one empty input upstream after a data stream - other order" in {
    opExec.open()
    counter = 0
    val commonTuples = (1 to 10).map(_ => tuple()).toList

    (0 to 9).map(i => {
      opExec.processTexeraTuple(Left(commonTuples(i)), linkID2)
    })
    assert(opExec.processTexeraTuple(Right(InputExhausted()), linkID2).isEmpty)

    val outputTuples: Set[Tuple] = opExec.processTexeraTuple(Right(InputExhausted()), linkID1).toSet
    assert(outputTuples.isEmpty)
    opExec.close()
  }

  it should "work with one empty input upstream before a data stream" in {
    opExec.open()
    counter = 0
    val commonTuples = (1 to 10).map(_ => tuple()).toList

    assert(opExec.processTexeraTuple(Right(InputExhausted()), linkID2).isEmpty)
    (0 to 9).map(i => {
      opExec.processTexeraTuple(Left(commonTuples(i)), linkID1)
    })

    val outputTuples: Set[Tuple] = opExec.processTexeraTuple(Right(InputExhausted()), linkID1).toSet
    assert(outputTuples.equals(commonTuples.toSet))
    opExec.close()
  }

  it should "work with one empty input upstream during a data stream" in {
    val linkID1 = linkID()
    val linkID2 = linkID()
    opExec.open()
    counter = 0
    val commonTuples = (1 to 10).map(_ => tuple()).toList

    (0 to 5).map(i => {
      opExec.processTexeraTuple(Left(commonTuples(i)), linkID1)
    })
    assert(opExec.processTexeraTuple(Right(InputExhausted()), linkID2).isEmpty)
    (6 to 9).map(i => {
      opExec.processTexeraTuple(Left(commonTuples(i)), linkID1)
    })

    val outputTuples: Set[Tuple] = opExec.processTexeraTuple(Right(InputExhausted()), linkID1).toSet
    assert(outputTuples.equals(commonTuples.toSet))
    opExec.close()
  }

  it should "work with two empty input upstreams" in {

    opExec.open()
    assert(opExec.processTexeraTuple(Right(InputExhausted()), linkID()).isEmpty)
    assert(opExec.processTexeraTuple(Right(InputExhausted()), linkID()).isEmpty)
    opExec.close()
  }

}
