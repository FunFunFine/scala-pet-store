package io.github.pauljamescleary.petstore

import cats.effect.Sync
import doobie.Transactor
import io.github.pauljamescleary.petstore.domain.authentication.Auth
import io.github.pauljamescleary.petstore.domain.orders.OrderService
import io.github.pauljamescleary.petstore.domain.pets.PetService
import io.github.pauljamescleary.petstore.domain.users.{UserRepositoryAlgebra, UserService}
import io.github.pauljamescleary.petstore.infrastructure.endpoint.{
  OrderEndpoints,
  PetEndpoints,
  UserEndpoints,
}
import io.github.pauljamescleary.petstore.infrastructure.repository.doobie.DoobieAuthRepositoryInterpreter
import org.http4s.HttpApp
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.Router
import tofu.HasContext
import tofu.syntax.monadic._
import tsec.authentication.SecuredRequestHandler
import tsec.mac.jca.HMACSHA256
import tsec.passwordhashers.jca.BCrypt

object Http {

  def mkApp[F[_]: Sync](xa: Transactor[F])(
      implicit
      userRepository: UserRepositoryAlgebra[F],
      hasPetService: F HasContext PetService[F],
      hasUserService: F HasContext UserService[F],
      hasOrderService: F HasContext OrderService[F],
  ): F[HttpApp[F]] =
    for {
      key <- HMACSHA256.generateKey[F]
      petService <- hasPetService.context
      userService <- hasUserService.context
      orderService <- hasOrderService.context
      authRepo = DoobieAuthRepositoryInterpreter[F, HMACSHA256](key, xa)
      authenticator = Auth.jwtAuthenticator[F, HMACSHA256](key, authRepo, userRepository)
      routeAuth = SecuredRequestHandler.apply(authenticator)

      httpApp = Router(
        "/users" -> UserEndpoints
          .endpoints[F, BCrypt, HMACSHA256](
            userService,
            BCrypt.syncPasswordHasher[F],
            routeAuth,
          ),
        "/pets" -> PetEndpoints.endpoints[F, HMACSHA256](petService, routeAuth),
        "/orders" -> OrderEndpoints.endpoints[F, HMACSHA256](orderService, routeAuth),
      ).orNotFound

    } yield httpApp

}
