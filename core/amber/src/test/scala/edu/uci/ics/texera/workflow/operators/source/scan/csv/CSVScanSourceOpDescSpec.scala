package edu.uci.ics.texera.workflow.operators.source.scan.csv

import edu.uci.ics.texera.workflow.common.WorkflowContext
import edu.uci.ics.texera.workflow.common.tuple.schema.{AttributeType, OperatorSchemaInfo, Schema}
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec

import scala.collection.convert.ImplicitConversions.`list asScalaBuffer`
class CSVScanSourceOpDescSpec extends AnyFlatSpec with BeforeAndAfter {

  val workflowContext = new WorkflowContext()
  var csvScanSourceOpDesc: CSVScanSourceOpDesc = _
  var parallelCsvScanSourceOpDesc: ParallelCSVScanSourceOpDesc = _
  before {
    csvScanSourceOpDesc = new CSVScanSourceOpDesc()
    parallelCsvScanSourceOpDesc = new ParallelCSVScanSourceOpDesc()
  }

  it should "infer schema from single-line-data csv" in {

    parallelCsvScanSourceOpDesc.fileName = Some("src/test/resources/country_sales_small.csv")
    parallelCsvScanSourceOpDesc.customDelimiter = Some(",")
    parallelCsvScanSourceOpDesc.hasHeader = true
    parallelCsvScanSourceOpDesc.setContext(workflowContext)
    val inferredSchema: Schema = parallelCsvScanSourceOpDesc.inferSchema()

    assert(inferredSchema.getAttributes.length == 14)
    assert(inferredSchema.getAttribute("Order ID").getType == AttributeType.INTEGER)
    assert(inferredSchema.getAttribute("Unit Price").getType == AttributeType.DOUBLE)

  }

  it should "infer schema from headerless single-line-data csv" in {

    parallelCsvScanSourceOpDesc.fileName =
      Some("src/test/resources/country_sales_headerless_small.csv")
    parallelCsvScanSourceOpDesc.customDelimiter = Some(",")
    parallelCsvScanSourceOpDesc.hasHeader = false
    parallelCsvScanSourceOpDesc.setContext(workflowContext)

    val inferredSchema: Schema = parallelCsvScanSourceOpDesc.inferSchema()

    assert(inferredSchema.getAttributes.length == 14)
    assert(inferredSchema.getAttribute("column-10").getType == AttributeType.DOUBLE)
    assert(inferredSchema.getAttribute("column-7").getType == AttributeType.INTEGER)
  }

  it should "infer schema from multi-line-data csv" in {

    csvScanSourceOpDesc.fileName = Some("src/test/resources/country_sales_small_multi_line.csv")
    csvScanSourceOpDesc.customDelimiter = Some(",")
    csvScanSourceOpDesc.hasHeader = true
    csvScanSourceOpDesc.setContext(workflowContext)

    val inferredSchema: Schema = csvScanSourceOpDesc.inferSchema()

    assert(inferredSchema.getAttributes.length == 14)
    assert(inferredSchema.getAttribute("Order ID").getType == AttributeType.INTEGER)
    assert(inferredSchema.getAttribute("Unit Price").getType == AttributeType.DOUBLE)
  }

  it should "infer schema from headerless multi-line-data csv" in {

    csvScanSourceOpDesc.fileName = Some("src/test/resources/country_sales_headerless_small.csv")
    csvScanSourceOpDesc.customDelimiter = Some(",")
    csvScanSourceOpDesc.hasHeader = false
    csvScanSourceOpDesc.setContext(workflowContext)

    val inferredSchema: Schema = csvScanSourceOpDesc.inferSchema()

    assert(inferredSchema.getAttributes.length == 14)
    assert(inferredSchema.getAttribute("column-10").getType == AttributeType.DOUBLE)
    assert(inferredSchema.getAttribute("column-7").getType == AttributeType.INTEGER)
  }

  it should "infer schema from headerless multi-line-data csv with custom delimiter" in {

    csvScanSourceOpDesc.fileName =
      Some("src/test/resources/country_sales_headerless_small_multi_line_custom_delimiter.csv")
    csvScanSourceOpDesc.customDelimiter = Some(";")
    csvScanSourceOpDesc.hasHeader = false
    csvScanSourceOpDesc.setContext(workflowContext)

    val inferredSchema: Schema = csvScanSourceOpDesc.inferSchema()

    assert(inferredSchema.getAttributes.length == 14)
    assert(inferredSchema.getAttribute("column-10").getType == AttributeType.DOUBLE)
    assert(inferredSchema.getAttribute("column-7").getType == AttributeType.INTEGER)
  }

  it should "create one worker with multi-line-data csv" in {

    csvScanSourceOpDesc.fileName =
      Some("src/test/resources/country_sales_headerless_small_multi_line_custom_delimiter.csv")
    csvScanSourceOpDesc.customDelimiter = Some(";")
    csvScanSourceOpDesc.hasHeader = false
    csvScanSourceOpDesc.setContext(workflowContext)

    val emptySchema = Schema.newBuilder().build()
    val operatorSchemaInfo = OperatorSchemaInfo(Array(emptySchema), Array(emptySchema))
    assert(csvScanSourceOpDesc.operatorExecutor(operatorSchemaInfo).topology.layers.length == 1)
    assert(
      csvScanSourceOpDesc
        .operatorExecutor(operatorSchemaInfo)
        .topology
        .layers
        .apply(0)
        .numWorkers == 1
    )
  }

}
