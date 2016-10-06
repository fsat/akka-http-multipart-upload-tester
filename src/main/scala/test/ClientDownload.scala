package test

import java.net.URL
import java.nio.file.{Files, Paths, Path}

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.{HttpResponse, Multipart}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import org.reactivestreams.Publisher

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

object ClientDownload {

  private val targetUrl =
    sys.props.get("test.url").map(new URL(_)).getOrElse(TestData.DefaultUrl)


  def doDownload()(implicit mat: ActorMaterializer, ec: ExecutionContext, http: HttpExt): Future[(Path, Option[Path])] = {
    println(s"Uploading bundle.conf from [${TestData.BundleConf}]")
    println(s"Uploading bundle from [${TestData.Bundle}]")

    val downloadUrl = new URL(s"$targetUrl/download")
    println(s"Downloading from [$downloadUrl]")

    val request = Get(s"$downloadUrl")

    def saveToFile(fileName: String, data: Publisher[Array[Byte]]): Future[Path] = {
      val dest = Paths.get("/tmp", fileName)
      Files.deleteIfExists(dest)
      Source.fromPublisher(data).map(ByteString(_))
        .runWith(FileIO.toPath(dest))
        .map { result =>
          if (result.wasSuccessful) {
            println(s"Written to [$dest]")
            dest
          } else
            throw result.getError
        }
    }

    def toByteArrayPublisher(data: Source[ByteString, _]): Publisher[Array[Byte]] =
      data.map(_.toArray).runWith(Sink.asPublisher(fanout = false))

    def bundleFileFromResponse(bundleParts: Seq[(String, String, Multipart.FormData.BodyPart)]): Future[Path] =
      bundleParts match {
        case Seq(entry) if entry._1 == "bundle" =>
          val (_, fileName, bodyPart) = entry
          saveToFile(fileName, toByteArrayPublisher(bodyPart.entity.dataBytes))

        case _ =>
          throw new RuntimeException("Unable to find bundle file in the response body")
      }

    def configFileFromResponse(configParts: Seq[(String, String, Multipart.FormData.BodyPart)]): Future[Option[Path]] =
      configParts.find(_._1 == "configuration") match {
        case Some((_, fileName, bodyPart)) =>
          saveToFile(fileName, toByteArrayPublisher(bodyPart.entity.dataBytes))
              .map(Some(_))

        case _ =>
          Future.successful(None)
      }

    Source.single(request)
      .via(http.outgoingConnection(downloadUrl.getHost, downloadUrl.getPort))
      .mapAsync(1) { response =>
        for {
          formData <- Unmarshal(response.entity.withoutSizeLimit()).to[Multipart.FormData]
          bundleAndOthers <- formData
            .parts
            .map { bodyPart =>
              (
                bodyPart.name,
                bodyPart.filename,
                bodyPart
                )
            }
            .collect {
              case (partName, Some(fileName), bodyPart) =>
                (partName, fileName, bodyPart)
            }
            .prefixAndTail(1)
            .runWith(Sink.head)

          (bundleParts, otherParts) = bundleAndOthers

          bundleFile <- bundleFileFromResponse(bundleParts)

          configAndOthers <- otherParts.prefixAndTail(1).runWith(Sink.head)
          (configParts, remaining) = configAndOthers
          configFile <- configFileFromResponse(configParts)
          _ <- remaining.runWith(Sink.ignore)
        } yield {
          (bundleFile, configFile)
        }
      }
      .runWith(Sink.head)
  }

  def main(args: Array[String]): Unit = {
    System.setProperty("akka.log-dead-letters", "false")
    System.setProperty("akka.log-dead-letters-during-shutdown", "false")

    println("Client starting")

    implicit val system = ActorSystem("test-client")
    implicit val mat = ActorMaterializer()
    import system.dispatcher

    implicit val http = Http()

    Try {
      val reply = Await.result(doDownload(), 30.seconds)
      println(s"\n\nGot response:")
      println(reply)
      println("\n\n")
    }.recover {
      case e =>
        e.printStackTrace(System.err)
    }

    println("Client shutting down...")
    Await.result(system.terminate(), 10.seconds)
    println("Client stopped")
  }

}
