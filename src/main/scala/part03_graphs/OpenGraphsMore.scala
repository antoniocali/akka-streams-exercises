package part03_graphs

import akka.actor.ActorSystem
import akka.stream.{
  ActorMaterializer,
  ClosedShape,
  FanOutShape2,
  UniformFanInShape
}
import akka.stream.scaladsl.{
  Broadcast,
  Flow,
  GraphDSL,
  RunnableGraph,
  Sink,
  Source,
  ZipWith
}

import java.util.Date

object OpenGraphsMore extends App {
  implicit val system = ActorSystem("more-open-graph")
  implicit val materializer = ActorMaterializer()

  val max3StaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    // step 2 -operators
    val max1 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))
    val max2 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))
    // step 3 - connect
    max1.out ~> max2.in0
    //shape
    UniformFanInShape(max2.out, max1.in0, max1.in1, max2.in1)
  }

  val source1 = Source(1 to 10)
  val source2 = Source((1 to 10).map(_ => 5))
  val source3 = Source((1 to 10).reverse)

  val sink = Sink.foreach[Int](x => println(s"max is: $x"))

  val max3RunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      //step 2 - shapes
      val max3Shape = builder.add(max3StaticGraph)

      source1 ~> max3Shape.in(0)
      source2 ~> max3Shape.in(1)
      source3 ~> max3Shape.in(2)
      max3Shape.out ~> sink

      //step 4
      ClosedShape
    }
  )

  max3RunnableGraph.run()

  /*
      Non uniform fan out shape
      example: bank transaction, and it's suspicous if > 10k
      Streams componet with input as txd and output for transaction and 2 output:
      output 1. txn unmodified
      output 2. only suspicious txn id
   */

  case class Txd(
      id: String,
      source: String,
      recipient: String,
      amount: Int,
      date: Date
  )
  val txdSource = Source(
    List(
      Txd("id1", "antonio", "jim", 50, new Date()),
      Txd("id2", "jim", "antonio", 150, new Date()),
      Txd("id3", "antonio", "pippo", 10050, new Date()),
      Txd("id4", "pippo", "donald", 100050, new Date()),
      Txd("id5", "donald", "jim", 300, new Date())
    )
  )

  val bankSink = Sink.foreach[Txd](println)
  val suspiciousSink =
    Sink.foreach[String](txdId => println(s"suspicious txd: $txdId"))

  val suspiciousTxnStaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    // step 2 - shapes
    val broadcast = builder.add(Broadcast[Txd](2))
    val suspTxdFilter =
      builder.add(Flow[Txd].filter(elem => elem.amount >= 10000))
    val getId = builder.add(Flow[Txd].map[String](elem => elem.id))
    // step 3 - tying shape
    broadcast.out(0) ~> suspTxdFilter ~> getId

    // step 4 - the shape
    new FanOutShape2[Txd, Txd, String](
      broadcast.in,
      broadcast.out(1),
      getId.out
    )
  }

  val suspiciousTxnRunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // step 2
      val suspiciousTxnShape = builder.add(suspiciousTxnStaticGraph)

      // step 3
      txdSource ~> suspiciousTxnShape.in
      suspiciousTxnShape.out0 ~> bankSink
      suspiciousTxnShape.out1 ~> suspiciousSink

      ClosedShape
    }
  )
  suspiciousTxnRunnableGraph.run()
}
