package csw.contract.generator

import java.nio.file.{Files, Paths}

import csw.contract.generator.codecs.ContractCodecs
import csw.contract.generator.models.Services
import io.bullet.borer.Encoder

object FilesGenerator extends ContractCodecs {

  def generate(services: Services, outputPath: String): Unit = {
    services.data.foreach {
      case (serviceName, service) =>
        service.endpoints.foreach {
          case (endpointName, endpoint) => writeData(s"$outputPath/$serviceName/endpoints", endpointName, endpoint)
        }
        service.models.foreach {
          case (modelName, model) => writeData(s"$outputPath/$serviceName/models", modelName, model)
        }
    }
  }

  def writeData[T: Encoder](dir: String, fileName: String, data: T): Unit = {
    Files.createDirectories(Paths.get(dir))
    Files.writeString(Paths.get(dir, s"$fileName.json"), JsonHelper.toJson(data))
  }
}