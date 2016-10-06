package test

import java.io.File
import java.nio.file.{Files, Path, Paths}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCodes, HttpResponse, HttpEntity, Multipart}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{Materializer, ActorMaterializer}
import akka.stream.scaladsl.{FileIO, Source, Sink}

import scala.concurrent.{Future, ExecutionContext, Await}
import scala.concurrent.duration._

object Server {
  val tmpDir = Paths.get(sys.props.get("java.io.tmpdir").getOrElse("/tmp"))
  val workDir = Files.createTempDirectory(tmpDir, "test-server")

  def startServer(): Unit = {
    implicit val system = ActorSystem("test-server")
    implicit val mat = ActorMaterializer()
    import system.dispatcher

    println(s"Server work directory [$workDir]")

    def extractBundleConf(splitParts: (Seq[Multipart.FormData.BodyPart], Source[Multipart.FormData.BodyPart, _])): (Multipart.FormData.BodyPart, Source[Multipart.FormData.BodyPart, _]) = {
      println("Extracting bundle.conf from the body parts")
      splitParts match {
        case (Seq(bundleConfPart), remainingParts) =>
          println("Extracting bundle.conf from the body parts - combining remaining parts as one source")
          bundleConfPart -> remainingParts
      }
    }

    def writeToFile(requestEntity: HttpEntity, destDir: Path, fileName: String)(implicit ec: ExecutionContext, materializer: Materializer): Future[Path] = {
      val tempFilePath = createTempFile(destDir, fileName).toPath
      val tempFileSink = FileIO.toPath(tempFilePath)
      println(s"About to write bundle.conf to [$tempFilePath] ...")
      requestEntity.dataBytes
        .runWith(tempFileSink)
        .map { result =>
          if (result.wasSuccessful) {
            println(s"Written bundle.conf to [$tempFilePath]")
            tempFilePath
          } else
            throw result.getError
        }
    }

    def createTempFile(destDir: Path, fileName: String): File = {
      Files.createDirectories(destDir)
      val bundleConf = destDir.resolve(fileName)
      Files.deleteIfExists(bundleConf)
      Files.createFile(bundleConf).toFile
    }


    // format: OFF
      val route =
        pathPrefix("test") {
          post {
            extractRequest { request =>
              complete {
                for {
                  multiPartFormData <- Unmarshal(request.entity).to[Multipart.FormData]
                  splitParts <- multiPartFormData.parts.prefixAndTail(1).runWith(Sink.head)
                  (bundleConfPart, remainingParts) = extractBundleConf(splitParts)

                  // When consuming multipart, it's important to materialize substream from entity bytes before moving on
                  // to the next bodypart. If this is not done, we will have substream timeout error.
                  bundleConfSaved <- writeToFile(bundleConfPart.entity, workDir, "saved-bundle.conf")

                  (secondPart, rest) <- remainingParts.prefixAndTail(1).runWith(Sink.head)

                  bundleZipSaved <- writeToFile(secondPart.head.entity, workDir, "saved-bundle.zip")

                  _ <- rest.map(_.entity.dataBytes.runWith(Sink.ignore)).runWith(Sink.ignore)
                } yield {
                  // I know using Source.fromFile is horrid, but I'm just trying to do something quick to display the saved bundle.conf contents
                  val fileContent = scala.io.Source.fromFile(bundleConfSaved.toFile).getLines().mkString("\n")
                  println("Saved bundle.conf:")
                  println(fileContent)
                  println("")

                  println("Saved bundle zip location:")
                  println(bundleZipSaved.toAbsolutePath)
                  println("")

                  if (fileContent.nonEmpty)
                    HttpResponse(StatusCodes.OK, entity = bundleConfSaved.toAbsolutePath.toString)
                  else
                    HttpResponse(StatusCodes.InternalServerError, entity = "Uploaded bundle.conf is empty, but there should be some content")
                }
              }
            }
          }
        }
      // format: ON

    println("Starting HTTP listener...")
    val server = Http(system).bindAndHandle(route, TestData.DefaultUrl.getHost, TestData.DefaultUrl.getPort)
    Await.ready(server, 10.seconds)
    println("HTTP listener started")

  }

  def main(args: Array[String]): Unit = {
    System.setProperty("akka.log-dead-letters", "false")
    System.setProperty("akka.log-dead-letters-during-shutdown", "false")
    System.setProperty("akka.http.parsing.max-content-length", "200m")

    startServer()
  }
}
