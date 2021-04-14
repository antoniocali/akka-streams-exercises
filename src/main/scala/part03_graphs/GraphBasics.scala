package part03_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{
  Balance,
  Broadcast,
  Flow,
  GraphDSL,
  Merge,
  RunnableGraph,
  Sink,
  Source,
  Zip
}

object GraphBasics extends App {
  implicit val system = ActorSystem("graph-basic")
  implicit val materializer = ActorMaterializer()

  val input = Source(1 to 10)
  val incrementer = Flow[Int].map(_ + 1)
  val multiplier = Flow[Int].map(_ * 10)

  val output = Sink.foreach[(Int, Int)](println)
  val simpleOutput = Sink.foreach[Int](println)

  // step 1 - setting up fundamentals for graph
  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() {
      implicit builder: GraphDSL.Builder[
        NotUsed
      ] => // builder -> Mutable data structure
        import GraphDSL.Implicits._ //brings operators

        // step 2 - add necessary components
        val broadcast =
          builder.add(
            Broadcast[Int](2)
          ) // fan - out operator (single input and 2 output)

        val zip =
          builder.add(
            Zip[Int, Int]
          ) // fan-in operator (2 inputs [Int, Int], 1 output)

        // step 3 - tying up components
        input ~> broadcast
        broadcast.out(0) ~> incrementer ~> zip.in0
        broadcast.out(1) ~> multiplier ~> zip.in1
        zip.out ~> output

        //step 4 - return a closed shape
        ClosedShape //Freeze the builder's shape

        // Shape
    } // Graph
  ) // runnableGraph (as we built so far)

//  graph.run()

  /**
    * 1 - one source to two different sinks
    */

  val graphEx1 = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))

      input ~> broadcast
      broadcast.out(0) ~> incrementer.to(simpleOutput)
      broadcast.out(1) ~> multiplier.to(simpleOutput)

      ClosedShape
    }
  )

//  graphEx1.run()

  /*
  ex 2
   */

  val sink1 = Sink.foreach[Int](elem => println(s"first sink: $elem"))
  val sink2 = Sink.foreach[Int](elem => println(s"second sink: $elem"))
  import scala.concurrent.duration._
  val fastSource = input.throttle(10, 1 seconds)
  val slowSource = input.throttle(2, 1 seconds)

  val graph2 = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      // operators
      val merge = builder.add(Merge[Int](2))
      val balance = builder.add(Balance[Int](2))

      fastSource ~> merge.in(0)
      slowSource ~> merge.in(1)

      merge ~> balance

      balance.out(0) ~> sink1
      balance.out(1) ~> sink2


      ClosedShape

    }
  )

  graph2.run()
}
