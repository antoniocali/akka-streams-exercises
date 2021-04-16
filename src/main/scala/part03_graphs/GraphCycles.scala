package part03_graphs

import akka.actor.ActorSystem
import akka.stream.{
  ActorMaterializer,
  ClosedShape,
  OverflowStrategy,
  UniformFanInShape
}
import akka.stream.scaladsl.{
  Broadcast,
  Flow,
  GraphDSL,
  Merge,
  MergePreferred,
  RunnableGraph,
  Sink,
  Source,
  Zip,
  ZipWith
}

object GraphCycles extends App {
  implicit val system = ActorSystem("graph-cycles")
  implicit val materializer = ActorMaterializer()

  val accelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceshape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val incrementeShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceshape ~> mergeShape ~> incrementeShape
    mergeShape <~ incrementeShape

    ClosedShape
  }
  //Cycle Deadlock
//  RunnableGraph.fromGraph(accelerator).run()

  /*
    Solution 1: MergePreferred
   */

  val correctAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceshape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(MergePreferred[Int](1))
    val incrementeShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceshape ~> mergeShape ~> incrementeShape
    mergeShape.preferred <~ incrementeShape

    ClosedShape
  }

//  RunnableGraph.fromGraph(correctAccelerator).run()

  /*
  Solution 2: Buffered
   */
  val bufferedAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceshape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val repeaterShape =
      builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map { x =>
        println(s"Repeat $x")
        Thread.sleep(1000)
        x
      })

    sourceshape ~> mergeShape ~> repeaterShape
    mergeShape <~ repeaterShape

    ClosedShape
  }
//  RunnableGraph.fromGraph(bufferedAccelerator).run()

  /*
    cycle risk deadlocking:
    - add bounds to the number of elements in the cycle
    boundedness vs liveness
   */

  val fibonacciGraph =
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val zip = builder.add(Zip[Int, Int])
      val mergePreferred = builder.add(MergePreferred[(Int, Int)](1))

      val fiboLogic = builder.add(Flow[(Int, Int)].map { pair =>
        val last = pair._1
        val previous = pair._2
        Thread.sleep(1000)
        (last + previous, last)
      })

      val broadcast = builder.add(Broadcast[(Int, Int)](2))
      val extractLast = builder.add(Flow[(Int, Int)].map[Int](_._1))

      zip.out ~> mergePreferred ~> fiboLogic ~> broadcast ~> extractLast
      broadcast ~> mergePreferred.preferred

      UniformFanInShape(extractLast.out, zip.in0, zip.in1)
    }

  val fiboGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val source1 = builder.add(Source.single(1))
      val source2 = builder.add(Source.single(1))

      val sink = builder.add(Sink.foreach[Int](println))
      val fibo = builder.add(fibonacciGraph)
      source1 ~> fibo.in(0)
      source2 ~> fibo.in(1)
      fibo.out ~> sink

      ClosedShape
    }
  )
  fiboGraph.run()

}
