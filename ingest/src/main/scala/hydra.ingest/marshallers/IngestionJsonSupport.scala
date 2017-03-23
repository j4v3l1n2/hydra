/*
 * Copyright (C) 2016 Pluralsight, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package hydra.ingest.marshallers

import hydra.core.ingest.{IngestorState, _}
import hydra.core.marshallers.HydraJsonSupport
import hydra.ingest.protocol.{IngestionReport, IngestionStatus}


/**
  * Created by alexsilva on 2/18/16.
  */
trait IngestionJsonSupport extends HydraJsonSupport {

  import spray.json._


  implicit val ingestorInfoFormat = jsonFormat3(IngestorInfo)

  implicit object IngestorStateFormat extends RootJsonFormat[IngestorState] {

    override def write(obj: IngestorState): JsValue = {
      val duratioOp = if (obj.duration > -1) Some(JsNumber(obj.duration)) else None
      val values: Map[String, JsValue] = Map(
        "status" -> Some(JsString(obj.status.name)),
        "message" -> Some(JsString(obj.status.message)),
        "startedAt" -> Some(obj.startedAt.toJson),
        "finishedAt" -> obj.finishedAt.map(_.toJson),
        "completed" -> Some(JsBoolean(obj.status.completed)),
        "durationMillis" -> duratioOp
      ).collect {
        case (key, Some(value)) => key -> value
      }
      JsObject(values)
    }

    override def read(json: JsValue): IngestorState = ???
  }


  implicit object IngestionReportFormat extends RootJsonFormat[IngestionReport] {

    def writeState[T <: IngestorState : JsonWriter](t: T) = t.toJson

    override def write(obj: IngestionReport): JsValue = {
      val ingestors = obj.ingestors.map(h => h._1 -> writeState(h._2))
      JsObject(
        Map(
          "statusCode" -> JsNumber(obj.statusCode.intValue),
          "ingestors" -> JsObject(ingestors)
        )
      )
    }

    override def read(json: JsValue): IngestionReport = ???

  }

}
