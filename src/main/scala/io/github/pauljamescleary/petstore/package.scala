package io.github.pauljamescleary.petstore

import cats.Monad
import cats.effect.{IO, Resource}
import io.github.pauljamescleary.petstore.A._
//import derevo.derive
import A._
import tofu.syntax.monadic._
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

//@ClassyOptics
case class Config(
    http: HttpConfig,
    db: DBConfig,
    kafka: KafkaConfig,
)
object Config {
  def apply[I[_]: Monad]: I[Config] = ???
  implicit val containsHttpConfig: Contains[Config, HttpConfig] with Label["httpConfig"] =
    GenContains[Config](_.http).label["httpConfig"]
  implicit val containsDbConfig: Contains[Config, DBConfig] with Label["dbConfig"] =
    GenContains[Config](_.db).label["dbConfig"]
  implicit val containsKafkaConfig: Contains[Config, KafkaConfig] with Label["kafkaConfig"] =
    GenContains[Config](_.kafka).label["kafkaConfig"]
}

//@ClassyOptics
case class Environment[F[_]](
    @promote config: Config,
    httpClient: HttpClient[F],
    database: DB[F],
    kafka: Kafka[F],
    security: Security[F],
    profile: Profile[F],
)
trait EnvLenses[F[_]] {
  implicit def appSubContext[C](
      implicit e: Environment[F] Contains C,
      local: WithLocal[F, Environment[F]],
  ): F WithLocal C = //WithContext aka HasContext
    WithLocal[F, Environment[F]].subcontext(e)

  implicit val containsConfig: Contains[Environment[F], Config] with Label["config"] =
    GenContains[Environment[F]](_.config).label["config"]
  implicit val containsDB: Contains[Environment[F], DB[F]] with Label["db"] =
    GenContains[Environment[F]](_.database).label["db"]
  implicit val containsProfile: Contains[Environment[F], Profile[F]] with Label["profile"] =
    GenContains[Environment[F]](_.profile).label["profile"]
  implicit val containsSecurity: Contains[Environment[F], Security[F]] with Label["security"] =
    GenContains[Environment[F]](_.security).label["security"]
  implicit val containsKafka: Contains[Environment[F], Kafka[F]] with Label["kafka"] =
    GenContains[Environment[F]](_.kafka).label["kafka"]
  implicit val anton: Contains[Environment[F], HttpClient[F]] with Label["httpClient"] =
    GenContains[Environment[F]](_.httpClient).label["httpClient"]
}

object Environment {
  implicit def appSubContext[C](implicit e: Environment[App] Contains C): App WithLocal C = //WithContext aka HasContext
    WithLocal[App, Environment[App]].subcontext(e)

  implicit val containsConfig: Contains[Environment[App], Config] with Label["config"] =
    GenContains[Environment[App]](_.config).label["config"]
  implicit val containsDB: Contains[Environment[App], DB[App]] with Label["db"] =
    GenContains[Environment[App]](_.database).label["db"]
  implicit val containsProfile: Contains[Environment[App], Profile[App]] with Label["profile"] =
    GenContains[Environment[App]](_.profile).label["profile"]
  implicit val containsSecurity: Contains[Environment[App], Security[App]] with Label["security"] =
    GenContains[Environment[App]](_.security).label["security"]
  implicit val containsKafka: Contains[Environment[App], Kafka[App]] with Label["kafka"] =
    GenContains[Environment[App]](_.kafka).label["kafka"]
  implicit val anton: Contains[Environment[App], HttpClient[App]] with Label["httpClient"] =
    GenContains[Environment[App]](_.httpClient).label["httpClient"]
}

case class User(name: String)

//@derive(representableK)
trait Security[F[_]] {
  def authenticate(login: Login): F[User]

  def checkAuth(user: User, op: Operation): F[Boolean]
}
object Security extends ContextEmbed[Security] {
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
  def apply[I[_], F[_]](
      implicit im: Monad[I],
      fm: Monad[F],
      db: DB[F],
      profile: Profile[F],
  ): I[Security[F]] = ???
}

object DBProfile {
  def apply[I[_]: Monad, F[_]: Monad: DB: Security]: I[Profile[F]] = ???
}

class Application[I[_]: Monad, F[_]: Monad] extends EnvLenses[F] {
  def init: I[Environment[F]] =
    for {
      config <- Config[I]
      security <- DBSecurity[I, F](
        implicitly[Monad[I]],
        implicitly[Monad[F]],
        DB.contextEmbed(
          implicitly[Monad[F]],
          Environment.appSubContext(Environment.containsDB),
          DB.representableK,
        ),
        Profile.contextEmbed(
          implicitly[Monad[F]],
          Environment.appSubContext(Environment.containsProfile),
          Profile.representableK, /*эмбед*/
        ),
      )
      httpClient <- HttpClient[I, F](config.http)
      db <- DB[I, F](config.db)
      profiles <- DBProfile[I, F]
      kafka <- Kafka[I, F](config.kafka)
    } yield Environment(
      config = config,
      httpClient = httpClient,
      database = db,
      kafka = kafka,
      security = security,
      profile = profiles,
    )

}

object A {
  type App[+A] = Env[Environment[App], A]
  type Login = String
  type Operation = String
  type Init[+A] = Resource[IO, A]
  def init: Init[Environment[App]] =
    for {
      config <- Config[Init]
      ctx = implicitly[HasContext[App, Profile[App]]]
      security <- DBSecurity[Init, App](
        implicitly[Monad[Init]],
        implicitly[Monad[App]],
        DB.contextEmbed(
          implicitly[Monad[App]],
          Environment.appSubContext(Environment.containsDB),
          DB.representableK,
        ),
        Profile.contextEmbed(
          implicitly[Monad[App]],
          Environment.appSubContext(Environment.containsProfile),
          Profile.representableK, /*эмбед*/
        ),
      )
      httpClient <- HttpClient[Init, App](config.http)
      db <- DB[Init, App](config.db)
      profiles <- DBProfile[Init, App]
      kafka <- Kafka[Init, App](config.kafka)
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
