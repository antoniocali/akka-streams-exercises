package part04_techniques

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import java.util.Date
import scala.concurrent.Future

object IntegratingWithExternalServices extends App {
  implicit val system = ActorSystem("external-source")
  implicit val materializer = ActorMaterializer()
  //Important have a different ExecutionContext
  implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")

  def genericExtService[A, B](element: A): Future[B] = ???

  //example: simplified PagerDuty
  case class PagerEvent(application: String, description: String, date: Date)

  val eventSource = Source(
    List(
      PagerEvent("akka", "broke", new Date()),
      PagerEvent("akka2", "broke2", new Date()),
      PagerEvent("akka3", "broke3", new Date())
    )
  )

  object PagerService {
    private val person = List("Antonio", "Daniel")
    private val emails =
      Map("Antonio" -> "antonio@antonio.com", "Daniel" -> "daniel@daniel.com")

    def processEvent(pagerEvent: PagerEvent): Future[String] =
      Future {
        val engineerIndex =
          (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % person.length
        val engineer = person(engineerIndex.toInt)
        val engineerMail = emails(engineer)

        println(s"Sending engineer $engineerMail a notificaiton: $pagerEvent")
        Thread.sleep(1000)
        engineerMail

      }
  }
  //guarantee relative order of elements
  val pagedEngineerEmails =
    eventSource.mapAsync[String](parallelism = 1)(event =>
      PagerService.processEvent(event)
    )
  val pagedEmailSink = Sink.foreach[String](email => println(s"sent to $email"))
//  pagedEngineerEmails.to(pagedEmailSink).run()

  class PagerActor extends Actor with ActorLogging {
    private val person = List("Antonio", "Daniel")
    private val emails =
      Map("Antonio" -> "antonio@antonio.com", "Daniel" -> "daniel@daniel.com")

    def processEvent(pagerEvent: PagerEvent): String = {
      val engineerIndex =
        (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % person.length
      val engineer = person(engineerIndex.toInt)
      val engineerMail = emails(engineer)

      println(s"Sending engineer $engineerMail a notificaiton: $pagerEvent")
      Thread.sleep(1000)
      engineerMail

    }

    override def receive: Receive = {
      case pagerEvent: PagerEvent => sender() ! processEvent(pagerEvent)
    }
  }
  import scala.concurrent.duration._
  implicit val timeout: Timeout = Timeout(4 seconds)
  val pagerActor = system.actorOf(Props[PagerActor], "pager-actor")
  val alternativePagedEngineerEmail =
    eventSource.mapAsync[String](parallelism = 4)(elem => (pagerActor.?(elem)(timeout)).mapTo[String])

  alternativePagedEngineerEmail.to(pagedEmailSink).run()
}
