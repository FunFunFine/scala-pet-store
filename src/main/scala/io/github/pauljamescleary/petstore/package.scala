package io.github.pauljamescleary.petstore

import cats.Monad
import cats.effect.{IO, Resource}
//import derevo.derive
import A._
import tofu._
import tofu.data.derived.ContextEmbed
import tofu.env._
import tofu.higherKind.RepresentableK
import tofu.higherKind.derived._
import tofu.optics._
import tofu.optics.macros._

case class HttpConfig(url: String)

case class DBConfig(connectionAddress: String)

case class KafkaConfig(url: String, pass: String)

//@derive(representableK)
trait HttpClient[F[_]] {
  def send(request: String): F[String]
}
object HttpClient extends ContextEmbed[HttpClient] {
  def apply[I[_]: Monad, F[_]: Monad](cfg: HttpConfig): I[HttpClient[F]] = ???
  implicit val representableK: RepresentableK[HttpClient] = genRepresentableK

}

//@derive(representableK)
trait DB[F[_]] {
  def get(key: Int): F[String]
}
object DB extends ContextEmbed[DB] {
  def apply[I[_]: Monad, F[_]: Monad](cfg: DBConfig): I[DB[F]] = ???
  implicit val representableK: RepresentableK[DB] = genRepresentableK

}

//@derive(representableK)
trait Kafka[F[_]] {
  def send[A](topic: String, body: A): F[Unit]
}
object Kafka extends ContextEmbed[Kafka] {
  def apply[I[_]: Monad, F[_]: Monad](cfg: KafkaConfig): I[Kafka[F]] = ???
  implicit val representableK: RepresentableK[Kafka] = genRepresentableK

}

@ClassyOptics
case class Config(
                   http: HttpConfig,
                   db: DBConfig,
                   kafka: KafkaConfig
                 )
object Config {
  def apply[I[_]: Monad]: I[Config] = ???
}

@ClassyOptics
case class Environment(
                        @promote config: Config,
                        httpClient: HttpClient[App],
                        database: DB[App],
                        kafka: Kafka[App],
                        security: Security[App],
                        profile: Profile[App],
                      )

object Environment {
  implicit def appSubContext[C](implicit e: Environment Contains C): App WithLocal C =
    WithLocal[App, Environment].subcontext(e)

  implicit val containsConfig: Contains[Environment, Config] = ???
  implicit val containsDB: Contains[Environment, DB[App]] = ???
  implicit val containsProfile: Contains[Environment, Profile[App]] = ???
  implicit val containsSecurity: Contains[Environment, Security[App]] = ???
  implicit val containsKafka: Contains[Environment, Kafka[App]] = ???
  implicit val containsHttp: Contains[Environment, HttpClient[App]] = ???
}

case class User(name: String)

//@derive(representableK)
trait Security[F[_]] {
  def authenticate(login: Login): F[User]

  def checkAuth(user: User, op: Operation): F[Boolean]
}
object Security extends ContextEmbed[Security]{
  implicit val representableK: RepresentableK[Security] = genRepresentableK

}

//@derive(representableK)
trait Profile[F[_]] {
  def profile(what: String): F[Unit]
}
object Profile extends ContextEmbed[Profile] {
  implicit val representableK: RepresentableK[Profile] = genRepresentableK
}

object DBSecurity {
  def apply[I[_]: Monad, F[_]: Monad: DB: Profile]: I[Security[F]] = ???
}

object DBProfile {
  def apply[I[_]: Monad, F[_]: Monad: DB: Security]: I[Profile[F]] = ???
}

object A {
  type App[+A]   = Env[Environment, A]
  type Login     = String
  type Operation = String
  type Init[+A]  = Resource[IO, A]
  def init: Init[Environment] =
    for {
      config     <- Config[Init]
      security   <- DBSecurity[Init, App]
      httpClient <- HttpClient[Init, App](config.http)
      db         <- DB[Init, App](config.db)
      profiles   <- DBProfile[Init, App]
      kafka      <- Kafka[Init, App](config.kafka)
    } yield Environment(
      config = config,
      httpClient = httpClient,
      database = db,
      kafka = kafka,
      security = security,
      profile = profiles,
    )

  def main(args: Array[String]): Unit = {}
}
