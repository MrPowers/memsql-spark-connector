package com.memsql.spark.interface.api

import com.memsql.spark.etl.api.configs._
import spray.json._

object ApiJsonProtocol extends JsonEnumProtocol {
  implicit val extractPhaseKindFormat = jsonEnum(ExtractPhaseKind)
  implicit val transformPhaseKindFormat = jsonEnum(TransformPhaseKind)
  implicit val loadPhaseKindFormat = jsonEnum(LoadPhaseKind)

  implicit def phaseFormat[T :JsonFormat] = jsonFormat2(Phase.apply[T])

  implicit val pipelineConfigFormat = jsonFormat4(PipelineConfig)

  implicit val phaseMetricRecordFormat = jsonFormat6(PhaseMetricRecord)
  implicit val pipelineMetricRecordFormat = jsonFormat6(PipelineMetricRecord)

  implicit val pipelineStateFormat = jsonEnum(PipelineState)
  // We explicitly specify the fields we want in pipelines because we don't
  // want to include metric records.
  implicit val pipelineFormat = jsonFormat(Pipeline.apply, "pipeline_id", "state", "jar", "batch_interval", "config", "last_updated", "error")
}
