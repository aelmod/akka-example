import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, _}

object Main extends App {
  val actorSystem = ActorSystem("my-actor-system")
  implicit val dispatcher = actorSystem.dispatcher


  val translation = actorSystem.actorOf(Props[TranslationActor], "translation")


  implicit val timeout = Timeout(5 seconds)

  translation ? "I" foreach println
  translation ? "HZ" foreach println
  translation ? 123 foreach println


  Await.ready(actorSystem.terminate(), Duration.Inf)
}

class TranslationActor extends Actor {
  val translations = Map(
    "I" -> "Я",
    "Have" -> "Маю"
  )

  override def receive: Receive = {
    case s: String => sender ! s -> translations.getOrElse(s, "Translation not found")
    case any => sender ! any.getClass.getName -> "ERROR"
  }
}
