package io.github.pauljamescleary.petstore

import io.github.pauljamescleary.petstore.domain.orders.{OrderRepositoryAlgebra, OrderService}
import io.github.pauljamescleary.petstore.domain.pets.{
  PetRepositoryAlgebra,
  PetService,
  PetValidation,
}
import io.github.pauljamescleary.petstore.domain.users.{
  UserRepositoryAlgebra,
  UserService,
  UserValidation,
}
import tofu.WithLocal
import tofu.optics.Contains
import tofu.optics.macros.ClassyOptics

@ClassyOptics
final case class Environment[F[_]](
    petRepository: PetRepositoryAlgebra[F],
    orderRepository: OrderRepositoryAlgebra[F],
    userRepository: UserRepositoryAlgebra[F],
    petValidation: PetValidation[F],
    petService: PetService[F],
    userValidation: UserValidation[F],
    orderService: OrderService[F],
    userService: UserService[F],
)

object Environment {
  implicit def appSubContext[F[_], C](
      implicit e: Environment[F] Contains C,
      wl: WithLocal[F, Environment[F]],
  ): F WithLocal C =
    WithLocal[F, Environment[F]].subcontext(e)

}
