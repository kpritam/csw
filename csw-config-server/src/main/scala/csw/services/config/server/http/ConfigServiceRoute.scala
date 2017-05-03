package csw.services.config.server.http

import akka.Done
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import csw.services.config.api.scaladsl.ConfigService
import csw.services.config.server.ActorRuntime

class ConfigServiceRoute(
    configService: ConfigService,
    actorRuntime: ActorRuntime,
    configExceptionHandler: ConfigExceptionHandler
) extends HttpSupport {

  import actorRuntime._

  def route: Route = handleExceptions(configExceptionHandler.exceptionHandler) {
    path("config" / FilePath) { filePath ⇒
      (get & rejectEmptyResponse) {
        (dateParam & idParam & latestParam) {
          case (Some(date), _, _) ⇒ complete(configService.getByTime(filePath, date))
          case (_, Some(id), _)   ⇒ complete(configService.getById(filePath, id))
          case (_, _, true)       ⇒ complete(configService.getLatest(filePath))
          case (_, _, _)          ⇒ complete(configService.getActive(filePath))
        }
      } ~
      head {
        idParam { id ⇒
          complete {
            configService.exists(filePath, id).map { found ⇒
              if (found) StatusCodes.OK else StatusCodes.NotFound
            }
          }
        }
      } ~
      post {
        (configDataEntity & annexParam & commentParam) { (configData, annex, comment) ⇒
          complete(StatusCodes.Created -> configService.create(filePath, configData, annex, comment))
        }
      } ~
      put {
        (configDataEntity & commentParam) { (configData, comment) ⇒
          complete(configService.update(filePath, configData, comment))
        }
      } ~
      delete {
        commentParam { comment ⇒
          complete(configService.delete(filePath, comment).map(_ ⇒ Done))
        }
      }
    } ~
    path("default" / FilePath) { filePath ⇒
      put {
        (idParam & commentParam) {
          case (Some(configId), comment) ⇒
            complete(configService.setActive(filePath, configId, comment).map(_ ⇒ Done))
          case (_, comment) ⇒ complete(configService.resetActive(filePath, comment).map(_ ⇒ Done))
        }
      } ~
      (get & rejectEmptyResponse) {
        println(s"------------------------getting default version of $filePath -----------------------")
        complete(configService.getActive(filePath))
      }
    } ~
    (path("history" / FilePath) & get) { filePath ⇒
      maxResultsParam { maxCount ⇒
        complete(configService.history(filePath, maxCount))
      }
    } ~
    (path("list") & get) {
      (typeParam & patternParam) { (fileType, pattern) ⇒
        complete(configService.list(fileType, pattern))
      }
    }
  }
}
