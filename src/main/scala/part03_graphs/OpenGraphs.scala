package part03_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{
  ActorMaterializer,
  ClosedShape,
  FlowShape,
  SinkShape,
  SourceShape
}
import akka.stream.scaladsl.{
  Broadcast,
  Concat,
  Flow,
  GraphDSL,
  RunnableGraph,
  Sink,
  Source
}

object OpenGraphs extends App {
  implicit val system = ActorSystem("open-graph")
  implicit val materializer = ActorMaterializer()
  /*
  composite source that concates 2 sources:
  -emits all the elements from first source and the all the element form the second
   */

  val source1 = Source(1 to 10)
  val source2 = Source(41 to 100)
  val sourceGraph = Source.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      //step 2 - declaring components
      val concat = builder.add(Concat[Int](2))
      //step 3 - tying together
      source1 ~> concat
      source2 ~> concat
      //step 4
      SourceShape(concat.out)
    }
  )
  sourceGraph.to(Sink.foreach(println)).run()

  /*
  one source but both sinks
   */

  val sink1 = Sink.foreach[Int](elem => println(s"sink1 : $elem"))
  val sink2 = Sink.foreach[Int](elem => println(s"sink2 : $elem"))

  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](2))
      broadcast ~> sink1
      broadcast ~> sink2
      SinkShape(broadcast.in)
    }
  )

  source1.to(sinkGraph).run()

  val flow1 = Flow[Int].map(_ + 1)
  val flow2 = Flow[Int].map(_ * 10)

  val flowGraph = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val incrementerShape = builder.add(flow1)
      val multiplierShape = builder.add(flow2)

      incrementerShape ~> multiplierShape
      FlowShape(incrementerShape.in, multiplierShape.out)
    }
  )

  Source[Int](1 to 10).via(flowGraph).to(Sink.foreach(print)).run()

}
