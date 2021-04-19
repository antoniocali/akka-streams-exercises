package part04_techniques

import akka.actor.ActorSystem
import akka.stream.javadsl.Sink
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Source}

import java.util.Date

object AdvancedBackPressured extends App {
  implicit val system = ActorSystem("advanced-back-pressured")
  implicit val materializer = ActorMaterializer()

  //controlled backpressure
  val controlledFlow = Flow[Int].map(_ * 2).buffer(10, OverflowStrategy.dropNew)

  case class PagerEvent(description: String, date: Date, nInstance: Int = 1)
  case class Notification(email: String, pagerEvent: PagerEvent)

  val events =
    List(
      PagerEvent("service", new Date()),
      PagerEvent("service2", new Date()),
      PagerEvent("service3", new Date()),
      PagerEvent("service4", new Date())
    )

  val eventSource = Source(events)

  val oncallEngineer =
    "lol@lol.com" // a fast service for fetchin on call events

  def sendEmail(notification: Notification) = {
    println(
      s"Sending mail ${notification.email} you have an event ${notification.pagerEvent}"
    )
  }

  val notificationSink = Flow[PagerEvent]
    .map[Notification](elem => Notification(oncallEngineer, elem))
    .to(Sink.foreach[Notification](sendEmail))

  // standard
//  eventSource.to(notificationSink).run()

  //Unbackpressured source
  def sendEmailSlow(notification: Notification) = {
    Thread.sleep(1000)
    println(
      s"Sending mail ${notification.email} you have an event ${notification.pagerEvent}"
    )
  }

  val aggragateNotificationFlow =
    Flow[PagerEvent].conflate((event1, event2) => {
      val nInstances = event1.nInstance + event2.nInstance
      PagerEvent(
        s"You have $nInstances that required your attention",
        new Date,
        nInstances
      )
    }).map(resultingEvent => Notification(oncallEngineer, resultingEvent))

//  eventSource.via(aggragateNotificationFlow).async.to(Sink.foreach[Notification](sendEmailSlow)).run()


  //Slow Producers: extrapolate/expand
  import scala.concurrent.duration._
  val slowCounter = Source(Stream.from(1)).throttle(1, 1 second)
  val hungrySink = Sink.foreach[Int](println)
  val extrapolatorFlow = Flow[Int].extrapolate(element => Iterator.from(element))
  val repeater = Flow[Int].extrapolate(element => Iterator.continually(element))
  slowCounter.via(repeater).to(hungrySink).run()

  val expander = Flow[Int].expand(element => Iterator.from(element))
}
