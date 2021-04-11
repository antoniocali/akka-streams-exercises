package part02_primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

object OperatorFusion extends App {
  implicit val system = ActorSystem("operator-fusion")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val simpleSource = Source(1 to 1000)
  val simpleFlow = Flow[Int].map(_ + 1)
  val simpleFlow2 = Flow[Int].map(_ * 10)
  val simpleSink = Sink.foreach[Int](println)

  //  this run on same actor (operator/component Fusion)
  simpleSource.via(simpleFlow).via(simpleFlow).to(simpleSink)

  // complex operators
  val complexFlow = Flow[Int].map { x =>
    Thread.sleep(1000)
    x + 1
  }
  val complexFlow2 = Flow[Int].map { x =>
    Thread.sleep(1000)
    x * 10
  }

  simpleSource.via(complexFlow).via(complexFlow2).to(simpleSink)

  //async boundaries
  simpleSource
    .via(complexFlow)
    .async // run on one actor
    .via(complexFlow2)
    .async // on a second actor
    .to(simpleSink)
//    .run() // third actor

  //ordering guarantess

  Source(1 to 3)
    .map { element =>
      println(s"Flow A: $element")
      element
    }
    .async
    .map { element =>
      println(s"Flow B: $element")
      element
    }
    .async
    .map { element =>
      println(s"Flow C: $element")
      element
    }
    .async
    .to(Sink.ignore)
    .run()
}
