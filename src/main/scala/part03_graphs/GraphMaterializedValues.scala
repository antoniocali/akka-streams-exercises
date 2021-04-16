package part03_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, FlowShape, SinkShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object GraphMaterializedValues extends App {
  implicit val system = ActorSystem("graph-materializer")
  implicit val materializer = ActorMaterializer()

  val wordSource = Source(List("akka", "is", "awesome", "rock", "LOL"))
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
    case Failure(_)     => println("BOO")
  }

  /*

   */

  def enhanceFlow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {
    val sink = Sink.fold[Int, B](0)((counter, _) => counter + 1)
    Flow.fromGraph(
      GraphDSL.create(sink) { implicit builder => sinkShape =>
        import GraphDSL.Implicits._

        val broadcast = builder.add(Broadcast[B](2))
        val originalFlowShape = builder.add(flow)
        originalFlowShape ~> broadcast ~> sinkShape

        FlowShape(originalFlowShape.in, broadcast.out(1))
      }
    )
  }

  val simpleFlow = Flow[Int].map(elem => elem)
  val simpleSource = Source(1 to 42)
  val sinkIgnore = Sink.ignore

  val enhancedFlowRunnable = simpleSource
    .viaMat(enhanceFlow(simpleFlow))(Keep.right)
    .toMat(sinkIgnore)(Keep.left)
    .run()

  enhancedFlowRunnable onComplete {
    case Success(value) => println(s"I've done ${value}")
    case Failure(_) => println("BOo")
  }

}
