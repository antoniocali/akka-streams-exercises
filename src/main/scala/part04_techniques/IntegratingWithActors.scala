package part04_techniques

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout

object IntegratingWithActors extends App {
  implicit val system = ActorSystem("integrating-actors")
  implicit val materializer = ActorMaterializer()

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case s: String =>
        log.info(s"Just received a string: $s")
        sender() ! s"$s$s"
      case n: Int =>
        log.info(s"Just received a number $n")
        sender() ! n + n

    }
  }

  val simpleActor: ActorRef = system.actorOf(Props[SimpleActor], "simple-actor")
  val numberSource = Source(1 to 10)

  // actor as a flow
  // based on ASK pattern
  import scala.concurrent.duration._
  implicit val timeout: Timeout = Timeout(2 seconds)
  val actorBasedFlow = Flow[Int].ask[Int](parallelism = 10)(simpleActor)
//  numberSource.via(actorBasedFlow).to(Sink.foreach(println)).run()
//  numberSource.ask[Int](simpleActor).to(Sink.ignore).run()

  /*
   Actor as a source
   */
  val actorPoweredSource = Source.actorRef[Int](
    bufferSize = 10,
    overflowStrategy = OverflowStrategy.dropHead
  )
  // we use the materialized Value as actorRef to use to send messages
  val matActorRef: ActorRef = actorPoweredSource
    .to(Sink.foreach[Int](elem => println(s"actor got a number ${elem}")))
    .run()
  matActorRef ! 10
  // terminating the stream
  matActorRef ! 23
  matActorRef ! akka.actor.Status.Success("Complete")

  /*
    Actor as destination/sink:
    -an init message
    -an ack message to confirm the reception
    -a complete message
    -a function to generate a message in case the stream throw an exception
   */
  case object StreamInit
  case object StreamAck
  case object StreamComplete
  case class StreamFail(ex: Throwable)

  class DestinationActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case StreamInit =>
        log.info("stream initialized")
        sender() ! StreamAck
      case StreamComplete =>
        log.info("stream completed")
        context.stop(self)
      case StreamFail(ex) =>
        log.warning(s"stream failed ${ex}")
      case message =>
        log.info(s"message: $message")
        sender() ! StreamAck
    }
  }

  val destinationActor =
    system.actorOf(Props[DestinationActor], "destination-actor")
  val actorPoweredSink = Sink.actorRefWithAck[Int](
    ref = destinationActor,
    onInitMessage = StreamInit,
    ackMessage = StreamAck,
    onCompleteMessage = StreamComplete,
    onFailureMessage = throwable => StreamFail(throwable)
  )

  Source(1 to 10).to(actorPoweredSink).run()

  //Sink.actorRef doesn't provide backpressured

}
