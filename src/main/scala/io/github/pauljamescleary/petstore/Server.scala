package io.github.pauljamescleary.petstore

import cats.Monad
import config._
import domain.users._
import domain.orders._
import domain.pets._
import infrastructure.endpoint._
import infrastructure.repository.doobie.{
  DoobieAuthRepositoryInterpreter,
  DoobieOrderRepositoryInterpreter,
  DoobiePetRepositoryInterpreter,
  DoobieUserRepositoryInterpreter,
}
import cats.effect._
import org.http4s.server.{Router, Server => H4Server}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.implicits._
import tsec.passwordhashers.jca.BCrypt
import doobie.util.ExecutionContexts
import io.circe.config.parser
import domain.authentication.Auth
import doobie.hikari.HikariTransactor
import doobie.util.transactor.Transactor
import tsec.authentication.SecuredRequestHandler
import tsec.mac.jca.HMACSHA256
import tofu.optics.Contains
import tofu.optics.macros._
import tofu.syntax.context._
import tofu.{HasContext, WithRun}
import tofu.syntax.context._
import tofu.syntax.monadic._
import tofu.syntax.lift._

import scala.concurrent.ExecutionContext
import io.estatico.newtype.macros.newtype
import tofu.lift.Lift

object Server extends IOApp {

  final case class Executors(
      serverEC: ExecutionContext,
      connectionEC: ExecutionContext,
      transactionEC: ExecutionContext,
  )

  def createServer[F[_]: ContextShift: ConcurrentEffect: Timer]: Resource[F, H4Server[F]] =
    for {
      conf <- Resource.liftF(parser.decodePathF[F, PetStoreConfig]("petstore"))
      serverEc <- ExecutionContexts.cachedThreadPool[F]
      connEc <- ExecutionContexts.fixedThreadPool[F](conf.db.connections.poolSize)
      txnEc <- ExecutionContexts.cachedThreadPool[F]
      xa <- DatabaseConfig.dbTransactor(conf.db, connEc, Blocker.liftExecutionContext(txnEc))
      key <- Resource.liftF(HMACSHA256.generateKey[F])
      authRepo = DoobieAuthRepositoryInterpreter[F, HMACSHA256](key, xa)
      petRepo = DoobiePetRepositoryInterpreter[F](xa)
      orderRepo = DoobieOrderRepositoryInterpreter[F](xa)
      userRepo = DoobieUserRepositoryInterpreter[F](xa)
      petValidation = PetValidationInterpreter[F](petRepo)
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
      server <- BlazeServerBuilder[F](serverEc)
        .bindHttp(conf.server.port, conf.server.host)
        .withHttpApp(httpApp)
        .resource
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
