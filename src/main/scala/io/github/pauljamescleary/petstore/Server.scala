package io.github.pauljamescleary.petstore

import cats.Monad
import config._
import domain.users._
import domain.orders._
import domain.pets._
import infrastructure.endpoint._
import infrastructure.repository.doobie.{DoobieAuthRepositoryInterpreter, DoobieOrderRepositoryInterpreter, DoobiePetRepositoryInterpreter, DoobieUserRepositoryInterpreter}
import cats.effect._
import org.http4s.server.{Router, Server => H4Server}
import org.http4s.server.blaze.BlazeServerBuilder
import tsec.passwordhashers.jca.BCrypt
import doobie.util.ExecutionContexts
import io.circe.config.parser
import domain.authentication.Auth
import doobie.util.transactor.Transactor
import io.github.pauljamescleary.petstore.A.App
import io.github.pauljamescleary.petstore.Server.{App, Environment1}
import tofu.env.Env
import tsec.authentication.SecuredRequestHandler
import tsec.mac.jca.HMACSHA256
import tofu.optics.macros._
import tofu.syntax.context._
import tofu.{HasContext, WithLocal}
import tofu.syntax.monadic._
import tofu.syntax.lift._

import scala.concurrent.ExecutionContext
import tofu.lift.Lift
import tofu.optics.{Contains, Label}


object PetStore extends IOApp {
  type App[+A] = Env[Environment, A]
  @ClassyOptics
  final case class Environment(

                              )

  object Environment {
    implicit def subContext[C](implicit e: Environment Contains C): App WithLocal C = //WithContext aka HasContext
      WithLocal[App, Environment].subcontext(e)
  }

  override def run(args: List[String]): IO[ExitCode] = ???
}




object Server extends IOApp {

  @ClassyOptics
  type App[+A] = Env[Environment1, A]
  final case class Environment1(a: Int)




  def createServer[I[+_]: Lift[Resource[F, *], *[_]]: Sync, F[_]: ContextShift: ConcurrentEffect: Timer]
      : Resource[F, H4Server[F]] =
    for {
      conf <- parser.decodePathF[I, PetStoreConfig]("petstore")

      xa <- makeTransactor[I, F]
      key <- HMACSHA256.generateKey[I]
      authRepo = DoobieAuthRepositoryInterpreter[I, F, HMACSHA256](key)
      petRepo = DoobiePetRepositoryInterpreter[I, F]
      orderRepo = DoobieOrderRepositoryInterpreter[I, F]
      userRepo = DoobieUserRepositoryInterpreter[I, F]
      petValidation = PetValidationInterpreter[I, F](petRepo)
      petService = PetService[F](petRepo, petValidation)
      userValidation = UserValidationInterpreter[F](userRepo)
      orderService = OrderService[F](orderRepo)
      userService = UserService[F](userRepo, userValidation)
      authenticator = Auth.jwtAuthenticator[F, HMACSHA256](key, authRepo, userRepo)
      routeAuth = SecuredRequestHandler(authenticator)
      httpApp = Router(
        "/users" -> UserEndpoints
          .endpoints[F, BCrypt, HMACSHA256](userService, BCrypt.syncPasswordHasher[F], routeAuth),
        "/pets" -> PetEndpoints.endpoints[F, HMACSHA256](petService, routeAuth),
        "/orders" -> OrderEndpoints.endpoints[F, HMACSHA256](orderService, routeAuth),
      ).orNotFound
      _ <- Resource.liftF(DatabaseConfig.initializeDb(conf.db))
      serverEc <- ExecutionContexts.cachedThreadPool[F].lift[I]
      server <- BlazeServerBuilder[F](serverEc)
        .bindHttp(conf.server.port, conf.server.host)
        .withHttpApp(httpApp)
        .resource
    } yield server

  def makeHttpApp[F[_]: Sync] = Router(
    "/users" -> UserEndpoints
      .endpoints[F, BCrypt, HMACSHA256](userService, BCrypt.syncPasswordHasher[F], routeAuth),
    "/pets" -> PetEndpoints.endpoints[F, HMACSHA256](petService, routeAuth),
    "/orders" -> OrderEndpoints.endpoints[F, HMACSHA256](orderService, routeAuth),
  ).orNotFound

  def makeServer[I[_]: Monad: Lift[Resource[F, *], *[_]], F[_]:Sync](conf: ServerConfig) = for {
    serverEc <- ExecutionContexts.cachedThreadPool[F].lift[I]
    server <- BlazeServerBuilder[F](serverEc)
      .bindHttp(conf.port, conf.host)
      .withHttpApp(httpApp)
      .resource.lift[I]
  } yield server

  def makeTransactor[I[_]: Monad: Lift[Resource[F, *], *[_]], F[_]: Async: ContextShift](
      implicit config: I HasContext PetStoreConfig,
  ): I[Transactor[F]] =
    for {
      conf <- context[I]
      connEc <- ExecutionContexts.fixedThreadPool[F](conf.db.connections.poolSize).lift[I]
      txnEc <- ExecutionContexts.cachedThreadPool[F].lift[I]
      xa <- DatabaseConfig.dbTransactor[I, F](conf.db, connEc, Blocker.liftExecutionContext(txnEc))
    } yield xa

  def run(args: List[String]): IO[ExitCode] = createServer.use(_ => IO.never).as(ExitCode.Success)
}
