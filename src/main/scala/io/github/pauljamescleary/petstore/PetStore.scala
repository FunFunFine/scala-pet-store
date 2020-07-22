package io.github.pauljamescleary.petstore

import cats.effect.{Resource, _}
import doobie.util.transactor.Transactor
import io.circe.config.parser
import io.github.pauljamescleary.petstore.config._
import io.github.pauljamescleary.petstore.domain.orders._
import io.github.pauljamescleary.petstore.domain.pets._
import io.github.pauljamescleary.petstore.domain.users._
import io.github.pauljamescleary.petstore.infrastructure.repository.doobie.{DoobieOrderRepositoryInterpreter, DoobiePetRepositoryInterpreter, DoobieUserRepositoryInterpreter}
import monix.eval.{Task, TaskApp}
import tofu.WithLocal
import tofu.env.Env
import tofu.optics.Contains
//import tofu.syntax.monadic._
import tofu.optics.macros._

object PetStore extends TaskApp {

  type App[+A] = Env[Environment, A]
  type Init[+A] = Resource[Task, A]

  @ClassyOptics
  final case class Environment(
      @promote config: PetStoreConfig,
      xa: Transactor[App],
      petRepository: PetRepositoryAlgebra[App],
      orderRepository: OrderRepositoryAlgebra[App],
      userRepository: UserRepositoryAlgebra[App],
      petValidation: PetValidation[App],
      petService: PetService[App],
      userValidation: UserValidation[App],
      orderService: OrderService[App],
      userService: UserService[App]
  )

  object Environment {
    implicit def subContext[C](implicit e: Environment Contains C): App WithLocal C = //WithContext aka HasContext
      WithLocal[App, Environment].subcontext(e)
  }
  def init: Transactor[App] => Init[Environment] = implicit xa =>
    for {
      conf <- parser.decodePathF[Init, PetStoreConfig]("petstore")
      petRepo = DoobiePetRepositoryInterpreter.make[App]
      orderRepo = DoobieOrderRepositoryInterpreter.make[App]
      userRepo = DoobieUserRepositoryInterpreter.make[App]
      petValidation = PetValidation.make[App]
      petService = PetService.make[App] //f
      userValidation = UserValidation.make[App]
      orderService = OrderService.make[App] //f
      userService = UserService.make[App] //f
    } yield Environment(
      conf,
      xa,
      petRepo,
      orderRepo,
      userRepo,
      petValidation,
      petService,
      userValidation,
      orderService,
      userService
    )
  override def run(args: List[String]): Task[ExitCode] = ??? //{

//    Env.apply[Environment, Unit](env => )
//    (for {
  //auth <- Authenticate.makeHMACSHA256[App]

  //      env <- init // Resource[Task, *]
//      server <- Http.mkServer[Task](env.httpApp.mapK(Lambda[App ~> Task](app => app.run(env))).app, env.config)
//      _ <- Resource.liftF(DatabaseConfig.initializeDb[Task](env.config.db))
//    } yield server)
//      .use(_ => Task.never[Unit].map(_ => ExitCode.Success))
//  }
}
