package part03_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, BidiShape, ClosedShape}
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}

object BidirectionalFlow extends App {
  implicit val system = ActorSystem("bidirectional-flow")
  implicit val materializer = ActorMaterializer()

  /*
  example: cryptography
   */
  def encrypt(n: Int)(s: String): String = s.map(c => (c + n).toChar)
  println(encrypt(3)("akka"))

  def decrypt(n: Int)(s: String): String = s.map(c => (c - n).toChar)

  //BidiFlow
  val bidiCryptoStaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val encryptionFlowShape = builder.add(Flow[String].map(encrypt(3)))
    val decryptionFlowShape = builder.add(Flow[String].map(decrypt(3)))

//    BidiShape(encryptionFlowShape.in, encryptionFlowShape.out, decryptionFlowShape.in, decryptionFlowShape.out)
    BidiShape.fromFlows(encryptionFlowShape, decryptionFlowShape)
  }

  val unencryptedStrings = List("hello", "my", "name", "is", "antonio")
  val unencryptedSource = Source(unencryptedStrings)
  val encryptedSource = Source(unencryptedStrings.map(encrypt(3)))

  val cryptoBidiGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val unencryptedSourceShape = builder.add(unencryptedSource)
      val encryptedSourceShape = builder.add(encryptedSource)

      val bidi = builder.add(bidiCryptoStaticGraph)
      val encryptedSink =
        builder.add(Sink.foreach[String](elem => println(s"Encrypted ${elem}")))
      val decryptedSink =
        builder.add(Sink.foreach[String](elem => println(s"Decrypted ${elem}")))

      unencryptedSourceShape ~> bidi.in1; bidi.out1 ~> encryptedSink
      decryptedSink <~ bidi.out2; bidi.in2 <~ encryptedSourceShape

      ClosedShape
    }
  )
  cryptoBidiGraph.run()

}
