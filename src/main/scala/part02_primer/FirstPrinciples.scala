package part02_primer

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

object FirstPrinciples extends App {
  val system = ActorSystem("first-principles")
  implicit val materializer = ActorMaterializer()(system)

  //Source
  val source = Source(1 to 10)

  //Sink
  val sink = Sink.foreach[Int](println)

  //how connect
  val graph = source.to(sink)
//  graph.run()(materializer)

  //flow (transform elements)
  val flow = Flow[Int].map(x => x + 1)
  val sourceWithFlow = source.via(flow)
  val flowWithSink = flow.to(sink)

//  sourceWithFlow.to(sink).run()
//  source.to(flowWithSink).run()
//  source.via(flow).to(sink).run()

  //nulls are not allowed on source
//  val illegalSource = Source.single[String](null)
//  illegalSource.to(Sink.foreach[String](println))
  //use option instead

  //many different source
  val finiteSource: Source[Int, NotUsed] = Source.single(5)
  val anotherFiniteSource = Source(List(1, 2, 3))

  val emptySource = Source.empty[String]
  val infiniteSource =
    Source(Stream.from(1)) // do not confuse Akka Stream with Collection stream

  //source from future
  import scala.concurrent.ExecutionContext.Implicits.global
  val futureSource = Source.fromFuture(Future(42))

  //SINKS
  val doNothingSink = Sink.ignore
  val forEachSink = Sink.foreach[String](println)
  val headSink = Sink.head[Int] // retrieves the head and closes the stream
  val foldSInk = Sink.fold[Int, Int](0)(_ + _)

  //Flows - usually map to collection operator
  val mapFlow = Flow[Int].map(_ * 10)
  val takeFlow = Flow[Int].take(5)
  // drop, filter
  // NOT flatMap!!!


  // Source -> Flow -> Flow -> ... -> Flow -> Sink
  val doubleFlow = source.via(mapFlow).via(takeFlow).to(sink)
  doubleFlow.run()

  //syntatic sugar
  val mapSource = Source(1 to 10).map(_ + 1) // equivalent to Source(1 to 10).via(Flow[Int].map(_ + 1))
  // run stream directly
  mapSource.runForeach(println) // equivalent to mapSource.to(Sink.forEach[Int](println)).run()

  //OPERATORS = components
  /**
   * Excercise: Stream takes name of Persons then keep first 2 names with names length > 5 chars
   */

  val excerciseSource = Source(List("Antonio", "Io", "Tu", "Pippo", "Sette", "Davide")).filter(_.length > 5).take(2)
  excerciseSource.runForeach(println)

}
