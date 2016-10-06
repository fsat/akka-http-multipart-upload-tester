package test

import java.net.URL

import akka.actor.ActorSystem
import akka.http.scaladsl.{HttpExt, Http}
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpEntity.IndefiniteLength
import akka.http.scaladsl.model.{HttpResponse, RequestEntity, MediaTypes, Multipart}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, FileIO, Source}

import scala.concurrent.{Future, ExecutionContext, Await}
import scala.concurrent.duration._
import scala.util.Try

object ClientUpload {

  private val targetUrl =
    sys.props.get("test.url").map(new URL(_)).getOrElse(TestData.DefaultUrl)


  def doUpload()(implicit mat: ActorMaterializer, ec: ExecutionContext, http: HttpExt): Future[HttpResponse] = {
    println(s"Uploading bundle.conf from [${TestData.BundleConf}]")
    println(s"Uploading bundle from [${TestData.Bundle}]")

    val uploadUrl = new URL(s"$targetUrl/upload")
    println(s"Uploading to [$uploadUrl]")

    val multiPartForm = Multipart.FormData(Source(List(
      Multipart.FormData.BodyPart(
        "bundleConf",
        IndefiniteLength(MediaTypes.`application/octet-stream`, FileIO.fromPath(TestData.BundleConf)),
        Map("filename" -> "bundle.conf")
      ),
      Multipart.FormData.BodyPart(
        "bundle",
        IndefiniteLength(MediaTypes.`application/octet-stream`, FileIO.fromPath(TestData.Bundle)),
        Map("filename" -> s"${TestData.Bundle.getFileName}")
      )
    )))

    val requestEntity = Await.result(Marshal(multiPartForm).to[RequestEntity], 3.seconds)

    val request = Post(s"$uploadUrl", requestEntity)

    Source.single(request)
      .via(http.outgoingConnection(uploadUrl.getHost, uploadUrl.getPort))
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
      val reply = Await.result(doUpload(), 30.seconds)
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
