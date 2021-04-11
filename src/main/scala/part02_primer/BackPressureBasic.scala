package part02_primer

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.util.Random

object BackPressureBasic extends App {
  implicit val system = ActorSystem("back-pressure")
  implicit val materializer = ActorMaterializer()

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>
    Thread.sleep(Random.nextInt(1000))
    println(s"Sink: ${x}")
  }

  //not back pressure
  fastSource.to(slowSink)

  //back pressure
  fastSource.async.to(slowSink)

  val simpleFlow = Flow[Int].map { x =>
    println(s"incoming ${x}")
    x + 1
  }

  fastSource.async.via(simpleFlow).async.to(slowSink)

  /*
   reactions to backpressunre (in order):
   - try to slow down if possible
   - buffer elements until there's more demand
   - drop down elements from the buffer if there's a overflow
   - tear down/kill whole stream (failure)
   */

  val bufferedFlow =
    simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropNew)
  fastSource.async.via(bufferedFlow).async.to(slowSink)
  import scala.concurrent.duration._
  fastSource.throttle(10, 1 seconds).runWith(Sink.foreach(println))
}
