package part02_primer

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object MaterializedStream extends App {
  implicit val system = ActorSystem("materialized-system")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val simpleGraph = Source(1 to 10).to(Sink.foreach[Int](println))
//  val simpleMaterializedValue: NotUsed = simpleGraph.run()

  val source = Source(1 to 10)
  val sink: Sink[Int, Future[Int]] = Sink.reduce[Int]((a, b) => a + b)
//  val someFuture: Future[Int] = source.runWith(sink)

//  val result: Unit = someFuture onComplete {
//    case Success(value)     => println(s"Sum of $value")
//    case Failure(exception) => println(s"Error $exception")
//  }

  //choosing materializing value
  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(_ + 1)
  val simpleSink = Sink.foreach[Int](println)
  val graph =
    simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)

//  graph.run() onComplete {
//    case Success(value)     => println("Stream finite")
//    case Failure(exception) => println(s"Failed with $exception")
//  }

  //sugars
//  val sum: Future[Int] =
//    Source(1 to 10).runWith(
//      Sink.reduce[Int](_ + _)
//    ) // equivalent to source.to(Sink.reduce)(Keep.right)
//  Source(1 to 10).runReduce[Int](_ + _) // same

  //backwards
//  Sink.foreach[Int](println).runWith(Source(1 to 10))

  //bothways
//  Flow[Int].map(x => x * 2).runWith(simpleSource, simpleSink)

  //exercise
  // return last element of the source
  val ex: Future[Int] =
    Source(1 to 10).toMat(Sink.reduce[Int]((_, b) => b))(Keep.right).run()

  ex onComplete {
    case Success(value)     => println(s"last value is $value")
    case Failure(exception) => println("error")
  }

  val mySource = Source(List("Hello this is antonio", "O b y", "d c s"))
  val mySink = Sink.fold[Int, String](0)((acc, newSentence) => acc + newSentence.split(" ").length)
   mySource.toMat(mySink)(Keep.right).run() onComplete {
     case Success(value) => println(s"I have $value words")
     case Failure(value) => println(value)
   }

//  val myFlow = Flow[String].map[List[String]](elem => elem.split(" ").toList).


}
