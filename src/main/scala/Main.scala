import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.pattern.{ask, pipe}
import akka.persistence.PersistentActor
import akka.stream.ActorMaterializer
import akka.util.{ByteString, Timeout}

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future}


object Main extends App {
  implicit val actorSystem = ActorSystem("my-actor-system")
  implicit val dispatcher = actorSystem.dispatcher

  val translationActor = "translation"
  val translation = actorSystem.actorOf(Props[TranslationActor], translationActor)

  implicit val timeout = Timeout(3 seconds)

  val translationPersistent: ActorRef = actorSystem.actorOf(Props[TranslationPersistentActor], "translation-persistent")

  val program = for {
    t1 <- translationPersistent ? TranslateCommand("I")
    t2 <- translationPersistent ? TranslateCommand("HZ")
    _ <- actorSystem.terminate()
  } yield (t1, t2)

  val programRes = Await.result(program, Duration.Inf)
  println(programRes._1)
  println(programRes._2)
}

class TranslationActor extends Actor with ActorLogging {
  final implicit val system = context.system
  final implicit val ec = system.dispatcher
  final implicit val materializer = ActorMaterializer()

  def responseFuture(phrase: String): Future[HttpResponse] =
    Http()
      .singleRequest(HttpRequest(
        uri = "http://api.lingualeo.com/gettranslates?port=1001",
        method = HttpMethods.POST,
        entity = FormData("word" -> phrase).toEntity(HttpCharsets.`UTF-8`)
      ))

  override def receive: Receive = {
    case s: String => {
      responseFuture(s)
        .flatMap(_.entity.dataBytes.runFold(ByteString(""))(_ ++ _))
        .map(res => Right(res.decodeString("utf-8")))
        .recover({
          case t: Throwable => Left(t.getMessage)
        })
        .map(r => {
          println(r)
          r
        })
        .pipeTo(sender)
    }

    case any =>
      sender ! Left(any.getClass.getName -> "ERROR")
  }
}

case class TranslatedEvent(phrase: String, translations: String)

case class TranslateReceivedEvent(phrase: String, translations: String, sender: ActorPath)

case class TranslateCommand(phrase: String)

case class ExampleState(events: List[String] = Nil) {
  def updated(evt: TranslatedEvent): ExampleState = copy(evt.phrase :: events)

  def size: Int = events.length

  override def toString: String = events.reverse.toString
}

class TranslationPersistentActor extends PersistentActor {
  final implicit val ec = context.dispatcher
  var state = ExampleState()

  def updateState(event: TranslatedEvent): Unit =
    state = state.updated(event)

  override def receiveRecover: Receive = {
    case e: TranslatedEvent => updateState(e)
  }

  implicit val timeout = Timeout(10 seconds)

  override def receiveCommand: Receive = {
    case TranslateCommand(p) => {
      val path = sender.path
      context.system.actorSelection("/user/" + Main.translationActor) ? p collect {
        case translations: Either[String, String] if translations.isRight => {
          self ! TranslateReceivedEvent(p, translations.right.get, path)
        }
        case _ => println("vsya huinya")
      }
    }
    case res: TranslateReceivedEvent => {
      persist(TranslatedEvent(res.phrase, res.translations)) { event =>
        updateState(event)
        context.system.actorSelection(res.sender) ! state
      }
    }
  }

  override def persistenceId: String = "translator-1"
}