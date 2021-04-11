package part01recap

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{
  Actor,
  ActorLogging,
  ActorSystem,
  OneForOneStrategy,
  PoisonPill,
  Props,
  Stash,
  SupervisorStrategy
}
import akka.pattern.ask
import akka.util.Timeout

import scala.util.{Failure, Success}

object Recap extends App {
  class SimpleActor extends Actor with ActorLogging with Stash {
    override def receive: Receive = {
      case "stashThis" => stash()
      case "unstash" =>
        unstashAll()
        context.become(anotherHandler)
      case "change" => context.become(anotherHandler)
      case "createChild" =>
        val childActor = context.actorOf(Props[SimpleActor], "mychild")
        childActor ! "hello"
      case "question" =>
        log.info("Received question")
        sender() ! "createChild"
      case message => log.info(s"I've received $message")
    }

    def anotherHandler: Receive = {
      case message => log.info(s"A different type of $message")
    }

    override def preStart(): Unit = {
      log.info("I'm starting")
    }

    override def postStop(): Unit = {
      log.info("I'm dying")
    }

    override def supervisorStrategy: SupervisorStrategy =
      OneForOneStrategy() {
        case _: RuntimeException => Restart
        case _                   => Stop
      }
  }

  //actor encapsulation (actor via actor system)
  val system = ActorSystem("akka-recap")
  val actor = system.actorOf(Props[SimpleActor], "simple-actor")

  // sending a message
  actor ! "hello"

  /*
    -message are sent async
    -actor will handle on some thread
    -many actors can share few threads
    -each message is process atomically (never race condition)
    -no need for locking
   */

  //actor can spawn other actors
  //guardians :
  //  - /system
  //  - /user
  //  - / = root guardian

  //lifecycle: start, stop, suspend, resume, restart
//  actor ! PoisonPill

  //supervision: how parent react to child dead

  // configur Akka Infrastructure: dispatcher, routers, mailbox
  // Schedulers

  import scala.concurrent.duration._
  import system.dispatcher
  system.scheduler.scheduleOnce(2 seconds) {
    actor ! "createChild"
  }
  implicit val timeout: Timeout = Timeout(5 seconds)
  // akka pattern: final state machine + ask pattern
  val future = actor ? "question"

  import akka.pattern.pipe

  val anotherActor = system.actorOf(Props[SimpleActor], "anotherSimpleActor")
  future.mapTo[String].pipeTo(anotherActor)



}
