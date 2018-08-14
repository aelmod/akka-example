import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main extends App {
  val actorSystem = ActorSystem("my-actor-system")
  val translation: ActorRef = actorSystem.actorOf(Props[TranslationActor], "translation")

  Await.ready(actorSystem.terminate(), Duration.Inf)
}

class TranslationActor extends Actor {
  override def receive: Receive = {
    case "sasay" => println("sasayu")
  }
}
