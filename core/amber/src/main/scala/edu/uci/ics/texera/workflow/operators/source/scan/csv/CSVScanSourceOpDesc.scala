package edu.uci.ics.texera.workflow.operators.source.scan.csv

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyDescription}
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle
import edu.uci.ics.amber.engine.operators.OpExecConfig
import edu.uci.ics.texera.workflow.common.operators.ManyToOneOpExecConfig
import edu.uci.ics.texera.workflow.common.tuple.schema.AttributeTypeUtils.inferSchemaFromRows
import edu.uci.ics.texera.workflow.common.tuple.schema.{
  Attribute,
  AttributeType,
  OperatorSchemaInfo,
  Schema
}
import edu.uci.ics.texera.workflow.operators.source.scan.ScanSourceOpDesc
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.univocity.parsers.csv.{CsvFormat, CsvParser, CsvParserSettings}

import java.io.{File, FileInputStream, IOException, InputStreamReader}
import scala.jdk.CollectionConverters.asJavaIterableConverter

class CSVScanSourceOpDesc extends ScanSourceOpDesc {

  @JsonProperty(defaultValue = ",")
  @JsonSchemaTitle("Delimiter")
  @JsonPropertyDescription("delimiter to separate each line into fields")
  @JsonDeserialize(contentAs = classOf[java.lang.String])
  var customDelimiter: Option[String] = None

  @JsonProperty(defaultValue = "true")
  @JsonSchemaTitle("Header")
  @JsonPropertyDescription("whether the CSV file contains a header line")
  var hasHeader: Boolean = true

  fileTypeName = Option("CSV")

  @throws[IOException]
  override def operatorExecutor(operatorSchemaInfo: OperatorSchemaInfo): OpExecConfig = {
    // fill in default values
    if (customDelimiter.get.isEmpty)
      customDelimiter = Option(",")

    filePath match {
      case Some(_) =>
        new ManyToOneOpExecConfig(operatorIdentifier, _ => new CSVScanSourceOpExec(this))
      case None =>
        throw new RuntimeException("File path is not provided.")
    }

  }

  /**
    * Infer Texera.Schema based on the top few lines of data.
    *
    * @return Texera.Schema build for this operator
    */
  @Override
  def inferSchema(): Schema = {
    if (customDelimiter.isEmpty) {
      return null
    }
    if (filePath.isEmpty) {
      return null
    }
    val inputReader = new InputStreamReader(new FileInputStream(new File(filePath.get)))

    val csvFormat = new CsvFormat()
    csvFormat.setDelimiter(customDelimiter.get.charAt(0))
    val csvSetting = new CsvParserSettings()
    csvSetting.setMaxCharsPerColumn(-1)
    csvSetting.setFormat(csvFormat)
    csvSetting.setHeaderExtractionEnabled(hasHeader)
    val parser = new CsvParser(csvSetting)
    parser.beginParsing(inputReader)

    var data: Array[Array[String]] = Array()
    val readLimit = limit.getOrElse(INFER_READ_LIMIT).min(INFER_READ_LIMIT)
    for (i <- 0 until readLimit) {
      val row = parser.parseNext()
      if (row != null) {
        data = data :+ row
      }
    }
    parser.stopParsing()
    inputReader.close()

    val attributeTypeList: Array[AttributeType] = inferSchemaFromRows(
      data.iterator.asInstanceOf[Iterator[Array[Object]]]
    )
    val header: Array[String] =
      if (hasHeader) parser.getContext.headers()
      else (1 to attributeTypeList.length).map(i => "column-" + i).toArray

    Schema
      .newBuilder()
      .add(header.indices.map(i => new Attribute(header(i), attributeTypeList(i))).asJava)
      .build()
  }

}
