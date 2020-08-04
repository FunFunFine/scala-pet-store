package io.github.pauljamescleary.petstore

import cats.Monad
import cats.data.Kleisli
import cats.effect._
import doobie.ExecutionContexts
import doobie.util.transactor.Transactor
import io.circe.config.parser
import io.github.pauljamescleary.petstore.config._
import io.github.pauljamescleary.petstore.domain.orders._
import io.github.pauljamescleary.petstore.domain.pets._
import io.github.pauljamescleary.petstore.domain.users._
import io.github.pauljamescleary.petstore.infrastructure.repository.doobie.{
  DoobieOrderRepositoryInterpreter,
  DoobiePetRepositoryInterpreter,
  DoobieUserRepositoryInterpreter,
}
import monix.eval.{Task, TaskApp}
import org.http4s.HttpApp
import org.http4s.server.blaze.BlazeServerBuilder
import tofu.WithRun
import tofu.concurrent.ContextT
import tofu.lift.Lift
import tofu.syntax.context.runContext
import tofu.syntax.funk.funK
import tofu.syntax.monadic._

import scala.concurrent.ExecutionContext
case class Application[F[_]](
    config: PetStoreConfig,
    transactor: Transactor[F],
    environment: Environment[F],
    serverEc: ExecutionContext,
)
object PetStore extends TaskApp {

  type Init[+A] = Task[A]
  type App[+A] = ContextT[Task, Environment, A]

  def run(args: List[String]): Task[ExitCode] = createApplication.use[Task, ExitCode] {
    case Application(config, transactor, environment, serverEc) =>
      Http
        .mkApp[App](transactor)
        .run(environment)
        .flatMap((app: HttpApp[App]) =>
          runServer[Init](config.server, liftApp[App, Init](app, environment), serverEc)
            .as(ExitCode.Success),
        )
  }

  def createApplication: Resource[Init, Application[App]] =
    for {
      conf <- Resource.liftF(parser.decodePathF[Init, PetStoreConfig]("petstore"))
      xa <- MkTransactor.make[Init, App](conf.db)
      env = initEnvironment(xa)
      ec <- ExecutionContexts.cachedThreadPool[Init]
    } yield Application(conf, xa, env, ec)

  def initEnvironment(implicit transactor: Transactor[App]): Environment[App] =
    Environment(
      petRepository = DoobiePetRepositoryInterpreter.make[App],
      orderRepository = DoobieOrderRepositoryInterpreter.make[App],
      userRepository = DoobieUserRepositoryInterpreter.make[App],
      petValidation = PetValidation.make[App],
      petService = PetService.make[App],
      userValidation = UserValidation.make[App],
      orderService = OrderService.make[App],
      userService = UserService.make[App],
    )

  def liftApp[F[_], G[_]: Monad](
      http: HttpApp[F],
      env: Environment[F],
  )(implicit L: Lift[G, F], wr: WithRun[F, G, Environment[F]]): HttpApp[G] = Kleisli { req =>
    for {
      responseF <- runContext(http(req.mapK(L.liftF)))(env)
      responseI = responseF.mapK(funK(fa => runContext(fa)(env)))
    } yield responseI
  }

  def runServer[F[_]: ConcurrentEffect: Timer](
      config: ServerConfig,
      app: HttpApp[F],
      ec: ExecutionContext,
  ): F[Unit] =
    BlazeServerBuilder
      .apply[F](ec)
      .bindHttp(config.port, config.host)
      .withHttpApp(app)
      .serve
      .compile
      .drain

}
