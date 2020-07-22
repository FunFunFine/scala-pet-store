package io.github.pauljamescleary.petstore

import cats.effect.{Resource, _}
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
import tofu.WithLocal
import tofu.env.Env
import tofu.optics.Contains
//import tofu.syntax.monadic._
import tofu.optics.macros._

object PetStore extends TaskApp {

  type App[+A] = Env[Environment, A]
  type Initiate[+A] = Resource[Task, A]

  case class Infrastructure[F[_]](config: PetStoreConfig, xa: Transactor[F])
  @ClassyOptics
  final case class Environment(
      petRepository: PetRepositoryAlgebra[App],
      orderRepository: OrderRepositoryAlgebra[App],
      userRepository: UserRepositoryAlgebra[App],
      petValidation: PetValidation[App],
      petService: PetService[App],
      userValidation: UserValidation[App],
      orderService: OrderService[App],
      userService: UserService[App],
  )

  object Environment {
    implicit def subContext[C](implicit e: Environment Contains C): App WithLocal C = //WithContext aka HasContext
      WithLocal[App, Environment].subcontext(e)
  }
  def init(implicit xa: Transactor[App]): Environment =
    Environment(
      petRepository = DoobiePetRepositoryInterpreter.make[App],
      orderRepository = DoobieOrderRepositoryInterpreter.make[App],
      userRepository = DoobieUserRepositoryInterpreter.make[App],
      petValidation = PetValidation.make[App],
      petService = PetService.make[App], //f
      userValidation = UserValidation.make[App],
      orderService = OrderService.make[App], //f
      userService = UserService.make[App], //f
    )


  override def run(args: List[String]): Task[ExitCode] =
    (for {
      conf <- Resource.liftF[Task, PetStoreConfig](
        parser.decodePathF[Task, PetStoreConfig]("petstore"),
      )
      xa <- MkTransactor.make[Task, App](conf.db)
    } yield xa).use(_ => Task.never[Unit].map(_ => ExitCode.Success))


}
