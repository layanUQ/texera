package edu.uci.ics.texera.workflow.operators.sortPartitions

import edu.uci.ics.amber.engine.common.InputExhausted
import edu.uci.ics.amber.engine.operators.sortPartitions.SortPartitionOpExec
import edu.uci.ics.texera.workflow.common.tuple.Tuple
import edu.uci.ics.texera.workflow.common.tuple.schema.{
  Attribute,
  AttributeType,
  OperatorSchemaInfo,
  Schema
}
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec

class SortPartitionsOpExecSpec extends AnyFlatSpec with BeforeAndAfter {
  val tupleSchema: Schema = Schema
    .newBuilder()
    .add(new Attribute("field1", AttributeType.STRING))
    .add(new Attribute("field2", AttributeType.INTEGER))
    .add(new Attribute("field3", AttributeType.BOOLEAN))
    .build()

  val tuple: (Int) => Tuple = (i) =>
    Tuple
      .newBuilder(tupleSchema)
      .add(new Attribute("field1", AttributeType.STRING), "hello")
      .add(new Attribute("field2", AttributeType.INTEGER), i)
      .add(
        new Attribute("field3", AttributeType.BOOLEAN),
        true
      )
      .build()

  var opExec: SortPartitionOpExec = _
  before {
    opExec =
      new SortPartitionOpExec("field2", OperatorSchemaInfo(Array(tupleSchema), Array(tupleSchema)))
  }

  it should "open" in {

    opExec.open()

  }

  it should "preserve the insertion order" in {

    opExec.open()
    opExec.processTexeraTuple(Left(tuple(3)), null)
    opExec.processTexeraTuple(Left(tuple(1)), null)
    opExec.processTexeraTuple(Left(tuple(2)), null)
    opExec.processTexeraTuple(Left(tuple(5)), null)

    val outputTuples: List[Tuple] = opExec.processTexeraTuple(Right(InputExhausted()), null).toList
    assert(outputTuples.size == 4)
    assert(outputTuples(0).equals(tuple(1)))
    assert(outputTuples(1).equals(tuple(2)))
    assert(outputTuples(2).equals(tuple(3)))
    assert(outputTuples(3).equals(tuple(5)))
    opExec.close()
  }

}
