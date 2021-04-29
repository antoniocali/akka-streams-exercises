package part04_techniques

import akka.actor.ActorSystem
import akka.stream.Supervision.{Resume, Stop}
import akka.stream.{ActorAttributes, ActorMaterializer}
import akka.stream.scaladsl.{RestartSource, Sink, Source}

import scala.util.Random

object FaultTolerance extends App {
  implicit val system = ActorSystem("fault-tolerance")
  implicit val materialzer = ActorMaterializer()

  //Logging
  val faultySource =
    Source(1 to 10).map(e => if (e == 6) throw new RuntimeException else e)

//  faultySource.log("trackingElement").to(Sink.ignore).run()

  //gracefuly terminate stream
  faultySource
    .recover {
      case _: RuntimeException => Int.MinValue
    }
    .log("graceful-source")
    .to(Sink.ignore)
//    .run()

  //Recover with another stream
  faultySource
    .recoverWithRetries(
      3,
      {
        case _: RuntimeException => Source(90 to 99)
      }
    )
    .log("recovery")
    .to(Sink.ignore)
//    .run()

  // backoff supervision
  import scala.concurrent.duration._
  val restartSource = RestartSource.onFailuresWithBackoff(
    minBackoff = 1 second,
    maxBackoff = 30 seconds,
    randomFactor = 0.2
  )(() => {
    val randomNumber = new Random().nextInt(20)
    Source(1 to 10).map(elem =>
      if (elem == randomNumber) throw new RuntimeException else elem
    )
  })
  restartSource.log("restart-backoff").to(Sink.ignore)

  //supervision strategy
  val numbers = Source(1 to 20)
    .map(elem =>
      if (elem == 13) throw new RuntimeException("bad luck") else elem
    )
    .log("supervision")
//    .run()

  val supervisorNumbers =
    numbers.withAttributes(ActorAttributes.supervisionStrategy {
      //Resume: it skips the faulty element
      //Stop: stop the stream
      //Restart: same as resume but clears internal state of component
      case _: RuntimeException => Resume
      case _                   => Stop
    })

  supervisorNumbers.to(Sink.ignore).run()
}
