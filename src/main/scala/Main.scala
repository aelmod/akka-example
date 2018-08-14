import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.pattern.{ask, pipe}
import akka.stream.ActorMaterializer
import akka.util.{ByteString, Timeout}

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future}

object Main extends App {
  implicit val actorSystem = ActorSystem("my-actor-system")
  implicit val dispatcher = actorSystem.dispatcher


  val translation = actorSystem.actorOf(Props[TranslationActor], "translation")

  implicit val timeout = Timeout(3 seconds)

  val program = for {
    t1 <- translation ? "I"
    t2 <- translation ? "HZ"
    t3 <- translation ? 123
    _ <- actorSystem.terminate()
  } yield (t1, t2, t3)

  val programRes = Await.result(program, Duration.Inf)
  println(programRes._1)
  println(programRes._2)
  println(programRes._3)
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
    case s: String =>
      responseFuture(s)
        .flatMap(_.entity.dataBytes.runFold(ByteString(""))(_ ++ _))
        .map(res => Right(res.decodeString("utf-8")))
        .recover({
          case t: Throwable => Left(t.getMessage)
        })
        .pipeTo(sender)

    case any =>
      sender ! Left(any.getClass.getName -> "ERROR")
  }
}
