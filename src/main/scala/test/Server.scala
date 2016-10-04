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
        case (Seq(bundleConfPart, bundlePart), remainingParts) =>
          println("Extracting bundle.conf from the body parts - combining remaining parts as one source")
          bundleConfPart -> (Source.single(bundlePart) ++ remainingParts)
      }
    }

    def writeToFile(requestEntity: HttpEntity, destDir: Path)(implicit ec: ExecutionContext, materializer: Materializer): Future[Path] = {
      val tempFilePath = createTempBundleConf(destDir).toPath
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

    def createTempBundleConf(destDir: Path): File = {
      Files.createDirectories(destDir)
      val bundleConf = destDir.resolve("bundle.conf")
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
                  splitParts <- multiPartFormData.parts.prefixAndTail(2).runWith(Sink.head)
                  (bundleConfPart, remainingParts) = extractBundleConf(splitParts)
                  bundleConfSaved <- writeToFile(bundleConfPart.entity, workDir)
                  _ <- remainingParts.runWith(Sink.ignore)
                } yield {
                  // I know using Source.fromFile is horrid, but I'm just trying to do something quick to display the saved bundle.conf contents
                  val fileContent = scala.io.Source.fromFile(bundleConfSaved.toFile).getLines().mkString("\n")
                  println("Saved bundle.conf:")
                  println(fileContent)
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
