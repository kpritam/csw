package csw.services.csclient

import akka.Done
import csw.services.config.api.models.ConfigId
import csw.services.config.api.scaladsl.ConfigService
import csw.services.config.client.internal.ActorRuntime
import csw.services.config.client.scaladsl.ConfigClientFactory
import csw.services.csclient.models.Options
import csw.services.csclient.utils.{CmdLineArgsParser, PathUtils}
import csw.services.location.commons.ClusterSettings
import csw.services.location.scaladsl.LocationServiceFactory

import scala.async.Async._
import scala.concurrent.duration.DurationDouble
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

class ConfigCliApp(clusterSettings: ClusterSettings) {

  val actorRuntime = new ActorRuntime()

  import actorRuntime._

  private val locationService              = LocationServiceFactory.withSettings(clusterSettings)
  private val configService: ConfigService = ConfigClientFactory.make(actorSystem, locationService)

  def start(args: Array[String]): Future[Done] =
    async {
      CmdLineArgsParser.parse(args) match {
        case Some(options) =>
          await(commandLineRunner(options))
          await(shutdown())
        case None =>
          await(shutdown())
      }
    } recoverWith {
      case NonFatal(ex) ⇒
        ex.printStackTrace()
        shutdown()
    }

  def shutdown(): Future[Done] = async {
    await(actorSystem.terminate())
    await(locationService.shutdown())
  }

  def commandLineRunner(options: Options): Future[Unit] = {

    def create(): Future[Unit] = async {
      val configData = PathUtils.fromPath(options.inputFilePath.get)
      val configId = await(configService.create(options.relativeRepoPath.get, configData, oversize = options.oversize,
          options.comment))
      println(s"File : ${options.relativeRepoPath.get} is created with id : ${configId.id}")
    }

    def update() = async {
      val configData = PathUtils.fromPath(options.inputFilePath.get)
      val configId   = await(configService.update(options.relativeRepoPath.get, configData, options.comment))
      println(s"File : ${options.relativeRepoPath.get} is updated with id : ${configId.id}")
    }

    def get(): Future[Unit] = async {
      val idOpt = options.id.map(ConfigId(_))

      val configDataOpt = options.date match {
        case Some(date) ⇒ await(configService.get(options.relativeRepoPath.get, date))
        case None       ⇒ await(configService.get(options.relativeRepoPath.get, idOpt))
      }

      configDataOpt match {
        case Some(configData) ⇒
          val outputFile = await(PathUtils.writeToPath(configData, options.outputFilePath.get))
          println(s"Output file is created at location : ${outputFile.getAbsolutePath}")
        case None ⇒
      }
    }

    def exists(): Future[Unit] = async {
      val exists = await(configService.exists(options.relativeRepoPath.get))
      println(s"File ${options.relativeRepoPath.get} exists in the repo? : $exists")
    }

    def delete(): Future[Unit] = async {
      await(configService.delete(options.relativeRepoPath.get))
      println(s"File ${options.relativeRepoPath.get} deletion is completed.")
    }

    def list(): Future[Unit] = async {
      val fileInfoes = await(configService.list())
      fileInfoes.foreach(i ⇒ println(s"${i.path}\t${i.id.id}\t${i.comment}"))
    }

    def history(): Future[Unit] = async {
      val histList = await(configService.history(options.relativeRepoPath.get, options.maxFileVersions))
      histList.foreach(h => println(s"${h.id.id}\t${h.time}\t${h.comment}"))
    }

    def setDefault(): Future[Unit] = async {
      val idOpt: Option[ConfigId] = options.id.map(ConfigId(_))
      await(configService.setDefault(options.relativeRepoPath.get, idOpt))
      println(s"${options.relativeRepoPath.get} file with id:${idOpt.getOrElse("latest")} is set as default")
    }

    def getDefault: Future[Unit] = async {
      val configDataOpt = await(configService.getDefault(options.relativeRepoPath.get))

      configDataOpt match {
        case Some(configData) ⇒
          val outputFile = await(PathUtils.writeToPath(configData, options.outputFilePath.get))
          println(
              s"Default version of repository file: ${options.relativeRepoPath.get} is saved at location: ${outputFile.getAbsolutePath}")
        case None ⇒
      }
    }

    options.op match {
      case "create"     => create()
      case "update"     => update()
      case "get"        => get()
      case "exists"     => exists()
      case "delete"     => delete()
      case "list"       => list()
      case "history"    => history()
      case "setDefault" => setDefault()
      case "getDefault" => getDefault
      case x            => throw new RuntimeException(s"Unknown operation: $x")
    }
  }
}

object ConfigCliApp {

  def main(args: Array[String]): Unit =
    Await.result(new ConfigCliApp(ClusterSettings()).start(args), 5.seconds)

}
