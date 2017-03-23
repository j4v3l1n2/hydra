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

package hydra.ingest.endpoints

import akka.actor._
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.StatusCodes.ServiceUnavailable
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.stream.ActorMaterializer
import com.github.vonnagy.service.container.http.routing.RoutedEndpoints
import hydra.common.logging.LoggingAdapter
import hydra.core.http.HydraDirectives
import hydra.core.ingest.IngestionParams
import hydra.core.marshallers.{GenericServiceResponse, HydraJsonSupport}
import hydra.ingest.HydraIngestorRegistry
import hydra.ingest.services.IngestionRequestHandler

/**
  * Created by alexsilva on 12/22/15.
  */
class IngestionEndpoint(implicit val system: ActorSystem, implicit val actorRefFactory: ActorRefFactory)
  extends RoutedEndpoints with LoggingAdapter with HydraJsonSupport with HydraDirectives with HydraIngestorRegistry {

  import hydra.ingest.RequestFactories._

  implicit val mat = ActorMaterializer()

  override val route: Route =
    post {
      requestEntityPresent {
        pathPrefix("ingest") {
          optionalHeaderValueByName(IngestionParams.HYDRA_REQUEST_LABEL_PARAM) { destination =>
            handleExceptions(excptHandler) {
              pathEndOrSingleSlash {
                broadcastRequest(destination.getOrElse("hydra"))
              }
            } ~ path(Segment) { ingestor =>
              publishToIngestor(destination.getOrElse("hydra"), ingestor)
            }
          }
        }
      }
    }

  val excptHandler = ExceptionHandler {
    case e: IllegalArgumentException => complete(GenericServiceResponse(400, e.getMessage))
    case e: Exception => complete(GenericServiceResponse(ServiceUnavailable.intValue, e.getMessage))
  }

  def broadcastRequest(correlationId: String) = {
    onSuccess(ingestorRegistry) { registry =>
      extractRequestContext { ctx =>
        val hydraReq = createRequest[String, HttpRequest](correlationId, ctx.request)
        onSuccess(hydraReq) { request =>
          imperativelyComplete { ictx =>
            actorRefFactory.actorOf(IngestionRequestHandler.props(request, registry, ictx))
          }
        }
      }
    }
  }

  def publishToIngestor(correlationId: String, ingestor: String) = {
    onSuccess(lookupIngestor(ingestor)) { result =>
      result.ref match {
        case Some(ref) =>
          extractRequestContext { ctx =>
            val hydraReq = createRequest[String, HttpRequest](correlationId, ctx.request)
            onSuccess(hydraReq) { req =>
              imperativelyComplete { ictx =>
                ingestorRegistry.foreach(r => actorRefFactory.actorOf(IngestionRequestHandler.props(req, r, ictx)))
              }
            }
          }
        case None => complete(404, GenericServiceResponse(404, s"Ingestor $ingestor not found."))
      }
    }
  }
}