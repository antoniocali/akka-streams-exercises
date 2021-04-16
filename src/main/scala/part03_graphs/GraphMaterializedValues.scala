package part03_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, SinkShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}

import scala.util.{Failure, Success}

object GraphMaterializedValues extends App {
  implicit val system = ActorSystem("graph-materializer")
  implicit val materializer = ActorMaterializer()

  val wordSource = Source(List("akka", "is", "awesome", "rock"))
  val printerSink = Sink.foreach[String](println)
  val counter = Sink.fold[Int, String](0)((count, _) => count + 1)

  val complexWordSink = Sink.fromGraph(
    GraphDSL.create(counter) { implicit builder => counterShape =>
      import GraphDSL.Implicits._

      // step 2
      val broadcast = builder.add(Broadcast[String](2))
      val lowerFlow =
        builder.add(Flow[String].filter(word => word == word.toLowerCase))
      val shortFlow = builder.add(Flow[String].filter(word => word.length < 5))

      // step 3
      broadcast.out(0) ~> lowerFlow ~> printerSink
      broadcast.out(1) ~> shortFlow ~> counterShape

      //step 4
      SinkShape(broadcast.in)

    }
  )

  val resultWordCount = wordSource.toMat(complexWordSink)(Keep.right).run()
  import scala.concurrent.ExecutionContext.Implicits.global
  resultWordCount onComplete {
    case Success(value) => println(s"Words are ${value}")
    case Failure(exception) => println("BOO")
  }

}
