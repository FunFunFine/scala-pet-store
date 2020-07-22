package io.github.pauljamescleary.petstore

import cats.effect.{ConcurrentEffect, Resource, Timer}
import doobie.Transactor
import doobie.util.ExecutionContexts
import io.github.pauljamescleary.petstore.config.PetStoreConfig
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
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.{Router, Server}
import tsec.authentication.SecuredRequestHandler
import tsec.mac.jca.HMACSHA256
import tsec.passwordhashers.jca.BCrypt

object Http {

  def mkServer[F[_]: ConcurrentEffect: Timer](
      xa: Transactor[F],
      userRepository: UserRepositoryAlgebra[F],
      petService: PetService[F],
      userService: UserService[F],
      orderService: OrderService[F],
      config: PetStoreConfig,
  ): Resource[F, Server[F]] =
    for {
      serverEc <- ExecutionContexts.cachedThreadPool[F]
      key <- Resource.liftF(HMACSHA256.generateKey[F])
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
      server <- BlazeServerBuilder[F](serverEc)
        .bindHttp(config.server.port, config.server.host)
        .withHttpApp(httpApp)
        .resource
    } yield server

}
