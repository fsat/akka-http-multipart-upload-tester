package test

import java.net.URL
import java.nio.file.{Paths, Path}

object TestData {
  val DefaultUrl = new URL("http://127.0.0.1:5555/test")
  val BundleConf = resolveFromClasspath("/bundle.conf")
//  val Bundle = resolveFromClasspath("/visualizer-v1.1-930890fc57c1c18e20a1d79a5a3248b0af8bd4c443cf802c762efd93a2844b8b.zip")
  val Bundle = resolveFromClasspath("/dummy-bundle-3e1f22ec0b81843e6273c300f0ba3958e0cdc6f65da58e02e1c9dddd3d3cab29.zip")

  private def resolveFromClasspath(fileName: String): Path =
    Paths.get(TestData.getClass.getResource(fileName).toURI.getPath)
}
